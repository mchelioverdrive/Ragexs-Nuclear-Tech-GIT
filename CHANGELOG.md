# Pull Request: Fix dark-adaptation world depth capture

## Unreleased

- Capture reusable dark-adaptation world depth at `RenderWorldLastEvent` and accept it only for
  the matching render generation, framebuffer, and display size.
- Add debug geometry coverage and remapped linear-depth inspection without full-size CPU reads.

- Fixed mathematical-black terrain recovery that previously multiplied several small linear factors and produced only about 1–3/255 display luminance (about 1.8/255 in the reported outdoor case).
- Replaced linear environmental scaling with bounded perceptual scaling and a target display luminance, added both cone and rod recovery, linearized depth-based shape cues, safe effect weights, and geometry/recovery debug views.
- Expanded diagnostics to expose requested versus clamped strength and every recovery stage; values such as strength `10.0` can no longer hide the renderer's actual bounded input or destabilize shader interpolation.
- Fixed the dark-adaptation viewport query crashing under LWJGL 2 by ensuring its reusable query
  buffer meets LWJGL's required capacity.
- Added a `runClient` preparation task that exposes ForgeGradle's generated MCP field and method CSV
  mappings at the location expected by legacy coremods.
- Fixed Hardcore Darkness 1.7.10 failing to transform `WorldProviderHell` in the development client
  with `Couldn't find MCP mappings`.
- Documented the generated, development-only `mcp/` directory and excluded it from version control.
