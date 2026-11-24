package nc.processor;

import nc.processor.ProcessorFluidTanks.OutputMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

public final class ProcessorItemPorts {
    static final Direction[] SIDES = Direction.values();
    private static final int DEFAULT = -1;

    private final BlockEntity owner;
    private final ItemStackHandler inventory;
    private final Predicate<ItemStack> ingredient;
    private final @Nullable ProcessorUpgrades upgrades;
    private final int inputs;
    private final int slots;
    private final boolean inputOnly;
    private final GroupMode[] groups = new GroupMode[SIDES.length];
    private final int[][] overrides = new int[SIDES.length][];
    private final Face[] faces = new Face[SIDES.length];
    private final OutputMode[] overflow;
    private Face unsided;
    private boolean autoOutput;
    private boolean pushing;

    ProcessorItemPorts(BlockEntity owner, ItemStackHandler inventory, Predicate<ItemStack> ingredient, int inputs,
                       @Nullable ProcessorUpgrades upgrades, boolean inputOnly) {
        this.owner = owner;
        this.inventory = inventory;
        this.ingredient = ingredient;
        this.inputs = inputs;
        this.upgrades = upgrades;
        this.inputOnly = inputOnly;
        slots = inventory.getSlots() + (upgrades == null ? 0 : ProcessorUpgrades.SLOTS);
        overflow = new OutputMode[inventory.getSlots() - inputs];
        Arrays.fill(overflow, OutputMode.HOLD);
        for (Direction side : SIDES) {
            overrides[side.ordinal()] = new int[slots];
            Arrays.fill(overrides[side.ordinal()], DEFAULT);
            groups[side.ordinal()] = inputOnly ? GroupMode.INPUT : GroupMode.BOTH;
        }
        revive();
    }

    boolean isOutput(int slot) {
        return slot >= inputs && slot < inputs + overflow.length;
    }

    GroupMode group(Direction side) {
        return groups[side.ordinal()];
    }

    SlotMode mode(int slot, @Nullable Direction side) {
        SlotMode normal = isOutput(slot) ? SlotMode.OUTPUT : SlotMode.INPUT;
        if (side == null) {
            return normal;
        }
        int explicit = overrides[side.ordinal()][slot];
        GroupMode group = group(side);
        return explicit != DEFAULT ? SlotMode.byId(explicit)
                : (normal == SlotMode.INPUT ? group.input : group.output) ? normal : SlotMode.OFF;
    }

    void cycleGroup(Direction side) {
        int face = side.ordinal();
        groups[face] = inputOnly ? (groups[face] == GroupMode.OFF ? GroupMode.INPUT : GroupMode.OFF)
                : GroupMode.byId((groups[face].ordinal() + 1) % GroupMode.MODES.length);
        Arrays.fill(overrides[face], DEFAULT);
        changed(side);
    }

    void cycleSlot(int slot, Direction side) {
        overrides[side.ordinal()][slot] = mode(slot, side).next(isOutput(slot)).ordinal();
        changed(side);
    }

    private void changed(Direction side) {
        refresh(side);
        refreshAutoOutput();
        owner.setChanged();
        Level level = owner.getLevel();
        if (level != null) {
            level.updateNeighborsAt(owner.getBlockPos(), owner.getBlockState().getBlock());
        }
    }

    OutputMode overflow(int slot) {
        return overflow[slot - inputs];
    }

    void cycleOverflow(int slot) {
        OutputMode next = overflow(slot).next();
        overflow[slot - inputs] = next;
        if (next == OutputMode.VOID) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        owner.setChanged();
    }

    boolean canProduce(int slot, ItemStack product) {
        if (product.isEmpty() || overflow(slot) == OutputMode.VOID) {
            return true;
        }
        ItemStack stored = inventory.getStackInSlot(slot);
        return (stored.isEmpty() || ItemStack.isSameItemSameTags(stored, product))
                && (overflow(slot) == OutputMode.VOID_EXCESS || product.getCount() <= room(slot, product, stored));
    }

    void produce(int slot, ItemStack product) {
        if (!product.isEmpty() && overflow(slot) != OutputMode.VOID) {
            ItemStack stored = inventory.getStackInSlot(slot);
            int added = Math.min(product.getCount(), room(slot, product, stored));
            if (added > 0) {
                inventory.setStackInSlot(slot, product.copyWithCount(stored.getCount() + added));
            }
        }
    }

