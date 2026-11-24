package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.recipe.DistillerRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class DistillerMenu extends AbstractContainerMenu {
    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;
    private static final int ENERGY = 7;
    private static final int CAPACITY = 11;
    private static final int POWER = 15;
    private static final int TANK_CAPACITY = 19;
    private static final int FIRST_TANK = 21;
    private static final int TANK_DATA_SIZE = 4;
    private static final int DATA_COUNT = FIRST_TANK
            + TANK_DATA_SIZE * (DistillerRecipe.INPUT_COUNT + DistillerRecipe.OUTPUT_COUNT);

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final @Nullable Distiller distiller;

    public DistillerMenu(int id) {
        this(id, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    DistillerMenu(int id, Distiller distiller, ContainerLevelAccess access) {
        this(id, distiller, access, new ContainerData() {
            @Override public int get(int index) { return menuValue(distiller, index); }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    private DistillerMenu(int id, @Nullable Distiller distiller, ContainerLevelAccess access, ContainerData data) {
        super(NCMachines.DISTILLER_MENU.get(), id);
        this.distiller = distiller;
        this.access = access;
        this.data = data;
        addDataSlots(data);
    }

    private static int menuValue(Distiller distiller, int index) {
        return switch (index) {
            case 0 -> distiller.halted() ? 1 : 0;
            case 1 -> distiller.processing() ? 1 : 0;
            case 2 -> distiller.trayCount();
            case 3 -> scaled(distiller.progressFraction());
            case 4 -> scaled(distiller.refluxBonus());
            case 5 -> scaled(distiller.reboilingBonus());
            case 6 -> scaled(distiller.distributionBonus());
            case ENERGY, ENERGY + 1, ENERGY + 2, ENERGY + 3 -> word(distiller.power(), index - ENERGY);
            case CAPACITY, CAPACITY + 1, CAPACITY + 2, CAPACITY + 3 -> word(distiller.energyCapacity(), index - CAPACITY);
            case POWER, POWER + 1, POWER + 2, POWER + 3 -> word(distiller.energyPerTick(), index - POWER);
            case TANK_CAPACITY, TANK_CAPACITY + 1 -> word(distiller.tankFor(false, 0).getCapacity(), index - TANK_CAPACITY);
            default -> tankValue(distiller, index - FIRST_TANK);
        };
    }

    private static int tankValue(Distiller distiller, int index) {
        int slot = index / TANK_DATA_SIZE;
        boolean output = slot >= DistillerRecipe.INPUT_COUNT;
        var tank = distiller.tankFor(output, output ? slot - DistillerRecipe.INPUT_COUNT : slot);
        int part = index % TANK_DATA_SIZE;
        return part < 2 ? word(BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid()), part)
                : word(tank.getFluidAmount(), part - 2);
    }

    private static int scaled(double fraction) { return (int) (10000 * fraction); }
    private static int word(long value, int part) { return (int) (value >>> (16 * part)) & 0xffff; }

    private long unpack(int start, int words) {
        long value = 0;
        for (int word = 0; word < words; word++) value |= (data.get(start + word) & 0xffffL) << (16 * word);
        return value;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player) {
        return (distiller == null || distiller.assembled()) && stillValid(access, player, NCMachines.block(Part.DISTILLER));
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (distiller == null || !stillValid(player)) return false;
        if (id == TOGGLE) {
            distiller.toggle();
            return true;
        }
        if (id == CLEAR) {
            distiller.clear();
            return true;
        }
        return false;
    }

    public boolean halted() { return data.get(0) != 0; }
    public boolean processing() { return data.get(1) != 0; }
    public int trayCount() { return data.get(2); }
    public float progress() { return (data.get(3) & 0xffff) / 10000F; }
    public float refluxBonus() { return (data.get(4) & 0xffff) / 100F; }
    public float reboilingBonus() { return (data.get(5) & 0xffff) / 100F; }
    public float distributionBonus() { return (data.get(6) & 0xffff) / 100F; }
    public long energy() { return unpack(ENERGY, 4); }
    public long capacity() { return unpack(CAPACITY, 4); }
    public long power() { return unpack(POWER, 4); }
    public int tankCapacity() { return (int) unpack(TANK_CAPACITY, 2); }

    public FluidStack fluid(int slot) {
        int index = FIRST_TANK + slot * TANK_DATA_SIZE;
        var fluid = BuiltInRegistries.FLUID.byId((int) unpack(index, 2));
        return new FluidStack(fluid, (int) unpack(index + 2, 2));
    }
}
