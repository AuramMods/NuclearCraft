package nc.client;

import nc.NuclearCraft;
import nc.processor.NuclearFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class NuclearFurnaceScreen extends AbstractContainerScreen<NuclearFurnaceMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/nuclear_furnace.png");

    public NuclearFurnaceScreen(NuclearFurnaceMenu menu, Inventory player, Component title) {
        super(menu, player, title);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.isBurning()) {
            int burn = menu.burnPixels(13);
            graphics.blit(TEXTURE, leftPos + 56, topPos + 48 - burn, 176, 13 - burn, 14, burn + 1);
        }
        graphics.blit(TEXTURE, leftPos + 79, topPos + 34, 176, 14, menu.progressPixels(24) + 1, 16);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
