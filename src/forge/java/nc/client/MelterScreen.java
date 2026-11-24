package nc.client;

import nc.NuclearCraft;
import nc.processor.MelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.EnumMap;

public final class MelterScreen extends AbstractContainerScreen<MelterMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/melter.png");
    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private final ProcessorItemControls items;

    public MelterScreen(MelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        items = new ProcessorItemControls(menu, menu.items());
    }

    @Override
    protected void init() {
        super.init();
        for (Direction side : Direction.values()) {
            sides.put(side, addRenderableWidget(Button.builder(
                            Component.translatable("gui.nuclearcraft.face." + side.getName() + ".short"),
                            ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                    items.button(side, side.ordinal())))
                    .bounds(leftPos + 27 + side.ordinal() * 17, topPos + 63, 16, 17).build()));
        }
        addRenderableWidget(Button.builder(items.label(), button -> {
            items.toggle();
            button.setMessage(items.label());
        }).bounds(leftPos + 27, topPos + 17, 39, 14).build());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return items.select(x - leftPos, y - topPos, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sides.forEach((side, button) -> button.setTooltip(Tooltip.create(items.tooltip(side))));
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        FluidStack fluid = menu.fluid();
        if (!fluid.isEmpty() && isHovering(112, 31, 24, 24, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.fluid_amount",
                    fluid.getDisplayName(), fluid.getAmount()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energyHeight = (int) (74 * menu.energy());
        graphics.blit(TEXTURE, leftPos + 8, topPos + 80 - energyHeight, 176, 90 - energyHeight, 16, energyHeight);
        int progressWidth = (int) (37 * menu.progress());
        graphics.blit(TEXTURE, leftPos + 74, topPos + 35, 176, 3, progressWidth, 16);

        FluidStack fluid = menu.fluid();
        if (!fluid.isEmpty()) {
            int height = Math.max(1, (int) (24 * menu.fillLevel()));
            int color = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
            graphics.fill(leftPos + 112, topPos + 55 - height, leftPos + 136, topPos + 55, 0xFF000000 | color);
        }
        ProcessorUpgradeSlots.draw(graphics, menu, 1, leftPos, topPos);
        items.draw(graphics, leftPos, topPos);
    }
}
