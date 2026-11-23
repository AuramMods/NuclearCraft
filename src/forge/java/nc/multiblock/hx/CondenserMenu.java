package nc.multiblock.hx;

import nc.init.NCCondenser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class CondenserMenu extends AbstractContainerMenu {
    static final int ENABLED = 0;
    static final int NETWORKS = 1;
    static final int ACTIVE_NETWORKS = 2;
    static final int ACTIVE_TUBES = 3;
    static final int COOLANT = 4;
    static final int RATE_LOW = 5;
    static final int RATE_HIGH = 6;
    static final int DATA_COUNT = 7;
    public static final int TOGGLE = 0;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final CondenserController controller;

    public CondenserMenu(int id, Inventory inventory) {
        this(id, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    CondenserMenu(int id, CondenserController controller, ContainerLevelAccess access, ContainerData data) {
        super(NCCondenser.MENU.get(), id);
        this.access = access;
        this.data = data;
        this.controller = controller;
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return (controller == null || controller.assembled()) && stillValid(access, player, NCCondenser.CONTROLLER.get());
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

    public int cooling() {
        return (data.get(RATE_HIGH) & 0xFFFF) << 16 | data.get(RATE_LOW) & 0xFFFF;
    }
}
