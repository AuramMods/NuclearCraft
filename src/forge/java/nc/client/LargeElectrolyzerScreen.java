package nc.client;

import nc.multiblock.machine.LargeElectrolyzerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class LargeElectrolyzerScreen extends AbstractContainerScreen<LargeElectrolyzerMenu> {
    private static final int TANK_Y = 84;
    private Button toggle;
    private Button clear;

    public LargeElectrolyzerScreen(LargeElectrolyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 238;
        inventoryLabelX = 30;
        inventoryLabelY = 144;
    }

    @Override protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleLabel(), ignored -> click(LargeElectrolyzerMenu.TOGGLE))
                .bounds(leftPos + 16, topPos + 116, 88, 18).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.decay_pool.clear"), ignored -> {
            if (canClear()) click(LargeElectrolyzerMenu.CLEAR);
        }).bounds(leftPos + 116, topPos + 116, 88, 18).build());
    }

    private void click(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static boolean canClear() { return Screen.hasShiftDown() || Screen.hasControlDown(); }

    private Component toggleLabel() {
        return Component.translatable(menu.halted() ? "gui.nuclearcraft.exchanger.enable" : "gui.nuclearcraft.exchanger.disable");
    }

    @Override protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff282828);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xff777777);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xffc6c6c6);
        bar(graphics, 23, menu.progress(), 0xff3b9b73);
        bar(graphics, 36, menu.capacity() == 0 ? 0 : (double) menu.energy() / menu.capacity(), 0xffbd554c);
        for (var slot : menu.slots) well(graphics, slot.x, slot.y);
        for (int slot = 0; slot < 7; slot++) {
            int x = tankX(slot);
            well(graphics, x, TANK_Y);
            FluidTankRenderer.draw(graphics, leftPos + x, topPos + TANK_Y, 16, menu.fluid(slot), menu.tankCapacity());
        }
    }

    private void well(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xffeeeeee);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xff555555);
    }

    private void bar(GuiGraphics graphics, int y, double fill, int color) {
        int x = leftPos + 12;
        int width = imageWidth - 24;
        graphics.fill(x, topPos + y, x + width, topPos + y + 6, 0xff505050);
        int amount = (int) ((width - 2) * Math.min(1, fill));
        graphics.fill(x + 1, topPos + y + 1, x + 1 + amount, topPos + y + 5, color);
    }

    private static int tankX(int slot) { return slot < 2 ? 16 + slot * 24 : slot < 6 ? 110 + (slot - 2) * 24 : 75; }

    private static String percent(double value) { return String.format(Locale.ROOT, "%.1f", value); }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.large_electrolyzer.inputs"), 13, 46, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.large_electrolyzer.output"), 109, 46, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.large_electrolyzer.electrolyte_label"), 62, 103, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics);
        toggle.setMessage(toggleLabel());
        clear.active = canClear();
        super.render(graphics, x, y, delta);
        renderTooltip(graphics, x, y);
        int width = imageWidth - 24;
        if (isHovering(12, 23, width, 6, x, y)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("gui.nuclearcraft.distiller.progress", Math.round(menu.progress() * 100)),
                    Component.translatable("gui.nuclearcraft.large_electrolyzer.terminals", menu.terminalPairs()),
                    Component.translatable("gui.nuclearcraft.large_electrolyzer.speed", percent(menu.speed()))),
                    Optional.empty(), x, y);
        } else if (isHovering(12, 36, width, 6, x, y)) {
            graphics.renderTooltip(font, List.of(Component.translatable("gui.nuclearcraft.distiller.stored", menu.energy(), menu.capacity()),
                    Component.translatable("gui.nuclearcraft.distiller.power", menu.power())), Optional.empty(), x, y);
        } else if (isHovering(116, 116, 88, 18, x, y)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.decay_pool.clear_hint"), x, y);
        }
        for (int slot = 0; slot < 7; slot++) {
            if (!isHovering(tankX(slot), TANK_Y, 16, 16, x, y)) continue;
            var stack = menu.fluid(slot);
            graphics.renderTooltip(font, List.of(
                    slot == 6 ? Component.translatable("gui.nuclearcraft.large_electrolyzer.electrolyte", percent(menu.electrolyteEfficiency()))
                            : slot < 2 ? Component.translatable("gui.nuclearcraft.distiller.input", slot + 1)
                            : Component.translatable("gui.nuclearcraft.distiller.output", slot - 1),
                    stack.isEmpty() ? Component.translatable("gui.nuclearcraft.electrolyzer.empty_tank")
                            : Component.translatable("gui.nuclearcraft.fluid_amount", stack.getDisplayName(), stack.getAmount())),
                    Optional.empty(), x, y);
        }
    }
}
