package nc.gametest;

import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemProcessorGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 550)
    public static void manufactoryDiscoversMaterialRecipesFromBothTagNamespaces(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        String root = "manufactory/grinding_qa_dust_from_";
        var ingot = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id(root + "ingot")).orElseThrow();
        var ore = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id(root + "ore")).orElseThrow();
        var raw = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id(root + "raw")).orElseThrow();
        helper.assertTrue(ingot.acceptsItem(Items.AMETHYST_SHARD.getDefaultInstance())
                        && ingot.products().get(0).stack().getCount() == 1
                        && ingot.ticks() == 400 && ingot.energyPerTick() == 20,
                "Make one dust from an ingot in the common tag namespace");
        helper.assertTrue(ore.acceptsItem(Items.DEEPSLATE.getDefaultInstance())
                        && !ore.acceptsItem(Items.CALCITE.getDefaultInstance())
                        && ore.defaults().ore() && raw.defaults().ore()
                        && raw.acceptsItem(Items.TUFF.getDefaultInstance())
                        && raw.products().get(0).stack().getCount() == 1 && raw.ticks() == 500,
                "Discover the two ore forms and honor the automatic grinding exclusion");

        helper.setBlock(POS, Machine.MANUFACTORY.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        var inventory = items(processor, Direction.UP);
        var energy = processor.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(inventory.insertItem(0, Items.DEEPSLATE.getDefaultInstance(), false).isEmpty(),
                "Insert an ore from a discovered family through Forge's item capability");
        energy.receiveEnergy(10_000, false);
        helper.succeedWhen(() -> {
            var result = inventory.getStackInSlot(1);
            helper.assertTrue(result.is(Items.INK_SAC) && result.getCount() == 2
                            && inventory.getStackInSlot(0).isEmpty() && energy.getEnergyStored() == 0,
                    "The Manufactory uses one ore and 10,000 FE to produce two tagged dusts");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 380)
    public static void manufactoryDiscoversSingleLogCraftingAndMakesSticks(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(NuclearCraft.id("manufactory/oak_planks")).isPresent()
                        && recipes.byKey(NuclearCraft.id("manufactory/logs/minecraft/moss_block")).isEmpty(),
                "Keep the stock recipe ID and require a crafting recipe for discovered logs");

        helper.setBlock(POS, Machine.MANUFACTORY.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        var inventory = items(processor, Direction.UP);
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.MUSHROOM_STEM), false).isEmpty(),
                "Accept a log supplied by the datapack through the normal item handler");
        power.receiveEnergy(3000, false);
        helper.startSequence().thenWaitUntil(() -> {
            ItemStack result = inventory.getStackInSlot(1);
            helper.assertTrue(result.is(Items.SPRUCE_PLANKS) && result.getCount() == 6
                            && inventory.getStackInSlot(0).isEmpty() && power.getEnergyStored() == 1000,
                    "Use the crafting product, stock plank yield, and exact log processing energy");
        }).thenExecute(() -> {
            inventory.extractItem(1, 6, false);
            helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.SPRUCE_PLANKS), false).isEmpty(),
                    "Feed one plank into the stock stick recipe");
        }).thenWaitUntil(() -> {
            ItemStack result = inventory.getStackInSlot(1);
            helper.assertTrue(result.is(Items.STICK) && result.getCount() == 4
                            && inventory.getStackInSlot(0).isEmpty() && power.getEnergyStored() == 0,
                    "One plank and 1,000 FE make four sticks");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 370)
    public static void manufactoryProcessesCoalIntoGraphite(GameTestHelper helper) {
        helper.setBlock(POS, Machine.MANUFACTORY.block());
        ItemProcessorBlockEntity processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        IItemHandler inventory = items(processor, Direction.UP);
        var energy = processor.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(energy.receiveEnergy(5000, true) == 5000 && energy.getEnergyStored() == 0,
                "Simulated energy delivery should leave the store unchanged");
        energy.receiveEnergy(5000, false);
        ItemStack coal = new ItemStack(Items.COAL);
        helper.assertTrue(inventory.insertItem(0, coal, true).isEmpty() && inventory.getStackInSlot(0).isEmpty(),
                "Simulated coal insertion should leave the slot empty");
        helper.assertTrue(inventory.insertItem(0, coal, false).isEmpty(), "Coal is a valid input");
        helper.assertTrue(inventory.extractItem(0, 1, false).isEmpty(), "Do not extract the input");
        helper.assertTrue(inventory.insertItem(1, coal, false).getCount() == 1, "Do not insert into the output");

        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(inventory.getStackInSlot(1).is(stack("coal_dust", 1).getItem()),
                "The first process should produce coal dust")).thenExecute(() -> {
                    var simulated = inventory.extractItem(1, 1, true);
                    helper.assertTrue(!simulated.isEmpty() && inventory.getStackInSlot(1).getCount() == 1,
                            "Simulated extraction should leave the result in the machine");
                    var dust = inventory.extractItem(1, 1, false);
                    helper.assertTrue(inventory.insertItem(0, dust, false).isEmpty(), "Feed the coal dust into the graphite recipe");
                }).thenWaitUntil(() -> {
                    helper.assertTrue(inventory.getStackInSlot(1).is(stack("graphite_dust", 1).getItem()),
                            "The second process should produce graphite dust");
                    helper.assertTrue(inventory.getStackInSlot(0).isEmpty() && energy.getEnergyStored() == 0,
                            "The two processes consume exactly one coal and 5,000 FE");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 430)
    public static void manufactoryGrindsPorkAndRawFishIntoGelatin(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var fish = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id("manufactory/gelatin_from_fish")).orElseThrow();
        for (var item : new Item[]{Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH}) {
            helper.assertTrue(fish.acceptsItem(item.getDefaultInstance()), "Include all four legacy fish variants");
        }
        helper.assertTrue(!fish.acceptsItem(Items.COOKED_COD.getDefaultInstance()), "The recipe uses raw fish");

        helper.setBlock(POS, Machine.MANUFACTORY.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        var inventory = items(machine, Direction.UP);
        var power = machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        helper.assertTrue(inventory.insertItem(0, Items.PORKCHOP.getDefaultInstance(), false).isEmpty()
                        && power.receiveEnergy(4_000, false) == 4_000,
                "Supply one raw porkchop and two batches of charge");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(inventory.getStackInSlot(1), stack("gelatin", 8))
                                && power.getEnergyStored() == 2_000,
                        "The pork recipe produces eight gelatin for 2,000 FE"))
                .thenExecute(() -> {
                    inventory.extractItem(1, 8, false);
                    helper.assertTrue(inventory.insertItem(0, Items.COD.getDefaultInstance(), false).isEmpty(),
                            "Supply a raw cod through the same input");
                })
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(inventory.getStackInSlot(1), stack("gelatin", 4))
                                && inventory.getStackInSlot(0).isEmpty() && power.getEnergyStored() == 0,
                        "The fish recipe produces four gelatin for 2,000 FE"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 520)
    public static void alloyFurnacePreservesSideModesProgressAndIngredients(GameTestHelper helper) {
        helper.setBlock(POS, Machine.ALLOY_FURNACE.block());
        ItemProcessorBlockEntity processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        var oldNorth = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        var staleHandler = oldNorth.orElseThrow(AssertionError::new);
        processor.cycleSide(Direction.NORTH);
        processor.cycleSide(Direction.NORTH);
        helper.assertTrue(!oldNorth.isPresent() && !staleHandler.isItemValid(0, new ItemStack(Items.COPPER_INGOT)),
                "Changing a side revokes the old handler");

        IItemHandler inputs = items(processor, Direction.UP);
        IItemHandler output = items(processor, Direction.NORTH);
        helper.assertTrue(output.insertItem(0, new ItemStack(Items.COPPER_INGOT), false).getCount() == 1,
                "The output side rejects inputs");
        helper.assertTrue(inputs.insertItem(0, stack("tin_ingot", 1), false).isEmpty(),
                "The first slot accepts tin for a reversed alloy recipe");
        helper.assertTrue(inputs.insertItem(1, new ItemStack(Items.COPPER_INGOT, 3), false).isEmpty(),
                "The second slot accepts the matching copper");
        processor.inventory().setStackInSlot(2, stack("bronze_ingot", 64));
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
        power.receiveEnergy(4000, false);

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(power.getEnergyStored() == 4000 && inputs.getStackInSlot(1).getCount() == 3,
                    "A full output must leave ingredients and energy in place");
            helper.assertTrue(output.extractItem(2, 64, false).getCount() == 64, "Clear the output through automation");
        });
        helper.runAtTickTime(32, () -> {
            var saved = processor.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Archive the machine while it is processing");
            helper.setBlock(POS, Blocks.AIR);
            helper.assertTrue(inputs.getStackInSlot(0).isEmpty(), "Removal revokes a cached input handler");
            helper.setBlock(POS, Machine.ALLOY_FURNACE.block());
            ItemProcessorBlockEntity restored = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
            restored.load(saved);
            helper.assertTrue(items(restored, Direction.NORTH).insertItem(0, stack("tin_ingot", 1), false).getCount() == 1,
                    "The output-only mode survives loading");
            helper.succeedWhen(() -> {
                var restoredOutput = items(restored, Direction.NORTH).getStackInSlot(2);
                helper.assertTrue(restoredOutput.is(stack("bronze_ingot", 1).getItem()) && restoredOutput.getCount() == 4,
                        "Processing resumes after reloading and yields exactly four bronze");
                var restoredInput = items(restored, Direction.UP);
                helper.assertTrue(restoredInput.getStackInSlot(0).isEmpty() && restoredInput.getStackInSlot(1).isEmpty(),
                        "The two ingredients are consumed once");
                var charge = restored.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
                helper.assertTrue(charge.getEnergyStored() == 0, "The recipe consumed its original 4,000 FE");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 450)
    public static void alloyFurnaceUsesCommonTagsForInvarWithoutThermal(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = (ItemProcessorRecipe) level.getRecipeManager().byKey(NuclearCraft.id("alloy_furnace/invar")).orElseThrow();
        helper.assertTrue(recipe.configuredTime() == 400 && recipe.configuredPower() == 15
                        && ItemStack.matches(recipe.getResultItem(level.registryAccess()), new ItemStack(Items.FLINT, 3)),
                "The tagged provider keeps the original Invar recipe and rates");

        helper.setBlock(POS, Machine.ALLOY_FURNACE.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        var inventory = items(machine, Direction.UP);
        var energy = machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.QUARTZ), false).isEmpty()
                        && inventory.insertItem(1, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty()
                        && energy.receiveEnergy(6_000, false) == 6_000,
                "Accept the common nickel tag and the reversed input order");
        helper.succeedWhen(() -> helper.assertTrue(ItemStack.matches(inventory.getStackInSlot(2), new ItemStack(Items.FLINT, 3))
                        && inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty()
                        && energy.getEnergyStored() == 0,
                "Produce three tagged Invar and consume exactly 6,000 FE"));
    }

    @GameTest(template = "empty")
    public static void alloyFurnaceSyncsFractionalRecipeTime(GameTestHelper helper) {
        var id = NuclearCraft.id("alloy_furnace/qa_fractional_time");
        var serializer = new ItemProcessorRecipe.Serializer(Machine.ALLOY_FURNACE);
        var recipe = serializer.fromJson(id, GsonHelper.parse("""
                {"ingredients":[{"ingredient":{"item":"minecraft:gold_nugget"},"count":3},
                   {"ingredient":{"item":"minecraft:iron_nugget"},"count":1}],
                 "result":{"item":"minecraft:quartz","count":4},
                 "ticks":44.44444444444444,"power_multiplier":1}
                """));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var synced = serializer.fromNetwork(id, buffer);
            helper.assertTrue(synced.ticks() == 400D / 9 && synced.powerMultiplier() == 1
                            && synced.products().get(0).stack().getCount() == 4,
                    "Preserve a nugget recipe's fractional time and product across synchronization");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 4100)
    public static void separatorWaitsForBothOutputsThenSuppliesFuelCrafting(GameTestHelper helper) {
        helper.setBlock(POS, Machine.SEPARATOR.block());
        ItemProcessorBlockEntity processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        processor.cycleSide(Direction.NORTH);
        processor.cycleSide(Direction.NORTH);
        IItemHandler input = items(processor, Direction.UP);
        IItemHandler output = items(processor, Direction.NORTH);
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(input.insertItem(0, new ItemStack(Items.BARRIER, 10), true).getCount() == 10,
                "Missing optional material tags cannot supply a barrier as an ingredient");
        helper.assertTrue(input.insertItem(0, stack("uranium_dust", 10), false).isEmpty(),
                "Shared uranium dust is a valid separator input");
        power.receiveEnergy(40_000, false);
        processor.inventory().setStackInSlot(2, stack("uranium_235_ingot", 64));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(output.getStackInSlot(1).isEmpty() && input.getStackInSlot(0).getCount() == 10
                            && power.getEnergyStored() == 40_000,
                    "A blocked second output prevents either product and preserves the input and energy");
            helper.assertTrue(output.extractItem(2, 64, false).getCount() == 64, "Both outputs support extraction");
        });
        helper.succeedWhen(() -> {
            ItemStack fertile = output.extractItem(1, 9, true);
            ItemStack fissile = output.extractItem(2, 1, true);
            helper.assertTrue(fertile.is(stack("uranium_238_ingot", 1).getItem()) && fertile.getCount() == 9
                            && fissile.is(stack("uranium_235_ingot", 1).getItem()) && fissile.getCount() == 1,
                    "Separation yields nine uranium-238 and one uranium-235");
            helper.assertTrue(power.getEnergyStored() == 0 && input.getStackInSlot(0).isEmpty(),
                    "The separator runs once and uses exactly 40,000 FE");
            var crafting = MaterialGameTests.craftingGrid(3, 3);
            for (int slot = 0; slot < 8; slot++) {
                crafting.setItem(slot, fertile.copyWithCount(1));
            }
            crafting.setItem(8, fissile);
            var level = helper.getLevel();
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, level).orElseThrow();
            var result = recipe.assemble(crafting, level.registryAccess());
            helper.assertTrue(result.is(stack("leu_235_pellet", 1).getItem()) && result.getCount() == 9,
                    "The separated materials can craft nine LEU-235 pellets");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 850)
    public static void separatorRecoversMixedFuelIsotopesWithoutReplacingSmallBatches(GameTestHelper helper) {
        var results = new ArrayList<Runnable>();
        for (int mass : new int[]{239, 241}) {
            for (String form : new String[]{"", "_carbide", "_za"}) {
                int index = results.size();
                BlockPos pos = new BlockPos(1 + 2 * (index % 3), 1, 1 + 2 * (index / 3));
                helper.setBlock(pos, Machine.SEPARATOR.block());
                var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(pos);
                var input = items(machine, Direction.UP);
                var power = machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
                String fuel = "mix_" + mass + switch (form) {
                    case "_za" -> "_za";
                    case "_carbide" -> "_pellet_carbide";
                    default -> "_pellet";
                };
                helper.assertTrue(input.insertItem(0, stack(fuel, 1), false).isEmpty(), "Accept " + fuel);
                power.receiveEnergy(8_000, false);
                helper.runAtTickTime(5, () -> {
                    if (!form.isEmpty()) {
                        helper.assertTrue(machine.saveWithoutMetadata().getLong("progress_half_ticks") > 0,
                                "The single-item " + fuel + " recipe has already used some of the energy");
                    }
                    helper.assertTrue(input.insertItem(0, stack(fuel, 8), false).isEmpty(), "Complete the " + fuel + " batch");
                });
                results.add(() -> helper.assertTrue(input.getStackInSlot(0).isEmpty()
                                && ItemStack.matches(input.getStackInSlot(1), stack("uranium_238" + form + "_ingot", 8))
                                && ItemStack.matches(input.getStackInSlot(2), stack("plutonium_" + mass + form + "_ingot", 1))
                                && power.getEnergyStored() == 0,
                        "Nine " + fuel + " recover the original isotopes for exactly 8,000 FE"));
            }
        }
        BlockPos fallback = new BlockPos(1, 1, 5);
        helper.setBlock(fallback, Machine.SEPARATOR.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(fallback);
        var input = items(machine, Direction.UP);
        helper.assertTrue(input.insertItem(0, stack("mix_239_pellet_carbide", 1), false).isEmpty(),
                "The single-pellet recipe still accepts smaller batches");
        machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new).receiveEnergy(8_000, false);
        helper.succeedWhen(() -> {
            results.forEach(Runnable::run);
            helper.assertTrue(input.getStackInSlot(0).isEmpty()
                            && ItemStack.matches(input.getStackInSlot(1), stack("mix_239_pellet", 1))
                            && ItemStack.matches(input.getStackInSlot(2), stack("graphite_dust", 1)),
                    "One carbide pellet separates into the pellet and its graphite");
        });
    }

    private static IItemHandler items(ItemProcessorBlockEntity processor, Direction side) {
        return processor.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static ItemStack stack(String item, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(item)), count);
    }
}
