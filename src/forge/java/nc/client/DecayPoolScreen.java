package nc.client;

import nc.NuclearCraft;
import nc.multiblock.machine.DecayPoolMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class DecayPoolScreen extends AbstractContainerScreen<DecayPoolMenu> {
    private static final ResourceLocation BACKGROUND = NuclearCraft.id("textures/gui/container/decay_pool_controller.png");
    private Button toggle;
    private Button clear;

    public DecayPoolScreen(DecayPoolMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 118;
    }

    @Override protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(toggleLabel(), button -> click(DecayPoolMenu.TOGGLE))
                .bounds(leftPos + 14, topPos + 94, 70, 18).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.decay_pool.clear"), button -> {
            if (Screen.hasShiftDown() || Screen.hasControlDown()) click(DecayPoolMenu.CLEAR);
        }).bounds(leftPos + 92, topPos + 94, 70, 18).build());
    }

    private void click(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private Component toggleLabel() {
        return Component.translatable(menu.halted() ? "gui.nuclearcraft.exchanger.enable" : "gui.nuclearcraft.exchanger.disable");
    }

    @Override protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 68);
        graphics.fill(leftPos + 2, topPos + 68, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xffc6c6c6);
        graphics.blit(BACKGROUND, leftPos, topPos + imageHeight - 6, 0, 70, imageWidth, 6);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = menu.processing() ? 0x404040 : 0x6c401d;
        centered(graphics, 6, color, "title", menu.lengthX(), menu.lengthY(), menu.lengthZ());
        centered(graphics, 21, color, "containers", menu.containers());
        centered(graphics, 33, color, "heat", String.format(Locale.ROOT, "%.2f", menu.heatRate()));
        centered(graphics, 45, color, "decay", String.format(Locale.ROOT, "%.5f", menu.decayRate()));
        centered(graphics, 57, color, "water", menu.feedAmount());
        centered(graphics, 69, color, "product", menu.productAmount());
        centered(graphics, 81, color, "rate", String.format(Locale.ROOT, "%.2f", menu.processRate()));
    }

    private void centered(GuiGraphics graphics, int y, int color, String key, Object... values) {
        Component label = Component.translatable("gui.nuclearcraft.decay_pool." + key, values);
        graphics.drawString(font, label, (imageWidth - font.width(label)) / 2, y, color, false);
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics);
        toggle.setMessage(toggleLabel());
        clear.active = Screen.hasShiftDown() || Screen.hasControlDown();
        super.render(graphics, x, y, delta);
        if (isHovering(92, 94, 70, 18, x, y)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.decay_pool.clear_hint"), x, y);
        }
    }
}
