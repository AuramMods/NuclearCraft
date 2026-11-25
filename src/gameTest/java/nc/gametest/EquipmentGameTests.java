package nc.gametest;

import nc.NuclearCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    @GameTest(template = "empty")
    public static void equipmentRecipesAndRepairUseForgeIngredients(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String family : List.of("boron", "tough", "hard_carbon", "boron_nitride")) {
            var material = stack(family.equals("boron_nitride") ? family : family + "_ingot");
            for (String piece : List.of("sword", "pickaxe", "shovel", "axe", "hoe", "helm", "chest", "legs", "boots")) {
                String id = piece + "_" + family;
                var item = stack(id);
                var recipe = (ShapedRecipe) level.getRecipeManager().byKey(NuclearCraft.id("crafting/" + id)).orElseThrow();
                var grid = MaterialGameTests.craftingGrid(recipe.getWidth(), recipe.getHeight());
                for (int i = 0; i < recipe.getIngredients().size(); i++) {
                    var ingredient = recipe.getIngredients().get(i);
                    if (ingredient.test(material)) {
                        grid.setItem(i, material.copy());
                    } else if (ingredient.test(Items.STICK.getDefaultInstance())) {
                        grid.setItem(i, Items.STICK.getDefaultInstance());
                    } else {
                        helper.assertTrue(ingredient.isEmpty(), "Expected a shared material, wooden rod, or empty slot: " + id);
                    }
                }
                helper.assertTrue(recipe.matches(grid, level) && recipe.assemble(grid, level.registryAccess()).is(item.getItem()),
                        "Recipe should craft " + id);
                var repair = family.equals("boron") && item.getItem() instanceof ArmorItem ? stack("boron_ore") : material;
                helper.assertTrue(item.getItem().isValidRepairItem(item, repair), "Material should repair " + id);
            }

            var recipe = (ShapedRecipe) level.getRecipeManager().byKey(
                    NuclearCraft.id("crafting/spaxelhoe_" + family)).orElseThrow();
            var grid = MaterialGameTests.craftingGrid(3, 3);
            grid.setItem(0, stack("axe_" + family));
            grid.setItem(1, stack("shovel_" + family));
            grid.setItem(2, stack("pickaxe_" + family));
            grid.setItem(3, stack("hoe_" + family));
            grid.setItem(4, Items.IRON_INGOT.getDefaultInstance());
            grid.setItem(5, stack("sword_" + family));
            grid.setItem(7, Items.IRON_INGOT.getDefaultInstance());
            var result = recipe.assemble(grid, level.registryAccess());
            helper.assertTrue(recipe.matches(grid, level) && result.is(stack("spaxelhoe_" + family).getItem())
                    && result.getItem().isValidRepairItem(result, material), "Five matching tools craft a repairable Spaxelhoe");
            grid.setItem(0, Items.IRON_AXE.getDefaultInstance());
            helper.assertTrue(!recipe.matches(grid, level), "A foreign axe cannot stand in for the rest of the set");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentPreservesLegacyStatsAndHarvestLevels(GameTestHelper helper) {
        var boron = stack("pickaxe_boron");
        var tough = stack("pickaxe_tough");
        var obsidian = Blocks.OBSIDIAN.defaultBlockState();
        helper.assertTrue(boron.getMaxDamage() == 547 && tough.getMaxDamage() == 929,
                "Pickaxes retain their legacy durability");
        helper.assertTrue(boron.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())
                        && !boron.isCorrectToolForDrops(obsidian) && tough.isCorrectToolForDrops(obsidian)
                        && tough.getDestroySpeed(obsidian) == 10F && tough.getDestroySpeed(Blocks.DIRT.defaultBlockState()) == 1F,
                "Mining respects the material tier and native harvest rules");
        var sword = stack("sword_boron_nitride");
        var hoe = stack("hoe_tough");
        helper.assertTrue(sword.getMaxDamage() == 1928 && sword.getItem().getEnchantmentValue(sword) == 20
                        && attribute(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) == 6.5
                        && attribute(hoe, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) == 0
                        && attribute(hoe, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED) == 0,
                "Tools expose legacy attack and enchantment values through Forge");
        var chest = stack("chest_boron_nitride");
        helper.assertTrue(chest.getMaxDamage() == 16 * 42 && chest.getItem().getEnchantmentValue(chest) == 20
                        && attribute(chest, EquipmentSlot.CHEST, Attributes.ARMOR) == 8
                        && attribute(chest, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) == 2
                        && chest.getAttributeModifiers(EquipmentSlot.HEAD).isEmpty()
                        && chest.is(ItemTags.TRIMMABLE_ARMOR) && chest.is(Tags.Items.ARMORS_CHESTPLATES),
                "Armor uses the correct slot, durability, defense, toughness, and enchantability");
        var hazmat = stack("chest_hazmat");
        helper.assertTrue(hazmat.getMaxDamage() == 0 && !hazmat.isDamageableItem()
                        && hazmat.getItem().getEnchantmentValue(hazmat) == 5
                        && attribute(hazmat, EquipmentSlot.CHEST, Attributes.ARMOR) == 7,
                "Stock Hazmat armor preserves its legacy defense and has no durability bar");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spaxelhoeMinesAndTillsWithOneTool(GameTestHelper helper) {
        var spaxel = stack("spaxelhoe_boron");
        helper.assertTrue(spaxel.getMaxDamage() == 2735 && spaxel.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) == 8F
                        && spaxel.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 8F
                        && spaxel.getDestroySpeed(Blocks.DIRT.defaultBlockState()) == 8F
                        && spaxel.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())
                        && !spaxel.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
                        && spaxel.canPerformAction(ToolActions.AXE_DIG) && spaxel.canPerformAction(ToolActions.PICKAXE_DIG)
                        && spaxel.canPerformAction(ToolActions.SHOVEL_DIG) && spaxel.canPerformAction(ToolActions.HOE_TILL)
                        && spaxel.is(Tags.Items.TOOLS),
                "The combined tool is effective across blocks and still respects tier requirements");
        var pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, Blocks.DIRT);
        var player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, spaxel);
        var absolute = helper.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(spaxel.getItem().useOn(context).consumesAction() && spaxel.getDamageValue() == 1,
                "Tilling uses the native Forge hoe action and costs one durability");
        helper.assertBlockPresent(Blocks.FARMLAND, pos);
        helper.succeed();
    }

    private static ItemStack stack(String id) {
        return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), id));
    }

    private static double attribute(ItemStack stack, EquipmentSlot slot, Attribute key) {
        return stack.getAttributeModifiers(slot).get(key).stream().mapToDouble(modifier -> modifier.getAmount()).sum();
    }
}
