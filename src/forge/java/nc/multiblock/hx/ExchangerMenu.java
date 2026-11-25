package nc.multiblock.hx;

import nc.init.NCHeatExchangers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class ExchangerMenu extends AbstractContainerMenu {
    static final int LENGTH_X = 0;
    static final int LENGTH_Y = 1;
    static final int LENGTH_Z = 2;
    static final int FLAGS = 3;
    static final int MANUAL = 1;
    static final int RUNNING = 2;

    // Container properties use 16-bit words. Counts, tank volumes and floating points use two.
    static final int NETWORKS = 4;
    static final int ACTIVE_NETWORKS = 6;
    static final int TUBES = 8;
    static final int ACTIVE_TUBES = 10;
    static final int SHELL_INPUT = 12;
    static final int SHELL_INPUT_CAPACITY = 14;
    static final int SHELL_OUTPUT = 16;
    static final int SHELL_OUTPUT_CAPACITY = 18;
    static final int TUBE_RATE = 20;
    static final int SHELL_RATE = 22;
    static final int HEAT_RATE = 24;
    static final int TEMPERATURE_DIFFERENCE = 26;
    static final int DATA_COUNT = 28;

    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final ExchangerController controller;
    private final boolean condenser;

    public ExchangerMenu(int id, boolean condenser) {
        this(id, condenser, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    ExchangerMenu(int id, boolean condenser, ExchangerController controller, ContainerLevelAccess access, ContainerData data) {
        super((condenser ? NCHeatExchangers.CONDENSER_MENU : NCHeatExchangers.MENU).get(), id);
        this.condenser = condenser;
        this.access = access;
        this.data = data;
        this.controller = controller;
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return (controller == null || controller.assembled()) && stillValid(access, player, (condenser ? NCHeatExchangers.CONDENSER : NCHeatExchangers.CONTROLLER).get());
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

    public boolean condenser() { return condenser; }
    public boolean enabled() { return (data.get(FLAGS) & MANUAL) != 0; }
    public boolean running() { return (data.get(FLAGS) & RUNNING) != 0; }
    public int lengthX() { return data.get(LENGTH_X); }
    public int lengthY() { return data.get(LENGTH_Y); }
    public int lengthZ() { return data.get(LENGTH_Z); }
    public int networks() { return unpack(NETWORKS); }
    public int activeNetworks() { return unpack(ACTIVE_NETWORKS); }
    public int tubes() { return unpack(TUBES); }
    public int activeTubes() { return unpack(ACTIVE_TUBES); }
    public int shellInput() { return unpack(SHELL_INPUT); }
    public int shellInputCapacity() { return unpack(SHELL_INPUT_CAPACITY); }
    public int shellOutput() { return unpack(SHELL_OUTPUT); }
    public int shellOutputCapacity() { return unpack(SHELL_OUTPUT_CAPACITY); }
    public float tubeInputRate() { return Float.intBitsToFloat(unpack(TUBE_RATE)); }
    public int shellInputRate() { return unpack(SHELL_RATE); }
    public float heatRate() { return Float.intBitsToFloat(unpack(HEAT_RATE)); }
    public float temperatureDifference() { return Float.intBitsToFloat(unpack(TEMPERATURE_DIFFERENCE)); }

    private int unpack(int field) {
        return (data.get(field) & 0xffff) | ((data.get(field + 1) & 0xffff) << 16);
    }
}