    private int room(int slot, ItemStack product, ItemStack stored) {
        return Math.max(0, Math.min(inventory.getSlotLimit(slot), product.getMaxStackSize()) - stored.getCount());
    }

    LazyOptional<IItemHandler> capability(@Nullable Direction side) {
        return (side == null ? unsided : faces[side.ordinal()]).handle;
    }

    void invalidate() {
        unsided.invalidate();
        for (Face face : faces) {
            face.invalidate();
        }
    }

    void revive() {
        unsided = new Face(null);
        for (Direction side : SIDES) {
            faces[side.ordinal()] = new Face(side);
        }
    }

    private void refresh(Direction side) {
        int index = side.ordinal();
        Face previous = faces[index];
        faces[index] = new Face(side);
        previous.invalidate();
    }

    void save(CompoundTag tag) {
        tag.put("inventory", inventory.serializeNBT());
        if (inputOnly) {
            int disabled = 0;
            for (Direction side : SIDES) {
                if (group(side) == GroupMode.OFF) {
                    disabled |= 1 << side.ordinal();
                }
            }
            tag.putInt("item_disabled_sides", disabled);
        } else {
            tag.putIntArray("item_sides", Arrays.stream(groups).mapToInt(GroupMode::ordinal).toArray());
        }
        int[] modes = new int[SIDES.length * slots];
        boolean customized = false;
        for (Direction side : SIDES) {
            int[] row = overrides[side.ordinal()];
            for (int slot = 0; slot < slots; slot++) {
                modes[side.ordinal() * slots + slot] = row[slot];
                customized |= row[slot] != DEFAULT;
            }
        }
        if (customized) {
            tag.putIntArray("item_slot_sides", modes);
        }
        if (overflow.length > 0) {
            tag.putIntArray("item_output_modes", Arrays.stream(overflow).mapToInt(OutputMode::ordinal).toArray());
        }
    }

    void load(CompoundTag tag) {
        CompoundTag savedInventory = tag.getCompound("inventory").copy();
        savedInventory.putInt("Size", inputs + overflow.length);
        inventory.deserializeNBT(savedInventory);
        int[] coarse = tag.getIntArray("item_sides");
        int disabled = tag.getInt("item_disabled_sides");
        int[] modes = tag.getIntArray("item_slot_sides");
        for (Direction side : SIDES) {
            int face = side.ordinal();
            GroupMode group = inputOnly ? ((disabled & (1 << face)) == 0 ? GroupMode.INPUT : GroupMode.OFF)
                    : coarse.length == SIDES.length ? GroupMode.byId(coarse[face]) : GroupMode.BOTH;
            int[] row = overrides[face];
            boolean changed = groups[face] != group;
            groups[face] = group;
            for (int slot = 0; slot < slots; slot++) {
                int mode = modes.length == SIDES.length * slots ? modes[face * slots + slot] : DEFAULT;
                int validMode = mode >= 0 && mode < SlotMode.MODES.length ? mode : DEFAULT;
                changed |= row[slot] != validMode;
                row[slot] = validMode;
            }
            if (changed) {
                refresh(side);
            }
        }
        int[] settings = tag.getIntArray("item_output_modes");
        for (int output = 0; output < overflow.length; output++) {
            overflow[output] = output < settings.length ? OutputMode.byId(settings[output]) : OutputMode.HOLD;
            if (overflow[output] == OutputMode.VOID) {
                inventory.setStackInSlot(inputs + output, ItemStack.EMPTY);
            }
        }
        refreshAutoOutput();
    }

    private void refreshAutoOutput() {
        autoOutput = false;
        for (int[] row : overrides) {
            for (int mode : row) {
                if (mode == SlotMode.AUTO_OUTPUT.ordinal()) {
                    autoOutput = true;
                    return;
                }
            }
        }
    }

    void push(Level level) {
        if (!autoOutput || pushing) {
            return;
        }
        pushing = true;
        try {
            int first = Math.floorMod(level.getGameTime(), SIDES.length);
            for (int slot = 0; slot < slots; slot++) {
                ItemStackHandler source = handler(slot);
                int index = index(slot);
                ItemStack stack = source.getStackInSlot(index);
                if (stack.isEmpty()) {
                    continue;
                }
                for (int offset = 0; offset < SIDES.length; offset++) {
                    Direction side = SIDES[(first + offset) % SIDES.length];
                    if (mode(slot, side) != SlotMode.AUTO_OUTPUT) {
                        continue;
                    }
                    int moved = pushToNeighbor(level, side, stack);
                    if (moved > 0) {
                        source.extractItem(index, moved, false);
                        break;
                    }
                }
            }
        } finally {
            pushing = false;
        }
    }

