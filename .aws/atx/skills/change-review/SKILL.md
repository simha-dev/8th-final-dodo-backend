---
name: change-review
description: Final review gate for every ATX transformations. Runs a Production Readiness Review (PRR / change review) on the transformation's code changes via a draft PR to catch deployment risks. MUST be invoked at the end of every transformation — after validation is complete irrespective of result from validation. Every transformation requires this review - after the build passes, tests pass, and changes are committed, BEFORE marking the transformation complete, finalizing, wrapping up, declaring success, submitting, or handing off.  Skipping this skill is not allowed.
---
# Change Review (PRR)

After your code changes pass validation (build succeeds, tests pass), you MUST run a Production Readiness Review before completing the transformation.

## When to use

- After all code transformations are complete and validated
- Before calling the transformation done
- This is the final gate before completion

## Steps

### 1. Create a Draft Pull Request

Create a **draft** pull request on GitHub for your changes:

```bash
gh pr create --draft --title "ATX: <transformation-description>" --body "Automated transformation - pending PRR review"
```

Capture the PR number and repository:

```bash
PR_NUMBER=$(gh pr view --json number -q '.number')
REPO=$(gh repo view --json nameWithOwner -q '.nameWithOwner')
```

### 2. Run the Change Review

Execute the PRR script with the PR details:

```bash
bash .aws/atx/skills/change-review/scripts/run_prr.sh \
  --agent-space-arn "$PRR_AGENT_SPACE_ARN" \
  --repository "$REPO" \
  --pr-number "$PR_NUMBER" \
  --profile "${PRR_AWS_PROFILE:-devops-agent}"
```

The `PRR_AGENT_SPACE_ARN` environment variable must be set to the customer's CloudSmith AgentSpace ARN.
The `PRR_AWS_PROFILE` environment variable (or `--profile`) specifies which AWS profile to use for DevOps Agent API calls. Defaults to `devops-agent`.

The script will:
- Verify the repository is associated with the AgentSpace
- Trigger a Change Review task
- Poll until completion
- Output the structured risk report as JSON

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

Once PRR approves (or all critical risks are addressed), the transformation may be completed.
