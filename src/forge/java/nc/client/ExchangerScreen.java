package nc.client;

import nc.NuclearCraft;
import nc.multiblock.hx.ExchangerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ExchangerScreen extends AbstractContainerScreen<ExchangerMenu> {
    private static final ResourceLocation BACKGROUND = NuclearCraft.id("textures/gui/container/condenser_controller.png");
    private Button toggle;

    public ExchangerScreen(ExchangerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 108;
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleLabel(), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ExchangerMenu.TOGGLE);
            }
        }).bounds(leftPos + 53, topPos + 79, 70, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        toggle.setMessage(toggleLabel());
    }

    private Component toggleLabel() {
        return Component.translatable(menu.enabled() ? "gui.nuclearcraft.exchanger.disable" : "gui.nuclearcraft.exchanger.enable");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight - 5);
        graphics.blit(BACKGROUND, leftPos, topPos + imageHeight - 5, 0, 161, imageWidth, 5);
        if (menu.condenser()) {
            gauge(graphics, 148, 14, menu.coolant(), 0xFF347DC5);
        } else {
            gauge(graphics, 136, 10, menu.coolant(), 0xFF347DC5);
            gauge(graphics, 153, 10, menu.shellOutput(), 0xFFDB9852);
        }
    }

    private void gauge(GuiGraphics graphics, int x, int width, float fill, int color) {
        graphics.fill(leftPos + x, topPos + 23, leftPos + x + width, topPos + 73, 0xFF282828);
        int level = Math.round(50 * fill);
        graphics.fill(leftPos + x, topPos + 73 - level, leftPos + x + width, topPos + 73, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, 0x404040, false);
        label(graphics, 23, "networks", menu.activeNetworks(), menu.networks());
        label(graphics, 39, "tubes", menu.activeTubes());
        label(graphics, 55, menu.condenser() ? "cooling" : "heat_transfer", menu.cooling());
    }

    private void label(GuiGraphics graphics, int y, String name, Object... values) {
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.exchanger." + name, values),
                9, y, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        if (menu.condenser()) {
            gaugeTooltip(graphics, 148, 14, "coolant", menu.coolant(), mouseX, mouseY);
        } else {
            gaugeTooltip(graphics, 136, 10, "shell_input", menu.coolant(), mouseX, mouseY);
            gaugeTooltip(graphics, 153, 10, "shell_output", menu.shellOutput(), mouseX, mouseY);
        }
    }

    private void gaugeTooltip(GuiGraphics graphics, int x, int width, String label, float fill, int mouseX, int mouseY) {
        if (isHovering(x, 23, width, 50, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.exchanger." + label,
                    Math.round(fill * 100F)), mouseX, mouseY);
        }
    }
}