    private int pushToNeighbor(Level level, Direction side, ItemStack stack) {
        BlockPos pos = owner.getBlockPos().relative(side);
        if (!level.hasChunkAt(pos)) {
            return 0;
        }
        BlockEntity neighbor = level.getBlockEntity(pos);
        if (neighbor == null) {
            return 0;
        }
        int count = stack.getCount();
        return neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite())
                .map(target -> count - ItemHandlerHelper.insertItemStacked(target, stack.copy(), false).getCount())
                .orElse(0);
    }

    private ItemStackHandler handler(int slot) {
        return slot < inventory.getSlots() ? inventory : Objects.requireNonNull(upgrades);
    }

    private int index(int slot) {
        return slot < inventory.getSlots() ? slot : slot - inventory.getSlots();
    }

    public enum GroupMode {
        BOTH(true, true, "both"), INPUT(true, false, "input"), OUTPUT(false, true, "output"), OFF(false, false, "off");

        private static final GroupMode[] MODES = values();
        private final boolean input;
        private final boolean output;
        private final String id;

        GroupMode(boolean input, boolean output, String id) {
            this.input = input;
            this.output = output;
            this.id = id;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.item_side." + id);
        }

        static GroupMode byId(int id) {
            return id >= 0 && id < MODES.length ? MODES[id] : BOTH;
        }
    }

    // Ordinals match the 1.12.2 per-slot mode values.
    public enum SlotMode {
        INPUT(true, false, "input"), OUTPUT(false, true, "output"), BOTH(true, true, "both"), OFF(false, false, "off"),
        AUTO_INPUT(true, false, "input"), AUTO_OUTPUT(false, true, "auto_output");

        private static final SlotMode[] MODES = values();
        private final boolean input;
        private final boolean output;
        private final String id;

        SlotMode(boolean input, boolean output, String id) {
            this.input = input;
            this.output = output;
            this.id = id;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.item_side." + id);
        }

        SlotMode next(boolean outputSlot) {
            return switch (this) {
                case INPUT, AUTO_INPUT, BOTH -> OUTPUT;
                case OUTPUT -> AUTO_OUTPUT;
                case AUTO_OUTPUT -> OFF;
                case OFF -> outputSlot ? OUTPUT : INPUT;
            };
        }

        static SlotMode byId(int id) {
            return id >= 0 && id < MODES.length ? MODES[id] : OFF;
        }
    }

    private final class Face implements IItemHandler {
        private final @Nullable Direction side;
        private final LazyOptional<IItemHandler> handle;
        private boolean valid = true;

        Face(@Nullable Direction side) {
            this.side = side;
            boolean active = false;
            for (int slot = 0; slot < slots; slot++) {
                active |= mode(slot, side) != SlotMode.OFF;
            }
            handle = active ? LazyOptional.of(() -> this) : LazyOptional.empty();
        }

        void invalidate() {
            valid = false;
            handle.invalidate();
        }

        private SlotMode available(int slot) {
            return valid && !owner.isRemoved() && slot >= 0 && slot < slots ? mode(slot, side) : SlotMode.OFF;
        }

        @Override public int getSlots() { return slots; }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return available(slot) == SlotMode.OFF ? ItemStack.EMPTY : handler(slot).getStackInSlot(index(slot)).copy();
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return !pushing && available(slot).input && !isOutput(slot)
                    ? handler(slot).insertItem(index(slot), simulate ? stack : stack.copy(), simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return !pushing && amount > 0 && available(slot).output
                    ? handler(slot).extractItem(index(slot), amount, simulate) : ItemStack.EMPTY;
        }

        @Override public int getSlotLimit(int slot) {
            return available(slot) == SlotMode.OFF ? 0 : handler(slot).getSlotLimit(index(slot));
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return available(slot).input && !stack.isEmpty()
                    && (slot < inputs ? ingredient.test(stack) : !isOutput(slot) && handler(slot).isItemValid(index(slot), stack));
        }
    }
}
