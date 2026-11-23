package nc.client;

import nc.NuclearCraft;
import nc.processor.ElectrolyzerMenu;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

import java.util.EnumMap;
import java.util.List;

public final class ElectrolyzerScreen extends AbstractContainerScreen<ElectrolyzerMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/electrolyzer.png");
    private static final int[] TANK_X = {50, 106, 126, 106, 126};
    private static final int[] TANK_Y = {41, 31, 31, 51, 51};
    private static final int TANK_SIZE = 16;

    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private Button overflow;
    private int selectedTank;

    ElectrolyzerScreen(ElectrolyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 178;
        inventoryLabelY = 84;
        titleLabelX = 29;
    }

    @Override
    protected void init() {
        super.init();
        for (Direction side : Direction.values()) {
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + side.getName() + ".short"),
                    ignored -> send(ElectrolyzerMenu.sideButton(selectedTank, side)))
                    .bounds(leftPos + 27 + side.ordinal() * 18, topPos + 69, 17, 14)
                    .build();
            sides.put(side, addRenderableWidget(button));
        }
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.electrolyzer.overflow"),
                        ignored -> send(ElectrolyzerMenu.OUTPUT_MODE_BUTTON + selectedTank))
                .bounds(leftPos + 136, topPos + 69, 35, 14).build());
    }

    private void send(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sides.forEach((side, button) -> button.setTooltip(Tooltip.create(Component.translatable(
                "gui.nuclearcraft.electrolyzer.side", Component.translatable("gui.nuclearcraft.face." + side.getName()),
                menu.sideMode(selectedTank, side).label()))));
        overflow.visible = selectedTank > 0;
        overflow.setTooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.electrolyzer.output_mode",
                menu.outputMode(selectedTank).label())));

        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int hovered = hoveredTank(mouseX, mouseY);
        if (hovered >= 0) {
            FluidStack fluid = menu.fluid(hovered);
            Component contents = fluid.isEmpty() ? Component.translatable("gui.nuclearcraft.electrolyzer.empty_tank")
                    : Component.translatable("gui.nuclearcraft.fluid_amount", fluid.getDisplayName(), fluid.getAmount());
            Component configure = hovered == 0 ? Component.translatable("gui.nuclearcraft.electrolyzer.select_input")
                    : Component.translatable("gui.nuclearcraft.electrolyzer.select_output", hovered);
            graphics.renderComponentTooltip(font, List.of(contents, configure), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energyHeight = (int) (86 * menu.energy());
        graphics.blit(TEXTURE, leftPos + 8, topPos + 92 - energyHeight, 176, 176 - energyHeight, 16, energyHeight);
        graphics.blit(TEXTURE, leftPos + 68, topPos + 30, 176, 3, (int) (37 * menu.progress()), 38);

        for (int tank = 0; tank < ElectrolyzerMenu.TANK_COUNT; tank++) {
            int x = leftPos + TANK_X[tank];
            int y = topPos + TANK_Y[tank];
            FluidTankRenderer.draw(graphics, x, y, TANK_SIZE, menu.fluid(tank), ProcessorFluidTanks.CAPACITY);
            if (tank == selectedTank) {
                graphics.renderOutline(x - 1, y - 1, TANK_SIZE + 2, TANK_SIZE + 2, 0xFFFFD37C);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tank = hoveredTank(mouseX, mouseY);
        if (button == 0 && tank >= 0) {
            selectedTank = tank;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int hoveredTank(double mouseX, double mouseY) {
        for (int tank = 0; tank < ElectrolyzerMenu.TANK_COUNT; tank++) {
            if (isHovering(TANK_X[tank], TANK_Y[tank], TANK_SIZE, TANK_SIZE, mouseX, mouseY)) {
                return tank;
            }
        }
        return -1;
    }
}
