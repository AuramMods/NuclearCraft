# Forge 1.20.1 port verification

The reference is NuclearCraft: Overhauled 2o.9.7 at `ef28c65`. The port builds with Java 17, Mojang mappings, and Forge 47.4.10, matching the Forge build installed in Auram. The Forge code currently comes from the legacy NuclearCraft source and uses no Neohaul implementation code.

## Gameplay coverage

- The Melter has an in-world block, menu, datapack recipes for ice, packed ice, and sulfur dust, and a Forge fluid registration for molten sulfur.
- The headless GameTest runs the full ice recipe, asserts its 4,000 FE cost and 1,000 mB water output, simulates and executes capability transfers, checks removal invalidation, and reloads the saved output tank.
- The reobfuscated development JAR booted in a clean Forge 47.4.10 dedicated server and produced 1,000 mB of water from ice in-world. The server stopped cleanly.
- The first solid-fuel reactor uses the original cuboidal frame and wall rules, with outward-only Forge item and fluid ports. It has a controller menu and can run on a redstone signal. The first supported combination is LECf-251 oxide fuel, a heavy water moderator, a beryllium-carbon reflector, and water heat sinks.
- The headless reactor tests assembled a five-block-wide shell, inserted one fuel and 1,000 mB of water through the ports, and verified one depleted fuel item and 4,000 mB of high-pressure steam. A separate test broke and repaired the shell, checked invalidation of previously acquired handlers, and loaded the saved coolant into a replacement controller.
- The steam turbine validates opposed inlets and outlets, centered bearings, a full shaft, matching blades per stage, and dynamo coil placement. It has a controller menu, redstone activation, three blade tiers, a stator, six coil tiers, and coil connectors. It accepts steam through an outward-facing Forge fluid handler and exposes exhaust and energy on the corresponding outward faces. It can also push into neighboring tanks and energy storage.
- Four headless turbine tests cover sided access, simulated transfers, full exhaust backpressure, save and reassembly, reversed flow through a two-wide shaft, rejection of mixed blade tiers, and a complete reactor-to-turbine-to-Melter chain.
- The legacy ore, ingot, dust, storage block, alloy, gem, compound, and part families have individual registry IDs. The port includes the original crafting patterns for the Melter and available solid-fission and turbine parts, plus vanilla smelting and blasting for ores and metal dusts. A headless GameTest crafted copper wire with mixed vanilla and NuclearCraft ingots and verified that crafting a water heat sink returns the bucket.
- A new world on a clean Forge dedicated server generated all eight legacy NuclearCraft ores using the packaged JAR. The ore recipes and drops preserve the default legacy ore-to-ingot progression.
- The Manufactory and Alloy Furnace have legacy crafting patterns, block models and screens. They accept Forge Energy on all faces and support per-face item modes. Their datapack recipes cover the graphite chain, ore processing, bronze, steel, ferroboron, tough alloy, and other starter materials, with optional outputs for installed tech mods.
- Headless tests verified the 5,000 FE coal-to-graphite chain and the 8,000 FE bronze recipe with either ingredient order. The Alloy Furnace test checked a full output, handler invalidation, and restoration of in-progress processing and face modes. Optional JEI categories cover all currently ported processing, reactor fuel, and turbine recipes.
- The Separator uses two extractable outputs and waits for room in both slots before spending energy or ingredients. Its regular material and fuel recipes are available through Forge tags. The solid-fission catalog contains 81 oxide, nitride, and zirconium alloy recipes from the original fuel tables, with legacy times, heat, efficiency, criticality, intrinsic flux, and self-priming values.
- The headless Separator test verified a 40,000 FE batch of ten uranium dust, a stall when the second output was full, and the exact nine-to-one output balance. The recovered items supplied the vanilla crafting recipe for nine unprocessed LEU-235 pellets.
- A hidden Forge development client joined a clean dedicated server running the packaged JAR, opened the Alloy Furnace screen, and synced an input-only face change from the server. JEI 15.20.0.129 found the bronze recipe after login. The temporary test copy remapped JEI's access transformer for the development client. The client measured master volume at zero, and the native observer recorded no visible windows or focus changes.
- A second hidden-client test opened the Separator, synced a side setting, and found both uranium outputs in JEI after multiplayer recipe sync. Master volume was zero and the native observer recorded no visible windows or focus changes.
- The Infuser accepts a counted item and a measured fluid ingredient. Its 139 packaged recipes include pellet and isotope oxide and nitride conversions, metal oxides, the heavy water moderator, concrete, and recipes with optional Thermal or Immersive Engineering outputs. Oxygen, nitrogen, and heavy water are registered fluids. Each Infuser face independently supports fluid input, output, auto-output, or disabled alongside its item setting. Shift-click a face in the machine screen to cycle its fluid mode.
- Headless GameTests verified that an LEU-235 pellet uses 6,000 FE and exactly 1,000 mB of oxygen. A short fluid supply and a full output each held the batch in place. The machine restored the input fluid and face modes on reload, invalidated old fluid handlers after setting changes and removal, and retained surplus fluid when an auto-output destination filled up.
- A hidden Forge development client opened the Infuser in multiplayer, synced its 1,234 mB tank and independent face settings, and displayed the Infuser oxygen ingredient in JEI. Master volume was zero. The native observer recorded no onscreen samples or focus activations.
- The single-block Electrolyzer uses one input and four output tanks, with independent fluid settings for each face and output tank. Select a tank in the screen to configure its faces. Each output can hold fluid, void excess, or void all. Its 64 datapack recipes include water, enriched waters, chemical intermediates, and the 54 fuel and depleted-fuel fluoride recipes backed by fluids registered in the original mod. Crafting uses the original graphite, advanced plate, copper solenoid, chassis, and motor pattern.
- A headless GameTest ran an entire 3,200-tick water batch, including a full oxygen tank stall and an in-progress reload. It used 128,000 FE and 500 mB of water to produce 500 mB of hydrogen and 250 mB of oxygen in separate tanks. Other GameTests verified that oxygen auto-output feeds the Infuser and that a four-product recipe only voids excess from the configured output.
- A hidden Forge development client joined the packaged server, found all 64 Electrolyzer recipes in JEI, and verified separate tank contents and server synchronization of a selected output's face and overflow settings. Master volume was zero, and the native observer recorded no onscreen windows or focus changes.
- The Cobblestone Generator, Water Source, and Nitrogen Collector are craftable in all three legacy tiers. The basic versions make 0.125 cobblestone items, 10 mB of water, and 5 mB of nitrogen per tick. Compact and dense versions multiply those rates by 8 and 64. Each collector stores its product for extraction through Forge capabilities and can push it into adjacent inventories and tanks. A clean Forge GameTest used a compact Nitrogen Collector to supply an Infuser and make one LEU-235 nitride fuel.
- JEI displays all nine collectors. A hidden multiplayer client opened their JEI category and verified that a custom nitrogen rate supplied by the server was reflected in both the basic and compact tiers. The native observer recorded zero visible windows or focus changes, and master volume was zero.
- The Decay Hastener uses the shared Forge item and energy interfaces and has 87 recipes derived from the legacy catalog. The uranium-238 recipe takes 12,230 ticks and 122,300 FE per radium dust. A headless test produced the two dusts needed for the original shaped recipe and used them to craft two Ra-Be sources. Legacy recipes whose outputs would need thallium or barium items and additional TBP and protactinium forms still need mappings.
- Ra-Be, Po-Be, and Cf-252 neutron sources align toward the reactor interior when a solid-fission shell assembles. Redstone activates each source. The source primes the first eligible fuel cell along that line unless a full reflector blocks it. The cell still needs enough flux to run. Headless tests used a source to start LEU-235 oxide, verified reflector blocking, and applied the stronger of two sources to the same cell.
- A silent, hidden client opened the Decay Hastener, found its 87 recipes in JEI, and received a powered source's facing, activity, and custom 0.85 efficiency from the dedicated server. The focus observer recorded zero onscreen windows and zero focus changes.
- Completing the fuel loop still requires native beryllium production from the Rock Crusher, neutron source managers, and reprocessing. Other remaining work includes the polonium production path, the Electrolyzer's advanced ingredients, extra coolers and cryogenic fluids, the rest of the Melter and reactor catalogs, reactor fuel decay effects, upgrades and per-slot automation modes, legacy port filters, turbine hot gas recipes and animated rendering, radiation, Wasteland generation, and the other Overhauled systems.

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
| Almost Unified | 0.11.0 | Recipe and output unification for common Forge material tags |

