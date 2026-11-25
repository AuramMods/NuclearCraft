package nc.client;

import nc.NuclearCraft;
import nc.multiblock.turbine.TurbineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

final class TurbineScreen extends AbstractContainerScreen<TurbineMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/turbine_controller.png");
    private static final int CONTROL_Y = 134;
    private static final int CLEAR_X = 92;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 18;
    private static final int STEAM_Y = 90;
    private static final int EXHAUST_Y = 103;
    private static final int ENERGY_Y = 116;
    private static final int BAR_X = 66;
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;
    private Button control;
    private Button clear;

    TurbineScreen(TurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 160;
    }

    @Override
    protected void init() {
        super.init();
        control = addRenderableWidget(Button.builder(toggleLabel(), button -> click(TurbineMenu.TOGGLE))
                .bounds(leftPos + 14, topPos + CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        clear = addRenderableWidget(Button.builder(text("clear"), button -> {
            if (modifierDown()) {
                click(TurbineMenu.CLEAR);
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
        return Component.translatable(menu.enabled() ? "gui.nuclearcraft.fission.disable" : "gui.nuclearcraft.fission.enable");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        control.setMessage(toggleLabel());
        clear.active = modifierDown();
        super.render(graphics, mouseX, mouseY, partialTick);
        meterTooltip(graphics, STEAM_Y, "tank_amount", menu.steamAmount(), menu.steamCapacity(), mouseX, mouseY);
        meterTooltip(graphics, EXHAUST_Y, "tank_amount", menu.exhaustAmount(), menu.exhaustCapacity(), mouseX, mouseY);
        meterTooltip(graphics, ENERGY_Y, "energy_amount", menu.energyStored(), menu.energyCapacity(), mouseX, mouseY);
        if (isHovering(CLEAR_X, CONTROL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, text("clear_hint"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blitNineSliced(TEXTURE, leftPos, topPos, imageWidth, imageHeight, 4, 176, 76, 0, 0);
        bar(graphics, STEAM_Y, menu.steamAmount(), menu.steamCapacity(), 0xFFD0D0D0);
        bar(graphics, EXHAUST_Y, menu.exhaustAmount(), menu.exhaustCapacity(), 0xFF909EA9);
        bar(graphics, ENERGY_Y, menu.energyStored(), menu.energyCapacity(), 0xFFEB5652);
    }

    private void bar(GuiGraphics graphics, int y, long amount, long capacity, int color) {
        int x = leftPos + BAR_X;
        int top = topPos + y;
        graphics.fill(x, top, x + BAR_WIDTH, top + BAR_HEIGHT, 0xFF313131);
        int width = capacity > 0 ? (int) ((BAR_WIDTH - 2) * Math.min(1D, (double) amount / capacity)) : 0;
        graphics.fill(x + 1, top + 1, x + 1 + width, top + BAR_HEIGHT - 1, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = menu.running() ? 0x404040 : 0x8C4200;
        boolean alternate = modifierDown();
        centered(graphics, 7, color, text("title", menu.lengthX(), menu.lengthY(), menu.lengthZ(), title));
        var status = Component.translatable(menu.processing() ? "gui.nuclearcraft.fission.running" : "gui.nuclearcraft.fission.idle");
        centered(graphics, 23, color, text("power", number(menu.power())).append("  •  ").append(status));
        centered(graphics, 36, color, alternate
                ? text("coils", menu.inletCoils(), menu.bearingsPerSide(), menu.outletCoils(), menu.bearingsPerSide())
                : text("dynamo_efficiency", percent(menu.conductivity())));
        centered(graphics, 49, color, alternate
                ? text("expansion", percent(menu.expansion()), percent(menu.idealExpansion()))
                : text("rotor_efficiency", percent(menu.rotorEfficiency())));
        double tension = menu.tension();
        int tensionColor = FastColor.ARGB32.lerp((float) Math.min(1D, 2D * tension), color, 0xB31E2B);
        double throughput = menu.throughput();
        long rated = menu.ratedFlow();
        centered(graphics, 62, tensionColor, alternate
                ? text("power_bonus", percent(menu.powerBonus()))
                : text(throughput > rated ? "overload" : "throughput", String.format(Locale.ROOT, "%,.1f", throughput), number(rated)));
        centered(graphics, 75, tensionColor, text("tension", percent(tension)));
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.fission.steam"), 8, STEAM_Y, 0x404040, false);
        graphics.drawString(font, text("exhaust"), 8, EXHAUST_Y, 0x404040, false);
        graphics.drawString(font, text("energy"), 8, ENERGY_Y, 0x404040, false);
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

    private void meterTooltip(GuiGraphics graphics, int y, String key, long amount, long capacity, int mouseX, int mouseY) {
        if (isHovering(BAR_X, y, BAR_WIDTH, BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, text(key, number(amount), number(capacity)), mouseX, mouseY);
        }
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%,.1f%%", value * 100D);
    }

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("gui.nuclearcraft.turbine." + key, args);
    }
}
