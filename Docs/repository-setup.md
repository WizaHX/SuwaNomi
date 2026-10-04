# Repository setup

Project directory: `/home/user/CodeProjects/SuwaNomi`.
Project repository: [WizaHX/SuwaNomi](https://github.com/WizaHX/SuwaNomi).

## Layout and history

- One top-level Git repository contains `Docs/`, project files, and `suwa-server/`.
- `Docs/` contains project documentation and feature plans.
- `suwa-server/` contains Suwayomi-Server imported as a Git subtree with original upstream history preserved.
- Commit project and server changes in the top-level repository. No submodule initialization is needed.

## Remotes and upstream updates

- `origin`: `https://github.com/WizaHX/SuwaNomi.git`
- `upstream`: `https://github.com/Suwayomi/Suwayomi-Server.git`

After cloning on another machine, add the upstream remote once:

```sh
git remote add upstream https://github.com/Suwayomi/Suwayomi-Server.git
```

From the project root, with a clean working tree:

```sh
git subtree pull --prefix=suwa-server upstream master
```

Resolve any merge conflicts, then build and test before pushing.

## Last inspected state (2026-10-04)

- Working branch: `change-migration`.
- Both `main` and `change-migration` pointed to subtree import commit `7c70c9b7`.
- Imported server baseline: `cff9169a378013f9eba6646ca1de1ae956ea509b`; the server subtree matched that upstream tree exactly.
- Local remote-tracking refs matched both branches. A live remote check failed because the shell could not obtain HTTPS credentials, so push status was not independently verified.
- Authentication is being configured through VS Code. Check current Git status and remote state before assuming a push is needed or complete.

Read applicable `AGENTS.md` instructions before implementation. None were found during this inspection.
