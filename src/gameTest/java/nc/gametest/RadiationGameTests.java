package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCGenerators;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRadiation;
import nc.radiation.ChunkRadiationSource;
import nc.radiation.PlayerRadiation;
import nc.radiation.RadiationSources;
import nc.radiation.WorldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationGameTests {
    private RadiationGameTests() {}

    @GameTest(template = "empty")
    public static void inventoryRadiationAndResistanceSurviveSave(GameTestHelper helper) {
        var player = player(helper, "TestDose");
        player.getInventory().setItem(0, stack("californium_252_ingot", 16));
        player.getInventory().offhand.set(0, stack("californium_252_oxide_ingot", 2));
        PlayerRadiation.tick(player, 5);
        double unshielded = 18 * 0.38;
        near(helper, PlayerRadiation.rate(player), unshielded, "Inventory and offhand contribute");
        near(helper, PlayerRadiation.dose(player), 5 * unshielded, "Dose is integrated over the tick interval");
        near(helper, RadiationSources.itemRate(stack("depleted_hecf_249_oxide", 1)),
                (0.0765 + 6 * 0.38 + 0.98 * 1.5 * 0.6 + 0.38 * 1.5 * 0.4) / 9,
                "Depleted fuel keeps its legacy mixed-isotope rate");

        ItemStack shielded = new ItemStack(Items.IRON_CHESTPLATE);
        shielded.getOrCreateTag().putDouble("ncRadiationResistance", 1);
        player.setItemSlot(EquipmentSlot.CHEST, shielded);
        PlayerRadiation.tick(player, 5);
        double main = 16 * 0.38;
        double offhand = 2 * 0.38;
        near(helper, PlayerRadiation.rate(player), main * main / (main + 1) + offhand * offhand / (offhand + 1),
                "Shielding applies to each stack separately");
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        var restored = player(helper, "TestReload");
        restored.load(saved);
        near(helper, PlayerRadiation.dose(restored), PlayerRadiation.dose(player),
                "Dose survives player serialization");
        restored.setItemInHand(InteractionHand.MAIN_HAND, NCRadiation.GEIGER_COUNTER.get().getDefaultInstance());
        helper.assertTrue(NCRadiation.GEIGER_COUNTER.get().use(helper.getLevel(), restored, InteractionHand.MAIN_HAND)
                .getResult().consumesAction(), "The Geiger counter handles a player reading");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treatmentAndDeathImmunityUseLegacyRates(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TestMedicine")) {
            @Override
            public boolean hurt(DamageSource source, float amount) {
                helper.assertTrue(source.type().msgId().equals("fatal_rads") && source.is(DamageTypeTags.BYPASSES_ARMOR),
                        "The fatal radiation damage source bypasses armor");
                helper.assertTrue(amount == Float.MAX_VALUE, "The fatal dose deals lethal damage");
                setHealth(0);
                return true;
            }
        };
        player.moveTo(5000000, 100, 5000000, 0, 0);
        player.getInventory().setItem(2, stack("californium_252_ingot", 64));
        PlayerRadiation.tick(player, 20);
        double dose = PlayerRadiation.dose(player);
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Crossing 40 percent causes radiation sickness");
        player.getInventory().clearContent();
        administer(helper, player, NCRadiation.RADAWAY.get().getDefaultInstance());
        administer(helper, player, NCRadiation.SLOW_RADAWAY.get().getDefaultInstance());
        PlayerRadiation.tick(player, 5);
        near(helper, PlayerRadiation.dose(player), dose * Math.pow(1 - 5E-7, 5) - 25.125,
                "Normal and slow RadAway drain distinct buffers at legacy rates");

        double doseBeforeRadX = PlayerRadiation.dose(player);
        administer(helper, player, NCRadiation.RAD_X.get().getDefaultInstance());
        player.getInventory().setItem(2, stack("californium_252_ingot", 64));
        PlayerRadiation.tick(player, 5);
        double raw = 64 * 0.38;
        near(helper, PlayerRadiation.rate(player), raw * raw / (raw + 25), "Rad-X adds 25 resistance");
        helper.assertTrue(PlayerRadiation.dose(player) > doseBeforeRadX, "Exposure continues while treatment runs");

        var respawn = player(helper, "TestRespawn");
        PlayerRadiation.onClone(new PlayerEvent.Clone(respawn, player, true));
        double retained = PlayerRadiation.dose(player) * 0.75;
        near(helper, PlayerRadiation.dose(respawn), retained, "Death retains the configured dose fraction");
        respawn.getInventory().setItem(2, stack("californium_252_ingot", 64));
        PlayerRadiation.tick(respawn, 5);
        near(helper, PlayerRadiation.dose(respawn), retained * Math.pow(1 - 5E-7, 5),
                "Respawn immunity protects against exposure while decay continues");
        helper.assertTrue(!respawn.hasEffect(MobEffects.WEAKNESS), "Immunity suppresses radiation sickness");

        PlayerRadiation.tick(player, 400);
        helper.assertTrue(!player.isAlive(), "Fatal radiation bypasses armor at the dose cap");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 630)
    public static void infuserMakesRadawayThroughForgeCapabilities(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        for (String id : new String[]{"crafting/geiger_counter", "crafting/rad_x", "infuser/radaway_slow",
                "infuser/radaway_slow_from_radaway"}) {
            helper.assertTrue(recipes.byKey(NuclearCraft.id(id)).isPresent(), "Missing recipe " + id);
        }
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Machine.INFUSER.block());
        var processor = helper.getBlockEntity(pos);
        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var fluid = processor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var energy = processor.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, stack("bioplastic", 2), false).isEmpty(), "Insert two bioplastic");
        var radaway = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("radaway"));
        helper.assertTrue(fluid.fill(new FluidStack(radaway, 250),
                FluidAction.EXECUTE) == 250, "Fill 250 mB of RadAway");
        helper.assertTrue(energy.receiveEnergy(3000, false) == 3000, "Supply 3000 FE");
        helper.succeedWhen(() -> {
            helper.assertTrue(items.getStackInSlot(1).is(NCRadiation.RADAWAY.get()), "Infuser dispenses one RadAway");
            helper.assertTrue(items.getStackInSlot(0).isEmpty() && fluid.getFluidInTank(0).isEmpty()
                    && energy.getEnergyStored() == 0, "Recipe spends exactly the input items, fluid and energy");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void geigerBlockMeasuresTheChunkAndUpdatesARedstoneComparator(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, NCRadiation.GEIGER_BLOCK.get());
        helper.setBlock(pos.east().below(), Blocks.STONE);
        helper.setBlock(pos.east(), Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        WorldRadiation.addLeak(helper.getLevel(), helper.absolutePos(pos), 100_000);
        helper.succeedWhen(() -> {
            helper.assertTrue(NCRadiation.GEIGER_BLOCK.get().getAnalogOutputSignal(helper.getBlockState(pos),
                    helper.getLevel(), helper.absolutePos(pos)) == 15, "The logarithmic Geiger output saturates at 15");
            var comparator = helper.getBlockState(pos.east());
            helper.assertTrue(comparator.is(Blocks.COMPARATOR) && comparator.getValue(ComparatorBlock.POWERED),
                    "A nearby comparator receives the changing radiation signal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void placedRtgExposesAnEmptyHandedPlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, NCGenerators.PassiveGenerator.RTG_CALIFORNIUM.block());
        var source = (ChunkRadiationSource) helper.getBlockEntity(pos);
        near(helper, source.radiationEmission(), 0.0765 / 8, "The placed RTG emits the legacy rate");
        var player = player(helper, "TestAmbient");
        BlockPos absolute = helper.absolutePos(pos);
        player.moveTo(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5, 0, 0);
        helper.succeedWhen(() -> {
            double ambient = WorldRadiation.rate(helper.getLevel(), absolute);
            helper.assertTrue(ambient > 0, "A placed RTG raises its chunk radiation");
            PlayerRadiation.tick(player, 5);
            near(helper, PlayerRadiation.rate(player), ambient, "Ambient radiation reaches a player with an empty inventory");
        });
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        // Keep inventory-only cases separate from the active emitters in the GameTest area.
        player.moveTo(5000000, 100, 5000000, 0, 0);
        return player;
    }

    private static ItemStack stack(String id, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), count);
    }

    private static void administer(GameTestHelper helper, FakePlayer player, ItemStack stack) {
        helper.assertTrue(stack.finishUsingItem(helper.getLevel(), player).isEmpty(),
                "Medicine is consumed in survival");
    }

    private static void near(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-10, message + " (expected " + expected + ", got " + actual + ")");
    }
}
