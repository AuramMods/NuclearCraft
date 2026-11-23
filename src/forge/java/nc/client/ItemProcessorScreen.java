package nc.client;

import nc.NuclearCraft;
import nc.processor.ItemProcessorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;

public final class ItemProcessorScreen extends AbstractContainerScreen<ItemProcessorMenu> {
    private final ResourceLocation texture;
    private final EnumMap<Direction, Button> sideButtons = new EnumMap<>(Direction.class);

    public ItemProcessorScreen(ItemProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        texture = NuclearCraft.id("textures/gui/container/" + menu.machine().id() + ".png");
    }

    @Override
    protected void init() {
        super.init();
        for (Direction face : Direction.values()) {
            int index = face.ordinal();
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + face.getName() + ".short"),
                    ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index))
                    .bounds(leftPos + 44 + index * 20, topPos + 63, 19, 17)
                    .build();
            sideButtons.put(face, addRenderableWidget(button));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sideButtons.forEach((side, button) -> button.setTooltip(Tooltip.create(Component.translatable(
                "gui.nuclearcraft.item_side.tooltip", Component.translatable("gui.nuclearcraft.face." + side.getName()),
                menu.sideMode(side).label()))));
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energyHeight = (int) (74 * menu.energy());
        graphics.blit(texture, leftPos + 8, topPos + 80 - energyHeight, 176, 90 - energyHeight, 16, energyHeight);
        int progressWidth = (int) (37 * menu.progress());
        var machine = menu.machine();
        graphics.blit(texture, leftPos + machine.progressX(), topPos + machine.progressY(),
                176, 3, progressWidth, machine.progressHeight());
    }
}
