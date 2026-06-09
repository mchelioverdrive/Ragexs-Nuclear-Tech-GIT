# Documentation Audit

This file records what was discovered during the documentation pass and what remains uncertain.

## Source areas inspected

- `README.md`
- `build.gradle`
- `gradle.properties`
- `src/main/resources/mcmod.info`
- `src/main/java/com/hbm/main/MainRegistry.java`
- `src/main/java/com/hbm/commands/*.java`
- `src/main/java/com/hbm/config/*.java`
- `src/main/java/com/hbm/inventory/recipes/loader/SerializableRecipe.java`
- `src/main/java/com/hbm/inventory/fluid/Fluids.java`
- User-provided RTM update summary for fork-specific design goals and major changes

## Features, commands, configs, or systems documented in this pass

- Runtime nuke enable/disable command and scheduled nuke toggle.
- Client-side `/ntmclient` command and `hbmClient.json` keys.
- Debug-only `/dumpthreadsandcrashgame` client command.
- Station and satellite management commands.
- Bedrock excavator drop editing command and default entry format.
- Recipe template activation behavior in `config/hbmRecipes/`.
- Dynamic machine config behavior in `hbmMachines.json`.
- Custom machine config file generation.
- Fallout, item pool, and fluid trait template behavior.
- Major main-config categories and high-impact toggles such as 528 mode, LBS mode, threaded atmospheres, explosion chunk loading, and nuclear warfare.
- Thermos/fork server startup warning.
- RNTM design goals and major upstream differences: realism focus, nuclear/radiation overhaul, chemistry expansion, world/space rework, industry/progression rebalance, warfare controls, compatibility cleanup, and removed/reworked fantasy features.

## Documentation gaps and uncertainties

These items could not be fully documented without deeper gameplay testing, comments, screenshots, or implementation-specific knowledge:

1. **Exact release-by-release upstream delta.** The RTM direction is documented, but a complete inherited/removed/reworked item-by-item comparison against every original NTM release would require generated registries and in-game validation.
2. **Full progression walkthrough.** The codebase contains many machines and recipes; a complete step-by-step progression guide would require in-game validation.
3. **Exact dependency packaging.** Build-time dependencies are declared, but runtime packaging expectations can vary in legacy 1.7.10 launcher setups.
4. **Complete machine-value reference.** `hbmMachines.json` is generated dynamically from registered tile entities, so exact contents depend on runtime registration and should be documented from a generated file.
5. **Complete recipe reference.** Recipes are numerous and generated as JSON templates; recipe browsing is better handled in-game or from generated files.
6. **Custom machine schema details.** The default file demonstrates fields, but user-facing documentation would benefit from a schema and tested examples.
7. **Fluid trait schema details.** Trait classes exist, but a complete validated schema and examples were not produced in this pass.
8. **Bedrock excavator drop persistence.** `/hbmbedrockdrop` edits `MiningConfig.excavatorBedrockDrops` in memory; persistence was not evident from the inspected command code.
9. **Permissions.** Most commands do not implement explicit permission overrides, so effective access depends on Forge/vanilla defaults and server tooling.
10. **Screenshots.** No current screenshots were present in the repository, so the README includes placeholders rather than images.
11. **External community links.** Modrinth and CurseForge links were available in the old README. No Discord or dedicated RNTM wiki link was verified in the repository.
12. **Client config key collision.** `ClientConfig` maps `ITEM_TOOLTIP_SHOW_CUSTOM_NUKE` through the `ITEM_TOOLTIP_SHOW_OREDICT` key during default registration, so the intended editable key needs code clarification.

## Recommended next documentation work

- Generate `hbm.cfg`, `hbmConfig/`, and `hbmRecipes/` from a clean runtime and add examples based on those generated files.
- Add screenshots for early industry, reactor setup, radiation UI, and space stations.
- Write a validated early-to-mid-game progression guide from actual gameplay.
- Generate a release-by-release upstream comparison table from registries, recipe outputs, and language files.
- Add a custom machine schema with one working example.
- Add a fluid traits schema with one safe example and one hazardous example.
- Clarify command permissions and persistence behavior in code or server docs.
