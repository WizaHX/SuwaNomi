# Current progress

Updated: 2026-10-06. Branch: `change-migration`; server implementation committed as `9bbf966e`, documentation cleanup as `ebf9d548`, and WebUI subtree import as `3c5ad8990`. Import-related documentation updates are uncommitted. Live remote state has not been checked.

## Completed

- Imported untouched Suwayomi-WebUI `master` (`f620015410dc78bc7579e895dd2bd8f8344693b6`) into `suwa-webUI/` with full history and `upstream-webui` remote. Imported tree matches upstream exactly; no client build run or GitHub push.

- Server API keeps the original manga ID and personal settings, replaces source data and chapters with the destination, then deletes the destination row. Read status uses each user's highest read chapter number; old chapter history is discarded. No enablement setting or admin requirement; authentication remains. Backup code and schema are unchanged.
- Invoke `migrateMangaSameId(input: { originalId: 49, destinationId: 75 }) { mangaId }` as a GraphQL mutation. The existing WebUI migration button does not call this API.
- Final code review passed 29 migration/API and backup tests plus style checks. Log: `/tmp/suwanomi-final-review-tests.log`.
- Owner's Docker smoke test: MangaBall → Qiscans retained 49 and removed 75; chapters 1–6 read, 7 unread. API confirmed source/IDs and 152 chapters; user-state/database inspection remains unfinished.

## Remaining

- Next client task: connect migration options to the server API. This import makes no UI behavior changes.

- Inspect a consistent test database snapshot for leftovers and confirm user progress; REST inspection did not show the owner's read/library state.
- Prepare full Docker packaging with WebView dependencies; the minimal image fails loading `libXext.so.6`. See [deployment notes](docker-deployment.md). ARM64/Pi and separate upstream-binary backup round trips remain untested.
- No further behavior removals are approved. Migration guards/validation and normal destination chapter processing remain. Keep backup restore and library synchronization idle during testing; old folders may remain if a removed extension's directory cannot be resolved.

Builds use Java 21 (`/home/user/.jdks/jbr-21.0.11`), `GRADLE_USER_HOME=/tmp/suwanomi-gradle`, and `-Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2`, from `suwa-server/`. No commit or push is authorized by a handoff entry.
