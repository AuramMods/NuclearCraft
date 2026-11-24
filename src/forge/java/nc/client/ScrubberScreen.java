package nc.client;

import nc.NuclearCraft;
import nc.processor.ProcessorFluidTanks;
import nc.processor.ScrubberMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;

public final class ScrubberScreen extends AbstractContainerScreen<ScrubberMenu> {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/gui/container/radiation_scrubber.png");
    private static final int[] TANK_X = {52, 136};
    private static final int[] TANK_Y = {35, 31};
    private static final int[] TANK_SIZE = {16, 24};
    private final EnumMap<Direction, Button> sides = new EnumMap<>(Direction.class);
    private int selection = ScrubberMenu.ITEMS;
    private Button redstone;
    private Button outputMode;

    public ScrubberScreen(ScrubberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        redstone = addRenderableWidget(Button.builder(Component.literal("R"), button -> send(ScrubberMenu.REDSTONE_BUTTON))
                .bounds(leftPos + 27, topPos + 63, 16, 14).build());
        for (Direction side : Direction.values()) {
            Button button = Button.builder(Component.translatable("gui.nuclearcraft.face." + side.getName() + ".short"),
                            ignored -> send(ScrubberMenu.sideButton(selection, side)))
                    .bounds(leftPos + 46 + side.ordinal() * 19, topPos + 63, 18, 14).build();
            sides.put(side, addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.scrubber.items"),
                        button -> selection = ScrubberMenu.ITEMS)
                .bounds(leftPos + 113, topPos + 17, 48, 13).build());
        outputMode = addRenderableWidget(Button.builder(Component.translatable("gui.nuclearcraft.electrolyzer.overflow"),
                        button -> send(ScrubberMenu.OUTPUT_MODE_BUTTON))
                .bounds(leftPos + 73, topPos + 17, 34, 13).build());
    }

    private void send(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < TANK_X.length; i++) {
            if (button == 0 && isHovering(TANK_X[i], TANK_Y[i], TANK_SIZE[i], TANK_SIZE[i], mouseX, mouseY)) {
                selection = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float tick) {
        redstone.setMessage(Component.literal("R").withStyle(menu.redstoneEnabled() ? ChatFormatting.RED : ChatFormatting.WHITE));
        redstone.setTooltip(Tooltip.create(Component.translatable(menu.redstoneEnabled()
                ? "gui.nuclearcraft.scrubber.redstone_enabled" : "gui.nuclearcraft.scrubber.redstone_disabled")));
        sides.forEach((side, button) -> {
            Component mode = selection == ScrubberMenu.ITEMS
                    ? menu.itemSide(side).label() : menu.fluidSide(selection, side).label();
            Component target = Component.translatable(switch (selection) {
                case 0 -> "gui.nuclearcraft.scrubber.fluid_input";
                case 1 -> "gui.nuclearcraft.scrubber.fluid_output";
                default -> "gui.nuclearcraft.scrubber.items";
            });
            button.setTooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.scrubber.side", target,
                    Component.translatable("gui.nuclearcraft.face." + side.getName()), mode)));
        });
        outputMode.visible = selection == 1;
        outputMode.setTooltip(Tooltip.create(Component.translatable("gui.nuclearcraft.electrolyzer.output_mode",
                menu.outputMode().label())));
        renderBackground(graphics);
        super.render(graphics, x, y, tick);
        renderTooltip(graphics, x, y);
        for (int i = 0; i < TANK_X.length; i++) {
            var fluid = menu.fluid(i);
            if (isHovering(TANK_X[i], TANK_Y[i], TANK_SIZE[i], TANK_SIZE[i], x, y)) {
                Component info = fluid.isEmpty()
                        ? Component.translatable(i == 0 ? "gui.nuclearcraft.scrubber.fluid_input"
                        : "gui.nuclearcraft.scrubber.fluid_output")
                        : Component.translatable("gui.nuclearcraft.fluid_amount", fluid.getDisplayName(), fluid.getAmount());
                graphics.renderTooltip(font, info, x, y);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, titleLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energy = (int) (74 * menu.energy());
        int empty = 74 - energy;
        graphics.blit(TEXTURE, leftPos + 8, topPos + 6 + empty, 176, 90 + empty, 16, energy);
        graphics.blit(TEXTURE, leftPos + 70, topPos + 35, 176, 3, (int) (37 * menu.progress()), 16);
        for (int i = 0; i < TANK_X.length; i++) {
            if (selection == i) {
                graphics.renderOutline(leftPos + TANK_X[i] - 1, topPos + TANK_Y[i] - 1,
                        TANK_SIZE[i] + 2, TANK_SIZE[i] + 2, 0xFFD0B550);
            }
            FluidTankRenderer.draw(graphics, leftPos + TANK_X[i], topPos + TANK_Y[i], TANK_SIZE[i],
                    menu.fluid(i), ProcessorFluidTanks.CAPACITY);
        }
    }
}
