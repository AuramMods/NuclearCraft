package nc.worldgen.structure.vault;

import nc.NuclearCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WastelandVaultGameTests {
    private WastelandVaultGameTests() {}

    @GameTest(template = "empty")
    public static void vaultHasConnectedSectorsAndSavesItsPieces(GameTestHelper helper) {
        var level = helper.getLevel();
        var anchor = new BlockPos(1004, 74, 4820);
        var pieces = VaultLayout.create(anchor, 256, RandomSource.create(947361));
        var context = StructurePieceSerializationContext.fromLevel(level);
        var pieceTags = pieces.stream().map(piece -> piece.createTag(context)).toList();
        var entrance = pieces.get(pieces.size() - 1);
        var reached = new HashSet<VaultPiece>();
        var pending = new ArrayDeque<VaultPiece>();
        pending.add(entrance);
        reached.add(entrance);
        while (!pending.isEmpty()) {
            var bounds = pending.removeFirst().getBoundingBox();
            for (var piece : pieces) {
                if (piece.getBoundingBox().intersects(bounds) && reached.add(piece)) {
                    pending.addLast(piece);
                }
            }
        }
        helper.assertTrue(reached.size() == pieces.size(), "Every room and connector is reachable from the entrance shaft");
        long rooms = pieceTags.stream().filter(tag -> tag.getInt("Type") == VaultPiece.TYPE_ROOM).count();
        long deep = pieceTags.stream().filter(tag -> tag.getInt("Type") == VaultPiece.TYPE_ROOM
                && tag.getInt("SectorType") == VaultSectorType.DEEP.ordinal()).count();
        helper.assertTrue(rooms >= 12 && deep >= 5 && pieceTags.stream().anyMatch(
                tag -> tag.getInt("Type") == VaultPiece.TYPE_HIDDEN_TRANSITION_SHAFT),
                "The vault contains both legacy sectors and the hidden shaft");
        helper.assertTrue(pieceTags.stream().filter(tag -> tag.getInt("RoomType") == VaultRoomType.DEEP_UNIQUE_LOOT_ROOM.ordinal()).count() == 1,
                "The deep sector has one unique loot room");

        var type = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(NuclearCraft.id("wasteland_vault"));
        var pos = new ChunkPos(anchor);
        var start = new StructureStart(type, pos, 0, new PiecesContainer(List.copyOf(pieces)));
        var saved = start.createTag(context, pos);
        var restored = StructureStart.loadStaticStart(context, saved, level.getSeed());
        helper.assertTrue(restored != null && restored.isValid() && restored.createTag(context, pos).equals(saved),
                "The registered Forge structure piece type preserves every connector, room, and door mask on disk");

        var local = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var center = local.getMiddleBlockPosition(232);
        var room = new VaultPiece(VaultPiece.TYPE_ROOM, BoundingBox.fromCorners(center.offset(-3, 0, -3), center.offset(3, 6, 3)),
                0, 0, VaultRoomType.ENTRY_ROOM.ordinal(), VaultSectorType.STANDARD.ordinal());
        room.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(7654), room.getBoundingBox(), local, center);
        helper.assertTrue(!level.getBlockState(center).isAir() && level.isEmptyBlock(center.above()),
                "A newly constructed absolute-coordinate piece places its floor and clears the interior");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vaultLootResolvesAllLegacyItemVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var table = level.getServer().getLootData().getLootTable(NuclearCraft.id("chests/vault"));
        var parameters = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO))).create(LootContextParamSets.CHEST);
        var expected = Set.of("nuclearcraft:radaway", "nuclearcraft:radaway_slow", "nuclearcraft:rad_x",
                "nuclearcraft:rad_shielding_heavy", "minecraft:enchanted_golden_apple", "nuclearcraft:plutonium_238_ingot",
                "nuclearcraft:californium_250_ingot", "nuclearcraft:pikelet", "nuclearcraft:congress_tart",
                "nuclearcraft:ricecake", "nuclearcraft:roentgen_josh_bucket");
        var observed = new HashSet<String>();
        var random = RandomSource.create(418L);
        for (int i = 0; i < 64; ++i) {
            var rolls = table.getRandomItems(parameters, random.nextLong());
            helper.assertTrue(rolls.size() >= 10 && rolls.size() <= 14, "The three vault pools keep the legacy roll counts");
            for (var stack : rolls) {
                observed.add(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
            }
        }
        helper.assertTrue(observed.equals(expected), "Expected every legacy vault reward, got " + observed);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalVaultRollsAndDistantRoomIndexSurviveSave(GameTestHelper helper) {
        var level = helper.getLevel();
        long seed = 5164099796394745566L;
        var origin = new ChunkPos(-666, 858);
        var distant = new ChunkPos(-675, 858);
        helper.assertTrue(WastelandVault.winsPlacementRoll(seed, origin, false)
                        && WastelandVault.winsPlacementRoll(seed, new ChunkPos(-29, 18), true)
                        && !WastelandVault.winsPlacementRoll(seed, new ChunkPos(5, 44), true),
                "The sparse roll and both village roll outcomes match the legacy seed");

        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, origin.x, origin.z);
        var pieces = VaultLayout.create(origin.getMiddleBlockPosition(68), 256, random);
        var vault = WastelandVault.get(level);
        var start = new StructureStart(vault, origin, 0, new PiecesContainer(List.copyOf(pieces)));
        var data = new VaultOverflow.OverflowData(level);
        data.discover(start, ignored -> { throw new AssertionError("A manually placed vault has no deferred placement"); });
        pieces.get(pieces.size() - 1).markNaturalEntrance();

        var discovered = new HashSet<ChunkPos>();
        data.discover(start, discovered::add);
        data.discover(start, ignored -> { throw new AssertionError("The same origin was indexed twice"); });
        var restored = new VaultOverflow.OverflowData(level, data.save(new CompoundTag()));
        helper.assertTrue(discovered.contains(distant) && restored.forChunk(distant).size() == 1
                        && restored.forChunk(origin).isEmpty(),
                "The deferred index saves and restores only rooms outside native structure range");
        helper.succeed();
    }
}
