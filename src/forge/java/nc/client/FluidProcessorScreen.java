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
    private static final int ITEM_INPUT = -1;
    private static final Layout ELECTROLYZER = new Layout(NuclearCraft.id("textures/gui/container/electrolyzer.png"),
            List.of(new Tank(50, 41, 16), new Tank(106, 31, 16), new Tank(126, 31, 16),
                    new Tank(106, 51, 16), new Tank(126, 51, 16)), 68);
    private static final Layout SALT_MIXER = new Layout(NuclearCraft.id("textures/gui/container/salt_mixer.png"),
            List.of(new Tank(46, 35, 16), new Tank(66, 35, 16), new Tank(123, 31, 24)), 84);
    private static final Layout CHEMICAL_REACTOR = new Layout(NuclearCraft.id("textures/gui/container/chemical_reactor.png"),
            List.of(new Tank(32, 35, 16), new Tank(52, 35, 16), new Tank(108, 31, 24), new Tank(136, 31, 24)), 70);

    private static final Layout ENRICHER = new Layout(NuclearCraft.id("textures/gui/container/enricher.png"),
            List.of(new Tank(66, 35, 16), new Tank(122, 31, 24)), 84);

    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private final Layout layout;
    private Button overflow;
    private int selectedTank;

    FluidProcessorScreen(FluidProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        layout = switch (menu.type()) {
            case ELECTROLYZER -> ELECTROLYZER;
            case SALT_MIXER -> SALT_MIXER;
            case CHEMICAL_REACTOR -> CHEMICAL_REACTOR;
            case ENRICHER -> ENRICHER;
        };
        imageHeight = 178;
        inventoryLabelY = 84;
        titleLabelX = 29;
    }

    @Override
    protected void init() {
        super.init();
        for (Direction side : Direction.values()) {
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + side.getName() + ".short"),
                    ignored -> send(selectedTank == ITEM_INPUT ? menu.itemSideButton(side)
                            : FluidProcessorMenu.sideButton(selectedTank, side)))
                    .bounds(leftPos + 27 + side.ordinal() * 18, topPos + 69, 17, 14)
                    .build();
            sides.put(side, addRenderableWidget(button));
        }
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.electrolyzer.overflow"),
                        ignored -> send(menu.outputModeButton(selectedTank)))
                .bounds(leftPos + 136, topPos + 69, 35, 14).build());
        if (menu.type().hasItemInput()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.fluid_processor.item_input"),
                            ignored -> selectedTank = ITEM_INPUT)
                    .bounds(leftPos + 136, topPos + 12, 35, 14)
                    .tooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.fluid_processor.item_sides")))
                    .build());
        }
    }

    private void send(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sides.forEach((side, button) -> {
            var mode = selectedTank == ITEM_INPUT
                    ? (menu.itemSideEnabled(side) ? ProcessorFluidTanks.Mode.INPUT : ProcessorFluidTanks.Mode.OFF)
                    : menu.sideMode(selectedTank, side);
            button.setTooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.electrolyzer.side",
                    Component.translatable("gui.nuclearcraft.face." + side.getName()), mode.label())));
        });
        overflow.visible = selectedTank >= menu.type().inputs();
        if (overflow.visible) {
            overflow.setTooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.electrolyzer.output_mode",
                    menu.outputMode(selectedTank).label())));
        }

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
                configure = menu.type().inputs() > 1 ? Component.translatable("gui.nuclearcraft.fluid_processor.select_input", hovered + 1)
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
        ResourceLocation texture = layout.texture;
        if (menu.type() != Machine.ELECTROLYZER) {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, 61);
            graphics.blit(texture, leftPos, topPos + 61, imageWidth, 19, 0F, 60F, imageWidth, 1, 256, 256);
            graphics.blit(texture, leftPos, topPos + 80, 0, 80, 130, 1);
            graphics.blit(texture, leftPos + 130, topPos + 80, 130, 81, imageWidth - 130, 1);
            graphics.blit(texture, leftPos, topPos + 81, imageWidth, 13, 0F, 81F, imageWidth, 1, 256, 256);
            graphics.blit(texture, leftPos, topPos + 94, 0, 82, imageWidth, 84);
            int compactEnergy = (int) (74 * menu.energy());
            graphics.blit(texture, leftPos + 8, topPos + 80 - compactEnergy, 176, 164 - compactEnergy, 16, compactEnergy);
            int progressY = menu.type().hasItemInput() ? 35 : 34;
            int progressHeight = menu.type().hasItemInput() ? 16 : 18;
            graphics.blit(texture, leftPos + layout.progressX, topPos + progressY, 176, 3, progressWidth, progressHeight);
        } else {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
            graphics.blit(texture, leftPos + 8, topPos + 92 - energyHeight, 176, 176 - energyHeight, 16, energyHeight);
            graphics.blit(texture, leftPos + layout.progressX, topPos + 30, 176, 3, progressWidth, 38);
        }

        for (int index = 0; index < layout.tanks.size(); index++) {
            Tank tank = layout.tanks.get(index);
            int x = leftPos + tank.x;
            int y = topPos + tank.y;
            FluidTankRenderer.draw(graphics, x, y, tank.size, menu.fluid(index), ProcessorFluidTanks.CAPACITY);
            if (index == selectedTank) {
                graphics.renderOutline(x - 1, y - 1, tank.size + 2, tank.size + 2, 0xFFFFD37C);
            }
        }
        if (selectedTank == ITEM_INPUT) {
            graphics.renderOutline(leftPos + 45, topPos + 34, 18, 18, 0xFFFFD37C);
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
        for (int index = 0; index < layout.tanks.size(); index++) {
            Tank tank = layout.tanks.get(index);
            if (isHovering(tank.x, tank.y, tank.size, tank.size, mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private record Tank(int x, int y, int size) {}
    private record Layout(ResourceLocation texture, List<Tank> tanks, int progressX) {}
}
