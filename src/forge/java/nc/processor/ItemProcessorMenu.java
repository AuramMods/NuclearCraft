package nc.processor;

import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity.SideMode;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public final class ItemProcessorMenu extends AbstractContainerMenu {
    static final int PROGRESS = 0;
    static final int ENERGY = 1;
    static final int FIRST_SIDE = 2;
    static final int DATA_COUNT = FIRST_SIDE + 6;

    private static final Direction[] SIDES = Direction.values();
    private final Machine machine;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable ItemProcessorBlockEntity processor;

    public ItemProcessorMenu(int id, Inventory player, Machine machine) {
        this(id, player, machine, new ItemStackHandler(machine.inputs() + 1), new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL, null);
    }

    ItemProcessorMenu(int id, Inventory player, Machine machine, ItemStackHandler inventory, ContainerData data,
                      ContainerLevelAccess access, @Nullable ItemProcessorBlockEntity processor) {
        super(machine.menu(), id);
        this.machine = machine;
        this.data = data;
        this.access = access;
        this.processor = processor;
        int firstInput = machine.inputs() == 1 ? 56 : 46;
        for (int index = 0; index < machine.inputs(); index++) {
            addSlot(new SlotItemHandler(inventory, index, firstInput + index * 20, 35));
        }
        addSlot(new SlotItemHandler(inventory, machine.inputs(), firstInput + machine.inputs() * 20 + 40, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
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

    public Machine machine() {
        return machine;
    }

    public float progress() {
        return data.get(PROGRESS) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public SideMode sideMode(Direction side) {
        return SideMode.byId(data.get(FIRST_SIDE + side.ordinal()));
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (processor == null || button < 0 || button >= SIDES.length || !stillValid(player)) {
            return false;
        }
        processor.cycleSide(SIDES[button]);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, machine.block());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = machine.inputs() + 1;
        boolean moved = index < machineSlots ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, machine.inputs(), false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        if (processor != null) {
            processor.setChanged();
        }
        return original;
    }
}
