package nc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

final class FluidTankRenderer {
    private FluidTankRenderer() {}

    static void draw(GuiGraphics graphics, int x, int y, int size, FluidStack fluid, int capacity) {
        draw(graphics, x, y, size, fluid, capacity, 1);
    }

    static void draw(GuiGraphics graphics, int x, int y, int size, FluidStack fluid, int capacity, float opacity) {
        draw(graphics, x, y, size, size, fluid, capacity, opacity);
    }

    static void draw(GuiGraphics graphics, int x, int y, int width, int height, FluidStack fluid, int capacity) {
        draw(graphics, x, y, width, height, fluid, capacity, 1);
    }

    private static void draw(GuiGraphics graphics, int x, int y, int width, int height, FluidStack fluid, int capacity,
                             float opacity) {
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        int filled = Math.max(1, (int) ((long) height * Math.min(fluid.getAmount(), capacity) / capacity));
        var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extension.getStillTexture(fluid));
        int color = extension.getTintColor(fluid);
        graphics.blit(x, y + height - filled, 0, width, filled, sprite,
                FastColor.ARGB32.red(color) / 255F, FastColor.ARGB32.green(color) / 255F,
                FastColor.ARGB32.blue(color) / 255F, FastColor.ARGB32.alpha(color) * opacity / 255F);
    }
}
