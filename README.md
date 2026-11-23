# NuclearCraft: Overhauled

The Forge 1.20.1 port is in development. The original Overhauled release and its complete source are on branch `1.12.2o`. The port uses that branch at `ef28c65` as its gameplay reference.

## Forge development

Use Java 17. Run `./gradlew build` to produce the reobfuscated JAR in `build/libs`. Run `./gradlew runGameTestServer` for headless gameplay tests, or `./gradlew runServer` for a dedicated-server development run. Both server runs use disposable directories under `build/runs`.

The active 1.20.1 code and data are in `src/forge`. Original sources are kept in `src/main` during migration, and the build reuses converted textures from that tree. See [port verification](docs/forge-port.md) for the current gameplay coverage and Auram targets.


Summary
-------

Brings complex nuclear engineering to Minecraft!

YouTube Channel: https://www.youtube.com/c/NuclearCraftMod

CurseForge Page: https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled


Issues & Pull Requests
----------------------

I welcome all bug reports, questions, suggestions and pull requests - I'd say most of the content that this mod has to offer is based on discussion and suggestions, and so is a crucial and exciting part of development. I will try to reply as quickly as possible, but keep in mind that I am very busy some parts of the year any may be rather inactive during these times.


Permissions
-----------

In practice, the license on any Minecraft mod I make means very little, but for those who want a some semblance of formality, let it be stated that all code, excluding any bundled APIs from other mods (which are covered by the respective licenses in their packages), is available under the [MIT License](https://github.com/turbodiesel4598/NuclearCraft/blob/master/LICENSE.md).
