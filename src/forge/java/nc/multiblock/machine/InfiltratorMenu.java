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

public final class InfiltratorMenu extends AbstractContainerMenu {
    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;
    private static final int MACHINE_SLOTS = 3;
    private static final int ENERGY = 6;
    private static final int CAPACITY = 10;
    private static final int POWER = 14;
    private static final int TANK_CAPACITY = 18;
    private static final int FIRST_TANK = 20;
    private static final int DATA_COUNT = FIRST_TANK + 3 * 4;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final @Nullable Infiltrator infiltrator;

    public InfiltratorMenu(int id, Inventory inventory) {
        this(id, inventory, null, ContainerLevelAccess.NULL, new ItemStackHandler(MACHINE_SLOTS), new SimpleContainerData(DATA_COUNT));
    }

    InfiltratorMenu(int id, Inventory inventory, Infiltrator infiltrator, ContainerLevelAccess access) {
        this(id, inventory, infiltrator, access, infiltrator.processItems(), new ContainerData() {
            @Override public int get(int index) { return menuValue(infiltrator, index); }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    private InfiltratorMenu(int id, Inventory inventory, @Nullable Infiltrator infiltrator, ContainerLevelAccess access,
                           ItemStackHandler items, ContainerData data) {
        super(NCMachines.INFILTRATOR_MENU.get(), id);
        this.infiltrator = infiltrator;
        this.access = access;
        this.data = data;
        addDataSlots(data);
        addSlot(new SlotItemHandler(items, 0, 14, 57));
        addSlot(new SlotItemHandler(items, 1, 38, 57));
        addSlot(new SlotItemHandler(items, 2, 111, 57) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 124 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 182));
    }

    private static int menuValue(Infiltrator machine, int index) {
        return switch (index) {
            case 0 -> machine.halted() ? 1 : 0;
            case 1 -> machine.processing() ? 1 : 0;
            case 2 -> machine.chambers();
            case 3 -> (int) (machine.progressFraction() * 10_000);
            case 4 -> (int) (machine.pressureEfficiency() * 100);
            case 5 -> (int) (machine.heatingBonus() * 100);
            case ENERGY, ENERGY + 1, ENERGY + 2, ENERGY + 3 -> word(machine.power(), index - ENERGY);
            case CAPACITY, CAPACITY + 1, CAPACITY + 2, CAPACITY + 3 -> word(machine.energyCapacity(), index - CAPACITY);
            case POWER, POWER + 1, POWER + 2, POWER + 3 -> word(machine.energyPerTick(), index - POWER);
            case TANK_CAPACITY, TANK_CAPACITY + 1 -> word(machine.pressureTank().getCapacity(), index - TANK_CAPACITY);
            default -> {
                int slot = (index - FIRST_TANK) / 4;
                int part = (index - FIRST_TANK) % 4;
                var tank = slot == 2 ? machine.pressureTank() : machine.tankFor(false, slot);
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
        return (infiltrator == null || infiltrator.assembled()) && stillValid(access, player, NCMachines.block(Part.INFILTRATOR));
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (infiltrator == null || !stillValid(player)) return false;
        if (id == TOGGLE) {
            infiltrator.toggle();
            return true;
        }
        if (id == CLEAR) {
            infiltrator.clear();
            return true;
        }
        return false;
    }

    public boolean halted() { return data.get(0) != 0; }
    public boolean processing() { return data.get(1) != 0; }
    public int chambers() { return data.get(2) & 0xffff; }
    public float progress() { return (data.get(3) & 0xffff) / 10_000F; }
    public int pressureEfficiency() { return data.get(4) & 0xffff; }
    public int heatingBonus() { return data.get(5) & 0xffff; }
    public long energy() { return unpack(ENERGY, 4); }
    public long capacity() { return unpack(CAPACITY, 4); }
    public long power() { return unpack(POWER, 4); }
    public int tankCapacity() { return (int) unpack(TANK_CAPACITY, 2); }

    public FluidStack fluid(int slot) {
        int index = FIRST_TANK + slot * 4;
        return new FluidStack(BuiltInRegistries.FLUID.byId((int) unpack(index, 2)), (int) unpack(index + 2, 2));
    }
}
