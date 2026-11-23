package nc.client;

import nc.NuclearCraft;
import nc.multiblock.hx.CondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class CondenserScreen extends AbstractContainerScreen<CondenserMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/condenser_controller.png");
    private Button toggle;

    public CondenserScreen(CondenserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 108;
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleLabel(), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CondenserMenu.TOGGLE);
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
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight - 5);
        graphics.blit(TEXTURE, leftPos, topPos + imageHeight - 5, 0, 160, imageWidth, 5);
        graphics.fill(leftPos + 148, topPos + 23, leftPos + 162, topPos + 73, 0xFF282828);
        int level = Math.round(50 * menu.coolant());
        graphics.fill(leftPos + 148, topPos + 73 - level, leftPos + 162, topPos + 73, 0xFF347DC5);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, 0x404040, false);
        label(graphics, 23, "networks", menu.activeNetworks(), menu.networks());
        label(graphics, 39, "tubes", menu.activeTubes());
        label(graphics, 55, "cooling", menu.cooling());
    }

    private void label(GuiGraphics graphics, int y, String name, Object... values) {
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.exchanger." + name, values),
                9, y, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        if (isHovering(148, 23, 14, 50, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.exchanger.coolant",
                    Math.round(menu.coolant() * 100F)), mouseX, mouseY);
        }
    }
}
