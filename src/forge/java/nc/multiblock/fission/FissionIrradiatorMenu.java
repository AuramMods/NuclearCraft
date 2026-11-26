package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public final class FissionIrradiatorMenu extends AbstractContainerMenu {
    public static final int CLEAR_FILTER = 0;
    public static final int SWITCH_INVENTORY = 1;
    private static final int FILTER = 0, FLUX = 1, PROGRESS = 2, HEAT = 3, CAPACITY = 4, STATUS = 5;
    private static final int INPUT_COUNT = 6, OUTPUT_COUNT = 7, ITEM_CAPACITY = 8;
    private static final int CONNECTED = 1, LINKED = 2, LOCAL = 4, OUTPUT = 8;
    private static final int PLAYER_START = 2;
    private static final int PLAYER_END = 38;

    private final SimpleContainerData data = new SimpleContainerData(2 * (ITEM_CAPACITY + 1));
    private final boolean port;
    private final @Nullable BlockEntity entity;
    private boolean local;

    public FissionIrradiatorMenu(int id, Inventory player, boolean port) {
        this(id, player, port, null);
    }

    private FissionIrradiatorMenu(int id, Inventory player, boolean port, @Nullable BlockEntity entity) {
        super((port ? NCFission.IRRADIATOR_PORT_MENU : NCFission.IRRADIATOR_MENU).get(), id);
        this.port = port;
        this.entity = entity;
        var handler = entity == null ? new ItemStackHandler(2) : new MenuItems();
        addSlot(new IrradiatorSlot(handler, 0, port ? 44 : 56));
        addSlot(new IrradiatorSlot(handler, 1, 116));
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

    static void interact(ServerLevel level, Player player, InteractionHand hand, BlockEntity entity) {
        var held = player.getItemInHand(hand);
        if (controller(entity) == null && IrradiatorItems.validInput(level, held) && localItems(entity).setFilter(held)) {
            FissionPartBlock.geometryChanged(level, entity.getBlockPos());
            return;
        }
        var block = entity.getBlockState().getBlock();
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new FissionIrradiatorMenu(id, inventory, entity instanceof FissionPortBlockEntity, entity), block.getName()));
    }

    private static IrradiatorItems localItems(BlockEntity entity) {
        return entity instanceof FissionIrradiator part ? part.localItems() : ((FissionPortBlockEntity) entity).localIrradiatorItems();
    }

    private static IrradiatorItems sharedItems(BlockEntity entity) {
        return entity instanceof FissionIrradiator part ? part.inventory() : ((FissionPortBlockEntity) entity).irradiatorItems();
    }

    private static @Nullable SolidFissionController controller(BlockEntity entity) {
        return entity instanceof FissionIrradiator part ? part.controller() : ((FissionPortBlockEntity) entity).irradiatorController();
    }

    private IrradiatorItems selected() {
        return local ? localItems(entity) : sharedItems(entity);
    }

    @Override
    public boolean stillValid(Player player) {
        if (entity == null) {
            return true;
        }
        var level = entity.getLevel();
        var pos = entity.getBlockPos();
        return level != null && player.level() == level && !entity.isRemoved() && level.getBlockEntity(pos) == entity
                && stillValid(ContainerLevelAccess.create(level, pos), player, entity.getBlockState().getBlock());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= PLAYER_END) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PLAYER_START
                ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
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
        if (entity == null || !(entity.getLevel() instanceof ServerLevel level) || !stillValid(player)) {
            return false;
        }
        if (id == SWITCH_INVENTORY && sharedItems(entity) != localItems(entity)) {
            local = !local;
            broadcastChanges();
            return true;
        }
        if (id == CLEAR_FILTER && controller(entity) == null && localItems(entity).setFilter(ItemStack.EMPTY)) {
            FissionPartBlock.geometryChanged(level, entity.getBlockPos());
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    private void refresh() {
        if (entity == null || !(entity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var controller = controller(entity);
        boolean linked = sharedItems(entity) != localItems(entity);
        local &= linked;
        var inventory = selected();
        var filter = inventory.filter();
        set(INPUT_COUNT, inventory.total(0));
        set(OUTPUT_COUNT, inventory.total(1));
        set(ITEM_CAPACITY, inventory.capacity());
        set(FILTER, filter == null ? 0 : BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(filter)));
        if (entity instanceof FissionIrradiator part) {
            var heat = controller == null ? SolidFissionCore.PartHeat.NONE : controller.partHeat(entity.getBlockPos());
            set(HEAT, (int) Math.min(Integer.MAX_VALUE, heat.amount()));
            set(CAPACITY, (int) Math.min(Integer.MAX_VALUE, heat.capacity()));
            set(PROGRESS, local ? 0 : (int) (1000 * part.progress(level)));
            set(FLUX, local ? 0 : (int) Math.min(Integer.MAX_VALUE, part.flux()));
        }
        set(STATUS, (controller != null ? CONNECTED : 0) | (linked ? LINKED : 0) | (local ? LOCAL : 0)
                | (port && entity.getBlockState().getValue(FissionWallBlock.ACTIVE) ? OUTPUT : 0));
    }

    // Each vanilla menu data value travels as a signed short.
    private void set(int index, int value) {
        data.set(2 * index, value & 0xffff);
        data.set(2 * index + 1, value >>> 16);
    }

    private int get(int index) {
        return (data.get(2 * index) & 0xffff) | ((data.get(2 * index + 1) & 0xffff) << 16);
    }

    public boolean port() { return port; }
    public boolean connected() { return (get(STATUS) & CONNECTED) != 0; }
    public boolean linked() { return (get(STATUS) & LINKED) != 0; }
    public boolean local() { return (get(STATUS) & LOCAL) != 0; }
    public boolean output() { return (get(STATUS) & OUTPUT) != 0; }
    public float progress() { return get(PROGRESS) / 1000F; }
    public int flux() { return get(FLUX); }
    public int heat() { return get(HEAT); }
    public int heatCapacity() { return get(CAPACITY); }
    public int itemCount(boolean output) { return get(output ? OUTPUT_COUNT : INPUT_COUNT); }
    public int itemCapacity() { return get(ITEM_CAPACITY); }
    public ItemStack filter() { return new ItemStack(BuiltInRegistries.ITEM.byId(get(FILTER))); }

    private final class IrradiatorSlot extends SlotItemHandler {
        IrradiatorSlot(IItemHandlerModifiable handler, int slot, int x) {
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
                selected().onContentsChanged(getSlotIndex());
            }
            super.setChanged();
        }
    }

    private final class MenuItems implements IItemHandlerModifiable {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int slot) { return selected().getStackInSlot(slot); }
        @Override public void setStackInSlot(int slot, ItemStack stack) { selected().setStackInSlot(slot, stack); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return selected().isItemValid(slot, stack); }
        @Override public int getSlotLimit(int slot) { return selected().getSlotLimit(slot); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return selected().insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return selected().extractItem(slot, amount, simulate);
        }
    }
}
