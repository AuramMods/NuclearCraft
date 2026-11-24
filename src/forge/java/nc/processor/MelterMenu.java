package nc.processor;

import nc.init.NCRegistries;
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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public final class MelterMenu extends AbstractContainerMenu {
    static final int PROGRESS = 0;
    static final int ENERGY = 1;
    static final int FLUID_AMOUNT = 2;
    static final int FLUID_ID = 3;
    static final int FLUID_SIDES = 4;
    static final int FLUID_OUTPUT_MODE = 5;
    static final int DATA_COUNT = 6;
    public static final int FIRST_FLUID_SIDE_BUTTON = 6;
    public static final int FLUID_OUTPUT_BUTTON = 12;
    public static final int UPGRADE_Y = 64;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable MelterBlockEntity melter;
    private final ProcessorItemSettings items;

    public MelterMenu(int id, Inventory player) {
        this(id, player, new ItemStackHandler(1), new ProcessorUpgrades(null), null, new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL);
    }

    MelterMenu(int id, Inventory player, ItemStackHandler input, ProcessorUpgrades upgrades,
               @Nullable MelterBlockEntity melter, ContainerData data,
               ContainerLevelAccess access) {
        super(NCRegistries.MELTER_MENU.get(), id);
        this.data = data;
        this.access = access;
        this.melter = melter;
        items = new ProcessorItemSettings(1, 0, ProcessorUpgrades.SLOTS, FLUID_OUTPUT_BUTTON + 1,
                melter == null ? null : melter.itemPorts());
        addSlot(new SlotItemHandler(input, 0, 56, 35));
        upgrades.addMenuSlots(this::addSlot, UPGRADE_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
        addDataSlots(items.data());
    }

    public ProcessorItemSettings items() {
        return items;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (melter == null || button < 0 || !stillValid(player)) {
            return false;
        }
        if (button < ProcessorItemPorts.SIDES.length) {
            melter.itemPorts().cycleGroup(Direction.from3DDataValue(button));
            return true;
        }
        if (button < FLUID_OUTPUT_BUTTON) {
            melter.fluidTanks().cycle(0, Direction.from3DDataValue(button - FIRST_FLUID_SIDE_BUTTON));
            return true;
        }
        if (button == FLUID_OUTPUT_BUTTON) {
            melter.fluidTanks().cycleSetting(0);
            return true;
        }
        return items.click(button) || ProcessorFluidActions.click(melter.fluidTanks(), button);
    }

    public float progress() {
        return data.get(PROGRESS) / 1000F;
    }

    public float energy() {
        return data.get(ENERGY) / 1000F;
    }

    public FluidStack fluid() {
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(FLUID_ID)), data.get(FLUID_AMOUNT));
    }

    public float fillLevel() {
        return data.get(FLUID_AMOUNT) / (float) ProcessorFluidTanks.CAPACITY;
    }

    public ProcessorFluidTanks.Mode sideMode(Direction side) {
        return ProcessorFluidTanks.Mode.read(data.get(FLUID_SIDES), side);
    }

    public ProcessorFluidTanks.OutputMode outputMode() {
        return ProcessorFluidTanks.OutputMode.byId(data.get(FLUID_OUTPUT_MODE));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NCRegistries.MELTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int upgrade = ProcessorUpgrades.slotFor(stack);
        int machineSlots = 1 + ProcessorUpgrades.SLOTS;
        boolean moved = index < machineSlots ? moveItemStackTo(stack, machineSlots, slots.size(), true)
                : upgrade >= 0 ? moveItemStackTo(stack, 1 + upgrade, 2 + upgrade, false)
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
}
