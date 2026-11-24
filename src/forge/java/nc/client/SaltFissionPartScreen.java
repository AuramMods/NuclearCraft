package nc.client;

import nc.NuclearCraft;
import nc.multiblock.fission.SaltFissionPartMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class SaltFissionPartScreen extends AbstractContainerScreen<SaltFissionPartMenu> {
    private static final int HEAT_X = 8;
    private static final int HEAT_Y = 6;
    private static final int HEAT_HEIGHT = 74;
    private static final Tank PORT_INPUT = new Tank(40, 31, 24);
    private static final Tank COMPONENT_INPUT = new Tank(56, 35, 16);
    private static final Tank OUTPUT = new Tank(112, 31, 24);
    private final ResourceLocation texture;
    private Button storage;

    public SaltFissionPartScreen(SaltFissionPartMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        texture = NuclearCraft.id("textures/gui/container/" + menu.kind().id() + ".png");
    }

    @Override
    protected void init() {
        super.init();
        storage = addRenderableWidget(Button.builder(Component.empty(), button -> send(SaltFissionPartMenu.SWITCH_BUFFER))
                .bounds(leftPos + 45, topPos + 55, 86, 14).build());
        refreshStorageButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshStorageButton();
    }

    private void refreshStorageButton() {
        storage.visible = menu.linked();
        storage.setMessage(Component.translatable(menu.local()
                ? "gui.nuclearcraft.irradiator.local_items" : "gui.nuclearcraft.irradiator.linked_items"));
    }

    private void send(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hovered(input(), mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, tankTooltip(false), mouseX, mouseY);
        } else if (hovered(OUTPUT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, tankTooltip(true), mouseX, mouseY);
        } else if (!menu.kind().port() && isHovering(HEAT_X, HEAT_Y, 16, HEAT_HEIGHT, mouseX, mouseY)) {
            var text = new ArrayList<Component>();
            text.add(menu.heatCapacity() > 0
                    ? Component.translatable("gui.nuclearcraft.salt_part.heat", menu.heat(), menu.heatCapacity())
                    : Component.translatable("gui.nuclearcraft.salt_part.no_cluster"));
            if (!menu.kind().fuel()) {
                text.add(Component.translatable("gui.nuclearcraft.jei.coolant_heater", menu.cooling()));
            }
            graphics.renderComponentTooltip(font, text, mouseX, mouseY);
        }
    }

    private List<Component> tankTooltip(boolean output) {
        var lines = new ArrayList<Component>();
        var fluid = menu.fluid(output);
        if (fluid.isEmpty()) {
            lines.add(Component.translatable("gui.nuclearcraft.electrolyzer.empty_tank"));
        } else {
            lines.add(Component.translatable("gui.nuclearcraft.fluid_amount", fluid.getDisplayName(), fluid.getAmount()));
        }
        if (menu.capacity(output) > 0) {
            lines.add(Component.translatable("gui.nuclearcraft.salt_part.capacity", menu.capacity(output)));
        }
        if (!output) {
            var filter = menu.filter();
            if (!filter.isEmpty()) {
                lines.add(Component.translatable("gui.nuclearcraft.salt_part.filter", filter.getDisplayName()));
            }
            var reserved = menu.reserved();
            if (!reserved.isEmpty()) {
                lines.add(Component.translatable("gui.nuclearcraft.salt_part.reserved", reserved.getDisplayName(), reserved.getAmount()));
            }
        }
        if (!fluid.isEmpty() || (!output && menu.kind().fuel() && !menu.filter().isEmpty())) {
            lines.add(Component.translatable("gui.nuclearcraft.salt_part.clear"));
        }
        return lines;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = menu.processing() ? 0xFFFFFF : 0x404040;
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, color, false);
        if (menu.kind().port() || !menu.connected()) {
            String key = !menu.connected() ? "disconnected" : menu.outputPort() ? "output" : "input";
            var status = Component.translatable("gui.nuclearcraft.salt_part." + key);
            graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, menu.linked() ? 20 : 64, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        Tank input = input();
        var filter = menu.filter();
        if (menu.fluid(false).isEmpty() && !filter.isEmpty()) {
            FluidTankRenderer.draw(graphics, leftPos + input.x, topPos + input.y, input.size, filter, 1, 0.45F);
        }
        drawTank(graphics, input, false);
        drawTank(graphics, OUTPUT, true);
        if (!menu.kind().port()) {
            int width = Math.round(37 * menu.progress());
            graphics.blit(texture, leftPos + 74, topPos + 35, 176, 3, width, 16);
            if (menu.heatCapacity() > 0) {
                int height = (int) (HEAT_HEIGHT * Math.min((long) menu.heat(), menu.heatCapacity()) / menu.heatCapacity());
                graphics.blit(texture, leftPos + HEAT_X, topPos + HEAT_Y + HEAT_HEIGHT - height,
                        176, 90 + HEAT_HEIGHT - height, 16, height);
            }
        }
    }

    private Tank input() {
        return menu.kind().port() ? PORT_INPUT : COMPONENT_INPUT;
    }

    private boolean hovered(Tank tank, double x, double y) {
        return isHovering(tank.x, tank.y, tank.size, tank.size, x, y);
    }

    private void drawTank(GuiGraphics graphics, Tank tank, boolean output) {
        FluidTankRenderer.draw(graphics, leftPos + tank.x, topPos + tank.y, tank.size,
                menu.fluid(output), menu.capacity(output));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && (hasShiftDown() || hasControlDown())) {
            if (hovered(input(), x, y)) {
                send(SaltFissionPartMenu.CLEAR_INPUT);
                return true;
            }
            if (hovered(OUTPUT, x, y)) {
                send(SaltFissionPartMenu.CLEAR_OUTPUT);
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private record Tank(int x, int y, int size) {}
}
