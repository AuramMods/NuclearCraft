package nc.client;

import nc.NuclearCraft;
import nc.processor.MelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public final class MelterScreen extends AbstractContainerScreen<MelterMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/melter.png");

    public MelterScreen(MelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        FluidStack fluid = menu.fluid();
        if (!fluid.isEmpty() && isHovering(112, 31, 24, 24, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.fluid_amount",
                    fluid.getDisplayName(), fluid.getAmount()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energyHeight = (int) (74 * menu.energy());
        graphics.blit(TEXTURE, leftPos + 8, topPos + 80 - energyHeight, 176, 90 - energyHeight, 16, energyHeight);
        int progressWidth = (int) (37 * menu.progress());
        graphics.blit(TEXTURE, leftPos + 74, topPos + 35, 176, 3, progressWidth, 16);

        FluidStack fluid = menu.fluid();
        if (!fluid.isEmpty()) {
            int height = Math.max(1, (int) (24 * menu.fillLevel()));
            int color = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
            graphics.fill(leftPos + 112, topPos + 55 - height, leftPos + 136, topPos + 55, 0xFF000000 | color);
        }
    }
}
