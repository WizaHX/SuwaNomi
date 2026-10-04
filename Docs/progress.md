# Project progress

Last updated: 2026-10-04.

This is the current handoff for continuing work. Verify the checkout before relying on recorded Git state. Requirements and design belong in the linked plans; update this file after meaningful milestones and before ending a session.

## Current state

- Phase: repository setup and migration planning. No application implementation has started.
- Current task: establish persistent progress tracking; documentation changes only.
- Last inspected branch: `change-migration`, at `d3bed883` (`docs`). The working tree was clean before this tracking task.
- Server baseline: `cff9169a378013f9eba6646ca1de1ae956ea509b`. The committed `suwa-server/` tree still exactly matches that upstream tree.
- Current tracking changes are uncommitted: this file, `AGENTS.md`, `README.md`, and `Docs/repository-setup.md`.
- Live push status is unverified. The current branch matches its local remote-tracking ref, but the earlier live GitHub check failed because the shell could not obtain HTTPS credentials. The owner was configuring authentication through VS Code; do not assume it is still broken or fixed.

## Completed

- Imported the server as a subtree with upstream history preserved: `7c70c9b7`. Repository layout and update commands are in [repository setup](repository-setup.md).
- Recorded migration requirements and the owner's server-only scope in [the migration plan](same-id-migration-plan.md). A separate WebUI project or custom WebUI build is out of scope.
- Separated deployment notes into [Docker deployment](docker-deployment.md) and added root agent instructions. These documents are present in `d3bed883`.
- Added this progress handoff, linked it from the README, and instructed agents to read and maintain it. Removed stale working-state notes from repository setup in favor of this file.

## Findings to carry forward

These are source-inspection findings, not runtime validation. Paths below are relative to `suwa-server/server/src/main/kotlin/suwayomi/tachidesk/`.

- `manga/model/table/MangaTable.kt`: manga ID is separate from source and URL; retaining it does not require ID swapping.
- `manga/model/table/ChapterUserTable.kt`: personal chapter state includes one `lastReadAt` value and cascades on chapter deletion. The inspected history/backup paths use this timestamp, not a full reading-event log.
- `opds/repository/ChapterRepository.kt` and `manga/impl/backup/proto/handlers/BackupMangaHandler.kt`: history depends on chapter records; independent historical storage needs query and backup integration.
- `server/database/migration/M0040_AddUniqueConstraintToChapterTable.kt`: `(URL, manga)` is unique. Keeping multiple historical chapters with identical blank URLs is not a complete solution.
- `graphql/mutations/MangaMutation.kt`: library-update inputs do not supply an explicit original/destination migration pair. A reliable interception point for the unchanged client has not been established.
- The WebUI migration flow cited in the plan comes from earlier research and needs read-only rechecking. Its source is not included in this repository.

## Remaining work and next step

1. **Next technical step:** inspect the unchanged client's migration request sequence and map it to server handlers. Establish whether original/destination pairing and completion navigation can work reliably with server-only changes; record concrete evidence in the migration plan.
2. Finalize history storage and visibility/backup behavior, chapter matching, destination conflicts, preserved settings, and cleanup/concurrency/recovery design.
3. Implement and validate the migration feature within the agreed scope once planning is resolved. Follow the migration plan's validation checklist using disposable data.
4. Confirm Raspberry Pi model and OS architecture, then inspect container packaging and choose the build/storage approach. This information is needed for deployment, not for the next source-inspection step.

Unresolved design questions are not completed features. Do not treat this checklist as a request to start application changes during a documentation-only task.

## Validation

- Server baseline check: `git rev-parse HEAD:suwa-server cff9169a^{tree}` returned identical tree hashes during this task.
- Documentation tracking changes: content review, local Markdown link existence checks, and `git diff --check` passed.
- No application build, automated application tests, runtime migration, Docker build, or Pi deployment has been performed in this work.
