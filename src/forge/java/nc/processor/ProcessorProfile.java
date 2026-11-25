package nc.processor;

import nc.init.NCItemProcessors.TankSlot;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ProcessorProfile {
    String id();
    Rates rate();
    int inputs();
    int outputs();
    int inputX(int slot);
    int inputY(int slot);
    int outputX(int slot);
    int outputY(int slot);
    int menuExtension();
    int progressX();
    int progressY();
    int progressHeight();
    boolean losesProgress();
    ProcessorBlock<ItemProcessorBlockEntity> block();
    BlockEntityType<ItemProcessorBlockEntity> entityType();
    MenuType<ItemProcessorMenu> menu();
    RecipeType<ItemProcessorRecipe> recipes();
    RecipeSerializer<ItemProcessorRecipe> serializer();
    @Nullable TankSlot tank();

    default int slots() {
        return inputs() + outputs();
    }

    default List<TankSlot> tanks() {
        TankSlot tank = tank();
        return tank == null ? List.of() : List.of(tank);
    }

    default int fluidInputs() {
        return hasFluidInput() ? 1 : 0;
    }

    default int fluidOutputs() {
        return hasFluidOutput() ? 1 : 0;
    }

    default boolean hasFluidInput() {
        return tank() != null && !tank().output();
    }

    default boolean hasFluidOutput() {
        return tank() != null && tank().output();
    }

    default int tankCapacity(int index) {
        return ProcessorFluidTanks.CAPACITY;
    }

    default boolean hasUpgrades() {
        return true;
    }

    default boolean generator() {
        return false;
    }

    default boolean consumesInputs() {
        return false;
    }

    default boolean scripted() {
        return false;
    }

    default boolean jeiEnabled() {
        return true;
    }

    default @Nullable ProcessorLayout layout() {
        return null;
    }

    default boolean defaultRedstoneControl() {
        return true;
    }

    interface Rates {
        default boolean enabled() {
            return true;
        }

        double defaultTime();
        double baseTime();
        double basePower();
        double time(double ticks);
        double power(int energyPerTick);
        double powerScale();
    }
}
