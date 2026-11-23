package nc.processor;

import nc.init.NCItemProcessors.Machine;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Objects;

public final class ItemProcessorBlockEntity extends BlockEntity implements MenuProvider {
    private static final Direction[] SIDES = Direction.values();

    private final Machine machine;
    private final RecipeManager.CachedCheck<Container, ItemProcessorRecipe> recipes;
    private final ProcessorEnergy power = new ProcessorEnergy(this, 512_000);
    private final SimpleContainer input;
    private final ItemStackHandler inventory;
    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, ItemAccess> ports = new EnumMap<>(Direction.class);
    private ItemAccess unsided = new ItemAccess(SideMode.BOTH);
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private ResourceLocation activeRecipe;
    private long progressHalfTicks;
    private int recipeTicks;

    public ItemProcessorBlockEntity(BlockPos pos, BlockState state, Machine machine) {
        super(machine.entityType(), pos, state);
        this.machine = machine;
        recipes = RecipeManager.createCheck(machine.recipes());
        input = new SimpleContainer(machine.inputs());
        inventory = new ItemStackHandler(machine.inputs() + 1) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if (slot >= machine.inputs() || stack.isEmpty()) {
                    return false;
                }
                if (level == null || level.isClientSide) {
                    return true;
                }
                refreshInput();
                return level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                        .anyMatch(recipe -> recipe.canInsert(input, slot, stack));
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        for (Direction side : SIDES) {
            sideModes.put(side, SideMode.BOTH);
            ports.put(side, new ItemAccess(SideMode.BOTH));
        }
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemProcessorBlockEntity processor) {
        boolean running = processor.process(level, level.hasNeighborSignal(pos));
        ProcessorBlock.setRunning(level, pos, state, running);
    }

    private void refreshInput() {
        for (int slot = 0; slot < machine.inputs(); slot++) {
            input.setItem(slot, inventory.getStackInSlot(slot));
        }
    }

    private boolean process(Level level, boolean halted) {
        refreshInput();
        ItemProcessorRecipe recipe = recipes.getRecipeFor(input, level).orElse(null);
        ResourceLocation next = recipe == null ? null : recipe.id();
        if (!Objects.equals(activeRecipe, next)) {
            progressHalfTicks = 0;
            activeRecipe = next;
            setChanged();
        }
        recipeTicks = recipe == null ? 0 : recipe.ticks();
        if (recipe == null) {
            return false;
        }
        ItemStack product = recipe.result();
        ItemStack stored = inventory.getStackInSlot(machine.inputs());
        boolean room = stored.isEmpty() || (ItemStack.isSameItemSameTags(stored, product)
                && product.getCount() <= stored.getMaxStackSize() - stored.getCount());
        if (!room || halted || !power.spend(recipe.energyPerTick())) {
            if (progressHalfTicks > 0) {
                long nextProgress = machine.losesProgress() && !halted ? Math.max(0, progressHalfTicks - 3)
                        : room ? progressHalfTicks : 0;
                if (nextProgress != progressHalfTicks) {
                    progressHalfTicks = nextProgress;
                    setChanged();
                }
            }
            return false;
        }
        progressHalfTicks += 2;
        if (progressHalfTicks >= 2L * recipe.ticks()) {
            int[] amounts = recipe.match(input);
            for (int slot = 0; slot < amounts.length; slot++) {
                inventory.extractItem(slot, amounts[slot], false);
            }
            product.grow(stored.getCount());
            inventory.setStackInSlot(machine.inputs(), product);
            progressHalfTicks = 0;
        }
        setChanged();
        return true;
    }

    public void cycleSide(Direction side) {
        SideMode next = sideModes.get(side).next();
        sideModes.put(side, next);
        ports.put(side, new ItemAccess(next)).invalidate();
        setChanged();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return (side == null ? unsided : ports.get(side)).capability.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unsided.invalidate();
        ports.values().forEach(ItemAccess::invalidate);
        energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        unsided = new ItemAccess(SideMode.BOTH);
        sideModes.forEach((side, mode) -> ports.put(side, new ItemAccess(mode)));
        energy = LazyOptional.of(() -> power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putInt("energy", power.getEnergyStored());
        tag.putLong("progress_half_ticks", progressHalfTicks);
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
        int[] modes = new int[SIDES.length];
        for (Direction side : SIDES) {
            modes[side.ordinal()] = sideModes.get(side).ordinal();
        }
        tag.putIntArray("item_sides", modes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        power.load(tag.getInt("energy"));
        progressHalfTicks = Math.max(0, tag.getLong("progress_half_ticks"));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        int[] modes = tag.getIntArray("item_sides");
        if (modes.length == SIDES.length) {
            for (Direction side : SIDES) {
                SideMode mode = SideMode.byId(modes[side.ordinal()]);
                sideModes.put(side, mode);
                ports.put(side, new ItemAccess(mode)).invalidate();
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public ItemProcessorMenu createMenu(int id, Inventory playerInventory, Player player) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case ItemProcessorMenu.PROGRESS -> recipeTicks == 0 ? 0
                            : (int) (1000L * Math.min(progressHalfTicks, 2L * recipeTicks) / (2L * recipeTicks));
                    case ItemProcessorMenu.ENERGY -> (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
                    default -> sideModes.get(SIDES[index - ItemProcessorMenu.FIRST_SIDE]).ordinal();
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return ItemProcessorMenu.DATA_COUNT;
            }
        };
        return new ItemProcessorMenu(id, playerInventory, machine, inventory, data,
                ContainerLevelAccess.create(player.level(), worldPosition), this);
    }

    public enum SideMode {
        BOTH(true, true, "both"), INPUT(true, false, "input"), OUTPUT(false, true, "output"), OFF(false, false, "off");

        private static final SideMode[] MODES = values();
        private final boolean input;
        private final boolean output;
        private final String id;

        SideMode(boolean input, boolean output, String id) {
            this.input = input;
            this.output = output;
            this.id = id;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.item_side." + id);
        }

        private SideMode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        static SideMode byId(int id) {
            return id >= 0 && id < MODES.length ? MODES[id] : BOTH;
        }
    }

    private final class ItemAccess implements IItemHandler {
        private final SideMode mode;
        private final LazyOptional<IItemHandler> capability;
        private boolean valid = true;

        ItemAccess(SideMode mode) {
            this.mode = mode;
            capability = mode == SideMode.OFF ? LazyOptional.empty() : LazyOptional.of(() -> this);
        }

        void invalidate() {
            valid = false;
            capability.invalidate();
        }

        private boolean visible(int slot) {
            return valid && !isRemoved() && slot >= 0 && (slot < machine.inputs() ? mode.input
                    : slot == machine.inputs() && mode.output);
        }

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return visible(slot) ? inventory.getStackInSlot(slot).copy() : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return visible(slot) && slot < machine.inputs() ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return visible(slot) && slot == machine.inputs() && amount > 0
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return visible(slot) ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return visible(slot) && inventory.isItemValid(slot, stack);
        }
    }
}
