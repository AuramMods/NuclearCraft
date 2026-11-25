package nc.client;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorMenu;
import nc.processor.ProcessorFluidActions;
import nc.processor.ProcessorLayout;
import nc.processor.ProcessorMenuNetwork;
import nc.processor.ProcessorProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public final class ItemProcessorScreen extends AbstractContainerScreen<ItemProcessorMenu> {
    private static final ResourceLocation STANDARD_TEXTURE = NuclearCraft.id("textures/gui/container/manufactory.png");
    private final ResourceLocation texture;
    private final @Nullable ProcessorLayout layout;
    private final EnumMap<Direction, Button> sideButtons = new EnumMap<>(Direction.class);
    private final ProcessorItemControls items;
    private Button slotConfig;
    private Button overflow;
    private int selectedTank = -1;

    public ItemProcessorScreen(ItemProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        items = new ProcessorItemControls(menu, menu.items());
        layout = menu.machine().layout();
        if (layout == null) {
            imageHeight += menu.machine().menuExtension();
        } else {
            imageWidth = layout.size().x();
            imageHeight = layout.size().y();
        }
        texture = NuclearCraft.id("textures/gui/container/" + menu.machine().id() + ".png");
    }

    @Override
    protected void init() {
        super.init();
        var machine = menu.machine();
        boolean fluidOutput = machine.hasFluidOutput();
        boolean compact = machine.menuExtension() == 0;
        var faces = layout == null
                ? new ProcessorLayout.Point(27, compact ? 56 : fluidOutput ? 65 : 63 + machine.menuExtension())
                : layout.faces();
        for (Direction face : Direction.values()) {
            int index = face.ordinal();
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + face.getName() + ".short"),
                    ignored -> send(selectedTank >= 0 || (!items.individual() && machine.tank() != null && hasShiftDown())
                            ? tankSideButton(Math.max(0, selectedTank), index) : items.button(face, index)))
                    .bounds(leftPos + faces.x() + index * 17, topPos + faces.y(), 16, layout != null || compact || fluidOutput ? 14 : 17)
                    .build();
            button.visible = faces.visible();
            sideButtons.put(face, addRenderableWidget(button));
        }
        var config = layout == null ? new ProcessorLayout.Point(27, 17) : layout.config();
        slotConfig = addRenderableWidget(Button.builder(items.label(), button -> {
            selectedTank = -1;
            items.toggle();
        }).bounds(leftPos + config.x(), topPos + config.y(), 39, 14).build());
        slotConfig.visible = config.visible() && menu.items().slots() > 0;
        var overflowOrigin = layout == null ? new ProcessorLayout.Point(136, 8) : layout.overflow();
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.electrolyzer.overflow"),
                        ignored -> send(items.outputSelected() ? items.overflowButton() : tankPolicyButton(Math.max(0, selectedTank))))
                .bounds(leftPos + overflowOrigin.x(), topPos + overflowOrigin.y(), 35, 14).build());
        var redstone = layout == null ? new ProcessorLayout.Point(108, 17) : layout.redstone();
        if (redstone.visible()) {
            int buttonId = machine.scripted() ? ItemProcessorMenu.REDSTONE_BUTTON : ProcessorMenuNetwork.STOCK_REDSTONE_BUTTON;
            addRenderableWidget(new ProcessorRedstoneButton(leftPos + redstone.x(), topPos + redstone.y(),
                    menu::redstoneControl, ignored -> send(buttonId)));
        }
    }

    private void send(int button) {
        if (menu.machine().scripted()) {
            ProcessorMenuNetwork.click(menu.containerId, button);
        } else {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    private int tankSideButton(int tank, int side) {
        if (!menu.machine().scripted()) {
            return FluidTankControls.faceButton(tank, ItemProcessorMenu.FLUID_SIDE_BUTTON + side);
        }
        int action = hasControlDown() ? ItemProcessorMenu.TANK_DISABLE : hasAltDown() ? ItemProcessorMenu.TANK_RESET : side;
        return ItemProcessorMenu.scriptedTankButton(tank, action);
    }

    private int tankPolicyButton(int tank) {
        return menu.machine().scripted() ? ItemProcessorMenu.scriptedTankButton(tank, ItemProcessorMenu.TANK_POLICY)
                : ItemProcessorMenu.FLUID_OUTPUT_BUTTON;
    }

    private int hoveredTank(double x, double y) {
        var tanks = menu.machine().tanks();
        for (int index = 0; index < tanks.size(); index++) {
            var tank = tanks.get(index);
            if (isHovering(tank.x(), tank.y(), tank.width(), tank.height(), x, y)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int tank = hoveredTank(x, y);
        if (button == 0 && tank >= 0) {
            if (FluidTankControls.clearRequested()) {
                send(menu.machine().scripted() ? ItemProcessorMenu.scriptedTankButton(tank, ItemProcessorMenu.TANK_CLEAR)
                        : ProcessorFluidActions.clearButton(tank));
            } else {
                selectedTank = selectedTank == tank ? -1 : tank;
                items.all();
            }
            return true;
        }
        return items.select(x - leftPos, y - topPos, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        slotConfig.setMessage(items.label());
        sideButtons.forEach((side, button) -> {
            Component face = Component.translatable("gui.nuclearcraft.face." + side.getName());
            Component itemMode = menu.sideMode(side).label();
            button.setTooltip(Tooltip.create(selectedTank >= 0 ? FluidTankControls.faceTooltip(side, menu.fluidSideMode(selectedTank, side))
                    : !items.individual() && menu.machine().tank() != null
                    ? Component.translatable("gui.nuclearcraft.fluid_side.tooltip", face, itemMode, menu.fluidSideMode(side).label())
                    : items.tooltip(side)));
        });
        overflow.visible = (layout == null || layout.overflow().visible()) && (items.individual() ? items.outputSelected()
                : selectedTank >= 0 || (layout == null && menu.machine().hasFluidOutput()));
        if (overflow.visible) {
            int tank = Math.max(0, selectedTank);
            overflow.setMessage(Component.translatable("gui.nuclearcraft.processor_items.overflow"));
            overflow.setTooltip(Tooltip.create(items.outputSelected() ? items.overflowTooltip()
                    : tank < menu.machine().fluidInputs() ? FluidTankControls.inputPolicy(menu.discardsFluidInput(tank))
                    : Component.translatable("gui.nuclearcraft.electrolyzer.output_mode", menu.fluidOutputMode(tank).label())));
        }
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int tank = hoveredTank(mouseX, mouseY);
        if (tank >= 0) {
            graphics.renderTooltip(font, FluidTankControls.tankTooltip(menu.fluid(tank)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (layout == null && menu.machine().menuExtension() == 0 && !usesCenteredTitle(menu.machine())) {
            super.renderLabels(graphics, mouseX, mouseY);
            return;
        }
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, titleLabelY, 0x404040, false);
        if (layout != null) {
            graphics.drawString(font, playerInventoryTitle, layout.player().x(), layout.player().y() - 12, 0x404040, false);
        } else if (menu.machine().hasFluidOutput()) {
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, 84, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        var machine = menu.machine();
        if (layout != null) {
            renderScriptedPanel(graphics, machine, layout);
        } else if (machine.hasFluidOutput()) {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, 61);
            graphics.blit(texture, leftPos, topPos + 61, imageWidth, 19, 0F, 60F, imageWidth, 1, 256, 256);
            graphics.blit(texture, leftPos, topPos + 80, 0, 80, 130, 1);
            graphics.blit(texture, leftPos + 130, topPos + 80, 130, 81, imageWidth - 130, 1);
            graphics.blit(texture, leftPos, topPos + 81, imageWidth, 13, 0F, 81F, imageWidth, 1, 256, 256);
            graphics.blit(texture, leftPos, topPos + 94, 0, 82, imageWidth, 84);
        } else {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
            if (machine.menuExtension() > 0) {
                graphics.fill(leftPos + 130, topPos + 74, leftPos + 170, topPos + 94, 0xFFC6C6C6);
            } else if (usesCenteredTitle(machine)) {
                graphics.fill(leftPos + 130, topPos + 62, leftPos + 170, topPos + 81, 0xFFC6C6C6);
            }
        }
        if (layout != null) {
            renderBars(graphics, layout);
        } else {
            int energyBar = 74 + (machine.hasFluidOutput() ? 0 : machine.menuExtension());
            int energyHeight = (int) (energyBar * menu.energy());
            int emptyHeight = energyBar - energyHeight;
            graphics.blit(texture, leftPos + 8, topPos + 6 + emptyHeight, 176, 90 + emptyHeight, 16, energyHeight);
            int progressWidth = (int) (37 * menu.progress());
            graphics.blit(texture, leftPos + machine.progressX(), topPos + machine.progressY(),
                    176, 3, progressWidth, machine.progressHeight());
        }
        var tanks = machine.tanks();
        for (int index = 0; index < tanks.size(); index++) {
            var tank = tanks.get(index);
            int x = leftPos + tank.x();
            int y = topPos + tank.y();
            FluidTankRenderer.draw(graphics, x, y, tank.width(), tank.height(),
                    menu.fluid(index), machine.tankCapacity(index));
            if (selectedTank == index) {
                graphics.renderOutline(x - 1, y - 1, tank.width() + 2, tank.height() + 2, 0xFFFFD37C);
            }
        }
        if (machine.hasUpgrades()) {
            ProcessorUpgradeSlots.draw(graphics, menu, machine.slots(), leftPos, topPos);
        }
        items.draw(graphics, leftPos, topPos);
    }

    private void renderBars(GuiGraphics graphics, ProcessorLayout layout) {
        var energy = layout.energy();
        var area = energy.area();
        int filled = (int) (area.height() * menu.energy());
        if (area.visible() && filled > 0) {
            int gap = area.height() - filled;
            if (layout.texture() != null && energy.sprite().visible()) {
                graphics.blit(layout.texture(), leftPos + area.x(), topPos + area.y() + gap,
                        energy.sprite().x(), energy.sprite().y() + gap, area.width(), filled,
                        layout.textureSize().x(), layout.textureSize().y());
            } else {
                graphics.fill(leftPos + area.x(), topPos + area.y() + gap, leftPos + area.x() + area.width(),
                        topPos + area.y() + area.height(), 0xFFD64224);
            }
        }
        var progress = layout.progress();
        area = progress.area();
        int width = (int) (area.width() * menu.progress());
        if (area.visible() && width > 0) {
            if (layout.texture() != null && progress.sprite().visible()) {
                graphics.blit(layout.texture(), leftPos + area.x(), topPos + area.y(),
                        progress.sprite().x(), progress.sprite().y(), width, area.height(),
                        layout.textureSize().x(), layout.textureSize().y());
            } else if (area.width() == 37 && area.height() == 16) {
                graphics.blit(STANDARD_TEXTURE, leftPos + area.x(), topPos + area.y(), 176, 3, width, area.height());
            } else {
                graphics.fill(leftPos + area.x(), topPos + area.y(), leftPos + area.x() + width,
                        topPos + area.y() + area.height(), 0xFF626262);
            }
        }
    }

    private void renderScriptedPanel(GuiGraphics graphics, ProcessorProfile machine, ProcessorLayout layout) {
        if (layout.texture() != null) {
            graphics.blit(layout.texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight,
                    layout.textureSize().x(), layout.textureSize().y());
            return;
        }
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFC6C6C6);
        var energy = layout.energy().area();
        if (energy.visible()) {
            graphics.fill(leftPos + energy.x(), topPos + energy.y(),
                    leftPos + energy.x() + energy.width(), topPos + energy.y() + energy.height(), 0xFF444444);
        }
        layout.itemInputs().forEach(slot -> drawWell(graphics, slot));
        layout.itemOutputs().forEach(slot -> drawWell(graphics, slot));
        layout.fluidInputs().forEach(tank -> drawWell(graphics, tank));
        layout.fluidOutputs().forEach(tank -> drawWell(graphics, tank));
        int playerStart = machine.slots();
        if (machine.hasUpgrades()) {
            drawWell(graphics, layout.speedUpgrade());
            drawWell(graphics, layout.energyUpgrade());
            playerStart += 2;
        }
        for (int slot = playerStart; slot < menu.slots.size(); slot++) {
            var inventorySlot = menu.slots.get(slot);
            drawWell(graphics, new ProcessorLayout.Rect(inventorySlot.x, inventorySlot.y, 16, 16));
        }
    }

    private void drawWell(GuiGraphics graphics, ProcessorLayout.Rect area) {
        if (!area.visible()) {
            return;
        }
        int x = leftPos + area.x();
        int y = topPos + area.y();
        int right = x + area.width();
        int bottom = y + area.height();
        graphics.fill(x - 1, y - 1, right + 1, bottom + 1, 0xFFFFFFFF);
        graphics.fill(x - 1, y - 1, right, bottom, 0xFF373737);
        graphics.fill(x, y, right, bottom, 0xFF8B8B8B);
    }

    private static boolean usesCenteredTitle(ProcessorProfile machine) {
        return machine.inputs() == 0 || machine == Machine.ELECTRIC_FURNACE;
    }
}
