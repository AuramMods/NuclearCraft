package nc.client;

import nc.NuclearCraft;
import nc.processor.ProcessorFluidActions;
import nc.processor.FluidProcessorMenu;
import nc.processor.ProcessorFluidTanks;
import nc.processor.ProcessorMenuNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.List;

public final class FluidProcessorScreen extends AbstractContainerScreen<FluidProcessorMenu> {
    private static final int ITEM_INPUT = -1;
    private static final Layout ELECTROLYZER = new Layout(NuclearCraft.id("textures/gui/container/electrolyzer.png"),
            List.of(new Tank(50, 41, 16), new Tank(106, 31, 16), new Tank(126, 31, 16),
                    new Tank(106, 51, 16), new Tank(126, 51, 16)), 68, 30, 38, true);
    private static final Layout SALT_MIXER = new Layout(NuclearCraft.id("textures/gui/container/salt_mixer.png"),
            List.of(new Tank(46, 35, 16), new Tank(66, 35, 16), new Tank(123, 31, 24)), 84, 34, 18, false);
    private static final Layout CHEMICAL_REACTOR = new Layout(NuclearCraft.id("textures/gui/container/chemical_reactor.png"),
            List.of(new Tank(32, 35, 16), new Tank(52, 35, 16), new Tank(108, 31, 24), new Tank(136, 31, 24)), 70, 34, 18, false);

    private static final Layout ENRICHER = new Layout(NuclearCraft.id("textures/gui/container/enricher.png"),
            List.of(new Tank(66, 35, 16), new Tank(122, 31, 24)), 84, 35, 16, false);

    private static final Layout SUPERCOOLER = new Layout(NuclearCraft.id("textures/gui/container/supercooler.png"),
            List.of(new Tank(56, 35, 16), new Tank(112, 31, 24)), 74, 35, 16, false);

    private static final Layout CENTRIFUGE = new Layout(NuclearCraft.id("textures/gui/container/centrifuge.png"),
            List.of(new Tank(40, 41, 16), new Tank(96, 31, 16), new Tank(116, 31, 16), new Tank(136, 31, 16),
                    new Tank(96, 51, 16), new Tank(116, 51, 16), new Tank(136, 51, 16)), 58, 30, 38, true);

    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private final Layout layout;
    private final ProcessorItemControls items;
    private Button slotConfig;
    private Button separation;
    private Button overflow;
    private int selectedTank;

    FluidProcessorScreen(FluidProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        items = new ProcessorItemControls(menu, menu.items());
        layout = switch (menu.type()) {
            case ELECTROLYZER -> ELECTROLYZER;
            case SALT_MIXER -> SALT_MIXER;
            case CHEMICAL_REACTOR -> CHEMICAL_REACTOR;
            case ENRICHER -> ENRICHER;
            case SUPERCOOLER -> SUPERCOOLER;
            case CENTRIFUGE -> CENTRIFUGE;
        };
        imageHeight = 178;
        inventoryLabelY = 84;
        inventoryLabelX = layout.fullTexture() ? 31 : 8;
        titleLabelX = 29;
    }

