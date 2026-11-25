package nc.multiblock.turbine;

import nc.init.NCTurbine;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class TurbineMenu extends AbstractContainerMenu {
    enum Reading {
        FLAGS, LENGTH_X, LENGTH_Y, LENGTH_Z, STEAM, STEAM_CAPACITY, EXHAUST, EXHAUST_CAPACITY,
        ENERGY, ENERGY_CAPACITY, POWER, INLET_COILS, OUTLET_COILS, BEARINGS_PER_SIDE, CONDUCTIVITY,
        ROTOR_EFFICIENCY, EXPANSION, IDEAL_EXPANSION, THROUGHPUT, RATED_FLOW, POWER_BONUS, TENSION
    }

    private static final Reading[] READINGS = Reading.values();
    // Each container property on the wire carries one 16-bit word.
    private static final int WORDS = Long.SIZE / Short.SIZE;
    private static final int DATA_COUNT = READINGS.length * WORDS;
    static final int MANUAL = 1;
    static final int RUNNING = 2;
    static final int PROCESSING = 4;
    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final TurbineController controller;

    public TurbineMenu(int id, Inventory inventory) {
        this(id, null, ContainerLevelAccess.NULL);
    }

    TurbineMenu(int id, TurbineController controller, ContainerLevelAccess access) {
        super(NCTurbine.MENU.get(), id);
        this.access = access;
        this.controller = controller;
        data = controller == null ? new SimpleContainerData(DATA_COUNT) : readings(controller);
        addDataSlots(data);
    }

    private static ContainerData readings(TurbineController controller) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return (int) (controller.menuValue(READINGS[index / WORDS]) >>> (Short.SIZE * (index % WORDS))) & 0xffff;
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return (controller == null || controller.assembled()) && stillValid(access, player, NCTurbine.CONTROLLER.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || !stillValid(player)) {
            return false;
        }
        if (id == TOGGLE) {
            controller.toggle();
            return true;
        }
        if (id == CLEAR && player.mayBuild() && player.level().mayInteract(player, controller.getBlockPos())) {
            controller.computerClear();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public boolean enabled() { return (read(Reading.FLAGS) & MANUAL) != 0; }
    public boolean running() { return (read(Reading.FLAGS) & RUNNING) != 0; }
    public boolean processing() { return (read(Reading.FLAGS) & PROCESSING) != 0; }
    public int lengthX() { return (int) read(Reading.LENGTH_X); }
    public int lengthY() { return (int) read(Reading.LENGTH_Y); }
    public int lengthZ() { return (int) read(Reading.LENGTH_Z); }
    public int steamAmount() { return (int) read(Reading.STEAM); }
    public int steamCapacity() { return (int) read(Reading.STEAM_CAPACITY); }
    public int exhaustAmount() { return (int) read(Reading.EXHAUST); }
    public int exhaustCapacity() { return (int) read(Reading.EXHAUST_CAPACITY); }
    public long energyStored() { return read(Reading.ENERGY); }
    public long energyCapacity() { return read(Reading.ENERGY_CAPACITY); }
    public long power() { return read(Reading.POWER); }
    public int inletCoils() { return (int) read(Reading.INLET_COILS); }
    public int outletCoils() { return (int) read(Reading.OUTLET_COILS); }
    public int bearingsPerSide() { return (int) read(Reading.BEARINGS_PER_SIDE); }
    public double conductivity() { return real(Reading.CONDUCTIVITY); }
    public double rotorEfficiency() { return real(Reading.ROTOR_EFFICIENCY); }
    public double expansion() { return real(Reading.EXPANSION); }
    public double idealExpansion() { return real(Reading.IDEAL_EXPANSION); }
    public double throughput() { return real(Reading.THROUGHPUT); }
    public long ratedFlow() { return read(Reading.RATED_FLOW); }
    public double powerBonus() { return real(Reading.POWER_BONUS); }
    public double tension() { return real(Reading.TENSION); }

    private double real(Reading reading) {
        return Double.longBitsToDouble(read(reading));
    }

    private long read(Reading reading) {
        int start = reading.ordinal() * WORDS;
        long value = 0L;
        for (int word = 0; word < WORDS; word++) {
            value |= (data.get(start + word) & 0xffffL) << (Short.SIZE * word);
        }
        return value;
    }
}