## Verified with Auram mod JARs

A disposable headless Forge 47.4.10 server ran the reobfuscated NuclearCraft JAR with the exact Ender IO and Mekanism JARs from Auram. Each Melter received one ice and 4,000 FE. Server block data confirmed both destinations held exactly 1,000 mB of water and both Melter output tanks were empty.

- Melter → Ender IO Fluid Conduit → Ender IO Fluid Tank
- Melter → Mekanism Basic Mechanical Pipe in pull mode → Ender IO Fluid Tank

The same packaged reactor ran with each mod's pipes at both vents. Each setup emptied a 1,000 mB source tank, delivered 4,000 mB of high-pressure steam to its destination, and produced one depleted fuel item. The two devices were Ender IO Fluid Conduits and Mekanism Basic Mechanical Pipes, using the versions listed above. A full restart of the same test world preserved the steam volumes and depleted fuel in both setups.

Two packaged reactor-to-turbine chains used Ender IO conduits or Mekanism transmitters for each transfer. Each delivered one fuel item from a barrel, drew 1,000 mB of water from an Ender IO tank, collected 16,000 mB of turbine exhaust in another tank, and returned one depleted fuel item to an output barrel. The Mekanism Basic Universal Cable powered a Melter that produced 1,000 mB of water from ice. With both turbines starting as soon as steam arrived and both Melters paused, the Ender IO Energy Conduit delivered 2,441 FE and the Mekanism cable delivered 18,891 FE. The ice recipe requires 4,000 FE.

