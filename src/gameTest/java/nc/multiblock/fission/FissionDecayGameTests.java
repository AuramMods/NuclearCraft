package nc.multiblock.fission;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.LegacyFissionFuelConfig.Family;
import nc.init.LegacyFissionFuelConfig.Profile;
import nc.init.LegacyFissionFuelConfig.Selection;
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

import java.util.HashSet;

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
    public static void fissionFuelCatalogKeepsItsLegacyArraySlotsAcrossNetworkSync(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var solid = manager.getAllRecipesFor(NCFission.FUEL_TYPE.get()).stream()
                .filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).toList();
        var pebble = manager.getAllRecipesFor(NCPebbleFission.FUEL_TYPE.get());
        var salt = manager.getAllRecipesFor(NCSaltFission.FUEL_TYPE.get()).stream()
                .filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).toList();
        helper.assertTrue(solid.size() == 81 && pebble.size() == 27 && salt.size() == 27,
                "Load every legacy fuel form");
        helper.assertTrue(manager.byKey(NuclearCraft.id("solid_fission/yellorium")).isEmpty(),
                "The optional solid fuel stays out of a clean Forge catalog");
        var selections = new HashSet<Selection>();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var solidSerializer = NCFission.FUEL_SERIALIZER.get();
            for (var recipe : solid) {
                buffer.clear();
                solidSerializer.toNetwork(buffer, recipe);
                var synced = solidSerializer.fromNetwork(recipe.id(), buffer);
                Selection selection = synced.parameters().legacy();
                String path = recipe.id().getPath();
                int form = path.endsWith("_oxide") ? 1 : path.endsWith("_nitride") ? 2 : 3;
                helper.assertTrue(synced.parameters().equals(recipe.parameters()) && synced.ticks() == recipe.ticks()
                                && selection != null && selection.index() % 5 == form && selections.add(selection)
                                && !synced.pebbleBed() && buffer.readableBytes() == 0 && synced.decayFactor() > 0,
                        "Solid fuel sync must preserve the values and its unique legacy array slot for " + recipe.id());
            }
            var pebbleSerializer = NCPebbleFission.FUEL_SERIALIZER.get();
            for (var recipe : pebble) {
                buffer.clear();
                pebbleSerializer.toNetwork(buffer, recipe);
                var synced = pebbleSerializer.fromNetwork(recipe.id(), buffer);
                Selection selection = synced.parameters().legacy();
                helper.assertTrue(synced.parameters().equals(recipe.parameters()) && synced.ticks() == recipe.ticks()
                                && selection != null && selection.index() % 5 == 0 && selections.add(selection)
                                && synced.pebbleBed() && buffer.readableBytes() == 0 && synced.decayFactor() > 0,
                        "TRISO sync must preserve its values and unique legacy array slot for " + recipe.id());
            }
            var saltSerializer = NCSaltFission.FUEL_SERIALIZER.get();
            for (var recipe : salt) {
                buffer.clear();
                saltSerializer.toNetwork(buffer, recipe);
                var synced = saltSerializer.fromNetwork(recipe.id(), buffer);
                Selection selection = synced.parameters().legacy();
                helper.assertTrue(synced.parameters().equals(recipe.parameters()) && synced.ticks() == recipe.ticks()
                                && selection != null && selection.index() % 5 == 4 && selections.add(selection)
                                && buffer.readableBytes() == 0 && synced.decayFactor() > 0,
                        "Salt fuel sync must preserve fractional ticks and its unique legacy array slot for " + recipe.id());
            }
            var leu = (FissionFuelStats) manager.byKey(NuclearCraft.id("solid_fission/leu_235_oxide")).orElseThrow();
            helper.assertTrue(new Selection(Family.URANIUM, 11).equals(leu.parameters().legacy()),
                    "LEU-235 oxide uses slot 11 in the original uranium section");
            var custom = solidSerializer.fromJson(NuclearCraft.id("solid_fission/leu_235_oxide"),
                    JsonParser.parseString("""
                            {"fuel":{"item":"nuclearcraft:leu_235_oxide"},"result":{"item":"nuclearcraft:depleted_leu_235_oxide"},
                             "ticks":55,"heat":18,"efficiency":0.75,"criticality":1}
                            """).getAsJsonObject());
            buffer.clear();
            solidSerializer.toNetwork(buffer, custom);
            var synced = solidSerializer.fromNetwork(custom.id(), buffer);
            helper.assertTrue(synced.parameters().legacy() == null && synced.ticks() == 55 && synced.heat() == 18
                            && synced.efficiency() == 0.75 && buffer.readableBytes() == 0,
                    "A replacement at the shipped ID can send its own values with no legacy binding");
            var external = solidSerializer.fromJson(NuclearCraft.id("solid_fission/yellorium"),
                    JsonParser.parseString("""
                            {"fuel":{"item":"minecraft:copper_ingot"},"result":{"item":"minecraft:gold_ingot"},
                             "ticks":2700,"heat":106,"efficiency":1,"criticality":102,"self_priming":true,
                             "legacy_fuel":{"family":"uranium","index":11,"profile":"yellorium"}}
                            """).getAsJsonObject());
            buffer.clear();
            solidSerializer.toNetwork(buffer, external);
            var syncedExternal = solidSerializer.fromNetwork(external.id(), buffer);
            helper.assertTrue(new Selection(Family.URANIUM, 11, Profile.YELLORIUM).equals(syncedExternal.parameters().legacy())
                            && syncedExternal.ticks() == 2700 && syncedExternal.heat() == 106
                            && !syncedExternal.selfPriming() && buffer.readableBytes() == 0,
                    "The optional fuel keeps its conversion profile and requires an external source");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1E-9;
    }
}
