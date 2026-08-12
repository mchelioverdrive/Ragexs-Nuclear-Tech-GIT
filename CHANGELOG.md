# Pull Request: Add ForgeGradle 1.2 devmods workflow

## Unreleased

- Added a dedicated `devmods/` layout for local, development-only external mods.
- Added ForgeGradle 1.2 SRG-to-MCP remapping for production mod JARs in `devmods/`.
- Added direct runtime loading for MCP-ready mod JARs in `devmods/deobf/`.
- Kept generated remapped JARs isolated under `build/devmods/remapped/` and out of release artifacts.
