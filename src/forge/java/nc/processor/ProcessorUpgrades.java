package nc.processor;

import nc.init.NCItemProcessors;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

final class ProcessorUpgrades extends ItemStackHandler {
    static final int SLOTS = 2;
    static final int SPEED = 0;
    static final int ENERGY = 1;
    private final @Nullable UpgradableProcessorBlockEntity owner;

    ProcessorUpgrades(@Nullable UpgradableProcessorBlockEntity owner) {
        super(SLOTS);
        this.owner = owner;
    }

    static int slotFor(ItemStack stack) {
        if (stack.is(NCItemProcessors.SPEED_UPGRADE.get())) {
            return SPEED;
        }
        return stack.is(NCItemProcessors.ENERGY_UPGRADE.get()) ? ENERGY : -1;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slotFor(stack) == slot;
    }

    @Override
    public int getSlotLimit(int slot) {
        return NCItemProcessors.upgradeLimit(slot);
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (owner != null) {
            owner.setChanged();
        }
    }

    void addMenuSlots(Consumer<Slot> add, int y) {
        for (int slot = 0; slot < SLOTS; slot++) {
            int index = slot;
            add.accept(new SlotItemHandler(this, slot, 132 + 20 * slot, y) {
                @Override
                public void setChanged() {
                    onContentsChanged(index);
                    super.setChanged();
                }
            });
        }
    }
}