    @Override
    protected void init() {
        super.init();
        for (Direction side : Direction.values()) {
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + side.getName() + ".short"),
                    ignored -> send(selectedTank == ITEM_INPUT ? items.button(side, menu.itemSideButton(side))
                            : FluidTankControls.faceButton(selectedTank, FluidProcessorMenu.sideButton(selectedTank, side))))
                    .bounds(leftPos + 27 + side.ordinal() * 17, topPos + 69, 16, 14)
                    .build();
            sides.put(side, addRenderableWidget(button));
        }
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.processor_items.overflow"),
                        ignored -> send(menu.outputModeButton(selectedTank)))
                .bounds(leftPos + 136, topPos + 9, 35, 14).build());
        boolean multipleInputs = menu.type().inputs() > 1;
        if (multipleInputs) {
            separation = addRenderableWidget(Button.builder(Component.empty(), ignored -> send(FluidProcessorMenu.SEPARATION_BUTTON))
                    .bounds(leftPos + 27, topPos + 16, 35, 14).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.fluid_processor.item_input"),
                        ignored -> {
                            selectedTank = ITEM_INPUT;
                            items.all();
                        })
                .bounds(leftPos + (multipleInputs ? 103 : 95), topPos + 16, 32, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.fluid_processor.item_sides")))
                .build());
        slotConfig = addRenderableWidget(Button.builder(items.label(), ignored -> {
            selectedTank = ITEM_INPUT;
            items.toggle();
        }).bounds(leftPos + (multipleInputs ? 64 : 50), topPos + 16, multipleInputs ? 37 : 40, 14).build());
        addRenderableWidget(new ProcessorRedstoneButton(leftPos + 27, topPos + (multipleInputs ? 53 : 16),
                menu::redstoneControl, ignored -> send(ProcessorMenuNetwork.STOCK_REDSTONE_BUTTON)));
    }

    private void send(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        slotConfig.setMessage(items.label());
        if (separation != null) {
            separation.setMessage(Component.translatable(menu.separatesInputs() ? "gui.nuclearcraft.processor_fluids.split"
                    : "gui.nuclearcraft.processor_fluids.shared"));
            separation.setTooltip(Tooltip.create(Component.translatable(menu.separatesInputs()
                    ? "gui.nuclearcraft.processor_fluids.split_help" : "gui.nuclearcraft.processor_fluids.shared_help")));
        }
        sides.forEach((side, button) -> button.setTooltip(Tooltip.create(selectedTank == ITEM_INPUT
                ? items.tooltip(side) : FluidTankControls.faceTooltip(side, menu.sideMode(selectedTank, side)))));
        overflow.visible = selectedTank >= 0;
        if (overflow.visible) {
            overflow.setTooltip(Tooltip.create(selectedTank < menu.type().inputs()
                    ? FluidTankControls.inputPolicy(menu.discardsInput(selectedTank))
                    : Component.translatable("gui.nuclearcraft.electrolyzer.output_mode", menu.outputMode(selectedTank).label())));
        }

        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int hovered = hoveredTank(mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, FluidTankControls.tankTooltip(menu.fluid(hovered)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int progressWidth = (int) (37 * menu.progress());
        int energyHeight = (int) (86 * menu.energy());
        ResourceLocation texture = layout.texture;
        if (!layout.fullTexture()) {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, 61);
            graphics.blit(texture, leftPos, topPos + 61, imageWidth, 19, 0F, 60F, imageWidth, 1, 256, 256);
            graphics.blit(texture, leftPos, topPos + 80, 0, 80, 130, 1);
            graphics.blit(texture, leftPos + 130, topPos + 80, 130, 81, imageWidth - 130, 1);
            graphics.blit(texture, leftPos, topPos + 81, imageWidth, 13, 0F, 81F, imageWidth, 1, 256, 256);
            graphics.blit(texture, leftPos, topPos + 94, 0, 82, imageWidth, 84);
            int compactEnergy = (int) (74 * menu.energy());
            graphics.blit(texture, leftPos + 8, topPos + 80 - compactEnergy, 176, 164 - compactEnergy, 16, compactEnergy);
        } else {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
            graphics.fill(leftPos + 130, topPos + 68, leftPos + 171, topPos + 93, 0xFFC6C6C6);
            graphics.blit(texture, leftPos + 8, topPos + 92 - energyHeight, 176, 176 - energyHeight, 16, energyHeight);
        }

        graphics.blit(texture, leftPos + layout.progressX, topPos + layout.progressY, 176, 3, progressWidth, layout.progressHeight);
        for (int index = 0; index < layout.tanks.size(); index++) {
            Tank tank = layout.tanks.get(index);
            int x = leftPos + tank.x;
            int y = topPos + tank.y;
            FluidTankRenderer.draw(graphics, x, y, tank.size, menu.fluid(index), ProcessorFluidTanks.CAPACITY);
            if (index == selectedTank) {
                graphics.renderOutline(x - 1, y - 1, tank.size + 2, tank.size + 2, 0xFFFFD37C);
            }
        }
        ProcessorUpgradeSlots.draw(graphics, menu, menu.type().hasItemInput() ? 1 : 0, leftPos, topPos);
        if (selectedTank == ITEM_INPUT && !items.individual() && menu.type().hasItemInput()) {
            graphics.renderOutline(leftPos + 45, topPos + 34, 18, 18, 0xFFFFD37C);
        }
        items.draw(graphics, leftPos, topPos);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (items.select(mouseX - leftPos, mouseY - topPos, button)) {
            return true;
        }
        int tank = hoveredTank(mouseX, mouseY);
        if (button == 0 && tank >= 0) {
            if (FluidTankControls.clearRequested()) {
                send(ProcessorFluidActions.clearButton(tank));
                return true;
            }
            selectedTank = tank;
            items.all();
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
    private record Layout(ResourceLocation texture, List<Tank> tanks, int progressX, int progressY, int progressHeight,
                          boolean fullTexture) {}
}
