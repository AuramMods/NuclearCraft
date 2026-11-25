package nc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

final class ProcessorRedstoneButton extends Button {
    private final BooleanSupplier setting;
    private boolean enabled;

    ProcessorRedstoneButton(int x, int y, BooleanSupplier setting, OnPress onPress) {
        super(x, y, 18, 14, Component.empty(), onPress, DEFAULT_NARRATION);
        this.setting = setting;
        refresh(setting.getAsBoolean());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean current = setting.getAsBoolean();
        if (enabled != current) {
            refresh(current);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    private void refresh(boolean enabled) {
        this.enabled = enabled;
        int color = enabled ? 0xFF7777 : 0xBBBBBB;
        setMessage(Component.translatable("gui.nuclearcraft.processor.redstone").withStyle(style -> style.withColor(color)));
        setTooltip(Tooltip.create(Component.translatable(enabled
                ? "gui.nuclearcraft.processor.redstone_enabled" : "gui.nuclearcraft.processor.redstone_disabled")));
    }
}