With the turbines enabled after each inlet accumulated exactly 4,000 mB of high-pressure steam, both networks delivered 5,051 FE to their paused Melters. After the Melters were enabled, each produced 1,000 mB of water from its ice.

In a separate headless test with Auram's installed KubeJS JAR, a server script removed the default Melter ice recipe and added a custom recipe with `event.custom`. The Melter produced the scripted 750 mB of water in-world.

With Auram's Almost Unified JAR and config alongside Mekanism, a furnace smelted one NuclearCraft copper dust into one vanilla copper ingot, matching the pack's priority for copper.

With separate north input and south output faces, Auram's Ender IO Item Conduits and Mekanism Basic Logistical Transporters each supplied one coal to a Manufactory and three copper plus one tin to an Alloy Furnace. Each network emptied its source barrels and collected one coal dust and four bronze ingots in the corresponding destination barrels.

For the Separator, Ender IO supplied ten Mekanism uranium ingots and Mekanism supplied ten NuclearCraft uranium dust. Both source barrels emptied and each destination collected exactly nine uranium-238 ingots and one uranium-235 ingot. A full restart preserved those counts. The server then confirmed the test chunk was unloaded before it was loaded again, and both source and destination inventories still matched.

The Infuser accepted 1,000 mB of Mekanism oxygen through an Ender IO Fluid Conduit and 1,000 mB of NuclearCraft oxygen through a Mekanism Basic Mechanical Pipe. Ender IO Item Conduits and Mekanism Basic Logistical Transporters supplied an LEU-235 pellet and extracted the oxide from each machine. Both networks emptied their source tanks and barrels and collected one LEU-235 oxide in each destination barrel.

Two Electrolyzer-to-Infuser chains used the exact installed Ender IO or Mekanism pipes and item transporters. Each drew 500 mB of water, collected 500 mB of hydrogen through a separate face, and delivered the batch's 250 mB of oxygen to the Infuser. Each started with 750 mB of buffered oxygen and delivered enough for one LEU-235 oxide. The source tanks and barrels emptied, and each destination barrel received exactly one oxide. A full server restart and a confirmed chunk unload and reload preserved the tank contents and item counts in both chains.

With the same versions of Ender IO and Mekanism, each network transferred nitrogen to an Infuser and extracted one LEU-235 nitride. Compact Water Sources filled each destination tank to 16,000 mB. Compact Cobblestone Generators with the base power setting changed to 2.5 FE/t used a 400 FE buffer to produce exactly 20 cobblestone each, which the item conduit or transporter collected. Both networks preserved the amounts after a server restart and a confirmed chunk unload and reload.

Using those same networks, two Decay Hasteners received uranium-238 and output a radium dust each. Two independently powered Ra-Be sources primed LEU-235 oxide in separate solid reactors. Each reactor took water and fuel through its outward ports, burned one fuel pellet, and discharged one depleted LEU-235 oxide. The networks collected 21,860 mB of high-pressure steam each from exactly 5,465 mB of water consumed per reactor. Both systems preserved the coolant, steam, radium dust, and depleted fuel after a full server restart and a confirmed chunk unload and reload.

An isolated Auram copy contains all 539 installed mod JARs and the pack's configuration and scripts. No installed mod JAR declared the `nuclearcraft` mod ID. Full Auram instance testing is pending.

## Registry migration references

