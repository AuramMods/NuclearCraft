package nc.init;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

final class NCFissionFuels {
    private static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

    private NCFissionFuels() {}

    static void register() {
        for (String isotope : List.of(
                "uranium_233", "uranium_235", "uranium_238", "neptunium_236", "neptunium_237",
                "plutonium_238", "plutonium_239", "plutonium_241", "plutonium_242",
                "americium_241", "americium_242", "americium_243",
                "curium_243", "curium_245", "curium_246", "curium_247", "berkelium_247", "berkelium_248",
                "californium_249", "californium_250", "californium_251", "californium_252")) {
            for (String form : List.of("", "_carbide", "_oxide", "_nitride", "_za")) {
                registerItem(isotope + form + "_ingot");
            }
        }
        for (String isotope : List.of("boron_10", "boron_11", "lithium_6", "lithium_7")) {
            registerItem(isotope + "_ingot");
        }
        for (String fuel : List.of(
                "tbu", "leu_233", "heu_233", "leu_235", "heu_235", "len_236", "hen_236",
                "lep_239", "hep_239", "lep_241", "hep_241", "mix_239", "mix_241", "lea_242", "hea_242",
                "lecm_243", "hecm_243", "lecm_245", "hecm_245", "lecm_247", "hecm_247", "leb_248", "heb_248",
                "lecf_249", "hecf_249", "lecf_251", "hecf_251")) {
            registerItem(fuel + "_pellet");
            registerItem(fuel + "_pellet_carbide");
            for (String form : List.of("_triso", "_oxide", "_nitride", "_za")) {
                if (!"lecf_251_oxide".equals(fuel + form)) {
                    registerItem(fuel + form);
                    registerItem("depleted_" + fuel + form);
                }
            }
        }
    }

    static void displayItems(CreativeModeTab.Output output) {
        ITEMS.forEach(item -> output.accept(item.get()));
    }

    private static void registerItem(String id) {
        ITEMS.add(NCRegistries.ITEMS.register(id, () -> new Item(new Item.Properties())));
    }
}
