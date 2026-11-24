package nc.processor;

import nc.processor.FluidProcessorBlockEntity.Machine;
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

public final class FluidProcessorMenu extends AbstractContainerMenu {
    private static final Direction[] SIDES = Direction.values();

    static final int PROGRESS = 0;
    static final int ENERGY = 1;
    static final int FIRST_TANK = 2;
    static final int FLUID_ID = 0;
    static final int AMOUNT = 1;
    static final int SIDE_MODE = 2;
    static final int OUTPUT_MODE = 3;
    static final int TANK_DATA_SIZE = 4;
    public static final int UPGRADE_Y = 69;

    private final Machine type;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable FluidProcessorBlockEntity machine;
    private final ProcessorItemSettings items;

    public FluidProcessorMenu(int id, Inventory player, Machine type) {
        this(id, player, type, new SimpleContainerData(dataCount(type)), ContainerLevelAccess.NULL, null);
    }

    FluidProcessorMenu(int id, Inventory player, Machine type, ContainerData data, ContainerLevelAccess access,
                       @Nullable FluidProcessorBlockEntity machine) {
        super(type.menu(), id);
        this.type = type;
        this.data = data;
        this.access = access;
        this.machine = machine;
        items = new ProcessorItemSettings(type.hasItemInput() ? 1 : 0, 0, ProcessorUpgrades.SLOTS,
                outputModeButton(type.tanks()) + SIDES.length,
                machine == null ? null : machine.itemPorts());
        if (type.hasItemInput()) {
            ItemStackHandler input = machine == null ? new ItemStackHandler(1) : machine.inventory();
            addSlot(new SlotItemHandler(input, 0, 46, 35));
        }
        ProcessorUpgrades upgrades = machine == null ? new ProcessorUpgrades(null) : machine.upgrades;
        upgrades.addMenuSlots(this::addSlot, UPGRADE_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 96 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 154));
        }
        addDataSlots(data);
        addDataSlots(items.data());
    }

    static int dataCount(Machine type) {
        return FIRST_TANK + type.tanks() * TANK_DATA_SIZE;
    }

    public ProcessorItemSettings items() {
        return items;
    }

    public int itemSideButton(Direction side) {
        return outputModeButton(type.tanks()) + side.ordinal();
    }

    public Machine type() {
        return type;
    }

    public int outputModeButton(int tank) {
        return type.tanks() * SIDES.length + tank;
    }

    public float progress() {
        return data.get(PROGRESS) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public FluidStack fluid(int tank) {
        int index = FIRST_TANK + tank * TANK_DATA_SIZE;
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(index + FLUID_ID)), data.get(index + AMOUNT));
    }

    public ProcessorFluidTanks.Mode sideMode(int tank, Direction side) {
        return ProcessorFluidTanks.Mode.read(data.get(FIRST_TANK + tank * TANK_DATA_SIZE + SIDE_MODE), side);
    }

    public ProcessorFluidTanks.OutputMode outputMode(int tank) {
        return ProcessorFluidTanks.OutputMode.byId(data.get(FIRST_TANK + tank * TANK_DATA_SIZE + OUTPUT_MODE));
    }

    public static int sideButton(int tank, Direction side) {
        return tank * SIDES.length + side.ordinal();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (machine == null || !stillValid(player) || button < 0) {
            return false;
        }
        if (button < outputModeButton(0)) {
            machine.cycleSide(button / SIDES.length, SIDES[button % SIDES.length]);
            return true;
        }
        int itemSide = button - outputModeButton(type.tanks());
        if (itemSide >= 0 && itemSide < SIDES.length) {
            machine.itemPorts().cycleGroup(SIDES[itemSide]);
            return true;
        }
        int tank = button - outputModeButton(0);
        if (tank >= type.inputs() && tank < type.tanks()) {
            machine.cycleOutputMode(tank);
            return true;
        }
        return items.click(button);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, type.block());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inputs = type.hasItemInput() ? 1 : 0;
        int upgrade = ProcessorUpgrades.slotFor(stack);
        int machineSlots = inputs + ProcessorUpgrades.SLOTS;
        boolean moved = index < machineSlots ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : upgrade >= 0 ? moveItemStackTo(stack, inputs + upgrade, inputs + upgrade + 1, false)
                : moveItemStackTo(stack, 0, inputs, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        if (machine != null) {
            machine.setChanged();
        }
        return original;
    }
}
