package nc.processor;

import nc.init.NCRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class MelterMenu extends AbstractContainerMenu {
    static final int PROGRESS = 0;
    static final int ENERGY = 1;
    static final int FLUID_AMOUNT = 2;
    static final int FLUID_ID = 3;
    static final int DATA_COUNT = 4;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public MelterMenu(int id, Inventory player) {
        this(id, player, new ItemStackHandler(1), new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL);
    }

    MelterMenu(int id, Inventory player, ItemStackHandler input, ContainerData data, ContainerLevelAccess access) {
        super(NCRegistries.MELTER_MENU.get(), id);
        this.data = data;
        this.access = access;
        addSlot(new SlotItemHandler(input, 0, 56, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    public float progress() {
        return data.get(PROGRESS) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public FluidStack fluid() {
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(FLUID_ID)), data.get(FLUID_AMOUNT));
    }

    public float fillLevel() {
        return data.get(FLUID_AMOUNT) / (float) MelterBlockEntity.TANK_CAPACITY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NCRegistries.MELTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }
}
