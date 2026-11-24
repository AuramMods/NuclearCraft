package nc.processor;

import nc.NuclearCraft;
import nc.init.NCRegistries;
import nc.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NuclearFurnaceGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private NuclearFurnaceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nuclearFurnaceRunsWithHoppersAndLegacySideRules(GameTestHelper helper) {
        helper.setBlock(POS, NCRegistries.NUCLEAR_FURNACE.get());
        helper.setBlock(POS.above(), Blocks.HOPPER);
        helper.setBlock(POS.below(), Blocks.HOPPER);
        var furnace = (NuclearFurnaceBlockEntity) helper.getBlockEntity(POS);
        var feeder = (HopperBlockEntity) helper.getBlockEntity(POS.above());
        var receiver = (HopperBlockEntity) helper.getBlockEntity(POS.below());
        var top = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var west = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        var bottom = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(AssertionError::new);
        var uranium = material("uranium_ingot");
        helper.assertTrue(top.getSlots() == 1 && west.getSlots() == 2 && bottom.getSlots() == 2,
                "The top exposes the charge and the five other faces expose fuel and output");
        helper.assertTrue(west.insertItem(0, new ItemStack(Items.COAL), false).is(Items.COAL), "Coal cannot fuel the furnace");
        helper.assertTrue(bottom.insertItem(0, uranium, true).isEmpty() && bottom.getStackInSlot(0).isEmpty(),
                "Fuel can enter from below and simulated insertion leaves it unconsumed");
        helper.assertTrue(west.insertItem(0, uranium, false).isEmpty() && west.extractItem(0, 1, false).isEmpty(),
                "Fuel can enter through the wall and stays there until the furnace needs it");
        feeder.setItem(0, new ItemStack(Items.COBBLESTONE, 2));

        helper.succeedWhen(() -> {
            helper.assertTrue(receiver.getItem(0).is(Items.STONE) && receiver.getItem(0).getCount() == 2,
                    "Two furnace smelts should move through the hoppers");
            helper.assertTrue(feeder.isEmpty() && furnace.isEmpty(), "The two charges and one fuel were consumed once");
            helper.assertTrue(furnace.getBlockState().getValue(NuclearFurnaceBlock.LIT)
                    && furnace.radiationEmission() == RadiationSources.LEU_235_FISSION,
                    "Stored burn time keeps the furnace lit and emitting the legacy Rad/t");
        });
    }

    @GameTest(template = "empty")
    public static void nuclearFurnaceKeepsFuelAndProgressAcrossReload(GameTestHelper helper) {
        helper.setBlock(POS, NCRegistries.NUCLEAR_FURNACE.get());
        var furnace = (NuclearFurnaceBlockEntity) helper.getBlockEntity(POS);
        var side = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 2));
        furnace.setItem(1, material("uranium_block"));
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 64));
        tick(helper, furnace, 12);
        helper.assertTrue(furnace.getItem(1).is(material("uranium_block").getItem()) && furnace.radiationEmission() == 0D,
                "A blocked output must leave the input and fuel untouched");
        var output = side.orElseThrow(AssertionError::new);
        helper.assertTrue(output.extractItem(1, 64, true).getCount() == 64 && output.getStackInSlot(1).getCount() == 64,
                "Simulated extraction leaves the full output untouched");
        output.extractItem(1, 64, false);

        tick(helper, furnace, 9);
        CompoundTag saved = furnace.saveWithoutMetadata();
        helper.assertTrue(saved.getInt("BurnTime") == 3192 && saved.getInt("BurnDuration") == 3200
                && saved.getInt("CookTime") == 9 && furnace.getItem(2).isEmpty(),
                "The uranium block ignites for 3,200 ticks and the first smelt takes ten ticks");
        helper.setBlock(POS, Blocks.AIR);
        helper.assertTrue(!side.isPresent(), "Breaking the furnace invalidates its old capability");
        helper.setBlock(POS, NCRegistries.NUCLEAR_FURNACE.get());
        var restored = (NuclearFurnaceBlockEntity) helper.getBlockEntity(POS);
        restored.load(saved);
        tick(helper, restored, 1);
        helper.assertTrue(restored.getItem(0).getCount() == 1 && restored.getItem(2).is(Items.IRON_INGOT)
                && restored.getItem(2).getCount() == 1 && restored.getItem(1).isEmpty()
                && restored.saveWithoutMetadata().getInt("BurnDuration") == 3200,
                "Restored progress finishes the exact pending smelt and preserves the original duration");
        helper.assertTrue(NuclearFurnaceBlockEntity.uraniumBurnTime(material("uranium_dust")) == 320
                && NuclearFurnaceBlockEntity.uraniumBurnTime(material("uranium_ingot")) == 320,
                "Uranium dusts and ingots both yield 320 ticks");
        helper.succeed();
    }

    private static void tick(GameTestHelper helper, NuclearFurnaceBlockEntity furnace, int count) {
        for (int i = 0; i < count; i++) {
            NuclearFurnaceBlockEntity.tick(helper.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
        }
    }

    private static ItemStack material(String path) {
        return ForgeRegistries.ITEMS.getValue(NuclearCraft.id(path)).getDefaultInstance();
    }
}
