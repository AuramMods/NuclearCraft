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
    static final int ENABLED = 0;
    static final int PROCESSING = 1;
    static final int STEAM = 2;
    static final int EXHAUST = 3;
    static final int ENERGY = 4;
    static final int POWER_LOW = 5;
    static final int POWER_HIGH = 6;
    static final int DATA_COUNT = 7;
    public static final int TOGGLE = 0;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final TurbineController controller;

    public TurbineMenu(int id, Inventory inventory) {
        this(id, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    TurbineMenu(int id, TurbineController controller, ContainerLevelAccess access, ContainerData data) {
        super(NCTurbine.MENU.get(), id);
        this.access = access;
        this.data = data;
        this.controller = controller;
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return (controller == null || controller.assembled()) && stillValid(access, player, NCTurbine.CONTROLLER.get());
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

    public boolean processing() {
        return data.get(PROCESSING) != 0;
    }

    public float steam() {
        return data.get(STEAM) / 1000F;
    }

    public float exhaust() {
        return data.get(EXHAUST) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public int power() {
        return (data.get(POWER_HIGH) & 0xFFFF) << 16 | data.get(POWER_LOW) & 0xFFFF;
    }
}
