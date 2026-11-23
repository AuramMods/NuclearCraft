package nc.multiblock.hx;

import nc.init.NCHeatExchangers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class ExchangerMenu extends AbstractContainerMenu {
    static final int ENABLED = 0;
    static final int NETWORKS = 1;
    static final int ACTIVE_NETWORKS = 2;
    static final int ACTIVE_TUBES = 3;
    static final int COOLANT = 4;
    static final int RATE_LOW = 5;
    static final int RATE_HIGH = 6;
    static final int SHELL_OUTPUT = 7;
    static final int DATA_COUNT = 8;
    public static final int TOGGLE = 0;

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
        if (id == TOGGLE && controller != null && stillValid(player)) {
            controller.toggle();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public boolean enabled() {
        return data.get(ENABLED) != 0;
    }

    public int networks() {
        return data.get(NETWORKS);
    }

    public int activeNetworks() {
        return data.get(ACTIVE_NETWORKS);
    }

    public int activeTubes() {
        return data.get(ACTIVE_TUBES);
    }

    public float coolant() {
        return data.get(COOLANT) / 1000F;
    }

    public boolean condenser() {
        return condenser;
    }

    public float shellOutput() {
        return data.get(SHELL_OUTPUT) / 1000F;
    }

    public int cooling() {
        return (data.get(RATE_HIGH) & 0xFFFF) << 16 | data.get(RATE_LOW) & 0xFFFF;
    }
}
