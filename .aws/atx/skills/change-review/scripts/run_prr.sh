#!/usr/bin/env bash
set -euo pipefail

# PRR (Change Review) via DevOps Agent APIs - Option A flow
# Usage: run_prr.sh --agent-space-arn ARN --repository org/repo --pr-number NUM [--region REGION] [--profile PROFILE]

REGION="${AWS_REGION:-us-east-1}"
AGENT_SPACE_ARN="${PRR_AGENT_SPACE_ARN:-}"
REPOSITORY="${PRR_REPOSITORY:-}"
PR_NUMBER="${PRR_PR_NUMBER:-}"
PROFILE="${PRR_AWS_PROFILE:-}"
POLL_INTERVAL=30
MAX_POLL_ATTEMPTS=120  # 60 min at 30s intervals

while [[ $# -gt 0 ]]; do
  case $1 in
    --agent-space-arn) AGENT_SPACE_ARN="$2"; shift 2;;
    --repository) REPOSITORY="$2"; shift 2;;
    --pr-number) PR_NUMBER="$2"; shift 2;;
    --region) REGION="$2"; shift 2;;
    --profile) PROFILE="$2"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 1;;
  esac
done

: "${AGENT_SPACE_ARN:?Required: --agent-space-arn or PRR_AGENT_SPACE_ARN env var}"
: "${REPOSITORY:?Required: --repository or PRR_REPOSITORY env var}"
: "${PR_NUMBER:?Required: --pr-number or PRR_PR_NUMBER env var}"

# Extract ID from ARN (arn:aws:devops-agent:REGION:ACCOUNT:agent-space/ID)
AGENT_SPACE_ID="${AGENT_SPACE_ARN##*/}"

# Build profile args if set
PROFILE_ARGS=()
if [[ -n "$PROFILE" ]]; then
  PROFILE_ARGS=(--profile "$PROFILE")
fi

echo "=== PRR Change Review ===" >&2
echo "AgentSpace: $AGENT_SPACE_ID" >&2
echo "Repository: $REPOSITORY" >&2
echo "PR Number:  $PR_NUMBER" >&2
[[ -n "$PROFILE" ]] && echo "Profile:    $PROFILE" >&2

# 1. Verify repo association
echo "Checking repository association..." >&2
ASSOCIATIONS=$(aws devops-agent list-associations \
  --agent-space-id "$AGENT_SPACE_ID" \
  --region "$REGION" \
  "${PROFILE_ARGS[@]}" \
  --output json)

REPO_NAME=$(echo "$REPOSITORY" | cut -d'/' -f2)
REPO_OWNER=$(echo "$REPOSITORY" | cut -d'/' -f1)

MATCH=$(echo "$ASSOCIATIONS" | jq -r --arg name "$REPO_NAME" --arg owner "$REPO_OWNER" \
  '.associations[] | select(.configuration.github.repoName == $name and .configuration.github.owner == $owner) | .associationId' 2>/dev/null || true)

if [[ -z "$MATCH" ]]; then
  echo "ERROR: Repository $REPOSITORY is not associated with AgentSpace $AGENT_SPACE_ID" >&2
  echo "Available associations:" >&2
  echo "$ASSOCIATIONS" | jq -r '.associations[]?.configuration.github | "\(.owner)/\(.repoName)"' >&2
  exit 1
fi
echo "Found association: $MATCH" >&2

# 2. Create backlog task (trigger PRR)
echo "Triggering Change Review..." >&2
DESCRIPTION=$(jq -nc --arg repo "$REPOSITORY" --arg pr "$PR_NUMBER" \
  '{agentInput: {content: {githubPrContent: [{repository: $repo, prNumber: $pr, hostname: "github.com"}]}}}')

TASK_RESPONSE=$(aws devops-agent create-backlog-task \
  --agent-space-id "$AGENT_SPACE_ID" \
  --task-type "CHANGE_REVIEW" \
  --title "PRR: ATX transformation validation" \
  --description "$DESCRIPTION" \
  --priority "HIGH" \
  --region "$REGION" \
  "${PROFILE_ARGS[@]}" \
  --output json)

TASK_ID=$(echo "$TASK_RESPONSE" | jq -r '.task.taskId')
echo "Task created: $TASK_ID" >&2

# 3. Poll until complete
echo "Polling for completion (up to ${MAX_POLL_ATTEMPTS} attempts, ${POLL_INTERVAL}s interval)..." >&2
for i in $(seq 1 $MAX_POLL_ATTEMPTS); do
  TASK_STATUS=$(aws devops-agent get-backlog-task \
    --agent-space-id "$AGENT_SPACE_ID" \
    --task-id "$TASK_ID" \
    --region "$REGION" \
    "${PROFILE_ARGS[@]}" \
    --output json)

  STATUS=$(echo "$TASK_STATUS" | jq -r '.task.status')
  echo "  [$i/$MAX_POLL_ATTEMPTS] Status: $STATUS" >&2

  case "$STATUS" in
    COMPLETED) break;;
    FAILED|TIMED_OUT)
      echo "ERROR: Task ended with status: $STATUS" >&2
      echo "$TASK_STATUS" | jq . >&2
      exit 1;;
  esac
  sleep "$POLL_INTERVAL"
done

if [[ "$STATUS" != "COMPLETED" ]]; then
  echo "ERROR: Timed out waiting for task completion" >&2
  exit 1
fi

# 4. Get execution ID
echo "Fetching execution..." >&2
EXECUTIONS=$(aws devops-agent list-executions \
  --agent-space-id "$AGENT_SPACE_ID" \
  --task-id "$TASK_ID" \
  --region "$REGION" \
  "${PROFILE_ARGS[@]}" \
  --output json)

EXECUTION_ID=$(echo "$EXECUTIONS" | jq -r '.executions[0].executionId')
echo "Execution: $EXECUTION_ID" >&2

# 5. Get risk report
echo "Fetching risk report..." >&2
JOURNAL=$(aws devops-agent list-journal-records \
  --agent-space-id "$AGENT_SPACE_ID" \
  --execution-id "$EXECUTION_ID" \
  --record-type "release_analysis_report" \
  --region "$REGION" \
  "${PROFILE_ARGS[@]}" \
  --output json)

# Output the report JSON to stdout
# The content field is a JSON string with structure: {type: "release_analysis_report", report: {...}}
REPORT=$(echo "$JOURNAL" | jq -r '.records[0].content' | jq -r '.report')
echo "$REPORT"

# Summary to stderr
ACTION=$(echo "$REPORT" | jq -r '.recommendedAction // "UNKNOWN"')
RISK_COUNT=$(echo "$REPORT" | jq -r '.risks | length // 0')
echo "" >&2
echo "=== RESULT ===" >&2
echo "Recommended Action: $ACTION" >&2
echo "Risks Found: $RISK_COUNT" >&2

if [[ "$ACTION" != "Standard Deployment" ]]; then
  echo "" >&2
  echo "Critical risks:" >&2
  echo "$REPORT" | jq -r '.risks[] | select(.severity == "critical") | "  - \(.title): \(.description)"' >&2
  exit 2  # non-zero to signal BLOCK to ATX
fi
