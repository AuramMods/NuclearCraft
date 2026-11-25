package nc.multiblock.fission;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class SolidFissionMenu extends AbstractContainerMenu {
    enum Reading {
        FLAGS, LENGTH_X, LENGTH_Y, LENGTH_Z, VOLUME, CLUSTERS, USEFUL_PARTS, HEAT, HEAT_CAPACITY,
        HEATING, COOLING, TEMPERATURE, EFFICIENCY, HEAT_MULTIPLIER, SPARSITY, OUTPUT_RATE,
        COLD_AMOUNT, COLD_CAPACITY, HOT_AMOUNT, HOT_CAPACITY
    }

    // Container property packets carry signed shorts.
    private static final int WORDS = Long.SIZE / Short.SIZE;
    private static final int DATA_COUNT = Reading.values().length * WORDS;
    static final int ENABLED = 1;
    static final int ACTIVE = 2;
    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;

    private final SolidFissionController controller;
    private final FissionVariant variant;
    private final ContainerLevelAccess access;
    private final ContainerData data = new SimpleContainerData(DATA_COUNT);

    public SolidFissionMenu(int id, Inventory player) {
        this(id, player, false);
    }

    public SolidFissionMenu(int id, Inventory player, boolean pebbleBed) {
        this(id, player, pebbleBed ? FissionVariant.PEBBLE : FissionVariant.SOLID);
    }

    public SolidFissionMenu(int id, Inventory player, FissionVariant variant) {
        this(id, null, ContainerLevelAccess.NULL, variant);
    }

    SolidFissionMenu(int id, SolidFissionController controller, ContainerLevelAccess access) {
        this(id, controller, access, controller.variant());
    }

    private SolidFissionMenu(int id, SolidFissionController controller, ContainerLevelAccess access,
                             FissionVariant variant) {
        super(variant.menu(), id);
        this.variant = variant;
        this.controller = controller;
        this.access = access;
        addDataSlots(data);
        refreshReadings();
    }

    @Override
    public void broadcastChanges() {
        refreshReadings();
        super.broadcastChanges();
    }

    @Override
    public void sendAllDataToRemote() {
        refreshReadings();
        super.sendAllDataToRemote();
    }

    private void refreshReadings() {
        if (controller != null) {
            controller.updateMenu(this);
        }
    }

    void clearReadings() {
        for (int index = 0; index < DATA_COUNT; index++) {
            data.set(index, 0);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return (controller == null || controller.assembled()) && stillValid(access, player, variant.controller());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || controller.isRemoved() || !stillValid(player)) {
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

    public FissionVariant variant() {
        return variant;
    }

    public boolean pebbleBed() {
        return variant == FissionVariant.PEBBLE;
    }

    public boolean enabled() {
        return (read(Reading.FLAGS) & ENABLED) != 0;
    }

    public boolean active() {
        return (read(Reading.FLAGS) & ACTIVE) != 0;
    }

    public int lengthX() { return (int) read(Reading.LENGTH_X); }
    public int lengthY() { return (int) read(Reading.LENGTH_Y); }
    public int lengthZ() { return (int) read(Reading.LENGTH_Z); }
    public long interiorVolume() { return read(Reading.VOLUME); }
    public int clusterCount() { return (int) read(Reading.CLUSTERS); }
    public int usefulParts() { return (int) read(Reading.USEFUL_PARTS); }
    public long heatStored() { return read(Reading.HEAT); }
    public long heatCapacity() { return read(Reading.HEAT_CAPACITY); }
    public long heating() { return read(Reading.HEATING); }
    public long cooling() { return read(Reading.COOLING); }
    public double temperature() { return real(Reading.TEMPERATURE); }
    public double efficiency() { return real(Reading.EFFICIENCY); }
    public double heatMultiplier() { return real(Reading.HEAT_MULTIPLIER); }
    public double sparsity() { return real(Reading.SPARSITY); }
    public double outputRate() { return real(Reading.OUTPUT_RATE); }
    public long coldAmount() { return read(Reading.COLD_AMOUNT); }
    public long coldCapacity() { return read(Reading.COLD_CAPACITY); }
    public long hotAmount() { return read(Reading.HOT_AMOUNT); }
    public long hotCapacity() { return read(Reading.HOT_CAPACITY); }

    public float heat() { return fraction(heatStored(), heatCapacity()); }
    public float coolant() { return fraction(coldAmount(), coldCapacity()); }
    public float steam() { return fraction(hotAmount(), hotCapacity()); }

    private static float fraction(long amount, long capacity) {
        return capacity > 0 ? (float) Math.min(1D, (double) amount / capacity) : 0F;
    }

    void set(Reading reading, double value) {
        set(reading, Double.doubleToLongBits(value));
    }

    void set(Reading reading, long value) {
        int start = reading.ordinal() * WORDS;
        for (int word = 0; word < WORDS; word++) {
            data.set(start + word, (int) (value >>> (Short.SIZE * word)) & 0xffff);
        }
    }

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
