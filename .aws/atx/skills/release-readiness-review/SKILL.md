---
name: release-readiness-review
description: Final review gate for every ATX transformations. Runs a Release Readiness Review on the transformation's code changes via a draft PR to catch deployment risks. MUST be invoked at the end of every transformation — after validation is complete irrespective of result from validation. Every transformation requires this review - after the build passes, tests pass, and changes are committed, BEFORE marking the transformation complete, finalizing, wrapping up, declaring success, submitting, or handing off.
---
# Release Readiness Review

After your code changes pass validation (build succeeds, tests pass), you MUST run a Release Readiness Review before completing the transformation.

## When to use

- After all code transformations are complete and validated
- Before calling the transformation done
- This is the final gate before completion

## Steps

### 1. Create a Draft Pull Request

Create a **draft** pull request for your changes on the repository's Git provider, then note its repository (`owner/repo`) and PR number to pass to the script in step 2.

Use the command appropriate for the repo's remote. For example, if the remote is **GitHub**, use the `gh` CLI:

```bash
gh pr create --draft --title "ATX: <transformation-description>" --body "Automated transformation - pending Release Readiness Review"

PR_NUMBER=$(gh pr view --json number -q '.number')
REPO=$(gh repo view --json nameWithOwner -q '.nameWithOwner')
```

For other providers, use the equivalent command. For **GitLab**, open a draft merge request with `glab` and capture the project path and MR iid:

```bash
glab mr create --draft --title "ATX: <transformation-description>" --description "Automated transformation - pending Release Readiness Review"

PR_NUMBER=$(glab mr view --output json | jq -r '.iid')
REPO=$(glab repo view --output json | jq -r '.path_with_namespace')
```

The review script auto-detects the provider from the repo's `origin` remote, so you pass the repository and change number the same way for either provider (for GitLab, `--pr-number` is the MR iid).

### 2. Run the Release Readiness Review

Execute the Release Readiness Review script with the PR details:

```bash
bash .aws/atx/skills/release-readiness-review/scripts/run_prr.sh \
  --agent-space-arn "$AGENT_SPACE_ARN" \
  --repository "$REPO" \
  --pr-number "$PR_NUMBER"
```

The `AGENT_SPACE_ARN` environment variable must be set to the customer's CloudSmith AgentSpace ARN.

The AWS profile is **optional**: if `PRR_AWS_PROFILE` (or `--profile`) is not set, the script uses the default AWS credentials. Set it only when the AgentSpace lives in a different account/profile than the one ATX is running with (e.g. `PRR_AWS_PROFILE=devops-agent`).

The script will:
- Verify the repository is associated with the AgentSpace
- Trigger a Release Readiness Review task
- Poll until completion
- Output the structured risk report as JSON

**If the script fails for any reason** — missing setup, AgentSpace not found, no permissions, repo not associated, task error — that is **not a blocker**. Read the error it printed, tell the user what went wrong, and proceed to complete the transformation. Only a report that actually came back and recommends against deployment should make you iterate (see below).

### 3. Review the Results

The script outputs a JSON risk report. Check these fields:

- `recommendedAction`: If `Standard Deployment`, proceed to complete the transformation.
- `recommendedAction`: If anything else (e.g., `Block Deployment`), review the `risks` array.

### 4. Iterate on Findings

If the report does NOT recommend `Standard Deployment`:

1. Read each risk in the `risks` array
2. For risks with `severity: "critical"`, apply the `suggestedFix` if provided
3. For risks with `confidenceLevel: "high"`, these are real issues - fix them
4. For risks with `confidenceLevel: "low"` or `severity: "low"`, use your judgment
5. After fixing, re-run validation (build command), commit changes, update the PR, and re-run step 2

If the report recommends `Standard Deployment`, proceed to complete the transformation.

### 5. Complete

Once the Release Readiness Review approves (or all critical risks are addressed), the transformation may be completed. If the Release Readiness Review could not run, note the reason for the user and complete the transformation — a failed review never blocks completion.
