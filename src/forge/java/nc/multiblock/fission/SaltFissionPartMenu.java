package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class SaltFissionPartMenu extends AbstractContainerMenu {
    public static final int CLEAR_INPUT = 0;
    public static final int CLEAR_OUTPUT = 1;
    private static final int FILTER = 0;
    private static final int RESERVED = 1;
    private static final int RESERVED_AMOUNT = 2;
    private static final int INPUT = 3;
    private static final int OUTPUT = 6;
    private static final int PROGRESS = 9;
    private static final int HEAT = 10;
    private static final int HEAT_CAPACITY = 11;
    private static final int COOLING = 12;
    private static final int STATUS = 13;
    private static final int DATA_SIZE = 14 * 2;
    private static final int CONNECTED = 1;
    private static final int PROCESSING = 2;
    private static final int OUTPUT_PORT = 4;

    private final Kind kind;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
    private final @Nullable ServerLevel level;
    private final BlockPos pos;
    private final @Nullable Block expected;
    private final @Nullable BlockEntity expectedEntity;
    private @Nullable SolidFissionController controller;
    private long nextSearch;

    public SaltFissionPartMenu(int id, Inventory player, Kind kind) {
        this(id, player, kind, null, BlockPos.ZERO, null);
    }

    private SaltFissionPartMenu(int id, Inventory player, Kind kind, @Nullable ServerLevel level,
                               BlockPos pos, @Nullable Block expected) {
        super(kind.menu(), id);
        this.kind = kind;
        this.level = level;
        this.pos = pos.immutable();
        this.expected = expected;
        expectedEntity = level == null ? null : level.getBlockEntity(pos);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
        refresh();
    }

    static void open(Player player, ServerLevel level, BlockPos pos, Kind kind) {
        Block block = level.getBlockState(pos).getBlock();
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new SaltFissionPartMenu(id, inventory, kind, level, pos, block), block.getName()));
    }

    public Kind kind() {
        return kind;
    }

    public FluidStack fluid(boolean output) {
        int index = output ? OUTPUT : INPUT;
        return fluidAt(index, get(index + 1));
    }

    public int capacity(boolean output) {
        return get((output ? OUTPUT : INPUT) + 2);
    }

    public FluidStack filter() {
        return fluidAt(FILTER, 1);
    }

    public FluidStack reserved() {
        return fluidAt(RESERVED, get(RESERVED_AMOUNT));
    }

    public float progress() {
        return get(PROGRESS) / 1000F;
    }

    public int heat() {
        return get(HEAT);
    }

    public int heatCapacity() {
        return get(HEAT_CAPACITY);
    }

    public int cooling() {
        return get(COOLING);
    }

    public boolean connected() {
        return (get(STATUS) & CONNECTED) != 0;
    }

    public boolean processing() {
        return (get(STATUS) & PROCESSING) != 0;
    }

    public boolean outputPort() {
        return (get(STATUS) & OUTPUT_PORT) != 0;
    }

    private FluidStack fluidAt(int index, int amount) {
        return new FluidStack(BuiltInRegistries.FLUID.byId(get(index)), amount);
    }

    // Vanilla sends each data slot as a signed short. Split volumes and registry IDs into unsigned words.
    private int get(int index) {
        return (data.get(2 * index) & 0xffff) | ((data.get(2 * index + 1) & 0xffff) << 16);
    }

    private void set(int index, int value) {
        data.set(2 * index, value & 0xffff);
        data.set(2 * index + 1, value >>> 16);
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return level == null || (player.level() == level && expected != null
                && (expectedEntity == null || (level.getBlockEntity(pos) == expectedEntity && !expectedEntity.isRemoved()))
                && stillValid(ContainerLevelAccess.create(level, pos), player, expected));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level == null || !stillValid(player) || (id != CLEAR_INPUT && id != CLEAR_OUTPUT)) {
            return false;
        }
        var part = part();
        if (part == null) {
            return false;
        }
        boolean output = id == CLEAR_OUTPUT;
        var tank = tank(part, output);
        if (tank != null && !tank.isEmpty()) {
            tank.drain(tank.getFluidAmount(), EXECUTE);
            return true;
        }
        return !output && part.clearFilter();
    }

    private void refresh() {
        if (level == null) {
            return;
        }
        var part = part();
        if (part == null) {
            return;
        }
        setTank(INPUT, tank(part, false));
        setTank(OUTPUT, tank(part, true));
        ResourceLocation filter = part.filter();
        set(FILTER, fluidId(part.coolant != null ? part.coolant.cold() : filter == null ? null : ForgeRegistries.FLUIDS.getValue(filter)));
        FluidStack reserved = part.vessel == null ? FluidStack.EMPTY : part.vessel.reservedForInspection();
        set(RESERVED, fluidId(reserved.getFluid()));
        set(RESERVED_AMOUNT, reserved.getAmount());
        double progress = part.vessel != null ? part.vessel.progressForInspection()
                : part.coolant != null && !kind.port() && part.controller != null
                    ? part.controller.heaterProgress(pos, part.coolant) : 0;
        set(PROGRESS, (int) (1000 * progress));
        set(COOLING, part.coolant != null ? NCSaltFission.cooling(part.coolant) : 0);
        var heat = part.controller == null || kind.port() ? SolidFissionCore.PartHeat.NONE : part.controller.partHeat(pos);
        set(HEAT, (int) Math.min(Integer.MAX_VALUE, heat.amount()));
        set(HEAT_CAPACITY, (int) Math.min(Integer.MAX_VALUE, heat.capacity()));
        set(STATUS, (part.controller != null ? CONNECTED : 0)
                | (heat.processing() ? PROCESSING : 0) | (part.outputPort ? OUTPUT_PORT : 0));
    }

    private static int fluidId(@Nullable Fluid fluid) {
        return fluid == null ? 0 : BuiltInRegistries.FLUID.getId(fluid);
    }

    private void setTank(int index, @Nullable FluidTank tank) {
        set(index, tank == null ? 0 : fluidId(tank.getFluid().getFluid()));
        set(index + 1, tank == null ? 0 : tank.getFluidAmount());
        set(index + 2, tank == null ? 0 : tank.getCapacity());
    }

    private @Nullable FluidTank tank(Part part, boolean output) {
        if (part.controller == null) {
            return null;
        }
        return part.coolant == null ? part.controller.existingSaltFuelTank(part.circuit(output), output)
                : part.controller.existingSaltCoolantTank(part.coolant, output);
    }

    private @Nullable Part part() {
        if (level == null || expected == null) {
            return null;
        }
        var state = level.getBlockState(pos);
        if (!state.is(expected)) {
            return null;
        }
        var entity = level.getBlockEntity(pos);
        if (entity instanceof FissionPortBlockEntity port && state.getBlock() instanceof FissionWallBlock wall) {
            return new Part(port.saltController(), null, port, wall.saltCoolant(), state.getValue(FissionWallBlock.ACTIVE));
        }
        if (entity instanceof SaltVessel vessel) {
            return new Part(interiorController(), vessel, null, null, false);
        }
        if (state.getBlock() instanceof SaltHeaterBlock heater) {
            return new Part(interiorController(), null, null, heater.coolant(), false);
        }
        return null;
    }

    private @Nullable SolidFissionController interiorController() {
        if (controller != null && !controller.containsSaltComponent(pos, kind)) {
            controller = null;
            nextSearch = 0;
        }
        if (controller == null && level != null && level.getGameTime() >= nextSearch) {
            nextSearch = level.getGameTime() + 20;
            Cuboid.visitNearby(level, new ChunkPos(pos), NCFission.searchLength(),
                    SolidFissionController.class, candidate -> {
                        if (controller == null && candidate.containsSaltComponent(pos, kind)) {
                            controller = candidate;
                        }
                    });
        }
        return controller;
    }

    private record Part(@Nullable SolidFissionController controller, @Nullable SaltVessel vessel,
                        @Nullable FissionPortBlockEntity port, @Nullable NCSaltCoolants coolant, boolean outputPort) {
        @Nullable ResourceLocation filter() {
            return vessel != null ? vessel.filter() : port != null ? port.fuelFilter() : null;
        }

        @Nullable ResourceLocation circuit(boolean output) {
            return output && vessel != null ? vessel.outputCircuit() : filter();
        }

        boolean clearFilter() {
            if (coolant != null || filter() == null) {
                return false;
            }
            if (vessel != null) {
                vessel.filter(null);
                FissionPartBlock.geometryChanged((ServerLevel) vessel.getLevel(), vessel.getBlockPos());
            } else if (port != null) {
                port.fuelFilter(null);
                FissionPartBlock.geometryChanged((ServerLevel) port.getLevel(), port.getBlockPos());
            }
            return true;
        }
    }

    public enum Kind {
        VESSEL("salt_fission_vessel"), HEATER("salt_fission_heater"),
        VESSEL_PORT("fission_vessel_port"), HEATER_PORT("fission_heater_port");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() { return id; }
        public boolean port() { return this == VESSEL_PORT || this == HEATER_PORT; }
        public boolean fuel() { return this == VESSEL || this == VESSEL_PORT; }

        MenuType<SaltFissionPartMenu> menu() {
            return NCSaltFission.partMenu(this);
        }
    }
}
