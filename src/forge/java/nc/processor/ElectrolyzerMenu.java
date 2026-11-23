package nc.processor;

import nc.init.NCRegistries;
import nc.recipe.ElectrolyzerRecipe;
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
import org.jetbrains.annotations.Nullable;

public final class ElectrolyzerMenu extends AbstractContainerMenu {
    public static final int TANK_COUNT = 1 + ElectrolyzerRecipe.OUTPUTS;
    private static final Direction[] SIDES = Direction.values();
    public static final int OUTPUT_MODE_BUTTON = TANK_COUNT * SIDES.length;

    static final int PROGRESS = 0;
    static final int ENERGY = 1;
    static final int FIRST_TANK = 2;
    static final int FLUID_ID = 0;
    static final int AMOUNT = 1;
    static final int SIDE_MODE = 2;
    static final int OUTPUT_MODE = 3;
    static final int TANK_DATA_SIZE = 4;
    static final int DATA_COUNT = FIRST_TANK + TANK_COUNT * TANK_DATA_SIZE;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable ElectrolyzerBlockEntity machine;

    public ElectrolyzerMenu(int id, Inventory player) {
        this(id, player, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, null);
    }

    ElectrolyzerMenu(int id, Inventory player, ContainerData data, ContainerLevelAccess access,
                     @Nullable ElectrolyzerBlockEntity machine) {
        super(NCRegistries.ELECTROLYZER_MENU.get(), id);
        this.data = data;
        this.access = access;
        this.machine = machine;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 96 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 154));
        }
        addDataSlots(data);
    }

    public float progress() {
        return data.get(PROGRESS) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public FluidStack fluid(int tank) {
        int index = FIRST_TANK + tank * TANK_DATA_SIZE;
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(index + FLUID_ID)), data.get(index + AMOUNT));
    }

    public ProcessorFluidTanks.Mode sideMode(int tank, Direction side) {
        return ProcessorFluidTanks.Mode.read(data.get(FIRST_TANK + tank * TANK_DATA_SIZE + SIDE_MODE), side);
    }

    public ElectrolyzerBlockEntity.OutputMode outputMode(int tank) {
        return ElectrolyzerBlockEntity.OutputMode.byId(data.get(FIRST_TANK + tank * TANK_DATA_SIZE + OUTPUT_MODE));
    }

    public static int sideButton(int tank, Direction side) {
        return tank * SIDES.length + side.ordinal();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (machine == null || !stillValid(player) || button < 0) {
            return false;
        }
        if (button < OUTPUT_MODE_BUTTON) {
            machine.cycleSide(button / SIDES.length, SIDES[button % SIDES.length]);
            return true;
        }
        int tank = button - OUTPUT_MODE_BUTTON;
        if (tank > 0 && tank < TANK_COUNT) {
            machine.cycleOutputMode(tank);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NCRegistries.ELECTROLYZER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
