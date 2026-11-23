package nc.client;

import nc.NuclearCraft;
import nc.multiblock.fission.SolidFissionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

final class SolidFissionScreen extends AbstractContainerScreen<SolidFissionMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/solid_fission_controller.png");
    private Button control;

    SolidFissionScreen(SolidFissionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        control = addRenderableWidget(Button.builder(toggleLabel(), button ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SolidFissionMenu.TOGGLE))
                .bounds(leftPos + 44, topPos + 125, 88, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        control.setMessage(toggleLabel());
    }

    private Component toggleLabel() {
        return Component.translatable(menu.enabled()
                ? "gui.nuclearcraft.fission.disable" : "gui.nuclearcraft.fission.enable");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        bar(graphics, 22, 42, menu.heat(), 0xFFD84320);
        bar(graphics, 72, 42, menu.coolant(), 0xFF315ED4);
        bar(graphics, 122, 42, menu.steam(), 0xFFBDBDBD);
    }

    private void bar(GuiGraphics graphics, int x, int y, float progress, int color) {
        int height = (int) (54 * progress);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 26, topPos + y + 54, 0xFF222222);
        graphics.fill(leftPos + x + 1, topPos + y + 54 - height, leftPos + x + 25, topPos + y + 54, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission.heat"), 22, 30, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission.water"), 72, 30, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission.steam"), 122, 30, 0x404040, false);
        graphics.drawCenteredString(font, Component.translatable(menu.active()
                ? "gui.nuclearcraft.fission.running" : "gui.nuclearcraft.fission.idle"), 88, 106, 0xFFFFFF);
    }
}
