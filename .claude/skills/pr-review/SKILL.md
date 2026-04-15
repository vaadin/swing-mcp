---
name: pr-review
description: Instructions used when reviewing a PR
---

When reviewing a PR, follow these rules: check that:

- the file placement and naming follows the current project structure
- tests are created for the PR
- Java code quality is good
- Additional rules from the `CONTRIBUTING.md` file, placed in the root of the git repo.

When grabbing a diff of the PR, use three-dotted version of git diff: diff from the merge base, not from the head of the target branch. Reason: the git branch may not be rebased on top of the target branch and you would get code removals, which is just noise.

Also check all PR reviews, there might be some already in the "request changes" state.

When this project is a GitHub project:

- You can use `gh pr diff PR_ID` to get the PR's diff
- Use `gh pr checks PR_ID` to check that all builds pass

