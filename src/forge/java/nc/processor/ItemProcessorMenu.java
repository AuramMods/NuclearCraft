package nc.processor;

import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity.SideMode;
import net.minecraft.core.Direction;
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
import org.jetbrains.annotations.Nullable;

public final class ItemProcessorMenu extends AbstractContainerMenu {
    static final int PROGRESS = 0;
    static final int ENERGY = 1;
    static final int FIRST_SIDE = 2;
    static final int DATA_COUNT = FIRST_SIDE + 6;
    static final int FLUID_ID = DATA_COUNT;
    static final int FLUID_AMOUNT = FLUID_ID + 1;
    static final int FLUID_SIDES = FLUID_AMOUNT + 1;
    static final int FLUID_DATA_COUNT = FLUID_SIDES + 1;
    public static final int FLUID_SIDE_BUTTON = 6;

    private static final Direction[] SIDES = Direction.values();
    private final Machine machine;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable ItemProcessorBlockEntity processor;

    public ItemProcessorMenu(int id, Inventory player, Machine machine) {
        this(id, player, machine, new ItemStackHandler(machine.slots()),
                new SimpleContainerData(machine.hasFluidInput() ? FLUID_DATA_COUNT : DATA_COUNT),
                ContainerLevelAccess.NULL, null);
    }

    ItemProcessorMenu(int id, Inventory player, Machine machine, ItemStackHandler inventory, ContainerData data,
                      ContainerLevelAccess access, @Nullable ItemProcessorBlockEntity processor) {
        super(machine.menu(), id);
        this.machine = machine;
        this.data = data;
        this.access = access;
        this.processor = processor;
        for (int index = 0; index < machine.inputs(); index++) {
            addSlot(new SlotItemHandler(inventory, index, machine.inputX(index), machine.inputY(index)));
        }
        for (int index = 0; index < machine.outputs(); index++) {
            addSlot(new SlotItemHandler(inventory, machine.inputs() + index, machine.outputX(index), machine.outputY(index)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        int extraHeight = machine.menuExtension();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 84 + extraHeight + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 142 + extraHeight));
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

    public FluidStack fluid() {
        if (!machine.hasFluidInput()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(FLUID_ID)), data.get(FLUID_AMOUNT));
    }

    public ProcessorFluidTanks.Mode fluidSideMode(Direction side) {
        return ProcessorFluidTanks.Mode.read(data.get(FLUID_SIDES), side);
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (processor == null || button < 0 || !stillValid(player)) {
            return false;
        }
        if (button < SIDES.length) {
            processor.cycleSide(SIDES[button]);
            return true;
        }
        int fluidSide = button - FLUID_SIDE_BUTTON;
        if (machine.hasFluidInput() && fluidSide >= 0 && fluidSide < SIDES.length) {
            processor.cycleFluidSide(SIDES[fluidSide]);
            return true;
        }
        return false;
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
        int machineSlots = machine.slots();
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
