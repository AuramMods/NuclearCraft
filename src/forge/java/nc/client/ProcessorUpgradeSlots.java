package nc.client;

import nc.init.NCItemProcessors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

final class ProcessorUpgradeSlots {
    private ProcessorUpgradeSlots() {}

    static void draw(GuiGraphics graphics, AbstractContainerMenu menu, int firstSlot, int left, int top) {
        drawSlot(graphics, menu.getSlot(firstSlot), left, top, true);
        drawSlot(graphics, menu.getSlot(firstSlot + 1), left, top, false);
    }

    private static void drawSlot(GuiGraphics graphics, Slot slot, int left, int top, boolean speed) {
        int x = left + slot.x;
        int y = top + slot.y;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFFFFFFF);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        if (!slot.hasItem()) {
            var item = speed ? NCItemProcessors.SPEED_UPGRADE : NCItemProcessors.ENERGY_UPGRADE;
            graphics.renderFakeItem(item.get().getDefaultInstance(), x, y);
            graphics.fill(x, y, x + 16, y + 16, 160, 0x998B8B8B);
        }
    }
}
