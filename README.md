# ATX - PRR Integration Demo

Demo application showcasing the integration between AWS Transform (ATX) and Production Readiness Review (PRR) via a client-side skill.

## Overview

When ATX performs code transformations, the PRR skill acts as a quality gate before completion. After ATX finishes its changes and validates the build, the skill instructs it to:

1. Create a draft PR in GitHub
2. Trigger a Change Review via the DevOps Agent API
3. Wait for the structured risk report
4. Iterate on critical findings, then complete

## Client-Side Skill

The skill lives at `.aws/atx/skills/change-review/` in each target repo:

```
.aws/atx/skills/change-review/
├── SKILL.md              # Instructions for ATX (when/how to invoke PRR)
└── scripts/
    └── run_prr.sh        # Calls DevOps Agent APIs via AWS CLI
```

### How it works

`run_prr.sh` performs the Option A (API-driven) flow:

1. `list-associations` - verifies the repo is associated with the AgentSpace
2. `create-backlog-task` - triggers a CHANGE_REVIEW with the PR number
3. `get-backlog-task` - polls until COMPLETED/FAILED
4. `list-executions` - gets the execution ID
5. `list-journal-records --record-type release_analysis_report` - fetches the risk report

### Configuration

| Env var | CLI override | Description |
|---------|-------------|-------------|
| `PRR_AGENT_SPACE_ARN` | `--agent-space-arn` | AgentSpace ARN (constant per customer) |
| `AWS_REGION` | `--region` | AWS region (defaults to us-east-1) |
| `PRR_REPOSITORY` | `--repository` | GitHub org/repo |
| `PRR_PR_NUMBER` | `--pr-number` | PR number to review |

### Prerequisites

- AWS credentials with `devops-agent:CreateBacklogTask`, `GetBacklogTask`, `ListExecutions`, `ListJournalRecords`, `ListAssociations` permissions
- AgentSpace with GitHub association and CHANGE_REVIEW capability
- `gh` CLI authenticated (for draft PR creation)
- `jq` installed

## Demo Repos

- `fake-aws-codebuild-jenkins-plugin` - Java/Maven Jenkins plugin (tested with `AWS/java-aws-sdk-v1-to-v2` transformation)
- `8th-final-dodo-backend` - Additional test repo with skill installed

## Quick Start

```bash
AWS_REGION=us-east-1 PRR_AGENT_SPACE_ARN=arn:aws:devops-agent:us-east-1:588148762356:agent-space/d6b49980-a10d-49ca-a6d5-fb1ffe72a078 atx custom def exec -n AWS/java-aws-sdk-v1-to-v2 -p . -c "mvn clean test" -x -t
```

## Running

```bash
# Set constants
export PRR_AGENT_SPACE_ARN="arn:aws:devops-agent:us-east-1:588148762356:agent-space/d6b49980-a10d-49ca-a6d5-fb1ffe72a078"
export AWS_REGION=us-east-1

# Run ATX transformation (skill is auto-discovered)
atx custom def exec -n AWS/java-aws-sdk-v1-to-v2 -p ./fake-aws-codebuild-jenkins-plugin -c "mvn clean test" -x -t

# Or invoke the skill script directly for testing
bash .aws/atx/skills/change-review/scripts/run_prr.sh \
  --repository "simha-dev/fake-aws-codebuild-jenkins-plugin" \
  --pr-number "1"
```
