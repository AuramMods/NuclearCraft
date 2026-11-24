package nc.client;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorMenu;
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

public final class ItemProcessorScreen extends AbstractContainerScreen<ItemProcessorMenu> {
    private final ResourceLocation texture;
    private final EnumMap<Direction, Button> sideButtons = new EnumMap<>(Direction.class);
    private final ProcessorItemControls items;
    private Button overflow;

    public ItemProcessorScreen(ItemProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        items = new ProcessorItemControls(menu, menu.items());
        imageHeight += menu.machine().menuExtension();
        texture = NuclearCraft.id("textures/gui/container/" + menu.machine().id() + ".png");
    }

    @Override
    protected void init() {
        super.init();
        boolean fluidOutput = menu.machine().hasFluidOutput();
        int sideY = fluidOutput ? 65 : 63 + menu.machine().menuExtension();
        for (Direction face : Direction.values()) {
            int index = face.ordinal();
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + face.getName() + ".short"),
                    ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            items.button(face, menu.machine().tank() != null && hasShiftDown()
                                    ? ItemProcessorMenu.FLUID_SIDE_BUTTON + index : index)))
                    .bounds(leftPos + 27 + index * 17, topPos + sideY, 16, fluidOutput ? 14 : 17)
                    .build();
            sideButtons.put(face, addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(items.label(), button -> {
            items.toggle();
            button.setMessage(items.label());
        }).bounds(leftPos + 27, topPos + 17, 39, 14).build());
        overflow = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.electrolyzer.overflow"),
                        ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                items.outputSelected() ? items.overflowButton() : ItemProcessorMenu.FLUID_OUTPUT_BUTTON))
                .bounds(leftPos + 136, topPos + 8, 35, 14).build());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return items.select(x - leftPos, y - topPos, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sideButtons.forEach((side, button) -> {
            Component face = Component.translatable("gui.nuclearcraft.face." + side.getName());
            Component itemMode = menu.sideMode(side).label();
            button.setTooltip(Tooltip.create(!items.individual() && menu.machine().tank() != null
                    ? Component.translatable("gui.nuclearcraft.fluid_side.tooltip", face, itemMode, menu.fluidSideMode(side).label())
                    : items.tooltip(side)));
        });
        overflow.visible = items.individual() ? items.outputSelected() : menu.machine().hasFluidOutput();
        if (overflow.visible) {
            overflow.setMessage(Component.translatable(items.individual() ? "gui.nuclearcraft.processor_items.overflow"
                    : "gui.nuclearcraft.electrolyzer.overflow"));
            overflow.setTooltip(Tooltip.create(items.individual() ? items.overflowTooltip()
                    : Component.translatable("gui.nuclearcraft.electrolyzer.output_mode", menu.fluidOutputMode().label())));
        }
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        FluidStack fluid = menu.fluid();
        var tank = menu.machine().tank();
        if (tank != null && !fluid.isEmpty() && isHovering(tank.x(), tank.y(), tank.size(), tank.size(), mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.nuclearcraft.fluid_amount",
                    fluid.getDisplayName(), fluid.getAmount()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.machine().menuExtension() == 0 && !usesCenteredTitle(menu.machine())) {
            super.renderLabels(graphics, mouseX, mouseY);
            return;
        }
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, titleLabelY, 0x404040, false);
        if (menu.machine().hasFluidOutput()) {
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, 84, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        var machine = menu.machine();
        if (machine.hasFluidOutput()) {
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
        int energyBar = 74 + (machine.hasFluidOutput() ? 0 : machine.menuExtension());
        int energyHeight = (int) (energyBar * menu.energy());
        int emptyHeight = energyBar - energyHeight;
        graphics.blit(texture, leftPos + 8, topPos + 6 + emptyHeight, 176, 90 + emptyHeight, 16, energyHeight);
        int progressWidth = (int) (37 * menu.progress());
        graphics.blit(texture, leftPos + machine.progressX(), topPos + machine.progressY(),
                176, 3, progressWidth, machine.progressHeight());
        var tank = machine.tank();
        if (tank != null) {
            FluidTankRenderer.draw(graphics, leftPos + tank.x(), topPos + tank.y(), tank.size(),
                    menu.fluid(), ProcessorFluidTanks.CAPACITY);
        }
        ProcessorUpgradeSlots.draw(graphics, menu, machine.slots(), leftPos, topPos);
        items.draw(graphics, leftPos, topPos);
    }

    private static boolean usesCenteredTitle(Machine machine) {
        return machine.inputs() == 0 || machine == Machine.ELECTRIC_FURNACE;
    }
}
