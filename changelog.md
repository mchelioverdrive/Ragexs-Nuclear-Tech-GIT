# Pull Request: Add environmental scotopic dark recovery

- Added a separate sky-light, starlight, moon-phase, and weather estimate for dark-adapted vision.
- Changed Hardcore Darkness mathematical-black recovery from depth-edge-created light to environmentally gated dim grayscale geometry, keeping sealed zero-light spaces black.
- Added a conservative scotopic-floor setting and expanded runtime adaptation diagnostics.

## Pull Request: Fix Hardcore Darkness in runClient

## Unreleased

- Added a `runClient` preparation task that exposes ForgeGradle's generated MCP field and method CSV
  mappings at the location expected by legacy coremods.
- Fixed Hardcore Darkness 1.7.10 failing to transform `WorldProviderHell` in the development client
  with `Couldn't find MCP mappings`.
- Documented the generated, development-only `mcp/` directory and excluded it from version control.
