# SuwaNomi

Personal Suwayomi customization project, focused on running the server in Docker on a Raspberry Pi.

- `Docs/`: design and implementation plans.
- `suwa-server/`: Suwayomi-Server imported as a Git subtree with upstream history preserved.

The first feature in development is an explicit server API for source migration that retains the manga ID and highest-read progress. The destination supplies all manga/chapter data; old per-chapter history is discarded. See the progress file for implementation and validation status.

## Documentation

- [Progress and next steps](Docs/progress.md)
- [Repository setup and upstream updates](Docs/repository-setup.md)
- [Docker deployment on Raspberry Pi](Docs/docker-deployment.md)
- [Same-ID migration plan](Docs/same-id-migration-plan.md)
- [Same-ID migration API usage](Docs/same-id-migration-api.md)

## License

The imported server retains its upstream license and copyright notices; see `suwa-server/LICENSE`.
