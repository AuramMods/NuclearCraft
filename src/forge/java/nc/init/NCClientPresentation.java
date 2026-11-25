package nc.init;

import nc.NuclearCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class NCClientPresentation {
    private static final List<CreativeModeTab.DisplayItemsGenerator> GROUPS = new ArrayList<>();
    private static final ForgeConfigSpec.Builder CONFIG = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue SINGLE_TAB = CONFIG
            .comment("Show NuclearCraft's creative entries in one tab. Reopen Minecraft to change the tab layout.")
            .define("single_creative_tab", false);
    private static final ForgeConfigSpec.BooleanValue CTRL_INFO = CONFIG
            .comment("Use Ctrl instead of Shift to expand NuclearCraft item descriptions.")
            .define("ctrl_info", false);
    private static final ForgeConfigSpec SPEC = CONFIG.build();

    private NCClientPresentation() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC, "nuclearcraft-client.toml");
        NCRegistries.TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.nuclearcraft.main"))
                .icon(() -> ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_nitride")).getDefaultInstance())
                .displayItems(NCClientPresentation::combined).build());
    }

    static RegistryObject<CreativeModeTab> category(String name, Supplier<ItemStack> icon,
                                                    CreativeModeTab.DisplayItemsGenerator entries) {
        GROUPS.add(entries);
        return NCRegistries.TABS.register(name, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.nuclearcraft." + name)).icon(icon)
                .displayItems((parameters, output) ->
                        entries.accept(parameters, singleTab() ? searchOnly(output) : output)).build());
    }

    private static boolean singleTab() {
        return SPEC.isLoaded() && SINGLE_TAB.get();
    }

    public static boolean ctrlInfo() {
        return SPEC.isLoaded() && CTRL_INFO.get();
    }

    private static void combined(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        var seen = ItemStackLinkedSet.createTypeAndTagSet();
        var destination = singleTab() ? output : searchOnly(output);
        CreativeModeTab.Output unique = (stack, visibility) -> {
            if (seen.add(stack)) {
                destination.accept(stack, visibility);
            }
        };
        GROUPS.forEach(entries -> entries.accept(parameters, unique));
    }

    private static CreativeModeTab.Output searchOnly(CreativeModeTab.Output output) {
        return (stack, visibility) -> {
            if (visibility != TabVisibility.PARENT_TAB_ONLY) {
                output.accept(stack, TabVisibility.SEARCH_TAB_ONLY);
            }
        };
    }
}
