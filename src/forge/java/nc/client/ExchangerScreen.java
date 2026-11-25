package nc.client;

import nc.NuclearCraft;
import nc.multiblock.hx.ExchangerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class ExchangerScreen extends AbstractContainerScreen<ExchangerMenu> {
    private static final ResourceLocation BACKGROUND = NuclearCraft.id("textures/gui/container/heat_exchanger_controller.png");
    private static final int CONTROL_Y = 86;
    private static final int CLEAR_X = 92;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 18;
    private static final int TANK_TOP = 24;
    private static final int TANK_HEIGHT = 50;
    private Button toggle;
    private Button clear;

    public ExchangerScreen(ExchangerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 112;
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleLabel(), button -> click(ExchangerMenu.TOGGLE))
                .bounds(leftPos + 14, topPos + CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        clear = addRenderableWidget(Button.builder(text("clear"), button -> {
            if (FluidTankControls.clearRequested()) {
                click(ExchangerMenu.CLEAR);
            }
        }).bounds(leftPos + CLEAR_X, topPos + CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void click(int action) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private Component toggleLabel() {
        return text(menu.enabled() ? "disable" : "enable");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blitNineSliced(BACKGROUND, leftPos, topPos, imageWidth, imageHeight, 4, 176, 76, 0, 0);
        if (menu.condenser()) {
            gauge(graphics, 148, 14, fill(menu.shellInput(), menu.shellInputCapacity()), 0xFF347DC5);
        } else {
            gauge(graphics, 136, 10, fill(menu.shellInput(), menu.shellInputCapacity()), 0xFF347DC5);
            gauge(graphics, 153, 10, fill(menu.shellOutput(), menu.shellOutputCapacity()), 0xFFDB9852);
        }
    }

    private void gauge(GuiGraphics graphics, int x, int width, float fill, int color) {
        int bottom = topPos + TANK_TOP + TANK_HEIGHT;
        graphics.fill(leftPos + x, topPos + TANK_TOP, leftPos + x + width, bottom, 0xFF282828);
        int level = Math.round(TANK_HEIGHT * fill);
        graphics.fill(leftPos + x, bottom - level, leftPos + x + width, bottom, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean alternate = FluidTankControls.clearRequested();
        int color = menu.running() ? 0x404040 : 0x8C4200;
        label(graphics, 7, imageWidth - 16, color, text("title", menu.lengthX(), menu.lengthY(), menu.lengthZ(), title));
        label(graphics, 24, 124, color, alternate ? text("networks", menu.activeNetworks(), menu.networks())
                : text("tubes", menu.activeTubes(), menu.tubes()));
        label(graphics, 37, 124, color, alternate && !menu.condenser() ? text("shell_rate", menu.shellInputRate())
                : text("tube_rate", format(menu.tubeInputRate())));
        label(graphics, 50, 124, color, text(menu.condenser() ? "cooling" : "heat_transfer", format(menu.heatRate())));
        label(graphics, 63, 124, color, text("mean_temperature", format(menu.temperatureDifference())));
    }

    private void label(GuiGraphics graphics, int y, int available, int color, Component label) {
        int width = font.width(label);
        if (width <= available) {
            graphics.drawString(font, label, 8 + (available - width) / 2, y, color, false);
            return;
        }
        float scale = (float) available / width;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(8, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, label, 0, 0, color, false);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        toggle.setMessage(toggleLabel());
        clear.active = FluidTankControls.clearRequested();
        super.render(graphics, mouseX, mouseY, delta);
        if (menu.condenser()) {
            gaugeTooltip(graphics, 148, 14, "coolant", menu.shellInput(), menu.shellInputCapacity(), mouseX, mouseY);
        } else {
            gaugeTooltip(graphics, 136, 10, "shell_input", menu.shellInput(), menu.shellInputCapacity(), mouseX, mouseY);
            gaugeTooltip(graphics, 153, 10, "shell_output", menu.shellOutput(), menu.shellOutputCapacity(), mouseX, mouseY);
        }
        if (isHovering(CLEAR_X, CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, text("clear_hint"), mouseX, mouseY);
        }
    }

    private void gaugeTooltip(GuiGraphics graphics, int x, int width, String label, int amount, int capacity, int mouseX, int mouseY) {
        if (isHovering(x, TANK_TOP, width, TANK_HEIGHT, mouseX, mouseY)) {
            var tooltip = text(label, Math.round(fill(amount, capacity) * 100F)).copy()
                    .append("\n").append(text("tank_amount", amount, capacity));
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private static float fill(int amount, int capacity) {
        return capacity > 0 ? Math.min(1F, (float) amount / capacity) : 0F;
    }

    private static String format(float value) {
        if (value >= 100_000F) {
            return String.format(Locale.ROOT, "%.3g", value);
        }
        return value == (int) value ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.nuclearcraft.exchanger." + key, args);
    }
}
