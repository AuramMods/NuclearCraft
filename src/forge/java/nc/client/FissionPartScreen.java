package nc.client;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionPartMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class FissionPartScreen extends AbstractContainerScreen<FissionPartMenu> {
    private static final int HEAT_HEIGHT = 74;
    private static final Tank PORT_INPUT = new Tank(40, 31, 24);
    private static final Tank COOLER_INPUT = new Tank(56, 35, 16);
    private static final Tank OUTPUT = new Tank(112, 31, 24);
    private final ResourceLocation texture;
    private Button storage;

    public FissionPartScreen(FissionPartMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        texture = NuclearCraft.id("textures/gui/container/" + menu.kind().id() + ".png");
    }

    @Override
    protected void init() {
        super.init();
        storage = addRenderableWidget(Button.builder(Component.empty(), button -> send(FissionPartMenu.SWITCH_BUFFER))
                .bounds(leftPos + 45, topPos + 55, 86, 14).build());
        refreshButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButton();
    }

    private void refreshButton() {
        storage.visible = menu.linked();
        storage.setMessage(Component.translatable(menu.local()
                ? "gui.nuclearcraft.irradiator.local_items" : "gui.nuclearcraft.irradiator.linked_items"));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (!menu.kind().port() && isHovering(8, 6, 16, HEAT_HEIGHT, mouseX, mouseY)) {
            var lines = new ArrayList<Component>();
            lines.add(menu.heatCapacity() > 0
                    ? Component.translatable("gui.nuclearcraft.salt_part.heat", menu.heat(), menu.heatCapacity())
                    : Component.translatable("gui.nuclearcraft.salt_part.no_cluster"));
            if (!menu.kind().fuel()) {
                lines.add(Component.translatable("gui.nuclearcraft.jei.gas_cooling_rate", menu.cooling()));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (menu.kind().fuel()) {
            var slot = menu.getSlot(0);
            if (!slot.hasItem() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                var lines = new ArrayList<Component>();
                var input = menu.fuelFilter();
                if (!input.isEmpty()) {
                    lines.add(Component.translatable("gui.nuclearcraft.salt_part.filter", input.getHoverName()));
                    if (!menu.connected()) {
                        lines.add(Component.translatable("gui.nuclearcraft.salt_part.clear"));
                    }
                }
                var reserved = menu.reserved();
                if (!reserved.isEmpty()) {
                    lines.add(Component.translatable("gui.nuclearcraft.fission_part.reserved", reserved.getHoverName()));
                }
                if (!lines.isEmpty()) {
                    graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        } else if (hovered(input(), mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, tankTooltip(false), mouseX, mouseY);
        } else if (hovered(OUTPUT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, tankTooltip(true), mouseX, mouseY);
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var tooltip = super.getTooltipFromContainerItem(stack);
        if (!menu.kind().fuel() || hoveredSlot == null || hoveredSlot.index >= 2) {
            return tooltip;
        }
        boolean output = hoveredSlot.index == 1;
        var lines = new ArrayList<>(tooltip);
        lines.add(Component.translatable("gui.nuclearcraft.fission_part.fuel_buffer", menu.fuelAmount(output),
                menu.fuelCapacity(output)));
        return lines;
    }

    private List<Component> tankTooltip(boolean output) {
        var lines = new ArrayList<Component>();
        var fluid = menu.gas(output);
        lines.add(fluid.isEmpty() ? Component.translatable("gui.nuclearcraft.electrolyzer.empty_tank")
                : Component.translatable("gui.nuclearcraft.fluid_amount", fluid.getDisplayName(), fluid.getAmount()));
        lines.add(Component.translatable("gui.nuclearcraft.salt_part.capacity", menu.capacity(output)));
        if (!output) {
            var filter = menu.gasFilter();
            var key = BuiltInRegistries.FLUID.getKey(filter.getFluid());
            if (menu.exactGasFilter()) {
                lines.add(Component.translatable("gui.nuclearcraft.salt_part.filter", filter.getDisplayName()));
                lines.add(Component.literal(key.toString()).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("gui.nuclearcraft.fission_part.gas_tag", "#forge:" + key.getPath()));
            }
        }
        if (!fluid.isEmpty() || (!output && menu.canClearGasFilter())) {
            lines.add(Component.translatable("gui.nuclearcraft.salt_part.clear"));
        }
        return lines;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6,
                menu.processing() ? 0xFFFFFF : 0x404040, false);
        if (menu.kind().port()) {
            var mode = Component.translatable(menu.outputPort() ? "gui.nuclearcraft.salt_part.output"
                    : "gui.nuclearcraft.salt_part.input");
            graphics.drawString(font, mode, (imageWidth - font.width(mode)) / 2, 19, 0x404040, false);
        }
        if (!menu.connected()) {
            var status = Component.translatable("gui.nuclearcraft.salt_part.disconnected");
            graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, 58, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.kind().fuel()) {
            var input = menu.getSlot(0);
            var sample = menu.fuelFilter();
            if (sample.isEmpty()) {
                sample = menu.reserved();
            }
            if (!input.hasItem() && !sample.isEmpty()) {
                int x = leftPos + input.x;
                int y = topPos + input.y;
                graphics.renderFakeItem(sample, x, y);
                graphics.fill(x, y, x + 16, y + 16, 0x80777777);
            }
        } else {
            Tank input = input();
            if (menu.gas(false).isEmpty()) {
                FluidTankRenderer.draw(graphics, leftPos + input.x, topPos + input.y, input.size, menu.gasFilter(), 1, 0.45F);
            }
            drawTank(graphics, input, false);
            drawTank(graphics, OUTPUT, true);
        }
        if (!menu.kind().port()) {
            int progress = Math.round(37 * menu.progress());
            graphics.blit(texture, leftPos + 74, topPos + 35, 176, 3, progress, 16);
            if (menu.heatCapacity() > 0) {
                int height = (int) (HEAT_HEIGHT * Math.min((long) menu.heat(), menu.heatCapacity()) / menu.heatCapacity());
                graphics.blit(texture, leftPos + 8, topPos + 6 + HEAT_HEIGHT - height,
                        176, 90 + HEAT_HEIGHT - height, 16, height);
            } else {
                graphics.fillGradient(leftPos + 8, topPos + 6, leftPos + 24, topPos + 80, 0xFF777777, 0xFF535353);
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && (hasShiftDown() || hasControlDown())) {
            if (menu.kind().fuel()) {
                var slot = menu.getSlot(0);
                if (!menu.connected() && !slot.hasItem() && !menu.fuelFilter().isEmpty()
                        && isHovering(slot.x, slot.y, 16, 16, x, y)) {
                    send(FissionPartMenu.CLEAR_INPUT);
                    return true;
                }
            } else if (hovered(input(), x, y) || hovered(OUTPUT, x, y)) {
                send(hovered(OUTPUT, x, y) ? FissionPartMenu.CLEAR_OUTPUT : FissionPartMenu.CLEAR_INPUT);
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private Tank input() {
        return menu.kind().port() ? PORT_INPUT : COOLER_INPUT;
    }

    private boolean hovered(Tank tank, double x, double y) {
        return isHovering(tank.x, tank.y, tank.size, tank.size, x, y);
    }

    private void drawTank(GuiGraphics graphics, Tank tank, boolean output) {
        FluidTankRenderer.draw(graphics, leftPos + tank.x, topPos + tank.y, tank.size,
                menu.gas(output), menu.capacity(output));
    }

    private record Tank(int x, int y, int size) {}
}
