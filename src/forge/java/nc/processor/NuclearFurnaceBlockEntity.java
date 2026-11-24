package nc.processor;

import nc.init.NCRegistries;
import nc.radiation.ChunkRadiationSource;
import nc.radiation.RadiationSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class NuclearFurnaceBlockEntity extends AbstractFurnaceBlockEntity implements ChunkRadiationSource {
    private static final int COOK_TIME = 10;
    private static final int[] TOP_SLOTS = {SLOT_INPUT};
    private static final int[] OTHER_SLOTS = {SLOT_FUEL, SLOT_RESULT};
    private static final TagKey<Item> URANIUM_BLOCKS = uraniumTag("storage_blocks");
    private static final TagKey<Item> URANIUM_INGOTS = uraniumTag("ingots");
    private static final TagKey<Item> URANIUM_DUSTS = uraniumTag("dusts");

    private final RecipeManager.CachedCheck<Container, SmeltingRecipe> smelting = RecipeManager.createCheck(RecipeType.SMELTING);

    public NuclearFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(NCRegistries.NUCLEAR_FURNACE_ENTITY.get(), pos, state, RecipeType.SMELTING);
        dataAccess.set(DATA_COOKING_TOTAL_TIME, COOK_TIME);
    }

    static void tick(Level level, BlockPos pos, BlockState state, NuclearFurnaceBlockEntity furnace) {
        furnace.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        int previousBurn = dataAccess.get(DATA_LIT_TIME);
        int previousProgress = dataAccess.get(DATA_COOKING_PROGRESS);
        int burn = Math.max(0, previousBurn - 1);
        int progress = previousProgress;
        boolean inventoryChanged = false;
        ItemStack input = items.get(SLOT_INPUT);
        ItemStack fuel = items.get(SLOT_FUEL);

        if (burn > 0 || !fuel.isEmpty() && !input.isEmpty()) {
            SmeltingRecipe recipe = input.isEmpty() ? null : smelting.getRecipeFor(this, level).orElse(null);
            ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.assemble(this, level.registryAccess());
            boolean canSmelt = canOutput(result);
            if (burn == 0 && canSmelt) {
                burn = getBurnDuration(fuel);
                if (burn > 0) {
                    dataAccess.set(DATA_LIT_DURATION, burn);
                    ItemStack remainder = fuel.getCraftingRemainingItem();
                    fuel.shrink(1);
                    if (fuel.isEmpty()) {
                        items.set(SLOT_FUEL, remainder);
                    }
                    inventoryChanged = true;
                }
            }
            if (burn > 0 && canSmelt) {
                if (++progress == COOK_TIME) {
                    progress = 0;
                    finishSmelting(input, result);
                    setRecipeUsed(recipe);
                    inventoryChanged = true;
                }
            } else {
                progress = 0;
            }
        } else {
            progress = Math.max(0, progress - BURN_COOL_SPEED);
        }

        dataAccess.set(DATA_LIT_TIME, burn);
        dataAccess.set(DATA_COOKING_PROGRESS, progress);
        boolean lit = burn > 0;
        boolean litChanged = state.getValue(NuclearFurnaceBlock.LIT) != lit;
        if (litChanged) {
            level.setBlock(pos, state.setValue(NuclearFurnaceBlock.LIT, lit), Block.UPDATE_ALL);
        }
        if (inventoryChanged || litChanged) {
            setChanged();
        } else if (burn != previousBurn || progress != previousProgress) {
            level.getChunkAt(pos).setUnsaved(true);
        }
    }

    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack stored = items.get(SLOT_RESULT);
        int limit = Math.min(getMaxStackSize(), result.getMaxStackSize());
        return (stored.isEmpty() || ItemStack.isSameItemSameTags(stored, result))
                && stored.getCount() + result.getCount() <= limit;
    }

    private void finishSmelting(ItemStack input, ItemStack result) {
        ItemStack stored = items.get(SLOT_RESULT);
        if (stored.isEmpty()) {
            items.set(SLOT_RESULT, result.copy());
        } else {
            stored.grow(result.getCount());
        }
        if (input.is(Items.WET_SPONGE) && items.get(SLOT_FUEL).is(Items.BUCKET)) {
            items.set(SLOT_FUEL, new ItemStack(Items.WATER_BUCKET));
        }
        input.shrink(1);
    }

    private static TagKey<Item> uraniumTag(String group) {
        return TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("forge", group + "/uranium"));
    }

    @Override
    protected int getBurnDuration(ItemStack stack) {
        return uraniumBurnTime(stack);
    }

    static int uraniumBurnTime(ItemStack stack) {
        if (stack.is(URANIUM_BLOCKS)) {
            return 3200;
        }
        return stack.is(URANIUM_INGOTS) || stack.is(URANIUM_DUSTS) ? 320 : 0;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean same = !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, items.get(slot));
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        if (stack.getCount() > limit) {
            stack.setCount(limit);
        }
        items.set(slot, stack);
        if (!same) {
            dataAccess.set(DATA_COOKING_PROGRESS, 0);
        }
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP_SLOTS : OTHER_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT || slot == SLOT_FUEL && uraniumBurnTime(stack) > 0;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return (side == Direction.UP ? slot == SLOT_INPUT : slot == SLOT_FUEL) && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.UP && slot == SLOT_RESULT;
    }

    @Override
    public double radiationEmission() {
        return dataAccess.get(DATA_LIT_TIME) > 0 ? RadiationSources.LEU_235_FISSION : 0D;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains("BurnTime", Tag.TAG_ANY_NUMERIC)) {
            dataAccess.set(DATA_LIT_TIME, tag.getInt("furnaceBurnTime"));
            dataAccess.set(DATA_COOKING_PROGRESS, tag.getInt("cookTime"));
        }
        if (tag.contains("BurnDuration", Tag.TAG_ANY_NUMERIC)) {
            dataAccess.set(DATA_LIT_DURATION, tag.getInt("BurnDuration"));
        } else if (tag.contains("currentItemBurnTime", Tag.TAG_ANY_NUMERIC)) {
            dataAccess.set(DATA_LIT_DURATION, tag.getInt("currentItemBurnTime"));
        }
        dataAccess.set(DATA_COOKING_TOTAL_TIME, COOK_TIME);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BurnDuration", dataAccess.get(DATA_LIT_DURATION));
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.nuclearcraft.nuclear_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new NuclearFurnaceMenu(id, player, this, dataAccess);
    }
}
