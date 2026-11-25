package nc.client;

import nc.NuclearCraft;
import nc.init.NCClientPresentation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class ItemDescriptions {
    private static final int MAX_WIDTH = 225;

    private ItemDescriptions() {}

    @SubscribeEvent
    public static void addTooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        var id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !NuclearCraft.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        String description = event.getItemStack().getDescriptionId();
        var tooltip = event.getToolTip();
        var details = descriptions(tooltip, description);
        if (details.isEmpty()) {
            return;
        }

        boolean control = NCClientPresentation.ctrlInfo();
        if (details.size() == 1 || infoKeyDown(control)) {
            tooltip.addAll(details);
        } else {
            tooltip.add(Component.translatable(control
                    ? "gui.nuclearcraft.inventory.ctrl_for_info" : "gui.nuclearcraft.inventory.shift_for_info")
                    .withStyle(ChatFormatting.ITALIC));
        }
    }

    private static boolean infoKeyDown(boolean control) {
        return control ? ClientModifiers.controlDown() : Screen.hasShiftDown();
    }

    private static List<Component> descriptions(List<Component> tooltip, String base) {
        var language = Language.getInstance();
        var lines = new ArrayList<Component>();
        if (language.has(base + ".desc")) {
            addLine(tooltip, lines, base + ".desc");
        } else {
            for (int index = 0; language.has(base + ".des" + index); index++) {
                addLine(tooltip, lines, base + ".des" + index);
            }
        }
        return lines;
    }

    private static void addLine(List<Component> tooltip, List<Component> lines, String key) {
        if (tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents text
                && text.getKey().equals(key))) {
            return;
        }
        var component = Component.translatable(key).withStyle(ChatFormatting.AQUA);
        var split = Minecraft.getInstance().font.getSplitter().splitLines(component, MAX_WIDTH, Style.EMPTY);
        if (split.size() <= 1) {
            lines.add(component);
            return;
        }
        for (var text : split) {
            var line = Component.empty();
            text.visit((style, part) -> {
                line.append(Component.literal(part).withStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(line);
        }
    }
}
