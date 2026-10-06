# Current progress

Updated: 2026-10-06. Branch: `change-migration`; WebUI integration committed as `a48ce5c9d`. Live remote state has not been checked.

## Completed

- Imported Suwayomi-WebUI baseline `f620015410dc78bc7579e895dd2bd8f8344693b6` in `suwa-webUI/`, with full history and `upstream-webui` remote.
- Server API keeps the original manga ID and personal settings, replaces source data and chapters with the destination, then deletes the destination row. Read status uses each user's highest read chapter number; old chapter history is discarded. No enablement setting or admin requirement; authentication remains. Backup code and schema are unchanged.
- WebUI single/bulk Migrate now invokes the server API directly, invalidates old cached manga/chapters and keeps navigation on the original ID. Copy keeps upstream behavior; its checkboxes are labeled separately. Failed requests leave the single-entry dialog open for retry.
- Final code review passed 29 migration/API and backup tests plus style checks. Log: `/tmp/suwanomi-final-review-tests.log`.
- Owner's Docker smoke test: MangaBall → Qiscans retained 49 and removed 75; chapters 1–6 read, 7 unread. API confirmed source/IDs and 152 chapters; user-state/database inspection remains unfinished.
- Client checks passed: full TypeScript check, lint on the five changed files, production Vite build, and isolated service/dialog checks for API dispatch, failures, cache eviction and copy options. Build output: `suwa-webUI/build/`. Logs: `/tmp/suwanomi-webui-{types,lint,build}.log`; check harness: `/tmp/suwanomi-ui-migration-check.cjs`. Node 24.20.0 in `/tmp`; installed dependencies from the unchanged lockfile. Upstream Vite emitted a legacy-minifier compatibility warning but completed.

## Remaining

- Bundle the custom client build with the server in Docker, then test the actual button end to end. The prior test server was offline during client validation.
- Inspect a consistent test database snapshot for leftovers and confirm user progress; REST inspection did not show the owner's read/library state.
- Prepare full Docker packaging with WebView dependencies; the minimal image fails loading `libXext.so.6`. See [deployment notes](docker-deployment.md). ARM64/Pi and separate upstream-binary backup round trips remain untested.
- No further behavior removals are approved. Migration guards/validation and normal destination chapter processing remain. Keep backup restore and library synchronization idle during testing; old folders may remain if a removed extension's directory cannot be resolved.

Builds use Java 21 (`/home/user/.jdks/jbr-21.0.11`), `GRADLE_USER_HOME=/tmp/suwanomi-gradle`, and `-Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2`, from `suwa-server/`. No commit or push is authorized by a handoff entry.
