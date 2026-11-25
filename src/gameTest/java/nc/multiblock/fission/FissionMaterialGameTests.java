package nc.multiblock.fission;

import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.NCFission;
import nc.recipe.FissionMaterialRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionMaterialGameTests {
    private FissionMaterialGameTests() {}

    @GameTest(template = "fission", timeoutTicks = 110)
    public static void recipeMaterialsDriveFluxAndWallPriming(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 5, 7))) {
            if (pos.getX() == 1 || pos.getX() == 7 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 7) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        var controllerPos = new BlockPos(1, 3, 3);
        var fuelPos = new BlockPos(3, 3, 3);
        var source = new BlockPos(3, 3, 1);
        helper.setBlock(controllerPos, NCFission.CONTROLLER.get());
        helper.setBlock(fuelPos, NCFission.CELL.get());
        helper.setBlock(fuelPos.east(), Blocks.COPPER_BLOCK);
        helper.setBlock(fuelPos.east(2), Blocks.EMERALD_BLOCK);
        helper.setBlock(fuelPos.north(), Blocks.GOLD_BLOCK);
        helper.setBlock(source, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(source.north(), Blocks.REDSTONE_BLOCK);
        var controller = (SolidFissionController) helper.getBlockEntity(controllerPos);
        var cell = (FissionFuelCell) helper.getBlockEntity(fuelPos);
        var materials = FissionMaterials.get(helper.getLevel());
        helper.assertTrue(materials.moderator(Blocks.COPPER_BLOCK.defaultBlockState()).flux() == 20
                        && materials.moderator(Blocks.IRON_BLOCK.defaultBlockState()).flux() == 90
                        && materials.maxModeratorLineFlux() == 360,
                "The exact moderator overrides the higher-priority tag and supplies the irradiator flux scale");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The fuel chamber can form around recipe blocks"))
                .thenExecute(() -> {
                    controller.inventory().setStackInSlot(0, new ItemStack(
                            ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_oxide"))));
                    controller.toggle();
                    helper.assertTrue(controller.primingTarget(helper.getLevel(), helper.absolutePos(source)) == null,
                            "A data-driven total reflector blocks the wall source");
                    helper.setBlock(fuelPos.east(), Blocks.IRON_BLOCK);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(cell.saveWithoutMetadata().getDouble("progress") == 0,
                            "Sufficient reflected flux still requires the unprimed fuel to start");
                    helper.setBlock(fuelPos.north(), Blocks.EMERALD_BLOCK);
                    helper.setBlock(fuelPos.east(), Blocks.COPPER_BLOCK);
                })
                .thenExecuteAfter(5, () -> {
                    var fuel = cell.saveWithoutMetadata();
                    helper.assertTrue(fuel.getDouble("source_efficiency") == 0.9D && fuel.getDouble("progress") == 0,
                            "A partial reflector passes the source but the tagged moderator stays below criticality");
                    helper.setBlock(fuelPos.east(), Blocks.IRON_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(cell.saveWithoutMetadata().getDouble("progress") > 0,
                        "An exact moderator and the custom partial reflector sustain the reaction"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void neutronMaterialsRoundTripWithTagsAndServerProfiles(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var tag = (FissionMaterialRecipe) recipes.byKey(new ResourceLocation("neutron_qa", "tag_moderator")).orElseThrow();
        var stock = (FissionMaterialRecipe) recipes.byKey(NuclearCraft.id("fission_reflector/lead_steel")).orElseThrow();
        for (var recipe : new FissionMaterialRecipe[]{tag, stock}) {
            var packet = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var serializer = NCFission.materialSerializer(recipe.kind());
                serializer.toNetwork(packet, recipe);
                var decoded = serializer.fromNetwork(recipe.id(), packet);
                helper.assertTrue(recipe.equals(decoded) && packet.readableBytes() == 0,
                        "Network sync preserves the selector, numeric values and optional server profile");
            } finally {
                packet.release();
            }
        }
        helper.succeed();
    }
}
