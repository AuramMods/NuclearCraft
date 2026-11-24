package nc.processor;

import nc.processor.ItemProcessorBlockEntity.ItemAccess;
import nc.processor.ItemProcessorBlockEntity.SideMode;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.Predicate;

final class ProcessorItemInput extends ItemStackHandler {
    private final UpgradableProcessorBlockEntity owner;
    private final Predicate<ItemStack> accepted;
    private final EnumMap<Direction, ItemAccess> sides = new EnumMap<>(Direction.class);
    private ItemAccess unsided;
    private int disabledSides;

    ProcessorItemInput(UpgradableProcessorBlockEntity owner, int inputs, Predicate<ItemStack> accepted) {
        super(inputs);
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
        sides.put(side, access(enabled(side))).invalidate();
    }

    LazyOptional<IItemHandler> capability(@Nullable Direction side) {
        return (side == null ? unsided : sides.get(side)).capability;
    }

    void invalidate() {
        unsided.invalidate();
        sides.values().forEach(ItemAccess::invalidate);
    }

    void revive() {
        unsided = access(true);
        for (Direction side : Direction.values()) {
            sides.put(side, access(enabled(side)));
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

    private ItemAccess access(boolean enabled) {
        return new ItemAccess(owner, this, getSlots(), enabled ? SideMode.INPUT : SideMode.OFF, owner.upgrades);
    }
}
