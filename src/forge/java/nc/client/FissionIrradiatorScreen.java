package nc.client;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionIrradiatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class FissionIrradiatorScreen extends AbstractContainerScreen<FissionIrradiatorMenu> {
    private static final int HEAT_HEIGHT = 74;
    private final ResourceLocation texture;
    private Button clearFilter;
    private Button storage;

    public FissionIrradiatorScreen(FissionIrradiatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        texture = NuclearCraft.id("textures/gui/container/fission_irradiator" + (menu.port() ? "_port" : "") + ".png");
    }

    @Override
    protected void init() {
        super.init();
        clearFilter = addRenderableWidget(Button.builder(Component.literal("×"), button -> send(FissionIrradiatorMenu.CLEAR_FILTER))
                .bounds(leftPos + 151, topPos + 22, 17, 17)
                .tooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.irradiator.clear_filter"))).build());
        storage = addRenderableWidget(Button.builder(Component.empty(), button -> send(FissionIrradiatorMenu.SWITCH_INVENTORY))
                .bounds(leftPos + 45, topPos + 59, 86, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.irradiator.switch_inventory"))).build());
        refreshButtons();
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        clearFilter.visible = !menu.connected() && !menu.filter().isEmpty() && !menu.getSlot(0).hasItem();
        storage.visible = menu.linked();
        storage.setMessage(Component.translatable(menu.local()
                ? "gui.nuclearcraft.irradiator.local_items" : "gui.nuclearcraft.irradiator.linked_items"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        var input = menu.getSlot(0);
        var filter = menu.filter();
        if (!filter.isEmpty() && !input.hasItem()) {
            int x = leftPos + input.x;
            int y = topPos + input.y;
            graphics.renderFakeItem(filter, x, y);
            graphics.fill(x, y, x + 16, y + 16, 0x80777777);
        }
        if (!menu.port()) {
            int width = Math.round(37 * menu.progress());
            graphics.blit(texture, leftPos + 74, topPos + 35, 176, 3, width, 16);
            if (menu.heatCapacity() > 0) {
                int height = (int) (HEAT_HEIGHT * Math.min((long) menu.heat(), menu.heatCapacity()) / menu.heatCapacity());
                graphics.blit(texture, leftPos + 8, topPos + 6 + HEAT_HEIGHT - height,
                        176, 90 + HEAT_HEIGHT - height, 16, height);
            } else {
                graphics.fillGradient(leftPos + 8, topPos + 6, leftPos + 24, topPos + 80, 0xFF777777, 0xFF535353);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, menu.flux() > 0 ? 0xFFFFFF : 0x404040, false);
        if (menu.port()) {
            var mode = Component.translatable(menu.output()
                    ? "gui.nuclearcraft.salt_part.output" : "gui.nuclearcraft.salt_part.input");
            graphics.drawString(font, mode, (imageWidth - font.width(mode)) / 2, 19, 0x404040, false);
        }
        if (!menu.connected()) {
            var status = Component.translatable("gui.nuclearcraft.salt_part.disconnected");
            graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, 64, 0x404040, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        var input = menu.getSlot(0);
        if (!input.hasItem() && !menu.filter().isEmpty() && isHovering(input.x, input.y, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.salt_part.filter",
                    menu.filter().getHoverName()), mouseX, mouseY);
        } else if (!menu.port() && isHovering(8, 6, 16, HEAT_HEIGHT, mouseX, mouseY)) {
            var heat = menu.heatCapacity() > 0
                    ? Component.translatable("gui.nuclearcraft.salt_part.heat", menu.heat(), menu.heatCapacity())
                    : Component.translatable("gui.nuclearcraft.salt_part.no_cluster");
            graphics.renderTooltip(font, heat, mouseX, mouseY);
        } else if (!menu.port() && isHovering(74, 35, 37, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.irradiator.flux", menu.flux()), mouseX, mouseY);
        }
    }
}
