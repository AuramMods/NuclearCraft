# Forge 1.20.1 port verification

The reference is NuclearCraft: Overhauled 2o.9.7 at `ef28c65`. The port builds with Java 17, Mojang mappings, and Forge 47.4.10, matching the Forge build installed in Auram. The Forge code currently comes from the legacy NuclearCraft source and uses no Neohaul implementation code.

## Gameplay coverage

- The Melter has an in-world block, menu, datapack recipes for ice, packed ice, and sulfur dust, and a Forge fluid registration for molten sulfur.
- The headless GameTest runs the full ice recipe, asserts its 4,000 FE cost and 1,000 mB water output, simulates and executes capability transfers, checks removal invalidation, and reloads the saved output tank.
- The reobfuscated development JAR booted in a clean Forge 47.4.10 dedicated server and produced 1,000 mB of water from ice in-world. The server stopped cleanly.
- The first solid-fuel reactor uses the original cuboidal frame and wall rules, with outward-only Forge item and fluid ports. It has a controller menu and can run on a redstone signal. The first supported combination is LECf-251 oxide fuel, a heavy water moderator, a beryllium-carbon reflector, and water heat sinks.
- The headless reactor tests assembled a five-block-wide shell, inserted one fuel and 1,000 mB of water through the ports, and verified one depleted fuel item and 4,000 mB of high-pressure steam. A separate test broke and repaired the shell, checked invalidation of previously acquired handlers, and loaded the saved coolant into a replacement controller.
- Remaining work includes the rest of the Melter and reactor catalogs, survival crafting, upgrades and side configuration, the legacy fuel port filters, radiation, world generation, and the rest of Overhauled. Reactor menu rendering, chunk unload and reload, and the downstream turbine chain still need runtime verification.

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

## Verified with Auram mod JARs

A disposable headless Forge 47.4.10 server ran the reobfuscated NuclearCraft JAR with the exact Ender IO and Mekanism JARs from Auram. Each Melter received one ice and 4,000 FE. Server block data confirmed both destinations held exactly 1,000 mB of water and both Melter output tanks were empty.

- Melter → Ender IO Fluid Conduit → Ender IO Fluid Tank
- Melter → Mekanism Basic Mechanical Pipe in pull mode → Ender IO Fluid Tank

The same packaged reactor ran with each mod's pipes at both vents. Each setup emptied a 1,000 mB source tank, delivered 4,000 mB of high-pressure steam to its destination, and produced one depleted fuel item. The two devices were Ender IO Fluid Conduits and Mekanism Basic Mechanical Pipes, using the versions listed above. A full restart of the same test world preserved the steam volumes and depleted fuel in both setups.

These tests cover processor fluid output and reactor coolant intake and steam output. Item and energy automation with the installed devices and testing the full Auram instance are pending. No installed Auram mod JAR claimed the `nuclearcraft` mod ID at the time of the test.


## Registry migration references

| Legacy 1.12.2 ID and metadata | Forge 1.20.1 ID |
| --- | --- |
| `nuclearcraft:solid_fission_sink`, 0 | `nuclearcraft:water_heat_sink` |
| `nuclearcraft:fission_reflector`, 0 | `nuclearcraft:beryllium_carbon_reflector` |
| `nuclearcraft:fuel_californium`, 9 | `nuclearcraft:lecf_251_oxide` |
| `nuclearcraft:depleted_fuel_californium`, 9 | `nuclearcraft:depleted_lecf_251_oxide` |

Melter and solid-fission recipes use the `nuclearcraft:melter` and `nuclearcraft:solid_fission` datapack recipe types. These IDs and evidence cover the ported subset. Legacy world data still requires an explicit conversion.
