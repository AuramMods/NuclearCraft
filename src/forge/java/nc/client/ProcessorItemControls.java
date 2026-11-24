package nc.client;

import nc.processor.ProcessorItemSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

final class ProcessorItemControls {
    private final AbstractContainerMenu container;
    private final ProcessorItemSettings menu;
    private int selected = -1;

    ProcessorItemControls(AbstractContainerMenu container, ProcessorItemSettings menu) {
        this.container = container;
        this.menu = menu;
    }

    void toggle() {
        selected = individual() ? -1 : 0;
    }

    void all() {
        selected = -1;
    }

    boolean individual() {
        return selected >= 0;
    }

    Component label() {
        return Component.translatable(individual() ? "gui.nuclearcraft.processor_items.all"
                : "gui.nuclearcraft.processor_items.slots");
    }

    boolean select(double x, double y, int button) {
        if (!individual() || button != 0) {
            return false;
        }
        for (int slot = 0; slot < menu.slots(); slot++) {
            Slot candidate = container.getSlot(slot);
            if (x >= candidate.x - 1 && x < candidate.x + 17 && y >= candidate.y - 1 && y < candidate.y + 17) {
                selected = slot;
                return true;
            }
        }
        return false;
    }

    int button(Direction side, int groupButton) {
        return individual() ? menu.slotButton(selected, side) : groupButton;
    }

    Component tooltip(Direction side) {
        Component face = Component.translatable("gui.nuclearcraft.face." + side.getName());
        return individual()
                ? Component.translatable("gui.nuclearcraft.processor_items.side", menu.slotName(selected), face,
                menu.mode(selected, side).label())
                : Component.translatable("gui.nuclearcraft.item_side.tooltip", face, menu.group(side).label());
    }

    boolean outputSelected() {
        return individual() && menu.isOutput(selected);
    }

    int overflowButton() {
        return menu.overflowButton(selected);
    }

    Component overflowTooltip() {
        return Component.translatable("gui.nuclearcraft.electrolyzer.output_mode", menu.overflow(selected).label());
    }

    void draw(GuiGraphics graphics, int left, int top) {
        if (individual()) {
            Slot slot = container.getSlot(selected);
            graphics.renderOutline(left + slot.x - 2, top + slot.y - 2, 20, 20, 0xFFFFD37C);
        }
    }
}
