package nc.client;

import nc.NuclearCraft;
import nc.multiblock.turbine.TurbineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

final class TurbineScreen extends AbstractContainerScreen<TurbineMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/turbine_controller.png");
    private Button control;

    TurbineScreen(TurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 76;
    }

    @Override
    protected void init() {
        super.init();
        control = addRenderableWidget(Button.builder(toggleLabel(), button ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TurbineMenu.TOGGLE))
                .bounds(leftPos + 105, topPos + 49, 63, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        control.setMessage(toggleLabel());
    }

    private Component toggleLabel() {
        return Component.translatable(menu.enabled() ? "gui.nuclearcraft.fission.disable" : "gui.nuclearcraft.fission.enable");
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
        bar(graphics, 26, menu.steam(), 0xFFD0D0D0);
        bar(graphics, 42, menu.exhaust(), 0xFF909EA9);
        bar(graphics, 58, menu.energy(), 0xFFEB5652);
    }

    private void bar(GuiGraphics graphics, int y, float progress, int color) {
        graphics.fill(leftPos + 58, topPos + y, leftPos + 97, topPos + y + 6, 0xFF313131);
        graphics.fill(leftPos + 59, topPos + y + 1, leftPos + 59 + (int) (37 * progress), topPos + y + 5, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission.steam"), 8, 24, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.turbine.exhaust"), 8, 40, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.turbine.energy"), 8, 56, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.turbine.power", menu.power()), 105, 24, 0x404040, false);
        graphics.drawString(font, Component.translatable(menu.processing()
                ? "gui.nuclearcraft.fission.running" : "gui.nuclearcraft.fission.idle"), 105, 37, 0x404040, false);
    }
}
