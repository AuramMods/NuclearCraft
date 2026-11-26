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

import java.util.List;
import java.util.Locale;

final class SolidFissionScreen extends AbstractContainerScreen<SolidFissionMenu> {
    private static final ResourceLocation SOLID_TEXTURE = NuclearCraft.id("textures/gui/container/solid_fission_controller.png");
    private static final ResourceLocation PEBBLE_TEXTURE = NuclearCraft.id("textures/gui/container/pebble_fission_controller.png");
    private static final ResourceLocation SALT_TEXTURE = NuclearCraft.id("textures/gui/container/salt_fission_controller.png");

    private static final int HEAT_Y = 100;
    private static final int COLD_Y = 113;
    private static final int HOT_Y = 126;
    private static final int BAR_X = 74;
    private static final int BAR_WIDTH = 92;
    private static final int BAR_HEIGHT = 8;
    private static final int CONTROL_Y = 147;
    private static final int CLEAR_X = 92;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 18;

    private Button control;
    private Button clear;

    SolidFissionScreen(SolidFissionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 172;
    }

    @Override
    protected void init() {
        super.init();
        control = addRenderableWidget(Button.builder(toggleLabel(), button -> click(SolidFissionMenu.TOGGLE))
                .bounds(leftPos + 14, topPos + CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        clear = addRenderableWidget(Button.builder(text("clear"), button -> {
            if (modifierDown()) {
                click(SolidFissionMenu.CLEAR);
            }
        }).bounds(leftPos + CLEAR_X, topPos + CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        clear.active = modifierDown();
    }

    private static boolean modifierDown() {
        return hasShiftDown() || ClientModifiers.controlDown();
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        control.setMessage(toggleLabel());
        clear.active = modifierDown();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (isHovering(BAR_X, HEAT_Y, BAR_WIDTH, BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, List.of(
                    text("heat_stored", number(menu.heatStored()), number(menu.heatCapacity())),
                    netHeating(), text("cluster_cooling", number(menu.cooling()))), mouseX, mouseY);
        } else if (isHovering(BAR_X, COLD_Y, BAR_WIDTH, BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, text("tank_amount", number(menu.coldAmount()), number(menu.coldCapacity())), mouseX, mouseY);
        } else if (isHovering(BAR_X, HOT_Y, BAR_WIDTH, BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, text("tank_amount", number(menu.hotAmount()), number(menu.hotCapacity())), mouseX, mouseY);
        } else if (isHovering(CLEAR_X, CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, text("clear_hint"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = switch (menu.variant()) {
            case SOLID -> SOLID_TEXTURE;
            case PEBBLE -> PEBBLE_TEXTURE;
            case SALT -> SALT_TEXTURE;
        };
        graphics.blitNineSliced(texture, leftPos, topPos, imageWidth, imageHeight, 4, 176, 114, 0, 0);
        bar(graphics, HEAT_Y, menu.heat(), 0xFFD84320);
        bar(graphics, COLD_Y, menu.coolant(), 0xFF315ED4);
        bar(graphics, HOT_Y, menu.steam(), 0xFFBDBDBD);
    }

    private void bar(GuiGraphics graphics, int y, float fraction, int color) {
        int x = leftPos + BAR_X;
        int top = topPos + y;
        int width = (int) ((BAR_WIDTH - 2) * fraction);
        graphics.fill(x, top, x + BAR_WIDTH, top + BAR_HEIGHT, 0xFF313131);
        graphics.fill(x + 1, top + 1, x + 1 + width, top + BAR_HEIGHT - 1, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean alternate = modifierDown();
        int color = menu.active() ? 0x404040 : 0x8C4200;
        centered(graphics, 7, color, text("title", menu.lengthX(), menu.lengthY(), menu.lengthZ(), title));
        String status = menu.active() ? "running" : menu.predicted() ? "predicted" : "idle";
        centered(graphics, 20, color, text("clusters_status", menu.clusterCount(), text(status)));
        double temperature = menu.temperature();
        centered(graphics, 33, color, alternate
                ? text("temperature_celsius", number(Math.round(temperature - 273.15D)))
                : text("temperature_kelvin", number(Math.round(temperature))));
        centered(graphics, 46, color, alternate ? text("heat_multiplier", percent(menu.heatMultiplier()))
                : text("efficiency", percent(menu.efficiency())));
        centered(graphics, 59, color, alternate ? text("sparsity", percent(menu.sparsity()))
                : text("useful_parts", menu.usefulParts(), number(menu.interiorVolume())));
        centered(graphics, 72, color, menu.variant() == FissionVariant.SOLID
                ? text("output_rate", decimal(menu.outputRate())) : text("coolant_speed", percent(menu.outputRate())));
        centered(graphics, 85, color, netHeating());
        graphics.drawString(font, text("heat"), 8, HEAT_Y, 0x404040, false);
        graphics.drawString(font, text(switch (menu.variant()) {
            case SOLID -> "water";
            case PEBBLE -> "cold_gas";
            case SALT -> "cold_nak";
        }), 8, COLD_Y, 0x404040, false);
        graphics.drawString(font, text(switch (menu.variant()) {
            case SOLID -> "steam";
            case PEBBLE -> "hot_gas";
            case SALT -> "hot_nak";
        }), 8, HOT_Y, 0x404040, false);
    }

    private Component netHeating() {
        return text("net_heating", number(menu.heating() - menu.cooling()));
    }

    private void centered(GuiGraphics graphics, int y, int color, Component label) {
        int width = font.width(label);
        int available = imageWidth - 16;
        if (width <= available) {
            graphics.drawString(font, label, (imageWidth - width) / 2, y, color, false);
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

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    private static String percent(double value) {
        return decimal(value * 100D) + "%";
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.nuclearcraft.fission." + key, args);
    }
}
