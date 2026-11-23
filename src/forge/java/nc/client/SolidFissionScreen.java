package nc.client;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionVariant;
import nc.multiblock.fission.SolidFissionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

final class SolidFissionScreen extends AbstractContainerScreen<SolidFissionMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/solid_fission_controller.png");
    private static final ResourceLocation PEBBLE_TEXTURE = NuclearCraft.id("textures/gui/container/pebble_fission_controller.png");
    private static final ResourceLocation SALT_TEXTURE = NuclearCraft.id("textures/gui/container/salt_fission_controller.png");
    private Button control;

    SolidFissionScreen(SolidFissionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        if (compact()) {
            imageHeight = 114;
        }
    }

    @Override
    protected void init() {
        super.init();
        control = addRenderableWidget(Button.builder(toggleLabel(), button ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SolidFissionMenu.TOGGLE))
                .bounds(leftPos + 44, topPos + (compact() ? 90 : 125), 88, 20).build());
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
        boolean pebble = compact();
        ResourceLocation texture = switch (menu.variant()) {
            case SOLID -> TEXTURE;
            case PEBBLE -> PEBBLE_TEXTURE;
            case SALT -> SALT_TEXTURE;
        };
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int top = pebble ? 34 : 42;
        int height = pebble ? 40 : 54;
        bar(graphics, 22, top, height, menu.heat(), 0xFFD84320);
        bar(graphics, 72, top, height, menu.coolant(), 0xFF315ED4);
        bar(graphics, 122, top, height, menu.steam(), 0xFFBDBDBD);
    }

    private void bar(GuiGraphics graphics, int x, int y, int height, float progress, int color) {
        int fill = (int) (height * progress);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 26, topPos + y + height, 0xFF222222);
        graphics.fill(leftPos + x + 1, topPos + y + height - fill, leftPos + x + 25, topPos + y + height, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean pebble = compact();
        int labels = pebble ? 22 : 30;
        int titleX = pebble ? (imageWidth - font.width(title)) / 2 : titleLabelX;
        graphics.drawString(font, title, titleX, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission.heat"), 22, labels, 0x404040, false);
        String cold = switch (menu.variant()) {
            case SOLID -> "water";
            case PEBBLE -> "cold_gas";
            case SALT -> "cold_nak";
        };
        String hot = switch (menu.variant()) {
            case SOLID -> "steam";
            case PEBBLE -> "hot_gas";
            case SALT -> "hot_nak";
        };
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission." + cold), pebble ? 62 : 72, labels, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission." + hot), pebble ? 116 : 122, labels, 0x404040, false);
        graphics.drawCenteredString(font, Component.translatable(menu.active()
                ? "gui.nuclearcraft.fission.running" : "gui.nuclearcraft.fission.idle"), 88, pebble ? 78 : 106, 0xFFFFFF);
    }

    private boolean compact() {
        return menu.variant() != FissionVariant.SOLID;
    }
}
