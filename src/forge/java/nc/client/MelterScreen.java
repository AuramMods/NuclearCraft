package nc.client;

import nc.NuclearCraft;
import nc.processor.MelterMenu;
import nc.processor.ProcessorFluidActions;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;

public final class MelterScreen extends AbstractContainerScreen<MelterMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/melter.png");
    private static final int TANK_X = 112;
    private static final int TANK_Y = 31;
    private static final int TANK_SIZE = 24;
    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private final ProcessorItemControls items;
    private Button slotConfig;
    private Button overflow;
    private boolean fluidSelected;

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
                            ignored -> send(fluidSelected
                                    ? FluidTankControls.faceButton(0, MelterMenu.FIRST_FLUID_SIDE_BUTTON + side.ordinal())
                                    : items.button(side, side.ordinal())))
                    .bounds(leftPos + 27 + side.ordinal() * 17, topPos + 56, 16, 14).build()));
        }
        slotConfig = addRenderableWidget(Button.builder(items.label(), button -> {
            fluidSelected = false;
            items.toggle();
        }).bounds(leftPos + 27, topPos + 17, 39, 14).build());
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.processor_items.overflow"),
                        ignored -> send(MelterMenu.FLUID_OUTPUT_BUTTON))
                .bounds(leftPos + 136, topPos + 8, 35, 14).build());
    }

    private void send(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && isHovering(TANK_X, TANK_Y, TANK_SIZE, TANK_SIZE, x, y)) {
            if (FluidTankControls.clearRequested()) {
                send(ProcessorFluidActions.clearButton(0));
            } else {
                fluidSelected = !fluidSelected;
                items.all();
            }
            return true;
        }
        return items.select(x - leftPos, y - topPos, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        slotConfig.setMessage(items.label());
        sides.forEach((side, button) -> button.setTooltip(Tooltip.create(fluidSelected
                ? FluidTankControls.faceTooltip(side, menu.sideMode(side)) : items.tooltip(side))));
        overflow.visible = fluidSelected;
        if (fluidSelected) {
            overflow.setTooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.electrolyzer.output_mode",
                    menu.outputMode().label())));
        }
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(TANK_X, TANK_Y, TANK_SIZE, TANK_SIZE, mouseX, mouseY)) {
            graphics.renderTooltip(font, FluidTankControls.tankTooltip(menu.fluid()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energyHeight = (int) (74 * menu.energy());
        graphics.blit(TEXTURE, leftPos + 8, topPos + 80 - energyHeight, 176, 90 - energyHeight, 16, energyHeight);
        int progressWidth = (int) (37 * menu.progress());
        graphics.blit(TEXTURE, leftPos + 74, topPos + 35, 176, 3, progressWidth, 16);

        int x = leftPos + TANK_X;
        int y = topPos + TANK_Y;
        FluidTankRenderer.draw(graphics, x, y, TANK_SIZE, menu.fluid(), ProcessorFluidTanks.CAPACITY);
        if (fluidSelected) {
            graphics.renderOutline(x - 1, y - 1, TANK_SIZE + 2, TANK_SIZE + 2, 0xFFFFD37C);
        }
        ProcessorUpgradeSlots.draw(graphics, menu, 1, leftPos, topPos);
        items.draw(graphics, leftPos, topPos);
    }
}
