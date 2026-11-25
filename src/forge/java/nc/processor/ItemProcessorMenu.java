package nc.processor;

import nc.processor.ProcessorItemPorts.GroupMode;
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
    static final int DATA_COUNT = 2;
    static final int FLUID_ID = DATA_COUNT;
    static final int FLUID_AMOUNT = FLUID_ID + 1;
    static final int FLUID_SIDES = FLUID_AMOUNT + 1;
    static final int FLUID_DATA_COUNT = FLUID_SIDES + 1;
    static final int FLUID_OUTPUT_MODE = FLUID_DATA_COUNT;
    public static final int FLUID_SIDE_BUTTON = 6;
    public static final int FLUID_OUTPUT_BUTTON = 12;
    public static final int TANK_RESET = 6;
    public static final int TANK_DISABLE = 7;
    public static final int TANK_CLEAR = 8;
    public static final int TANK_POLICY = 9;
    public static final int REDSTONE_BUTTON = 199;
    private static final int SCRIPTED_TANK_BUTTON = 200;
    private static final int TANK_ACTIONS = 10;
    private static final int TANK_DATA = 6;

    private static final Direction[] SIDES = Direction.values();
    private final ProcessorProfile machine;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable ItemProcessorBlockEntity processor;
    private final ProcessorItemSettings items;

    public ItemProcessorMenu(int id, Inventory player, ProcessorProfile machine) {
        this(id, player, machine, new ItemStackHandler(machine.slots()), new ProcessorUpgrades(null),
                new SimpleContainerData(dataSize(machine)),
                ContainerLevelAccess.NULL, null);
    }

    ItemProcessorMenu(int id, Inventory player, ProcessorProfile machine, ItemStackHandler inventory, ProcessorUpgrades upgrades,
                      ContainerData data, ContainerLevelAccess access, @Nullable ItemProcessorBlockEntity processor) {
        super(machine.menu(), id);
        this.machine = machine;
        this.data = data;
        this.access = access;
        this.processor = processor;
        items = new ProcessorItemSettings(machine.inputs(), machine.outputs(), upgradeSlots(), FLUID_OUTPUT_BUTTON + 1,
                processor == null ? null : processor.itemPorts());
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
        var layout = machine.layout();
        var playerOrigin = layout == null ? new ProcessorLayout.Point(8, 84 + machine.menuExtension()) : layout.player();
        if (machine.hasUpgrades()) {
            if (layout == null) {
                upgrades.addMenuSlots(this::addSlot, upgradeY());
            } else {
                var speed = layout.speedUpgrade();
                var energy = layout.energyUpgrade();
                upgrades.addMenuSlots(this::addSlot, speed.itemX(), speed.itemY(), energy.itemX(), energy.itemY());
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, playerOrigin.x() + column * 18, playerOrigin.y() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, playerOrigin.x() + column * 18, playerOrigin.y() + 58));
        }
        addDataSlots(data);
        addDataSlots(items.data());
    }

    static int dataSize(ProcessorProfile machine) {
        return redstoneDataIndex(machine) + 1;
    }

    static int redstoneDataIndex(ProcessorProfile machine) {
        if (machine.scripted()) {
            return DATA_COUNT + machine.tanks().size() * TANK_DATA;
        }
        return machine.tank() == null ? DATA_COUNT : FLUID_OUTPUT_MODE + 1;
    }

    public boolean redstoneControl() {
        return data.get(redstoneDataIndex(machine)) != 0;
    }

    public ProcessorProfile machine() {
        return machine;
    }

    public int upgradeY() {
        return machine.scripted() ? 33 + machine.menuExtension() : 64 + (machine.hasFluidOutput() ? 0 : machine.menuExtension());
    }

    private int upgradeSlots() {
        return machine.hasUpgrades() ? ProcessorUpgrades.SLOTS : 0;
    }

    public float progress() {
        return data.get(PROGRESS) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public GroupMode sideMode(Direction side) {
        return items.group(side);
    }

    public ProcessorItemSettings items() {
        return items;
    }

    public FluidStack fluid() {
        return fluid(0);
    }

    public FluidStack fluid(int tank) {
        if (tank >= machine.tanks().size()) {
            return FluidStack.EMPTY;
        }
        if (machine.scripted()) {
            int start = DATA_COUNT + tank * TANK_DATA;
            return new FluidStack(BuiltInRegistries.FLUID.byId(readInt(start)), readInt(start + 2));
        }
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(FLUID_ID)), data.get(FLUID_AMOUNT));
    }

    private int readInt(int start) {
        return (data.get(start) & 0xFFFF) | (data.get(start + 1) << 16);
    }

    public ProcessorFluidTanks.Mode fluidSideMode(Direction side) {
        return fluidSideMode(0, side);
    }

    public ProcessorFluidTanks.Mode fluidSideMode(int tank, Direction side) {
        return ProcessorFluidTanks.Mode.read(data.get(machine.scripted() ? DATA_COUNT + tank * TANK_DATA + 4 : FLUID_SIDES), side);
    }

    public ProcessorFluidTanks.OutputMode fluidOutputMode() {
        return fluidOutputMode(0);
    }

    public ProcessorFluidTanks.OutputMode fluidOutputMode(int tank) {
        return tank >= machine.fluidInputs() && tank < machine.tanks().size()
                ? ProcessorFluidTanks.OutputMode.byId(fluidPolicy(tank)) : ProcessorFluidTanks.OutputMode.HOLD;
    }

    public boolean discardsFluidInput() {
        return discardsFluidInput(0);
    }

    public boolean discardsFluidInput(int tank) {
        return tank < machine.fluidInputs() && fluidPolicy(tank) != 0;
    }

    private int fluidPolicy(int tank) {
        return data.get(machine.scripted() ? DATA_COUNT + tank * TANK_DATA + 5 : FLUID_OUTPUT_MODE);
    }

    static int scriptedTankData(ProcessorFluidTanks tanks, int index) {
        int part = index - DATA_COUNT;
        int tank = part / TANK_DATA;
        var stack = tanks.stored(tank);
        return switch (part % TANK_DATA) {
            case 0 -> BuiltInRegistries.FLUID.getId(stack.getFluid()) & 0xFFFF;
            case 1 -> BuiltInRegistries.FLUID.getId(stack.getFluid()) >>> 16;
            case 2 -> stack.getAmount() & 0xFFFF;
            case 3 -> stack.getAmount() >>> 16;
            case 4 -> tanks.sideModes(tank);
            case 5 -> tanks.setting(tank);
            default -> 0;
        };
    }

    public static int scriptedTankButton(int tank, int action) {
        return SCRIPTED_TANK_BUTTON + tank * TANK_ACTIONS + action;
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
        if (button == (machine.scripted() ? REDSTONE_BUTTON : ProcessorMenuNetwork.STOCK_REDSTONE_BUTTON)) {
            processor.toggleRedstoneControl();
            return true;
        }
        ProcessorFluidTanks tanks = processor.fluidTanks();
        if (machine.scripted()) {
            if (button < SCRIPTED_TANK_BUTTON) {
                return items.click(button);
            }
            int tank = (button - SCRIPTED_TANK_BUTTON) / TANK_ACTIONS;
            int action = (button - SCRIPTED_TANK_BUTTON) % TANK_ACTIONS;
            if (tanks == null || tank >= tanks.size()) {
                return false;
            }
            switch (action) {
                case TANK_RESET -> tanks.reset(tank, true);
                case TANK_DISABLE -> tanks.reset(tank, false);
                case TANK_CLEAR -> tanks.clear(tank);
                case TANK_POLICY -> tanks.cycleSetting(tank);
                default -> tanks.cycle(tank, SIDES[action]);
            }
            return true;
        }
        int fluidSide = button - FLUID_SIDE_BUTTON;
        if (machine.tank() != null && fluidSide >= 0 && fluidSide < SIDES.length) {
            processor.cycleFluidSide(SIDES[fluidSide]);
            return true;
        }
        if (tanks != null && button == FLUID_OUTPUT_BUTTON) {
            tanks.cycleSetting(0);
            return true;
        }
        return items.click(button) || ProcessorFluidActions.click(tanks, button);
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
        int upgrade = machine.hasUpgrades() ? ProcessorUpgrades.slotFor(stack) : -1;
        int machineSlots = machine.slots() + upgradeSlots();
        boolean moved = index < machineSlots ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : upgrade >= 0
                ? moveItemStackTo(stack, machine.slots() + upgrade, machine.slots() + upgrade + 1, false)
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
