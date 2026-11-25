package nc.multiblock.fission;

import nc.init.NCFission;
import nc.recipe.FissionCoolantRecipes;
import nc.recipe.FluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public final class FissionPartMenu extends AbstractContainerMenu {
    public static final int CLEAR_INPUT = 0;
    public static final int CLEAR_OUTPUT = 1;
    public static final int SWITCH_BUFFER = 2;
    private static final int FILTER = 0, RESERVED = 1, INPUT = 2, OUTPUT = 5, PROGRESS = 8;
    private static final int HEAT = 9, HEAT_CAPACITY = 10, COOLING = 11, STATUS = 12;
    private static final int FUEL_IN = 13, FUEL_OUT = 14, IN_LIMIT = 15, OUT_LIMIT = 16;
    private static final int GAS_BLOCK = 17;
    private static final int CONNECTED = 1, LINKED = 2, LOCAL_VIEW = 4, PROCESSING = 8, OUTPUT_PORT = 16, GAS_FILTER = 32;

    private final SimpleContainerData data = new SimpleContainerData(36);
    private final Kind kind;
    private final Level level;
    private final @Nullable BlockEntity entity;
    private boolean local;

    public FissionPartMenu(int id, Inventory inventory, Kind kind) {
        this(id, inventory, kind, null);
    }

    private FissionPartMenu(int id, Inventory inventory, Kind kind, @Nullable BlockEntity entity) {
        super(NCFission.partMenu(kind), id);
        this.kind = kind;
        level = inventory.player.level();
        this.entity = entity;
        if (kind.fuel()) {
            var items = entity == null ? new ItemStackHandler(2) : new MenuItems();
            addSlot(new FuelSlot(items, 0, kind.port() ? 44 : 56));
            addSlot(new FuelSlot(items, 1, 116));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
        refresh();
    }

    static void open(Player player, BlockEntity entity, Kind kind) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new FissionPartMenu(id, inventory, kind, entity), entity.getBlockState().getBlock().getName()));
    }

    static void interactFuel(Player player, InteractionHand hand, BlockEntity entity, Kind kind) {
        ItemStack held = player.getItemInHand(hand);
        FissionFuelItems storage = localItems(entity);
        if (controller(entity) == null && storage.acceptsRecipe(held) && storage.select(FissionFuelItems.id(held))) {
            if (entity.getLevel() instanceof ServerLevel level) {
                FissionPartBlock.geometryChanged(level, entity.getBlockPos());
            }
            player.displayClientMessage(Component.translatable(
                    "gui.nuclearcraft.salt_part.filter", held.getHoverName()), true);
        } else {
            open(player, entity, kind);
        }
    }

    private static @Nullable SolidFissionController controller(BlockEntity entity) {
        if (entity instanceof FissionFuelCell cell) {
            return cell.controller();
        }
        if (entity instanceof PebbleCooler cooler) {
            return cooler.controller();
        }
        var port = (FissionPortBlockEntity) entity;
        return FissionPartBlock.role(port.getBlockState()) == FissionPartBlock.Role.COOLER_PORT
                ? port.pebbleController() : port.itemFuelController();
    }

    private static FissionFuelItems localItems(BlockEntity entity) {
        return entity instanceof FissionFuelCell cell ? cell.localItems()
                : ((FissionPortBlockEntity) entity).localFuelItems();
    }

    private FissionFuelItems sharedItems(boolean output) {
        return entity instanceof FissionFuelCell cell ? cell.items(output) : ((FissionPortBlockEntity) entity).fuelItems();
    }

    private FissionFuelItems selectedItems(int slot) {
        return local ? localItems(entity) : sharedItems(slot == 1);
    }

    private CoolantTanks localGas() {
        return entity instanceof PebbleCooler cooler ? cooler.localTanks() : ((FissionPortBlockEntity) entity).localPebbleGas();
    }

    private CoolantTanks sharedGas() {
        return entity instanceof PebbleCooler cooler ? cooler.tanks() : ((FissionPortBlockEntity) entity).pebbleGas();
    }

    private CoolantTanks selectedGas() {
        return local ? localGas() : sharedGas();
    }

    private boolean linkedStorage() {
        return kind.fuel() ? sharedItems(false) != localItems(entity) || sharedItems(true) != localItems(entity)
                : sharedGas() != localGas();
    }

    @Override
    public boolean stillValid(Player player) {
        if (entity == null) {
            return true;
        }
        var level = entity.getLevel();
        var pos = entity.getBlockPos();
        return level != null && player.level() == level && !entity.isRemoved()
                && level.hasChunkAt(pos) && level.getBlockEntity(pos) == entity
                && stillValid(ContainerLevelAccess.create(level, pos), player, entity.getBlockState().getBlock());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!kind.fuel() || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < 2 ? moveItemStackTo(stack, 2, slots.size(), true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || !stillValid(player) || !(entity.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        if (id == SWITCH_BUFFER && linkedStorage()) {
            local = !local;
            broadcastChanges();
            return true;
        }
        if (id != CLEAR_INPUT && id != CLEAR_OUTPUT) {
            return false;
        }
        if (kind.fuel()) {
            if (id == CLEAR_INPUT && controller(entity) == null && localItems(entity).select(null)) {
                FissionPartBlock.geometryChanged(level, entity.getBlockPos());
                return true;
            }
            return false;
        }
        FluidTank tank = selectedGas().tank(id == CLEAR_OUTPUT);
        if (!tank.isEmpty()) {
            tank.drain(tank.getFluidAmount(), FluidAction.EXECUTE);
            return true;
        }
        if (id != CLEAR_INPUT) {
            return false;
        }
        return entity instanceof PebbleCooler cooler ? cooler.selectFilter(FluidStack.EMPTY)
                : ((FissionPortBlockEntity) entity).selectGasFilter(FluidStack.EMPTY);
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    private void refresh() {
        if (entity == null) {
            return;
        }
        boolean linked = linkedStorage();
        local &= linked;
        var controller = controller(entity);
        var pos = entity.getBlockPos();
        var heat = controller == null || kind.port() ? SolidFissionCore.PartHeat.NONE : controller.partHeat(pos);
        set(HEAT, (int) Math.min(Integer.MAX_VALUE, heat.amount()));
        set(HEAT_CAPACITY, (int) Math.min(Integer.MAX_VALUE, heat.capacity()));
        boolean processing = heat.processing();
        double progress = 0D;
        boolean gasFilter = false;
        if (kind.fuel()) {
            var input = selectedItems(0);
            var output = selectedItems(1);
            set(FUEL_IN, input.total(0));
            set(FUEL_OUT, output.total(1));
            set(IN_LIMIT, input.capacity());
            set(OUT_LIMIT, output.capacity());
            var filter = input.filter();
            set(FILTER, filter == null ? 0 : BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(filter)));
            if (entity instanceof FissionFuelCell cell && !local) {
                var reserved = cell.reservedFuel();
                set(RESERVED, reserved.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(reserved.getItem()));
                progress = cell.progressFraction();
            } else {
                set(RESERVED, 0);
            }
        } else {
            PebbleCoolant gas = entity instanceof PebbleCooler cooler ? cooler.gas()
                    : ((FissionWallBlock) entity.getBlockState().getBlock()).coolant();
            var tanks = selectedGas();
            setTank(INPUT, tanks.cold);
            setTank(OUTPUT, tanks.hot);
            ResourceLocation filter = tanks.filter();
            if (filter == null && entity instanceof PebbleCooler cooler) {
                filter = cooler.filter();
            }
            gasFilter = filter != null;
            FluidStack input = tanks.cold.getFluid();
            if (input.isEmpty() && filter != null) {
                input = new FluidStack(BuiltInRegistries.FLUID.get(filter), 1);
            }
            var recipe = FissionCoolantRecipes.displayedRecipe(level, gas, input);
            var fluid = filter != null ? BuiltInRegistries.FLUID.get(filter)
                    : recipe.input().alternatives().stream().findFirst().orElse(FluidStack.EMPTY).getFluid();
            set(FILTER, BuiltInRegistries.FLUID.getId(fluid));
            set(GAS_BLOCK, BuiltInRegistries.BLOCK.getId(entity.getBlockState().getBlock()));
            set(COOLING, recipe.cooling());
            if (controller != null && !kind.port() && !local) {
                progress = controller.gasProgress(pos);
                processing = controller.isCooling(pos);
            }
        }
        set(PROGRESS, (int) (1000 * progress));
        set(STATUS, (controller != null ? CONNECTED : 0) | (linked ? LINKED : 0) | (local ? LOCAL_VIEW : 0)
                | (processing ? PROCESSING : 0) | (gasFilter ? GAS_FILTER : 0)
                | (kind.port() && entity.getBlockState().getValue(FissionWallBlock.ACTIVE) ? OUTPUT_PORT : 0));
    }

    private void setTank(int index, FluidTank tank) {
        set(index, BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid()));
        set(index + 1, tank.getFluidAmount());
        set(index + 2, tank.getCapacity());
    }

    // Vanilla menu data is split into unsigned words so full capacities reach the client.
    private void set(int index, int value) {
        data.set(2 * index, value & 0xffff);
        data.set(2 * index + 1, value >>> 16);
    }

    private int get(int index) {
        return (data.get(2 * index) & 0xffff) | ((data.get(2 * index + 1) & 0xffff) << 16);
    }

    public Kind kind() { return kind; }
    public boolean connected() { return (get(STATUS) & CONNECTED) != 0; }
    public boolean linked() { return (get(STATUS) & LINKED) != 0; }
    public boolean local() { return (get(STATUS) & LOCAL_VIEW) != 0; }
    public boolean processing() { return (get(STATUS) & PROCESSING) != 0; }
    public boolean exactGasFilter() { return (get(STATUS) & GAS_FILTER) != 0; }
    public boolean canClearGasFilter() { return exactGasFilter() && !connected(); }
    public boolean outputPort() { return (get(STATUS) & OUTPUT_PORT) != 0; }
    public float progress() { return get(PROGRESS) / 1000F; }
    public int heat() { return get(HEAT); }
    public int heatCapacity() { return get(HEAT_CAPACITY); }
    public int cooling() { return get(COOLING); }
    public int fuelAmount(boolean output) { return get(output ? FUEL_OUT : FUEL_IN); }
    public int fuelCapacity(boolean output) { return get(output ? OUT_LIMIT : IN_LIMIT); }
    public ItemStack fuelFilter() { return new ItemStack(BuiltInRegistries.ITEM.byId(get(FILTER))); }
    public ItemStack reserved() { return new ItemStack(BuiltInRegistries.ITEM.byId(get(RESERVED))); }
    public FluidStack gasFilter() { return new FluidStack(BuiltInRegistries.FLUID.byId(get(FILTER)), 1); }
    public int capacity(boolean output) { return get((output ? OUTPUT : INPUT) + 2); }

    public @Nullable FluidIngredient gasInput() {
        var block = BuiltInRegistries.BLOCK.byId(get(GAS_BLOCK));
        PebbleCoolant material = block instanceof PebbleCoolerBlock cooler ? cooler.gas()
                : block instanceof FissionWallBlock wall ? wall.coolant() : null;
        FluidStack input = gas(false);
        if (input.isEmpty() && exactGasFilter()) {
            input = gasFilter();
        }
        return material == null ? null : FissionCoolantRecipes.displayedInput(level, material, input);
    }

    public FluidStack gas(boolean output) {
        int index = output ? OUTPUT : INPUT;
        return new FluidStack(BuiltInRegistries.FLUID.byId(get(index)), get(index + 1));
    }

    private final class FuelSlot extends SlotItemHandler {
        FuelSlot(IItemHandlerModifiable handler, int slot, int x) {
            super(handler, slot, x, 35);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getSlotIndex() == 0 && super.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public void setChanged() {
            if (entity != null) {
                selectedItems(getSlotIndex()).onContentsChanged(getSlotIndex());
            }
            super.setChanged();
        }
    }

    private final class MenuItems implements IItemHandlerModifiable {
        @Override public int getSlots() { return 2; }
        @Override public void setStackInSlot(int slot, ItemStack stack) { selectedItems(slot).setStackInSlot(slot, stack); }
        @Override public ItemStack getStackInSlot(int slot) { return selectedItems(slot).getStackInSlot(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            FissionFuelItems items = selectedItems(slot);
            return items.isItemValid(slot, stack) && (items.total(slot) <= items.getStackInSlot(slot).getCount()
                    || ItemStack.isSameItemSameTags(stack, items.getStackInSlot(slot)));
        }
        @Override public int getSlotLimit(int slot) { return selectedItems(slot).getSlotLimit(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return selectedItems(slot).insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return selectedItems(slot).extractItem(slot, amount, simulate);
        }
    }

    public enum Kind {
        CELL("solid_fission_cell", true, false), CELL_PORT("fission_cell_port", true, true),
        CHAMBER("pebble_fission_chamber", true, false), COOLER("pebble_fission_cooler", false, false),
        CHAMBER_PORT("fission_chamber_port", true, true), COOLER_PORT("fission_cooler_port", false, true);

        private final String id;
        private final boolean fuel;
        private final boolean port;

        Kind(String id, boolean fuel, boolean port) {
            this.id = id;
            this.fuel = fuel;
            this.port = port;
        }

        public String id() { return id; }
        public boolean fuel() { return fuel; }
        public boolean port() { return port; }
    }
}
