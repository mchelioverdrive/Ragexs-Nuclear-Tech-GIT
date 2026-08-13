# Pull Request: Prevent stacked thunder darkness in RTM dark adaptation

## Unreleased

- Fixed stacked thunderstorm attenuation between Minecraft, Hardcore Darkness, and RTM by using
  rain-level sustained low-light attenuation for both rain and thunderstorm cloud cover.
- Corrected only vanilla's additional thunder fog multiplier during active nighttime RTM and
  Hardcore Darkness adaptation; normal rain fog darkening remains intact.
- Kept rendered lightning flashes in exposure metering, so a flash can still temporarily damage
  dark adaptation before the normal cone and rod recovery resumes.

- Corrected scotopic color perception by recovering bounded luminance before chroma reconstruction,
  making deep rod vision nearly monochrome while preserving limited twilight color and locally bright color.
- Replaced the false saturation-based contrast loss with blurred-neighborhood luminance detail loss,
  strengthened rod-driven acuity loss in deep darkness, and added subtle extra central acuity loss.
- Kept near-field and exact-black recovery achromatic and made scarcity-weighted visual noise monochrome.

- Added a low-light signal-recovery and scotopic tone-mapping patch that dynamically raises Hardcore
  Darkness's sky-only information carrier as eyes adapt, amplifies retained dark terrain RGB with a
  bounded gain, and reserves depth recovery for effectively black pixels.
- Added a subtle depth-bounded near-field perception boost without creating a Minecraft light source,
  and changed configured rod recovery time to mean approximately 95% adaptation instead of one time
  constant.
- Preserved a configurable, sky-only carrier signal before Hardcore Darkness removes nighttime
  texture information, while retaining its removed global RGB floor and RTM's final dark-adaptation
  control.
- Moved dark-adaptation depth capture from the pre-HUD color-copy path to highest-priority
  `RenderWorldLastEvent`, preserving completed world geometry without depending on hand or HUD depth.
- Added current-render generation, active-framebuffer, size, copy-success, and debug-only 16×12
  geometry-coverage validation so stale or cleared depth cannot be advertised to the shader.
- Added exact geometry-mask and remapped linear-depth views plus active-framebuffer restoration for
  vanilla and Angelica rendering.

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
