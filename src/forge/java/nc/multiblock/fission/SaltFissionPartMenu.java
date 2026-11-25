package nc.multiblock.fission;

import nc.init.NCSaltFission;
import nc.recipe.FissionCoolantRecipes;
import nc.recipe.FluidIngredient;
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
import net.minecraft.world.level.Level;
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
    public static final int SWITCH_BUFFER = 2;
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
    private static final int HEATER_BLOCK = 14;
    private static final int DATA_SIZE = 15 * 2;
    private static final int CONNECTED = 1;
    private static final int PROCESSING = 2;
    private static final int OUTPUT_PORT = 4;
    private static final int LINKED = 8;
    private static final int LOCAL_VIEW = 16;
    private static final int CAN_CLEAR_FILTER = 32;

    private final Kind kind;
    private final Level playerLevel;
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
    private final @Nullable ServerLevel level;
    private final BlockPos pos;
    private final @Nullable Block expected;
    private final @Nullable BlockEntity expectedEntity;
    private boolean viewLocal;

    public SaltFissionPartMenu(int id, Inventory player, Kind kind) {
        this(id, player, kind, null, BlockPos.ZERO, null);
    }

    private SaltFissionPartMenu(int id, Inventory player, Kind kind, @Nullable ServerLevel level,
                               BlockPos pos, @Nullable Block expected) {
        super(kind.menu(), id);
        this.kind = kind;
        playerLevel = player.player.level();
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

    public @Nullable FluidIngredient coolantInput() {
        var block = BuiltInRegistries.BLOCK.byId(get(HEATER_BLOCK));
        FissionCoolant material = block instanceof SaltHeaterBlock heater ? heater.coolant()
                : block instanceof FissionWallBlock wall ? wall.saltCoolant() : null;
        return material == null ? null : FissionCoolantRecipes.displayedInput(playerLevel, material, fluid(false));
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

    public boolean linked() {
        return (get(STATUS) & LINKED) != 0;
    }

    public boolean local() {
        return (get(STATUS) & LOCAL_VIEW) != 0;
    }

    public boolean canClearFilter() {
        return (get(STATUS) & CAN_CLEAR_FILTER) != 0;
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
        if (level == null || !stillValid(player)) {
            return false;
        }
        var part = part();
        if (part == null) {
            return false;
        }
        if (id == SWITCH_BUFFER && part.linked()) {
            viewLocal = !viewLocal;
            broadcastChanges();
            return true;
        }
        if (id != CLEAR_INPUT && id != CLEAR_OUTPUT) {
            return false;
        }
        boolean output = id == CLEAR_OUTPUT;
        var tank = part.tank(viewLocal, output);
        if (!tank.isEmpty()) {
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
        boolean linked = part.linked();
        viewLocal &= linked;
        var input = part.tank(viewLocal, false);
        setTank(INPUT, input);
        setTank(OUTPUT, part.tank(viewLocal, true));
        ResourceLocation filter = part.filter();
        var feed = input == null ? FluidStack.EMPTY : input.getFluid();
        var recipe = part.coolant == null ? null : FissionCoolantRecipes.displayedRecipe(level, part.coolant, feed);
        var filterFluid = filter == null ? null : ForgeRegistries.FLUIDS.getValue(filter);
        if (recipe != null) {
            filterFluid = recipe.input().accepts(feed) ? feed.getFluid()
                    : recipe.input().alternatives().stream().findFirst().orElse(FluidStack.EMPTY).getFluid();
        }
        set(FILTER, fluidId(filterFluid));
        set(HEATER_BLOCK, part.coolant == null ? 0 : BuiltInRegistries.BLOCK.getId(expected));
        FluidStack reserved = part.vessel == null ? FluidStack.EMPTY : part.vessel.reservedForInspection();
        set(RESERVED, fluidId(reserved.getFluid()));
        set(RESERVED_AMOUNT, reserved.getAmount());
        double progress = part.vessel != null ? part.vessel.progressForInspection()
                : part.coolant != null && !kind.port() && part.controller != null
                    ? part.controller.heaterProgress(pos, part.coolant) : 0;
        set(PROGRESS, (int) (1000 * progress));
        set(COOLING, recipe == null ? 0 : recipe.cooling());
        var heat = part.controller == null || kind.port() ? SolidFissionCore.PartHeat.NONE : part.controller.partHeat(pos);
        set(HEAT, (int) Math.min(Integer.MAX_VALUE, heat.amount()));
        set(HEAT_CAPACITY, (int) Math.min(Integer.MAX_VALUE, heat.capacity()));
        set(STATUS, (part.controller != null ? CONNECTED : 0)
                | (heat.processing() ? PROCESSING : 0) | (part.outputPort ? OUTPUT_PORT : 0)
                | (linked ? LINKED : 0) | (viewLocal ? LOCAL_VIEW : 0)
                | (part.controller == null && filter != null ? CAN_CLEAR_FILTER : 0));
    }

    private static int fluidId(@Nullable Fluid fluid) {
        return fluid == null ? 0 : BuiltInRegistries.FLUID.getId(fluid);
    }

    private void setTank(int index, @Nullable FluidTank tank) {
        set(index, tank == null ? 0 : fluidId(tank.getFluid().getFluid()));
        set(index + 1, tank == null ? 0 : tank.getFluidAmount());
        set(index + 2, tank == null ? 0 : tank.getCapacity());
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
            return new Part(port.saltController(), null, port, null, wall.saltCoolant(), state.getValue(FissionWallBlock.ACTIVE));
        }
        if (entity instanceof SaltVessel vessel) {
            return new Part(vessel.controller(), vessel, null, null, null, false);
        }
        if (entity instanceof SaltHeater heater) {
            return new Part(heater.controller(), null, null, heater, heater.coolant(), false);
        }
        return null;
    }

    private record Part(@Nullable SolidFissionController controller, @Nullable SaltVessel vessel,
                        @Nullable FissionPortBlockEntity port, @Nullable SaltHeater heater,
                        @Nullable FissionCoolant coolant, boolean outputPort) {
        @Nullable CoolantTanks sharedCoolant() {
            return coolant == null || controller == null ? null : controller.connectedSaltCoolant(coolant);
        }

        CoolantTanks localCoolant() {
            return heater == null ? port.localSaltCoolant() : heater.localTanks();
        }

        @Nullable ResourceLocation filter() {
            return vessel != null ? vessel.filter() : port != null ? port.fuelFilter() : null;
        }

        FluidTank tank(boolean local, boolean output) {
            if (coolant != null) {
                var shared = local ? null : sharedCoolant();
                return (shared == null ? localCoolant() : shared).tank(output);
            }
            if (vessel != null) {
                return local ? vessel.localTanks().tank(output) : vessel.fuelTank(output);
            }
            return (local || controller == null ? port.localVesselFuel()
                    : controller.saltFuelBank().circuit(port.fuelFilter())).tank(output);
        }

        boolean linked() {
            if (controller == null) {
                return false;
            }
            if (coolant != null) {
                return sharedCoolant() != null;
            }
            return vessel == null || vessel.fuelTank(false) != vessel.localTanks().tank(false)
                    || vessel.fuelTank(true) != vessel.localTanks().tank(true);
        }

        boolean clearFilter() {
            if (coolant != null) {
                return false;
            }
            return vessel == null ? port.selectVesselFilter(null) : vessel.selectFilter(null);
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
