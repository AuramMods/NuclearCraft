package nc.gametest;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.NCGasCoolants;
import nc.init.NCRegistries;
import nc.recipe.FluidContainerRecipe;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialGameTests {
    @GameTest(template = "empty")
    public static void legacyFurnaceFuelsKeepTheirBurnTimes(GameTestHelper helper) {
        for (String id : new String[]{"graphite_ingot", "graphite_dust", "coal_dust"}) {
            helper.assertTrue(ForgeHooks.getBurnTime(item(id).getDefaultInstance(), RecipeType.SMELTING) == 1_600,
                    id + " burns for 1,600 ticks");
        }
        helper.assertTrue(ForgeHooks.getBurnTime(item("graphite_block").getDefaultInstance(), RecipeType.SMELTING)
                == 16_000, "A graphite block burns for 16,000 ticks");
        for (Item sugar : new Item[]{Items.SUGAR_CANE, Items.SUGAR}) {
            helper.assertTrue(ForgeHooks.getBurnTime(sugar.getDefaultInstance(), RecipeType.SMELTING) == 200,
                    sugar + " burns for 200 ticks");
        }
        helper.assertTrue(ForgeHooks.getBurnTime(Items.COAL.getDefaultInstance(), RecipeType.SMELTING) == 1_600,
                "Vanilla coal retains its original burn time");
        helper.assertTrue(ForgeHooks.getBurnTime(item("boron_nitride_dust").getDefaultInstance(), RecipeType.SMELTING)
                == 0, "Other dusts do not become furnace fuel");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void taggedMaterialPackingKeepsItsMatchingRulesAfterSync(GameTestHelper helper) {
        var id = NuclearCraft.id("materials/packing/copper_block");
        var json = JsonParser.parseString("""
                {"pattern":["III","III","III"],"key":{"I":{"tag":"forge:ingots/copper"}},
                 "result":{"item":"minecraft:copper_block"}}
                """).getAsJsonObject();
        var packing = NCRegistries.MATERIAL_SHAPED.get();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packing.toNetwork(buffer, RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
            var synced = packing.fromNetwork(id, buffer);
            var grid = craftingGrid(3, 3);
            for (int slot = 0; slot < grid.getContainerSize(); slot++) {
                grid.setItem(slot, Items.COPPER_INGOT.getDefaultInstance());
            }
            helper.assertTrue(synced.matches(grid, helper.getLevel())
                            && synced.assemble(grid, helper.getLevel().registryAccess()).is(Items.COPPER_BLOCK),
                    "Nine of the same tagged ingot produce the synced block");
            grid.setItem(0, item("copper_ingot").getDefaultInstance());
            helper.assertTrue(!synced.matches(grid, helper.getLevel()), "Mixed ingot IDs cannot craft a storage block");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingUsesSharedCopperAndLeavesWaterBucket(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var copper = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("forge", "ingots/copper"));
        helper.assertTrue(item("copper_ingot").getDefaultInstance().is(copper)
                && Items.COPPER_INGOT.getDefaultInstance().is(copper), "Both copper ingots belong to the Forge tag");

        var crafting = craftingGrid(2, 3);
        crafting.setItem(0, Items.COPPER_INGOT.getDefaultInstance());
        crafting.setItem(1, item("copper_ingot").getDefaultInstance());
        crafting.setItem(2, Items.IRON_INGOT.getDefaultInstance());
        crafting.setItem(3, Items.IRON_INGOT.getDefaultInstance());
        crafting.setItem(4, item("copper_ingot").getDefaultInstance());
        crafting.setItem(5, Items.COPPER_INGOT.getDefaultInstance());
        var wire = recipes.getRecipeFor(RecipeType.CRAFTING, crafting, helper.getLevel()).orElseThrow();
        var result = wire.assemble(crafting, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(item("wire_copper")) && result.getCount() == 2,
                "Copper wire recipes accept both tagged ingots and yield two wires");

        crafting.clearContent();
        crafting.setItem(0, item("empty_sink").getDefaultInstance());
        crafting.setItem(1, Items.WATER_BUCKET.getDefaultInstance());
        var sink = recipes.getRecipeFor(RecipeType.CRAFTING, crafting, helper.getLevel()).orElseThrow();
        var remainder = sink.getRemainingItems(crafting);
        helper.assertTrue(sink.assemble(crafting, helper.getLevel().registryAccess()).is(item("water_heat_sink"))
                && remainder.get(1).is(Items.BUCKET), "Crafting the water heat sink returns the empty bucket");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fissionCraftingDrainsOnlyTheMatchedFluidContainer(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var recipes = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(FluidContainerRecipe.class::isInstance).toList();
        helper.assertTrue(recipes.size() == 39, "All 39 legacy fluid-container recipes load");
        for (var recipe : recipes) {
            var display = recipe.getIngredients();
            helper.assertTrue(display.size() == 2 && display.get(1).getItems().length > 0,
                    "Recipe lookup can display a compatible bucket for " + recipe.getId());
            var grid = craftingGrid(2, 2);
            var bucket = display.get(1).getItems()[0].copy();
            grid.setItem(3, display.get(0).getItems()[0].copy());
            grid.setItem(0, bucket);
            helper.assertTrue(recipe.matches(grid, level), "The reversed arrangement works for " + recipe.getId());
            helper.assertTrue(recipe.getRemainingItems(grid).get(0).is(Items.BUCKET)
                            && grid.getItem(0).is(bucket.getItem()) && FluidUtil.getFluidContained(bucket).isPresent(),
                    "Crafting returns the container and leaves the input stack intact during calculation");
            grid.setItem(2, Items.STICK.getDefaultInstance());
            helper.assertTrue(!recipe.matches(grid, level), "An extra item does not match the two-input recipe");
        }
        var grid = craftingGrid(2, 1);
        grid.setItem(1, item("empty_cooler").getDefaultInstance());
        grid.setItem(0, Items.WATER_BUCKET.getDefaultInstance());
        var oxygen = (FluidContainerRecipe) manager.byKey(NuclearCraft.id("crafting/" + NCGasCoolants.OXYGEN.coolerId()))
                .orElseThrow();
        helper.assertTrue(!oxygen.matches(grid, level), "An incompatible fluid does not match");
        grid.setItem(0, FluidUtil.getFilledBucket(new FluidStack(NCGasCoolants.OXYGEN.cold(), 1_000)));
        helper.assertTrue(oxygen.matches(grid, level)
                        && oxygen.assemble(grid, level.registryAccess()).is(item(NCGasCoolants.OXYGEN.coolerId())),
                "Craft a pebble cooler with the tagged fluid in the player-sized grid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftsLegacyFoodAndPortableEnderChest(GameTestHelper helper) {
        var grid = craftingGrid(3, 3);
        var recipes = helper.getLevel().getRecipeManager();
        for (Item wool : new Item[]{Items.BLACK_WOOL, Items.PURPLE_WOOL}) {
            fillGrid(grid, Items.AIR, Items.STRING, Items.AIR, wool, Items.ENDER_CHEST, wool,
                    item("tough_ingot"), wool, item("tough_ingot"));
            assertCrafts(helper, grid, item("portable_ender_chest"), 1);
        }
        grid.setItem(7, Items.BLACK_WOOL.getDefaultInstance());
        helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).isEmpty(),
                "The purple and black wool recipes each use one color");
        fillGrid(grid, Items.BREAD, Items.BREAD, Items.BREAD, Items.COOKED_PORKCHOP, Items.COOKED_BEEF,
                Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.BROWN_MUSHROOM, Items.BROWN_MUSHROOM);
        assertCrafts(helper, grid, item("dominos"), 4);
        fillGrid(grid, Items.COOKED_SALMON, item("silica"));
        assertCrafts(helper, grid, item("fish_a_la_fred"), 1);
        fillGrid(grid, item("flour"), item("cocoa_solids"), item("flour"));
        assertCrafts(helper, grid, Items.COOKIE, 8);
        fillGrid(grid, item("graham_cracker"), item("dark_chocolate"), item("marshmallow"), item("graham_cracker"));
        helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).isEmpty(),
                "The stock s'more uses the milk chocolate bar");
        Item previous = item("graham_cracker");
        for (String id : new String[]{"smore", "moresmore", "foursmore"}) {
            Item next = item(id);
            fillGrid(grid, previous, item("milk_chocolate"), item("marshmallow"), previous);
            assertCrafts(helper, grid, next, 1);
            previous = next;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftsDimensionalBlendFromObsidianAndEndStoneDust(GameTestHelper helper) {
        var grid = craftingGrid(3, 3);
        var obsidian = item("obsidian_dust");
        fillGrid(grid, obsidian, obsidian, item("end_stone_dust"), obsidian, obsidian);
        assertCrafts(helper, grid, item("dimensional_blend"), 2);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void prismarineShardsMakeCoolingPartsAndCrystals(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        var grinding = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id("manufactory/prismarine_dust")).orElseThrow();
        helper.assertTrue(grinding.acceptsItem(Items.PRISMARINE_SHARD.getDefaultInstance())
                        && !grinding.acceptsItem(Items.PRISMARINE_CRYSTALS.getDefaultInstance())
                        && ItemStack.matches(grinding.getResultItem(level.registryAccess()), Items.PRISMARINE_CRYSTALS.getDefaultInstance())
                        && grinding.ticks() == 400 && grinding.energyPerTick() == 20,
                "Grinding makes a different prismarine item for 8,000 FE");

        var grid = craftingGrid(3, 3);
        var parts = new String[][]{
                {"prismarine_heat_sink", "empty_sink"},
                {"prismarine_coolant_heater", "standard_coolant_heater"},
                {"fission_heater_port_prismarine", "fission_heater_port_standard"}
        };
        for (String[] pair : parts) {
            var recipe = (CraftingRecipe) recipes.byKey(NuclearCraft.id("crafting/" + pair[0])).orElseThrow();
            fillGrid(grid, Items.AIR, Items.PRISMARINE_SHARD, Items.AIR, Items.PRISMARINE_SHARD, item(pair[1]),
                    Items.PRISMARINE_SHARD, Items.AIR, Items.PRISMARINE_SHARD, Items.AIR);
            helper.assertTrue(recipe.matches(grid, level), "Craft " + pair[0] + " with four prismarine shards");
            grid.setItem(1, Items.PRISMARINE_CRYSTALS.getDefaultInstance());
            helper.assertTrue(!recipe.matches(grid, level), "Crystals cannot replace shards in " + pair[0]);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftsElitePlatingMushroomsAndRecords(GameTestHelper helper) {
        var grid = craftingGrid(3, 3);
        fillGrid(grid, item("rhodochrosite_dust"), item("calcium_sulfate"), item("obsidian_dust"), item("magnesium_dust"));
        assertCrafts(helper, grid, item("crystal_binder"), 2);

        Item binder = item("crystal_binder");
        Item boron = item("boron_ingot");
        fillGrid(grid, binder, boron, binder, boron, item("plate_du"), boron, binder, boron, binder);
        assertCrafts(helper, grid, item("plate_elite"), 1);

        Item plating = item("plate_elite");
        Item borax = item("borax");
        fillGrid(grid, plating, borax, plating, borax, item("extreme_ingot"), borax, plating, borax, plating);
        assertCrafts(helper, grid, item("radiation_scrubber"), 1);

        Item glowing = item("glowing_mushroom");
        fillGrid(grid, Items.BROWN_MUSHROOM, Items.GLOWSTONE_DUST);
        assertCrafts(helper, grid, glowing, 1);
        fillGrid(grid, glowing);
        assertCrafts(helper, grid, Items.BROWN_MUSHROOM, 1);

        fillGrid(grid, Items.MUSIC_DISC_CAT, item("tough_ingot"));
        assertCrafts(helper, grid, item("record_wanderer"), 1);
        fillGrid(grid, item("record_wanderer"), item("uranium_235_ingot"));
        assertCrafts(helper, grid, item("record_end_of_the_world"), 1);
        fillGrid(grid, item("record_end_of_the_world"), item("silver_ingot"));
        assertCrafts(helper, grid, item("record_money_for_nothing"), 1);
        fillGrid(grid, item("record_money_for_nothing"), item("dimensional_blend"));
        assertCrafts(helper, grid, item("record_hyperspace"), 1);
        helper.succeed();
    }

    private static void fillGrid(TransientCraftingContainer grid, Item... inputs) {
        grid.clearContent();
        for (int slot = 0; slot < inputs.length; slot++) {
            grid.setItem(slot, inputs[slot].getDefaultInstance());
        }
    }

    private static void assertCrafts(GameTestHelper helper, TransientCraftingContainer grid, Item output, int count) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        var result = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(result.is(output) && result.getCount() == count, "Crafting should produce " + count + " " + output);
    }

    static TransientCraftingContainer craftingGrid(int width, int height) {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, width, height);
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id));
    }
}
