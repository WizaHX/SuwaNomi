# Project progress

Last updated: 2026-10-05.

## Current state

- Branch `change-migration`, based on `0f9b54df`. All feature/documentation changes are uncommitted; no push attempted and live remote state is unverified.
- Imported server baseline: `cff9169a378013f9eba6646ca1de1ae956ea509b`.
- The owner approved an explicit server-only API accepting original/destination IDs. The unchanged WebUI migration button does not invoke it. See [upstream comparison and request trace](migration-request-trace.md).
- Minimal upstream changes and unchanged bidirectional backup compatibility are required. Only the highest-read cutoff carries over; old per-chapter history is discarded.
- Removed the enablement setting and administrator permission requirement as requested. The latest API tests and style checks pass; other pending behavior choices remain unchanged.

## Implemented, uncommitted

- Explicit client invocation; no migration enablement setting or configuration change is needed.
- One GraphQL migration command available to any authenticated user. See [API usage](same-id-migration-api.md).
- Keeps original ID and manga-level personal state; fetches destination content before cleanup; replaces source fields/chapters transactionally and removes the separate destination row, including its personal state. Destinations already in the library or with downloads are allowed.
- Computes each user's highest original read chapter number. Every destination chapter at/below that cutoff becomes read, including gaps; chapters above stay unread. With no read history, destination chapters stay unread. The destination supplies all names/URLs/metadata. Old chapter bookmarks, reading dates and positions are discarded.
- Local-source manga are allowed on either side; only the destination must be available. Original extension/site availability is irrelevant. Upstream’s source-or-stub cleanup may leave unresolvable old folders; local content files remain in the separate local directory.
- In-memory guards prevent overlapping refresh/download writes. Path checks prevent deleting shared/unrelated storage. Files are removed before changing source metadata.
- On failure the original source/reading state remain, deleted downloads stay deleted, and normal use/retry is available. No persistent locks, journal, recovery endpoints or custom database schema.
- Main implementation: `suwa-server/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/migration/` and `graphql/mutations/MigrationMutation.kt`.

## Removed complexity

- Removed the migration enablement setting, configuration lookup, service check, and setting-specific test/setup. The administrator permission requirement is also removed; existing authentication remains.

- Latest structural cleanup removed redundant temporary variables and binding-check ID arguments, avoids an intermediate URL list, and removes an unnecessary `suspend` declaration. No behavior or upstream integration changes; 10 fewer production lines.

- Removed the destination personal-state/duplicate-entry validation, local-source prohibition and installed-original-extension requirement (review items 3, 4 and 5).
- Removed `MigrationChapterMatcher`, all old/new chapter mapping, name/special fallback, per-chapter date/bookmark copying, and matched/discarded-count response fields. The API returns only `mangaId`.
- Prior simplification removed the journal table/database migration, recovery query, cancel command, persistent locks, redundant wrappers and coroutine lease machinery.
- Backup schemas, serializers, restore code and ServerConfig registry remain unchanged. Custom logic stays in new migration files with small upstream integration points.

## Remaining review items (original numbering)

| Original item | Remaining addition |
| --- | --- |
| 2 | Separate read cutoff for every user |
| 6 | Always fetch fresh destination details/chapters |
| 7 | Validate destination content (empty lists, names, URLs and numbers) |
| 8 | In-memory busy/source/file guards |
| 9 | Shared-directory and safe-path checks |
| 10 | Clean destination download/cache paths as well as original paths |
| 11 | Repeated binding checks and locked atomic replacement |

The explicit API remains; its enablement setting was removed at the owner’s request. Upstream-style chapter parsing/name cleanup is still repeated in the migration service. Items 1 and 3–5 were removed at the owner's request.

## Validation

- Latest permission change: both `MigrationApiTest` tests and `:server:ktlintCheck` passed. The API test verifies that a user with no special permissions can migrate and an unauthenticated visitor cannot. `git diff --check` passed. The preceding revision passed all 29 migration/API and backup tests; that broader suite was not rerun for this permission-only change. No backup code/schema changes, commits or pushes.
- Updated tests exercise destination ranges 1–45, 15–40 and 35–44 with highest read 30, unread gaps/later unread chapters, per-user cutoffs/no read history, authoritative destination data, disposable-file cleanup, authentication and non-admin access, failure/retry and backup round trips without copied reading dates.
- Regression coverage now also includes used destinations and migration back, a missing original extension through the API, and actual local-source chapters followed by migration back online with local files intact.
- Latest validation log: `/tmp/suwanomi-auth-tests.log`; preceding full migration/backup run: `/tmp/suwanomi-no-setting-tests.log`.
- Build setup: `JAVA_HOME=/home/user/.jdks/jbr-21.0.11`, `GRADLE_USER_HOME=/tmp/suwanomi-gradle`, wrapper Gradle 9.7.1; `-Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2`. No build/dependency version changes.
- Separate upstream-binary import/export, live extension, Docker/Pi and process-kill tests have not been run. Backup round trips use unchanged upstream protobuf serializers/handlers in this build.
- Keep backup restore and library synchronization idle during migration; those unchanged upstream operations are not covered by the in-memory guards.

## Next step

Local Docker testing requested on Linux Mint AMD64. Docker client is installed, but daemon access is blocked: host user is not in the socket’s `docker` group. No container was started. After host access is configured and the session restarted, run a disposable container migration smoke test; see [Docker notes](docker-deployment.md).

The owner has deferred decisions on the remaining review items and repeated chapter parsing/name cleanup. Resume that review when requested. A real-extension API smoke test with disposable data remains outstanding; Docker packaging and Pi deployment follow it. No commit/push is implicitly authorized.

## Project and deployment

[Repository setup](repository-setup.md), [migration requirements](same-id-migration-plan.md), [Docker deployment](docker-deployment.md). Hardware: Raspberry Pi 4 Model B, reported 64-bit OS; expected `linux/arm64`, on-device verification pending.
