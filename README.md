# ATX - Release Readiness Review Integration Demo

Demo application showcasing the integration between AWS Transform (ATX) and the Release Readiness Review via a client-side skill.

## Overview

When ATX performs code transformations, the Release Readiness Review skill acts as a quality gate before completion. After ATX finishes its changes and validates the build, the skill instructs it to:

1. Create a draft PR in GitHub
2. Trigger a Release Readiness Review via the DevOps Agent API
3. Wait for the structured risk report
4. Iterate on critical findings, then complete

## Client-Side Skill

The skill lives at `.aws/atx/skills/release-readiness-review/` in each target repo:

```
.aws/atx/skills/release-readiness-review/
├── SKILL.md              # Instructions for ATX (when/how to invoke the Release Readiness Review)
└── scripts/
    └── run_prr.sh        # Calls DevOps Agent APIs via AWS CLI
```

### How it works

`run_prr.sh` performs the Option A (API-driven) flow:

1. `list-associations` - verifies the repo is associated with the AgentSpace
2. `create-backlog-task` - triggers the review (task type `CHANGE_REVIEW`) with the PR number
3. `get-backlog-task` - polls until COMPLETED/FAILED
4. `list-executions` - gets the execution ID
5. `list-journal-records --record-type release_analysis_report` - fetches the risk report

### Configuration

| Env var | CLI flag | Description |
|---------|----------|-------------|
| `AGENT_SPACE_ARN` | `--agent-space-arn` | AgentSpace ARN (constant per customer) |
| `AWS_REGION` | `--region` | AWS region (defaults to us-east-1) |
| `PRR_AWS_PROFILE` | `--profile` | Optional AWS profile; uses default credentials if unset |
| — | `--repository` | GitHub org/repo (GitLab: project path); per-invocation, CLI only |
| — | `--pr-number` | PR number (GitLab: MR iid); per-invocation, CLI only |

### Prerequisites

- The Release Readiness Review (DevOps Agent) is currently **only available in `us-east-1`**. Region defaults to us-east-1; do not set another region.
- AWS credentials with `aidevops:CreateBacklogTask`, `GetBacklogTask`, `ListExecutions`, `ListJournalRecords`, `ListAssociations` permissions
- AgentSpace with the target repository associated (GitHub association)
- `gh` or `glab` CLI authenticated (for draft PR creation)
- `jq` installed

## Demo Repos

- `8th-final-dodo-backend` - Test repo with skill installed (tested with `AWS/java-aws-sdk-v1-to-v2` transformation)

## Quick Start

```bash
AWS_REGION=us-east-1 AGENT_SPACE_ARN=arn:aws:aidevops:us-east-1:588148762356:agent-space/d6b49980-a10d-49ca-a6d5-fb1ffe72a078 atx custom def exec -n AWS/java-aws-sdk-v1-to-v2 -p . -c "mvn clean test" -x -t
```

## Running

```bash
# Set constants
export AGENT_SPACE_ARN="<YOUR_DEVOPS_AGENT_SPACE_ARN"
export AWS_REGION=us-east-1

# Run ATX transformation (skill is auto-discovered)
atx custom def exec -n AWS/java-aws-sdk-v1-to-v2 -p /path/to/repo -c "mvn clean test" -x -t

# Or invoke the skill script directly for testing
bash .aws/atx/skills/release-readiness-review/scripts/run_prr.sh \
  --repository "simha-dev/fake-aws-codebuild-jenkins-plugin" \
  --pr-number "1"
```
