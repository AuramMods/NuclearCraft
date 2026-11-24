package nc.client;

import nc.processor.ProcessorFluidActions;
import nc.processor.ProcessorFluidTanks.Mode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

final class FluidTankControls {
    private FluidTankControls() {}

    static boolean clearRequested() {
        return Screen.hasShiftDown() || Screen.hasControlDown();
    }

    static int faceButton(int tank, int regular) {
        if (Screen.hasControlDown()) {
            return ProcessorFluidActions.disableButton(tank);
        }
        return Screen.hasAltDown() ? ProcessorFluidActions.restoreButton(tank) : regular;
    }

    static Component faceTooltip(Direction side, Mode mode) {
        return Component.translatable("gui.nuclearcraft.electrolyzer.side",
                Component.translatable("gui.nuclearcraft.face." + side.getName()), mode.label())
                .append("\n").append(Component.translatable("gui.nuclearcraft.processor_fluids.face_help"));
    }

    static Component inputPolicy(boolean discard) {
        return Component.translatable(discard ? "gui.nuclearcraft.processor_fluids.discard_input"
                : "gui.nuclearcraft.processor_fluids.keep_input");
    }

    static Component tankTooltip(FluidStack fluid) {
        Component amount = fluid.isEmpty() ? Component.translatable("gui.nuclearcraft.electrolyzer.empty_tank")
                : Component.translatable("gui.nuclearcraft.fluid_amount", fluid.getDisplayName(), fluid.getAmount());
        return amount.copy().append("\n").append(Component.translatable("gui.nuclearcraft.processor_fluids.tank_help"));
    }
}
