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

## Working state and agent instructions

See [progress and next steps](progress.md) for the latest inspected branch, baseline, validation, and authentication status. Check current Git status and remote state before assuming a push is needed or complete.

Read the root [AGENTS.md](../AGENTS.md) and any applicable directory-specific instructions before making changes.
