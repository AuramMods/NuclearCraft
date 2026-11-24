package nc.processor;

import nc.init.NCRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import static net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS;
import static net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME;
import static net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.DATA_LIT_DURATION;
import static net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.DATA_LIT_TIME;
import static net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.NUM_DATA_VALUES;

public final class NuclearFurnaceMenu extends AbstractContainerMenu {
    private static final int FURNACE_SLOTS = 3;
    private static final int HOTBAR_START = 30;
    private static final int END = 39;

    private final Container furnace;
    private final ContainerData data;

    public NuclearFurnaceMenu(int id, Inventory player) {
        this(id, player, new SimpleContainer(FURNACE_SLOTS), new SimpleContainerData(NUM_DATA_VALUES));
    }

    NuclearFurnaceMenu(int id, Inventory player, Container furnace, ContainerData data) {
        super(NCRegistries.NUCLEAR_FURNACE_MENU.get(), id);
        this.furnace = furnace;
        this.data = data;
        addSlot(new Slot(furnace, 0, 56, 17));
        addSlot(new Slot(furnace, 1, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return NuclearFurnaceBlockEntity.uraniumBurnTime(stack) > 0;
            }
        });
        addSlot(new FurnaceResultSlot(player.player, furnace, 2, 116, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(player, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(player, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return furnace.stillValid(player);
    }

    public boolean isBurning() {
        return data.get(DATA_LIT_TIME) > 0;
    }

    public int burnPixels(int pixels) {
        return scale(data.get(DATA_LIT_TIME), data.get(DATA_LIT_DURATION), pixels);
    }

    public int progressPixels(int pixels) {
        return scale(data.get(DATA_COOKING_PROGRESS), data.get(DATA_COOKING_TOTAL_TIME), pixels);
    }

    private static int scale(int value, int total, int pixels) {
        return total > 0 ? (int) ((long) Mth.clamp(value, 0, total) * pixels / total) : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < FURNACE_SLOTS) {
            moved = moveItemStackTo(stack, FURNACE_SLOTS, END, index == 2);
        } else if (player.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SimpleContainer(stack), player.level()).isPresent()) {
            moved = moveItemStackTo(stack, 0, 1, false);
        } else if (NuclearFurnaceBlockEntity.uraniumBurnTime(stack) > 0) {
            moved = moveItemStackTo(stack, 1, 2, false);
        } else if (index < HOTBAR_START) {
            moved = moveItemStackTo(stack, HOTBAR_START, END, false);
        } else {
            moved = moveItemStackTo(stack, FURNACE_SLOTS, HOTBAR_START, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (index == 2) {
            slot.onQuickCraft(stack, original);
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
