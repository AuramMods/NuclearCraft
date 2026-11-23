# Forge 1.20.1 port verification

The reference is NuclearCraft: Overhauled 2o.9.7 at `ef28c65`. The port builds with Java 17, Mojang mappings, and Forge 47.4.10, matching the Forge build installed in Auram. The Forge code currently comes from the legacy NuclearCraft source and uses no Neohaul implementation code.

## Gameplay coverage

- The Melter has an in-world block, menu, datapack recipes for ice, packed ice, and sulfur dust, and a Forge fluid registration for molten sulfur.
- The headless GameTest runs the full ice recipe, asserts its 4,000 FE cost and 1,000 mB water output, simulates and executes capability transfers, checks removal invalidation, and reloads the saved output tank.
- The reobfuscated development JAR booted in a clean Forge 47.4.10 dedicated server and produced 1,000 mB of water from ice in-world. The server stopped cleanly.
- The remaining Melter recipes, survival crafting, upgrades, and side configuration are pending. The solid-fuel reactor and the remaining Overhauled content still need porting.

## Auram targets

| Installed mod | Version | Interactions to verify |
| --- | --- | --- |
| Ender IO | 8.2.17-beta | Fluid, item, and energy conduits on sided processors and multiblock ports |
| Mekanism | 10.4.16.80 | Mechanical Pipes, Logistical Transporters, Universal Cables, and chemical interoperability where applicable |
| Thermal Series | Expansion 11.0.1.29, Dynamics 11.0.1.23 | Transfer, storage, and shared material tags |
| Applied Energistics 2 | 15.4.10 | Item and fluid import/export and shared material tags |
| Refined Storage | 1.12.4 | Item and fluid import/export |
| Immersive Engineering | 10.2.0-183 | Fluid, item, and power automation |
| Create | 6.0.8 | Fluid pipes, funnels, and shared material tags |
| Industrial Foregoing | 3.5.21 | Automation through standard capabilities |
| PneumaticCraft | 6.0.22 | Fluid and item automation and shared tags |
| JEI, Patchouli, and KubeJS | 15.20.0.129, 84.1, and 2001.6.5-build.16 | Recipe display, guidebook content, and pack recipe overrides |

Ender IO's sibling source uses sided Forge fluid capabilities and simulates source draining and destination filling before it executes a transfer. The headless Melter test exercises those capability semantics. Test runs with the actual conduits and other Auram devices are pending.
