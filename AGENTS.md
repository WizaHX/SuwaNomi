# Working on SuwaNomi

These instructions apply to this repository. Read any more specific `AGENTS.md` files in the directories you change. Follow the user's current instructions when they revise project decisions.

## Project context

SuwaNomi is a personal customization of Suwayomi-Server, intended to run through Docker on a Raspberry Pi. Desktop packaging and general-purpose upstream compatibility are not project goals. Use `Docs/docker-deployment.md` for the recorded hardware and architecture verification status.

- Read `README.md` for the project overview.
- Read `Docs/progress.md` before starting work for the current state, evidence, and next steps. Verify its Git observations against the live checkout.
- Read `Docs/repository-setup.md` for repository layout and upstream update procedures.
- Read `Docs/docker-deployment.md` for deployment decisions.
- For migration work, read `Docs/same-id-migration-plan.md`. Keep feature-specific requirements there rather than duplicating them here.

## Scope and workflow

- Match the requested scope. Planning or inspection requests do not authorize application implementation.
- Inspect relevant code before choosing a design. Distinguish verified behavior from assumptions and unresolved decisions.
- Make focused changes using existing conventions; avoid unrelated refactoring, dependency upgrades, and formatting churn.
- This is a server customization. Do not introduce a separate WebUI/site project or require a custom WebUI build for the planned migration feature.
- Proceed with routine, reversible work within the request. Ask only when missing information materially blocks the work or changes its scope.
- Keep documentation current: repository notes, deployment notes, and feature plans belong in their respective documents.
- Update `Docs/progress.md` after meaningful milestones and before ending a work session. Record completed and unfinished work, validation results, blockers, and the exact next step. Include relevant file paths or commit hashes as evidence; distinguish uncommitted changes from commits and local commits from verified pushes.
- Keep the progress file a concise current handoff, not a transcript. Replace stale state, retain useful findings, and link to plans for requirements and design decisions. A next-step entry is not authorization beyond the user's request.
- Report what changed, what was checked, and any remaining limitations. Never claim unrun tests passed.

## Git and imported source

- Run `git status` before editing. Preserve existing user changes and check the current branch rather than assuming `main`.
- `suwa-server/` is a Git subtree, not a separate repository or submodule. Work from the top-level repository and preserve upstream history and license notices.
- Do not pull upstream, rewrite history, change remotes, commit, push, or publish as an incidental part of another task. Do so when requested or included in the agreed workflow.
- Local remote-tracking refs do not prove current GitHub state. Verify remote state when needed and report authentication failures accurately.
- Upstream contribution instructions about contacting maintainers or opening upstream PRs are not required for this personal project.

## Implementation and validation

- Run Gradle commands from `suwa-server/` using its wrapper. Check the current build files and CI for the required Java version; older prose documentation may be stale.
- Useful existing commands:
  - `./gradlew :server:test --tests '<fully.qualified.TestClass>'` for a focused test class.
  - `./gradlew :server:test` for server tests.
  - `./gradlew ktlintCheck` for Kotlin style checks.
  - `./gradlew :server:shadowJar` for the server artifact.
- Choose checks appropriate to the change. Documentation-only edits need link/content review and `git diff --check`, not an application build.
- Kotlin compilation currently depends on `ktlintFormat`; inspect the diff after builds for incidental formatting changes and keep unrelated changes out of the result.
- Use disposable databases, storage directories, and containers for migration or destructive-path testing. Never test cleanup against the real library.
- Keep secrets, tokens, personal configuration, databases, downloads, and generated build artifacts out of Git.
- Inspect the final diff before reporting completion. Leave application code unchanged when the task only concerns documentation.
