package nc.multiblock.fission;

import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.recipe.FissionFuelStats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionDecayGameTests {
    private FissionDecayGameTests() {}

    @GameTest(template = "empty")
    public static void fuelDecayUsesLegacyStateAndDaughterFractions(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, NCFission.CELL.get());
        var entity = (FissionFuelCell) helper.getBlockEntity(pos);
        var initial = new CompoundTag();
        initial.putDouble("decayProcessHeat", 200);
        initial.putDouble("baseProcessDecayFactor", 0.1);
        initial.putDouble("decayHeatFraction", 0.1);
        initial.putDouble("iodineFraction", 0.5);
        initial.putDouble("poisonFraction", 0.1);
        entity.load(initial);
        var decay = entity.decay();
        var settings = new FissionDecaySettings(new FissionDecaySettings.Step(10, 10, 1, 5),
                new FissionDecaySettings.Step(10, 10, 5, 5), new FissionDecaySettings.Step(10, 20, 1, 0),
                0.95, 0.05);
        helper.assertTrue(decay.heating() == 20, "The converted part recovers 20 H/t from saved fuel");
        decay.tick(1, settings, false);
        CompoundTag saved = entity.saveWithoutMetadata();
        helper.assertTrue(near(saved.getDouble("decayHeatFraction"), 0.09)
                        && near(saved.getDouble("iodineFraction"), 0.5)
                        && near(saved.getDouble("poisonFraction"), 0.345),
                "The loaded fuel loses heat and transfers the decayed fractions in order");
        decay.tick(1, settings, false);
        helper.assertTrue(entity.saveWithoutMetadata().equals(saved), "One game tick advances the decay chain only once");
        entity.load(saved);
        var fuel = (FissionFuelStats) helper.getLevel().getRecipeManager()
                .byKey(NuclearCraft.id("solid_fission/lecf_251_oxide")).orElseThrow();
        helper.assertTrue(near(decay.criticality(fuel), 71 * 1.245) && near(decay.heating(), 18),
                "Reloaded poison changes criticality without restoring lost heat");
        decay.tick(2, null, false);
        helper.assertTrue(!decay.hasFractions() && decay.heating() == 0, "Disabling decay clears the active fractions");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelDecayHandlesZeroDurationsAndUsesTheLatestFuelStats(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, NCSaltFission.VESSEL.get());
        var entity = (SaltVessel) helper.getBlockEntity(pos);
        var fuel = (FissionFuelStats) helper.getLevel().getRecipeManager()
                .byKey(NuclearCraft.id("salt_fission/lecf_251")).orElseThrow();
        var decay = entity.decay();
        var immediate = new FissionDecaySettings.Step(0, 0, 1, 1);
        var settings = new FissionDecaySettings(immediate, immediate, immediate, 1, 0);
        decay.remember(fuel, 400);
        decay.tick(1, settings, true);
        helper.assertTrue(decay.heating() == 40 && decay.criticality(fuel) == 71,
                "Zero buildup time reaches the equilibrium for a burning fuel immediately");
        decay.tick(2, settings, false);
        CompoundTag saved = entity.saveWithoutMetadata();
        helper.assertTrue(saved.getDouble("decayHeatFraction") == 0
                        && saved.getDouble("iodineFraction") == 0.1 && saved.getDouble("poisonFraction") == 0.1,
                "Zero lifetime removes the existing fraction and preserves the transferred daughter");
        decay.tick(3, settings, false);
        decay.tick(4, settings, false);
        helper.assertTrue(!decay.hasFractions(), "Only the remaining transferred daughter waits another tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelDecayCatalogPreservesAndSynchronizesEveryFactor(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var solid = manager.getAllRecipesFor(NCFission.FUEL_TYPE.get());
        var pebble = manager.getAllRecipesFor(NCPebbleFission.FUEL_TYPE.get());
        var salt = manager.getAllRecipesFor(NCSaltFission.FUEL_TYPE.get());
        helper.assertTrue(solid.size() == 81 && pebble.size() == 27 && salt.size() == 27,
                "All legacy fuel forms have a decay factor");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (var recipe : solid) {
                buffer.clear();
                NCFission.FUEL_SERIALIZER.get().toNetwork(buffer, recipe);
                var synced = NCFission.FUEL_SERIALIZER.get().fromNetwork(recipe.id(), buffer);
                helper.assertTrue(synced.decayFactor() == recipe.decayFactor() && synced.radiation() == recipe.radiation()
                                && !synced.pebbleBed() && buffer.readableBytes() == 0 && synced.decayFactor() > 0,
                        "Item fuel network round trip must preserve decay and radiation for " + recipe.id());
            }
            for (var recipe : pebble) {
                buffer.clear();
                NCPebbleFission.FUEL_SERIALIZER.get().toNetwork(buffer, recipe);
                var synced = NCPebbleFission.FUEL_SERIALIZER.get().fromNetwork(recipe.id(), buffer);
                helper.assertTrue(synced.decayFactor() == recipe.decayFactor() && synced.radiation() == recipe.radiation()
                                && synced.pebbleBed() && buffer.readableBytes() == 0 && synced.decayFactor() > 0,
                        "TRISO network round trip must preserve decay and radiation for " + recipe.id());
            }
        } finally {
            buffer.release();
        }
        helper.assertTrue(salt.stream().allMatch(recipe -> recipe.decayFactor() > 0),
                "The molten-salt catalog already carries its original decay factors");
        helper.succeed();
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1E-9;
    }
}
