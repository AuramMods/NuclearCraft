package nc.radiation;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import nc.NuclearCraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationCommandGameTests {
    private RadiationCommandGameTests() {}

    @GameTest(template = "empty")
    public static void chunkCommandsUseBlockCoordinatesAndSquareRadius(GameTestHelper helper) throws CommandSyntaxException {
        var level = helper.getLevel();
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var center = new BlockPos(500_015, 64, -500_001);
        var origin = new ChunkPos(center);
        var source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(center)).withSuppressedOutput();
        try {
            helper.assertTrue(dispatcher.execute("nc_set_chunk_radiation 4.25e-4", source) == 1
                            && WorldRadiation.rate(level, center) == 4.25e-4,
                    "The default target is the source chunk and accepts scientific notation");
            helper.assertTrue(dispatcher.execute("nc_set_chunk_radiation 1 500016 0", source) == 1
                            && WorldRadiation.rate(level, new BlockPos(500016, 64, 0)) == 1,
                    "The optional X and Z arguments are absolute block coordinates");
            helper.assertTrue(dispatcher.execute("nc_set_chunk_radius_radiation 0.01 1", source) == 9,
                    "A one-chunk radius affects nine chunks");
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var pos = new ChunkPos(origin.x + dx, origin.z + dz);
                    helper.assertTrue(WorldRadiation.rate(level, pos.getBlockAt(0, 64, 0)) == 0.01
                                    && level.getChunkSource().getChunkNow(pos.x, pos.z) == null,
                            "Radiation is recorded throughout the square without loading terrain");
                }
            }
            helper.assertTrue(WorldRadiation.rate(level, center.offset(32, 0, 0)) == 0,
                    "The square stops at the requested radius");
            rejected(helper, dispatcher, source, "nc_set_chunk_radius_radiation 1 2147483647");
            rejected(helper, dispatcher, source, "nc_set_chunk_radiation NaN");
            rejected(helper, dispatcher, source, "nc_set_chunk_radiation 1e999");
            rejected(helper, dispatcher, source.withPermission(0), "nc_set_chunk_radiation 500");
            helper.assertTrue(WorldRadiation.rate(level, center) == 0.01,
                    "Invalid arguments and unauthorized sources leave the stored rate alone");
            dispatcher.execute("nc_set_chunk_radiation -12", source);
            helper.assertTrue(WorldRadiation.rate(level, center) == 0,
                    "A negative radiation rate clears the chunk as it did in the legacy mod");
        } finally {
            WorldRadiation.setRegionRate(level, origin, 1, 0);
            WorldRadiation.setChunkRate(level, new ChunkPos(500016 >> 4, 0), 0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerCommandSetsDoseAndPreservesTreatment(GameTestHelper helper) throws CommandSyntaxException {
        var level = helper.getLevel();
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RadiationCommandQA"));
        var source = level.getServer().createCommandSourceStack().withLevel(level)
                .withEntity(player).withSuppressedOutput();
        var state = new CompoundTag();
        state.putDouble("immunity_ticks", 120);
        state.putDouble("radaway", 40);
        var persisted = new CompoundTag();
        persisted.put("nuclearcraft:radiation", state);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        helper.assertTrue(dispatcher.execute("nc_set_player_radiation 42.5", source) == 1
                        && PlayerRadiation.dose(player) == 42.5,
                "The command targets its sender even with temporary immunity");
        dispatcher.execute("nc_set_player_radiation 1e8 @s", source);
        helper.assertTrue(PlayerRadiation.dose(player) == RadiationConfig.MAX_DOSE.get(),
                "The selector form clamps the dose to the configured maximum");
        dispatcher.execute("nc_set_player_radiation -1 @s", source);
        helper.assertTrue(PlayerRadiation.dose(player) == 0 && state.getDouble("immunity_ticks") == 120
                        && state.getDouble("radaway") == 40,
                "Clearing the dose keeps immunity and queued RadAway");
        helper.succeed();
    }

    private static void rejected(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandSourceStack source, String command) {
        try {
            dispatcher.execute(command, source);
        } catch (CommandSyntaxException expected) {
            return;
        }
        helper.fail("Expected the server to reject " + command);
    }
}
