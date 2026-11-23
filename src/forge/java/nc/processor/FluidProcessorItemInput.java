package nc.processor;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.Predicate;

final class FluidProcessorItemInput extends ItemStackHandler {
    private final BlockEntity owner;
    private final Predicate<ItemStack> accepted;
    private final EnumMap<Direction, Port> sides = new EnumMap<>(Direction.class);
    private Port unsided;
    private int disabledSides;

    FluidProcessorItemInput(BlockEntity owner, Predicate<ItemStack> accepted) {
        super(1);
        this.owner = owner;
        this.accepted = accepted;
        revive();
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot == 0 && !stack.isEmpty() && accepted.test(stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        owner.setChanged();
    }

    int disabledSides() {
        return disabledSides;
    }

    void toggle(Direction side) {
        disabledSides ^= sideBit(side);
        sides.put(side, new Port(enabled(side))).invalidate();
    }

    LazyOptional<IItemHandler> capability(@Nullable Direction side) {
        return (side == null ? unsided : sides.get(side)).capability;
    }

    void invalidate() {
        unsided.invalidate();
        sides.values().forEach(Port::invalidate);
    }

    void revive() {
        unsided = new Port(true);
        for (Direction side : Direction.values()) {
            sides.put(side, new Port(enabled(side)));
        }
    }

    void save(CompoundTag tag) {
        tag.put("inventory", serializeNBT());
        tag.putInt("item_disabled_sides", disabledSides);
    }

    void load(CompoundTag tag) {
        deserializeNBT(tag.getCompound("inventory"));
        for (Direction side : Direction.values()) {
            if (((disabledSides ^ tag.getInt("item_disabled_sides")) & sideBit(side)) != 0) {
                toggle(side);
            }
        }
    }

    private boolean enabled(Direction side) {
        return (disabledSides & sideBit(side)) == 0;
    }

    static int sideBit(Direction side) {
        return 1 << side.ordinal();
    }

    private final class Port implements IItemHandler {
        private final LazyOptional<IItemHandler> capability;
        private boolean valid;

        Port(boolean enabled) {
            valid = enabled;
            capability = enabled ? LazyOptional.of(() -> this) : LazyOptional.empty();
        }

        void invalidate() {
            valid = false;
            capability.invalidate();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return visible(slot) ? FluidProcessorItemInput.this.getStackInSlot(slot).copy() : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return visible(slot) ? FluidProcessorItemInput.this.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return visible(slot) ? FluidProcessorItemInput.this.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return visible(slot) && FluidProcessorItemInput.this.isItemValid(slot, stack);
        }

        private boolean visible(int slot) {
            return valid && slot == 0 && !owner.isRemoved();
        }
    }
}
