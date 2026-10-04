# SuwaNomi

Personal Suwayomi customization project, focused on running the server in Docker on a Raspberry Pi.

- `Docs/`: design and implementation plans.
- `suwa-server/`: Suwayomi-Server imported as a Git subtree with upstream history preserved.

The first planned feature is optional source migration that retains the manga ID and reading history. Application changes have not started.

## Upstream updates

From the project root, with a clean working tree:

```sh
git subtree pull --prefix=suwa-server upstream master
```

Resolve any merge conflicts, then build and test before pushing. The `upstream` remote points to https://github.com/Suwayomi/Suwayomi-Server.git. After cloning this project on another machine, add it once:

```sh
git remote add upstream https://github.com/Suwayomi/Suwayomi-Server.git
```

Commit project and server changes in this top-level repository. No submodule initialization is needed.

## License

The imported server retains its upstream license and copyright notices; see `suwa-server/LICENSE`.
