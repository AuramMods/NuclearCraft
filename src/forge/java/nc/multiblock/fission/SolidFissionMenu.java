package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class SolidFissionMenu extends AbstractContainerMenu {
    static final int ENABLED = 0;
    static final int ACTIVE = 1;
    static final int HEAT = 2;
    static final int COOLANT = 3;
    static final int STEAM = 4;
    static final int DATA_COUNT = 5;
    public static final int TOGGLE = 0;

    private final SolidFissionController controller;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public SolidFissionMenu(int id, Inventory player) {
        this(id, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    SolidFissionMenu(int id, SolidFissionController controller, ContainerLevelAccess access, ContainerData data) {
        super(NCFission.MENU.get(), id);
        this.controller = controller;
        this.access = access;
        this.data = data;
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return (controller == null || controller.assembled()) && stillValid(access, player, NCFission.CONTROLLER.get());
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

    public boolean active() {
        return data.get(ACTIVE) != 0;
    }

    public float heat() {
        return data.get(HEAT) / 1000F;
    }

    public float coolant() {
        return data.get(COOLANT) / 1000F;
    }

    public float steam() {
        return data.get(STEAM) / 1000F;
    }
}
