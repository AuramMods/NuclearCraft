package nc.client;

import nc.NuclearCraft;
import nc.processor.FluidProcessorBlockEntity.Machine;
import nc.processor.FluidProcessorMenu;
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

public final class FluidProcessorScreen extends AbstractContainerScreen<FluidProcessorMenu> {
    private static final ResourceLocation ELECTROLYZER = NuclearCraft.id("textures/gui/container/electrolyzer.png");
    private static final ResourceLocation SALT_MIXER = NuclearCraft.id("textures/gui/container/salt_mixer.png");
    private static final int[] ELECTROLYZER_TANK_X = {50, 106, 126, 106, 126};
    private static final int[] ELECTROLYZER_TANK_Y = {41, 31, 31, 51, 51};
    private static final int[] MIXER_TANK_X = {46, 66, 123};
    private static final int[] MIXER_TANK_Y = {35, 35, 31};

    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private final boolean mixer;
    private final int[] tankX;
    private final int[] tankY;
    private Button overflow;
    private int selectedTank;

    FluidProcessorScreen(FluidProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        mixer = menu.type() == Machine.SALT_MIXER;
        tankX = mixer ? MIXER_TANK_X : ELECTROLYZER_TANK_X;
        tankY = mixer ? MIXER_TANK_Y : ELECTROLYZER_TANK_Y;
        imageHeight = 178;
        inventoryLabelY = 84;
        titleLabelX = 29;
    }

    @Override
    protected void init() {
        super.init();
        for (Direction side : Direction.values()) {
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + side.getName() + ".short"),
                    ignored -> send(FluidProcessorMenu.sideButton(selectedTank, side)))
                    .bounds(leftPos + 27 + side.ordinal() * 18, topPos + 69, 17, 14)
                    .build();
            sides.put(side, addRenderableWidget(button));
        }
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.electrolyzer.overflow"),
                        ignored -> send(menu.outputModeButton(selectedTank)))
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
        overflow.visible = selectedTank >= menu.type().inputs();
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
            Component configure;
            if (hovered < menu.type().inputs()) {
                configure = mixer ? Component.translatable("gui.nuclearcraft.salt_mixer.select_input", hovered + 1)
                        : Component.translatable("gui.nuclearcraft.electrolyzer.select_input");
            } else {
                configure = Component.translatable("gui.nuclearcraft.electrolyzer.select_output",
                        hovered - menu.type().inputs() + 1);
            }
            graphics.renderComponentTooltip(font, List.of(contents, configure), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int progressWidth = (int) (37 * menu.progress());
        int energyHeight = (int) (86 * menu.energy());
        if (mixer) {
            graphics.blit(SALT_MIXER, leftPos, topPos, 0, 0, imageWidth, 61);
            graphics.blit(SALT_MIXER, leftPos, topPos + 61, imageWidth, 19, 0F, 60F, imageWidth, 1, 256, 256);
            graphics.blit(SALT_MIXER, leftPos, topPos + 80, 0, 80, 130, 1);
            graphics.blit(SALT_MIXER, leftPos + 130, topPos + 80, 130, 81, imageWidth - 130, 1);
            graphics.blit(SALT_MIXER, leftPos, topPos + 81, imageWidth, 13, 0F, 81F, imageWidth, 1, 256, 256);
            graphics.blit(SALT_MIXER, leftPos, topPos + 94, 0, 82, imageWidth, 84);
            int mixerEnergy = (int) (74 * menu.energy());
            graphics.blit(SALT_MIXER, leftPos + 8, topPos + 80 - mixerEnergy, 176, 164 - mixerEnergy, 16, mixerEnergy);
            graphics.blit(SALT_MIXER, leftPos + 84, topPos + 34, 176, 3, progressWidth, 18);
        } else {
            graphics.blit(ELECTROLYZER, leftPos, topPos, 0, 0, imageWidth, imageHeight);
            graphics.blit(ELECTROLYZER, leftPos + 8, topPos + 92 - energyHeight, 176, 176 - energyHeight, 16, energyHeight);
            graphics.blit(ELECTROLYZER, leftPos + 68, topPos + 30, 176, 3, progressWidth, 38);
        }

        for (int tank = 0; tank < tankX.length; tank++) {
            int x = leftPos + tankX[tank];
            int y = topPos + tankY[tank];
            int size = tankSize(tank);
            FluidTankRenderer.draw(graphics, x, y, size, menu.fluid(tank), ProcessorFluidTanks.CAPACITY);
            if (tank == selectedTank) {
                graphics.renderOutline(x - 1, y - 1, size + 2, size + 2, 0xFFFFD37C);
            }
        }
    }

    private int tankSize(int tank) {
        return mixer && tank == 2 ? 24 : 16;
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
        for (int tank = 0; tank < tankX.length; tank++) {
            if (isHovering(tankX[tank], tankY[tank], tankSize(tank), tankSize(tank), mouseX, mouseY)) {
                return tank;
            }
        }
        return -1;
    }
}
