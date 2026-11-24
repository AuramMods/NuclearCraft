package nc.client;

import nc.multiblock.machine.DistillerMenu;
import nc.recipe.DistillerRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class DistillerScreen extends AbstractContainerScreen<DistillerMenu> {
    private static final int TANK_SIZE = 18;
    private static final int TANK_Y = 84;
    private Button toggle;
    private Button clear;

    public DistillerScreen(DistillerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 242;
        imageHeight = 136;
    }

    @Override protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleLabel(), ignored -> click(DistillerMenu.TOGGLE))
                .bounds(leftPos + 12, topPos + 112, 102, 18).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.decay_pool.clear"), ignored -> {
            if (canClear()) click(DistillerMenu.CLEAR);
        }).bounds(leftPos + 128, topPos + 112, 102, 18).build());
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
        bar(graphics, 24, menu.progress(), 0xff3b9b73);
        bar(graphics, 40, menu.capacity() == 0 ? 0 : (double) menu.energy() / menu.capacity(), 0xffbd554c);
        for (int slot = 0; slot < DistillerRecipe.INPUT_COUNT + DistillerRecipe.OUTPUT_COUNT; slot++) {
            int x = leftPos + tankX(slot);
            int y = topPos + TANK_Y;
            graphics.fill(x - 1, y - 1, x + TANK_SIZE + 1, y + TANK_SIZE + 1, 0xffeeeeee);
            graphics.fill(x, y, x + TANK_SIZE, y + TANK_SIZE, 0xff454545);
            FluidTankRenderer.draw(graphics, x, y, TANK_SIZE, menu.fluid(slot), menu.tankCapacity());
        }
    }

    private void bar(GuiGraphics graphics, int y, double fill, int color) {
        int x = leftPos + 12;
        int width = imageWidth - 24;
        graphics.fill(x, topPos + y, x + width, topPos + y + 6, 0xff505050);
        int amount = (int) ((width - 2) * Math.min(1, fill));
        graphics.fill(x + 1, topPos + y + 1, x + 1 + amount, topPos + y + 5, color);
    }

    private static int tankX(int slot) { return 12 + slot * 21 + (slot < DistillerRecipe.INPUT_COUNT ? 0 : 8); }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0x404040, false);
        row(graphics, 53, "trays", menu.trayCount());
        row(graphics, 70, "inputs");
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.distiller.outputs"), tankX(DistillerRecipe.INPUT_COUNT),
                70, 0x404040, false);
    }

    private void row(GuiGraphics graphics, int y, String label, Object... values) {
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.distiller." + label, values), 12, y, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics);
        toggle.setMessage(toggleLabel());
        clear.active = canClear();
        super.render(graphics, x, y, delta);
        int width = imageWidth - 24;
        if (isHovering(12, 24, width, 6, x, y)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("gui.nuclearcraft.distiller.progress", Math.round(menu.progress() * 100)),
                    Component.translatable("gui.nuclearcraft.distiller.reflux", Math.round(menu.refluxBonus())),
                    Component.translatable("gui.nuclearcraft.distiller.reboiling", Math.round(menu.reboilingBonus())),
                    Component.translatable("gui.nuclearcraft.distiller.distribution", Math.round(menu.distributionBonus()))),
                    Optional.empty(), x, y);
        } else if (isHovering(12, 40, width, 6, x, y)) {
            graphics.renderTooltip(font, List.of(Component.translatable("gui.nuclearcraft.distiller.stored", menu.energy(), menu.capacity()),
                    Component.translatable("gui.nuclearcraft.distiller.power", menu.power())), Optional.empty(), x, y);
        } else if (isHovering(128, 112, 102, 18, x, y)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.decay_pool.clear_hint"), x, y);
        }
        for (int slot = 0; slot < DistillerRecipe.INPUT_COUNT + DistillerRecipe.OUTPUT_COUNT; slot++) {
            if (!isHovering(tankX(slot), TANK_Y, TANK_SIZE, TANK_SIZE, x, y)) continue;
            var fluid = menu.fluid(slot);
            boolean input = slot < DistillerRecipe.INPUT_COUNT;
            graphics.renderTooltip(font, List.of(
                    Component.translatable("gui.nuclearcraft.distiller." + (input ? "input" : "output"),
                            input ? slot + 1 : slot - DistillerRecipe.INPUT_COUNT + 1),
                    fluid.isEmpty() ? Component.translatable("gui.nuclearcraft.electrolyzer.empty_tank")
                            : Component.translatable("gui.nuclearcraft.fluid_amount", fluid.getDisplayName(), fluid.getAmount())),
                    Optional.empty(), x, y);
        }
    }
}
