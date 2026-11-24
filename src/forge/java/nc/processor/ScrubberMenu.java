package nc.processor;

import nc.init.NCRadiation;
import nc.processor.ItemProcessorBlockEntity.SideMode;
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

public final class ScrubberMenu extends AbstractContainerMenu {
    private static final Direction[] SIDES = Direction.values();
    private static final int PROGRESS = 0;
    private static final int ENERGY = 1;
    private static final int REDSTONE = 2;
    private static final int OUTPUT_MODE = 3;
    private static final int FIRST_TANK = 4;
    private static final int FIRST_SIDE = 10;
    private static final int DATA_COUNT = FIRST_SIDE + 6;
    public static final int ITEMS = 2;
    public static final int OUTPUT_MODE_BUTTON = 18;
    public static final int REDSTONE_BUTTON = 19;

    private final @Nullable RadiationScrubber scrubber;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public ScrubberMenu(int id, Inventory player) {
        this(id, player, null, ContainerLevelAccess.NULL);
    }

    ScrubberMenu(int id, Inventory player, @Nullable RadiationScrubber scrubber, ContainerLevelAccess access) {
        super(NCRadiation.SCRUBBER_MENU.get(), id);
        this.scrubber = scrubber;
        this.access = access;
        data = scrubber == null ? new SimpleContainerData(DATA_COUNT) : machineData(scrubber);
        ItemStackHandler inventory = scrubber == null ? new ItemStackHandler(2) : scrubber.inventory();
        addSlot(new SlotItemHandler(inventory, 0, 32, 35));
        addSlot(new SlotItemHandler(inventory, 1, 112, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    private static ContainerData machineData(RadiationScrubber machine) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROGRESS -> machine.progress();
                    case ENERGY -> machine.charge();
                    case REDSTONE -> machine.redstoneControl() ? 1 : 0;
                    case OUTPUT_MODE -> machine.outputMode().ordinal();
                    default -> {
                        if (index >= FIRST_SIDE) {
                            yield machine.itemMode(SIDES[index - FIRST_SIDE]);
                        }
                        int offset = index - FIRST_TANK;
                        var fluid = machine.fluid(offset / 3);
                        yield switch (offset % 3) {
                            case 0 -> BuiltInRegistries.FLUID.getId(fluid.getFluid());
                            case 1 -> fluid.getAmount();
                            default -> machine.tankModes(offset / 3);
                        };
                    }
                };
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        };
    }

    public float progress() { return data.get(PROGRESS) / 1000F; }
    public float energy() { return data.get(ENERGY) / 1000F; }
    public boolean redstoneEnabled() { return data.get(REDSTONE) != 0; }

    public FluidStack fluid(int tank) {
        int index = FIRST_TANK + 3 * tank;
        return new FluidStack(BuiltInRegistries.FLUID.byId(data.get(index)), data.get(index + 1));
    }

    public ProcessorFluidTanks.Mode fluidSide(int tank, Direction side) {
        return ProcessorFluidTanks.Mode.read(data.get(FIRST_TANK + 3 * tank + 2), side);
    }

    public SideMode itemSide(Direction side) {
        return SideMode.byId(data.get(FIRST_SIDE + side.ordinal()));
    }

    public ProcessorFluidTanks.OutputMode outputMode() {
        return ProcessorFluidTanks.OutputMode.byId(data.get(OUTPUT_MODE));
    }

    public static int sideButton(int selected, Direction side) {
        return selected * SIDES.length + side.ordinal();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (scrubber == null || button < 0 || !stillValid(player)) {
            return false;
        }
        if (button < OUTPUT_MODE_BUTTON) {
            Direction side = SIDES[button % SIDES.length];
            int target = button / SIDES.length;
            if (target == ITEMS) {
                scrubber.cycleItems(side);
            } else {
                scrubber.cycleTank(target, side);
            }
            return true;
        }
        if (button == OUTPUT_MODE_BUTTON) {
            scrubber.cycleOutput();
            return true;
        }
        if (button == REDSTONE_BUTTON) {
            scrubber.toggleRedstone();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NCRadiation.SCRUBBER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!(index < 2 ? moveItemStackTo(stack, 2, slots.size(), true) : moveItemStackTo(stack, 0, 1, false))) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        if (scrubber != null) {
            scrubber.setChanged();
        }
        return original;
    }
}
