package nc.radiation;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

final class ShieldingSettings {
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> levels;
    private static ForgeConfigSpec.BooleanValue tileShielding;
    private static ForgeConfigSpec.BooleanValue defaultRecipes;
    private static ForgeConfigSpec.BooleanValue horseArmor;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> custom;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> defaults;

    private ShieldingSettings() {}

    static void define(ForgeConfigSpec.Builder builder) {
        levels = builder.comment("Resistance added by light, medium, and heavy shielding, in that order.")
                .defineListAllowEmpty("radiation_shielding_level", List.of(0.01D, 0.1D, 1D),
                        entry -> entry instanceof Number number
                                && number.doubleValue() >= 1E-18 && number.doubleValue() <= 1000D);
        tileShielding = builder.define("radiation_tile_shielding", true);
        defaultRecipes = builder.define("radiation_shielding_default_recipes", true);
        horseArmor = builder.define("radiation_horse_armor", false);

        var blocked = new ArrayList<>(List.of("nuclearcraft:helm_hazmat", "nuclearcraft:chest_hazmat",
                "nuclearcraft:legs_hazmat", "nuclearcraft:boots_hazmat", "ic2:hazmat_helmet", "ic2:hazmat_chestplate",
                "ic2:hazmat_leggings", "ic2:rubber_boots"));
        var inherent = new ArrayList<>(List.of("nuclearcraft:helm_hazmat_2.0", "nuclearcraft:chest_hazmat_3.0",
                "nuclearcraft:legs_hazmat_2.0", "nuclearcraft:boots_hazmat_2.0", "ic2:hazmat_helmet_2.0",
                "ic2:hazmat_chestplate_3.0", "ic2:hazmat_leggings_2.0", "ic2:rubber_boots_2.0",
                "ic2:quantum_helmet_2.0", "ic2:quantum_chestplate_3.0", "ic2:quantum_leggings_2.0",
                "ic2:quantum_boots_2.0", "gravisuite:gravichestplate_3.0", "ic2:itemarmorquantumhelmet_2.0",
                "ic2:itemarmorquantumchestplate_3.0", "ic2:itemarmorquantumlegs_2.0", "ic2:itemarmorquantumboots_2.0",
                "gravisuit:gravisuit_3.0", "gravisuit:nucleargravisuit_3.0"));
        double[] suitResistance = {1D, 1.3D, 1.6D, 2D};
        double[] chestResistance = {1.5D, 1.95D, 2.4D, 3D};
        for (int tier = 1; tier <= suitResistance.length; tier++) {
            for (String part : List.of("helmet", "chest", "chest_jetpack", "leggings", "boots", "gravity_boots")) {
                String id = "extraplanets:tier" + tier + "_space_suit_" + part;
                blocked.add(id);
                double resistance = part.startsWith("chest") ? chestResistance[tier - 1] : suitResistance[tier - 1];
                inherent.add(id + "_" + resistance);
            }
        }
        blacklist = builder.defineListAllowEmpty("radiation_shielding_item_blacklist", blocked, ShieldingSettings::itemId);
        custom = builder.defineListAllowEmpty("radiation_shielding_custom_stacks", List.of(), ShieldingSettings::itemId);
        defaults = builder.comment("Inherent armor resistance entries use namespace:item_resistance.")
                .defineListAllowEmpty("radiation_shielding_default_levels", inherent, ShieldingSettings::entry);
    }

    static double level(int tier, double fallback) {
        if (!RadiationConfig.loaded()) {
            return fallback;
        }
        var configured = levels.get();
        return tier < configured.size() ? configured.get(tier).doubleValue() : fallback;
    }

    static double inherent(String id) {
        if (!RadiationConfig.loaded()) {
            return 0;
        }
        double resistance = 0;
        for (String entry : defaults.get()) {
            int split = entry.lastIndexOf('_');
            if (split == id.length() && entry.startsWith(id)) {
                resistance = Double.parseDouble(entry.substring(split + 1));
            }
        }
        return resistance;
    }

    static boolean tileShielding() {
        return RadiationConfig.loaded() && tileShielding.get();
    }

    static boolean horseArmor() {
        return RadiationConfig.loaded() && horseArmor.get();
    }

    static boolean canUpgrade(String id) {
        return RadiationConfig.loaded()
                && (custom.get().contains(id) || defaultRecipes.get() && !blacklist.get().contains(id));
    }

    private static boolean itemId(Object value) {
        if (!(value instanceof String id)) {
            return false;
        }
        var location = ResourceLocation.tryParse(id);
        return location != null && location.toString().equals(id);
    }

    private static boolean entry(Object value) {
        if (!(value instanceof String entry)) {
            return false;
        }
        int split = entry.lastIndexOf('_');
        if (split < 1 || !itemId(entry.substring(0, split))) {
            return false;
        }
        try {
            return Double.isFinite(Double.parseDouble(entry.substring(split + 1)));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
