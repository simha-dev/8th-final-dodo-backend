# DevOps Agent - Release Readiness Code Review

A client-side ATX skill that runs a **Release Readiness Review** on a transformation's
code changes before the transformation is marked complete. It acts as the final quality
gate: after ATX finishes its changes and the build/tests pass, the skill submits the
changes (via a draft PR) to the DevOps Agent and surfaces any deployment risks so ATX can
iterate before handing off.

## What it does

1. Has ATX create a **draft** pull/merge request for its changes.
2. Triggers a Release Readiness Review against that change via the DevOps Agent APIs.
3. Polls until the review completes and returns a structured risk report.
4. If the report recommends anything other than `Standard Deployment`, ATX fixes the
   flagged risks, re-validates, updates the PR, and re-runs the review.

A review that **fails to run** (missing setup, no association, permissions, etc.) never
blocks the transformation - the skill reports the reason and ATX proceeds. Only a review
that actually returns risks causes ATX to iterate.

## How it works

`scripts/run_release_readiness_review.sh` performs the API-driven flow:

1. `list-associations` - verifies the repo is associated with the AgentSpace
2. `create-backlog-task` - triggers the review (task type `RELEASE_READINESS_REVIEW`)
3. `get-backlog-task` - polls until `COMPLETED`/`FAILED` (up to 45 minutes)
4. `list-executions` - resolves the execution
5. `list-journal-records --record-type release_analysis_report` - fetches the risk report

The script writes only the risk report JSON to stdout; all progress and diagnostics go to
stderr.

## Installation

Copy this folder into a location where ATX discovers skills in your repository. The
conventional location is:

```
.aws/atx/skills/devops-agent-release-readiness-code-review/
├── SKILL.md
└── scripts/
    └── run_release_readiness_review.sh
```

You may place it elsewhere - the `SKILL.md` references the script by a `<skill-dir>`
placeholder, so adjust the invocation path to wherever you install it.

## Provider support

The provider is auto-detected from the repo's `origin` remote (override with
`--provider github|gitlab`). Supported:

- **GitHub** (github.com and GitHub Enterprise Server)
- **GitLab** (gitlab.com and self-managed, including nested group paths)

For Enterprise / self-managed instances the host is taken from the association's
`instanceIdentifier`; for the public hosts it is omitted.

## Configuration

| Env var | CLI flag | Description |
|---------|----------|-------------|
| `AGENT_SPACE_ARN` | `--agent-space-arn` | DevOps AgentSpace ARN (constant per customer) |
| `AWS_REGION` | `--region` | AWS region; defaults to `us-east-1` (the only supported region) |
| `DEVOPS_AWS_PROFILE` | `--profile` | Optional AWS profile; uses default credentials if unset |
| - | `--repository` | `owner/repo` (GitHub) or full project path (GitLab); per-invocation, CLI only |
| - | `--pr-number` | PR number (GitHub) or MR iid (GitLab); per-invocation, CLI only |

## Prerequisites

- The Release Readiness Review (DevOps Agent) is currently **only available in `us-east-1`**.
- AWS credentials with `aidevops:CreateBacklogTask`, `GetBacklogTask`, `ListExecutions`,
  `ListJournalRecords`, and `ListAssociations` permissions.
- The target repository must be associated with the DevOps AgentSpace.
- `gh` (GitHub) or `glab` (GitLab) CLI authenticated, for draft PR/MR creation.
- `jq` installed.

## Reading the result

The script outputs the risk report JSON. Key field: `recommendedAction`.

- `Standard Deployment` - proceed and complete the transformation.
- Anything else - review the `risks` array, address `critical` / `high`-confidence items,
  then re-run.