| Legacy 1.12.2 ID and metadata | Forge 1.20.1 ID |
| --- | --- |
| `nuclearcraft:solid_fission_sink`, 0 | `nuclearcraft:water_heat_sink` |
| `nuclearcraft:fission_reflector`, 0 | `nuclearcraft:beryllium_carbon_reflector` |
| `nuclearcraft:fission_source`, 0 | `nuclearcraft:fission_source_radium_beryllium` |
| `nuclearcraft:fission_source`, 1 | `nuclearcraft:fission_source_polonium_beryllium` |
| `nuclearcraft:fission_source`, 2 | `nuclearcraft:fission_source_californium` |
| `nuclearcraft:fuel_californium`, 9 | `nuclearcraft:lecf_251_oxide` |
| `nuclearcraft:depleted_fuel_californium`, 9 | `nuclearcraft:depleted_lecf_251_oxide` |
| `nuclearcraft:uranium`, 5 | `nuclearcraft:uranium_235_ingot` |
| `nuclearcraft:pellet_uranium`, 4 | `nuclearcraft:leu_235_pellet` |
| `nuclearcraft:fuel_uranium`, 9 | `nuclearcraft:leu_235_oxide` |
| `nuclearcraft:depleted_fuel_uranium`, 9 | `nuclearcraft:depleted_leu_235_oxide` |
| `nuclearcraft:turbine_dynamo_coil`, 0 | `nuclearcraft:turbine_dynamo_coil_magnesium` |
| `nuclearcraft:turbine_dynamo_coil`, 1 | `nuclearcraft:turbine_dynamo_coil_beryllium` |
| `nuclearcraft:turbine_dynamo_coil`, 2 | `nuclearcraft:turbine_dynamo_coil_aluminum` |
| `nuclearcraft:turbine_dynamo_coil`, 3 | `nuclearcraft:turbine_dynamo_coil_gold` |
| `nuclearcraft:turbine_dynamo_coil`, 4 | `nuclearcraft:turbine_dynamo_coil_copper` |
| `nuclearcraft:turbine_dynamo_coil`, 5 | `nuclearcraft:turbine_dynamo_coil_silver` |

The datapack recipe types include `nuclearcraft:manufactory`, `nuclearcraft:separator`, `nuclearcraft:alloy_furnace`, `nuclearcraft:decay_hastener`, `nuclearcraft:infuser`, `nuclearcraft:electrolyzer`, `nuclearcraft:melter`, `nuclearcraft:solid_fission`, and `nuclearcraft:turbine`. Item processor recipes take counted Forge item ingredients. The Separator uses a `results` array in output slot order. Manufactory, Alloy Furnace, Decay Hastener, and Infuser recipes use a single `result` object. Alloy Furnace inputs are interchangeable. The Infuser and Electrolyzer have a `fluid_input` object with a `fluid` ID or a `tag` and an `amount` in mB. The Electrolyzer's `results` array contains up to four `fluid` and `amount` objects in outlet tank order. Turbine recipes accept Forge fluid tags. Legacy world data still requires an explicit conversion.

Legacy `ore`, `ingot` and `ingot2`, `dust` and `dust2`, and `ingot_block` and `ingot_block2` variants use per-material IDs such as `nuclearcraft:boron_ore`, `nuclearcraft:zirconia_ingot`, `nuclearcraft:zirconia_dust`, and `nuclearcraft:lithium_block`. The metadata index follows each corresponding legacy enum. Alloy variants use the same `<material>_ingot` pattern.

Ore generation uses Forge biome modifiers and configured and placed features. The default modifiers target the Overworld biome tag with the legacy height bands and attempts per chunk. A pack can override `data/nuclearcraft/forge/biome_modifier/ore_<material>.json` with `{"type":"forge:none"}` to disable an ore, or replace the configured and placed features to control its shape, height, and frequency.

Collector settings moved to `world/serverconfig/nuclearcraft-collectors.toml`. Use `defaultconfigs/nuclearcraft-collectors.toml` as a template for new worlds. The three entries of legacy `processor_passive_rate` map to `collectors.cobblestone_rate`, `collectors.water_rate`, and `collectors.nitrogen_rate`. The collector-specific equivalents of `machine_update_rate`, `passive_push`, and `cobble_gen_power` are `collectors.update_interval`, `collectors.auto_output`, and `collectors.cobblestone_power`. Setting a family's production rate to zero stops generation while retaining its block IDs. The three legacy `register_passive` entries still need an explicit equivalent for registry availability.

The three entries of legacy `fission_source_efficiency` map to the `neutron_sources` section in `world/serverconfig/nuclearcraft-fission.toml`. Its settings are `radium_beryllium_efficiency`, `polonium_beryllium_efficiency`, and `californium_efficiency`, with defaults of 0.9, 0.95, and 1 respectively.
