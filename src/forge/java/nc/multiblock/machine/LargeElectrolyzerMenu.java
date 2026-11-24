package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.machine.MachinePartBlock.Part;
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

public final class LargeElectrolyzerMenu extends AbstractContainerMenu {
    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;
    private static final int MACHINE_SLOTS = 6;
    private static final int TERMINALS = 2;
    private static final int PROGRESS = 4;
    private static final int ELECTROLYTE_EFFICIENCY = 5;
    private static final int SPEED = 9;
    private static final int ENERGY = 13;
    private static final int CAPACITY = 17;
    private static final int POWER = 21;
    private static final int TANK_CAPACITY = 25;
    private static final int FIRST_TANK = 27;
    private static final int DATA_COUNT = FIRST_TANK + 7 * 4;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final @Nullable LargeElectrolyzer electrolyzer;

    public LargeElectrolyzerMenu(int id, Inventory inventory) {
        this(id, inventory, null, ContainerLevelAccess.NULL, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_COUNT));
    }

    LargeElectrolyzerMenu(int id, Inventory inventory, LargeElectrolyzer electrolyzer, ContainerLevelAccess access) {
        this(id, inventory, electrolyzer, access, electrolyzer.processItems(), new ContainerData() {
            @Override public int get(int index) { return menuValue(electrolyzer, index); }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    private LargeElectrolyzerMenu(int id, Inventory inventory, @Nullable LargeElectrolyzer electrolyzer, ContainerLevelAccess access,
                           ItemStackHandler items, ContainerData data) {
        super(NCMachines.ELECTROLYZER_MENU.get(), id);
        this.electrolyzer = electrolyzer;
        this.access = access;
        this.data = data;
        addDataSlots(data);
        addSlot(new SlotItemHandler(items, 0, 16, 57));
        addSlot(new SlotItemHandler(items, 1, 40, 57));
        for (int slot = 0; slot < 4; slot++) {
            addSlot(new SlotItemHandler(items, 2 + slot, 110 + slot * 24, 57) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, 9 + row * 9 + column, 30 + column * 18, 155 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 30 + column * 18, 213));
    }

    private static int menuValue(LargeElectrolyzer machine, int index) {
        return switch (index) {
            case 0 -> machine.halted() ? 1 : 0;
            case 1 -> machine.processing() ? 1 : 0;
            case TERMINALS, TERMINALS + 1 -> word(machine.terminalPairs(), index - TERMINALS);
            case PROGRESS -> (int) (machine.progressFraction() * 10_000);
            case ELECTROLYTE_EFFICIENCY, ELECTROLYTE_EFFICIENCY + 1, ELECTROLYTE_EFFICIENCY + 2, ELECTROLYTE_EFFICIENCY + 3 ->
                    word(Double.doubleToLongBits(machine.electrolyteEfficiency()), index - ELECTROLYTE_EFFICIENCY);
            case SPEED, SPEED + 1, SPEED + 2, SPEED + 3 ->
                    word(Double.doubleToLongBits(machine.speed()), index - SPEED);
            case ENERGY, ENERGY + 1, ENERGY + 2, ENERGY + 3 -> word(machine.power(), index - ENERGY);
            case CAPACITY, CAPACITY + 1, CAPACITY + 2, CAPACITY + 3 -> word(machine.energyCapacity(), index - CAPACITY);
            case POWER, POWER + 1, POWER + 2, POWER + 3 -> word(machine.energyPerTick(), index - POWER);
            case TANK_CAPACITY, TANK_CAPACITY + 1 -> word(machine.pressureTank().getCapacity(), index - TANK_CAPACITY);
            default -> {
                int slot = (index - FIRST_TANK) / 4;
                int part = (index - FIRST_TANK) % 4;
                var tank = slot == 6 ? machine.pressureTank() : machine.tankFor(slot >= 2, slot < 2 ? slot : slot - 2);
                yield part < 2 ? word(BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid()), part) : word(tank.getFluidAmount(), part - 2);
            }
        };
    }

    private static int word(long value, int part) { return (int) (value >>> (16 * part)) & 0xffff; }

    private long unpack(int start, int words) {
        long value = 0;
        for (int word = 0; word < words; word++) value |= (data.get(start + word) & 0xffffL) << (16 * word);
        return value;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var copy = stack.copy();
        if (index < MACHINE_SLOTS ? !moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)
                : !moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override public boolean stillValid(Player player) {
        return (electrolyzer == null || electrolyzer.assembled()) && stillValid(access, player, NCMachines.block(Part.ELECTROLYZER));
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (electrolyzer == null || !stillValid(player)) return false;
        if (id == TOGGLE) {
            electrolyzer.toggle();
            return true;
        }
        if (id == CLEAR) {
            electrolyzer.clear();
            return true;
        }
        return false;
    }

    public boolean halted() { return data.get(0) != 0; }
    public boolean processing() { return data.get(1) != 0; }
    public int terminalPairs() { return (int) unpack(TERMINALS, 2); }
    public float progress() { return (data.get(PROGRESS) & 0xffff) / 10_000F; }
    public double electrolyteEfficiency() { return Double.longBitsToDouble(unpack(ELECTROLYTE_EFFICIENCY, 4)) * 100; }
    public double speed() { return Double.longBitsToDouble(unpack(SPEED, 4)); }
    public long energy() { return unpack(ENERGY, 4); }
    public long capacity() { return unpack(CAPACITY, 4); }
    public long power() { return unpack(POWER, 4); }
    public int tankCapacity() { return (int) unpack(TANK_CAPACITY, 2); }

    public FluidStack fluid(int slot) {
        int index = FIRST_TANK + slot * 4;
        return new FluidStack(BuiltInRegistries.FLUID.byId((int) unpack(index, 2)), (int) unpack(index + 2, 2));
    }
}
