package nc.equipment;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;
import java.util.function.Predicate;

public final class EquipmentConfig {
    private static final List<Boolean> ENABLED = List.of(true, true, true, true);
    private static final ForgeConfigSpec.Builder AVAILABILITY = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> REGISTER_TOOL = AVAILABILITY
            .comment("Enable crafting and creative listing by family: boron, tough alloy, hard carbon, boron nitride.")
            .worldRestart().define("register_tool", ENABLED, value -> validList(value, 4, Boolean.class::isInstance));
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> REGISTER_ARMOR = AVAILABILITY
            .comment("Enable crafting and creative listing by family: boron, tough alloy, hard carbon, boron nitride.")
            .worldRestart().define("register_armor", ENABLED, value -> validList(value, 4, Boolean.class::isInstance));
    private static final ForgeConfigSpec AVAILABILITY_SPEC = AVAILABILITY.build();

    private static final ForgeConfigSpec.Builder STATS = new ForgeConfigSpec.Builder();
    private static final Numbers MINING_LEVEL = new Numbers("tool_mining_level", 0, 15,
            List.of(2, 2, 3, 3, 3, 3, 4, 4));
    private static final Numbers TOOL_DURABILITY = new Numbers("tool_durability", 1, 32767,
            List.of(547, 2735, 929, 4645, 1245, 6225, 1928, 9640));
    private static final Numbers TOOL_SPEED = new Numbers("tool_speed", 1, 255,
            List.of(8D, 8D, 10D, 10D, 11D, 11D, 12D, 12D));
    private static final Numbers TOOL_DAMAGE = new Numbers("tool_attack_damage", 0, 255,
            List.of(2.5D, 2.5D, 3D, 3D, 3D, 3D, 3.5D, 3.5D));
    private static final Numbers TOOL_ENCHANTABILITY = new Numbers("tool_enchantability", 1, 255,
            List.of(6, 6, 15, 15, 12, 12, 20, 20));
    private static final Numbers ARMOR_DURABILITY = new Numbers("armor_durability", 1, 127,
            List.of(22, 30, 34, 42));
    private static final Numbers ARMOR_TOUGHNESS = new Numbers("armor_toughness", 0, 8,
            List.of(1D, 2D, 1D, 2D));
    private static final Numbers ARMOR_ENCHANTABILITY = new Numbers("armor_enchantability", 1, 255,
            List.of(6, 15, 12, 20));
    private static final List<Numbers> ARMOR_DEFENSE = List.of(
            new Numbers("armor_boron", 1, 25, List.of(2, 5, 7, 3)),
            new Numbers("armor_tough", 1, 25, List.of(3, 6, 7, 3)),
            new Numbers("armor_hard_carbon", 1, 25, List.of(3, 5, 7, 3)),
            new Numbers("armor_boron_nitride", 1, 25, List.of(3, 6, 8, 3)));
    private static final ForgeConfigSpec STATS_SPEC = STATS.build();

    private EquipmentConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, AVAILABILITY_SPEC, "nuclearcraft-equipment-availability.toml");
        context.registerConfig(ModConfig.Type.SERVER, STATS_SPEC, "nuclearcraft-equipment.toml");
    }

    public static boolean isEnabled(EquipmentFamily family, boolean armor) {
        var values = armor ? REGISTER_ARMOR : REGISTER_TOOL;
        return !AVAILABILITY_SPEC.isLoaded() || values.get().get(family.ordinal());
    }

    static int miningLevel(int index) { return MINING_LEVEL.get(index).intValue(); }
    static int toolDurability(int index) { return TOOL_DURABILITY.get(index).intValue(); }
    static float toolSpeed(int index) { return TOOL_SPEED.get(index).floatValue(); }
    static float toolDamage(int index) { return TOOL_DAMAGE.get(index).floatValue(); }
    static int toolEnchantability(int index) { return TOOL_ENCHANTABILITY.get(index).intValue(); }
    static int armorDurability(int family) { return ARMOR_DURABILITY.get(family).intValue(); }
    static float armorToughness(int family) { return ARMOR_TOUGHNESS.get(family).floatValue(); }
    static int armorEnchantability(int family) { return ARMOR_ENCHANTABILITY.get(family).intValue(); }
    static int armorDefense(int family, int slot) { return ARMOR_DEFENSE.get(family).get(slot).intValue(); }

    private static boolean validList(Object value, int length, Predicate<Object> validEntry) {
        return value instanceof List<?> list && list.size() == length && list.stream().allMatch(validEntry);
    }

    private static final class Numbers {
        private final List<Number> defaults;
        private final ForgeConfigSpec.ConfigValue<List<Number>> value;

        private Numbers(String key, double min, double max, List<Number> defaults) {
            this.defaults = defaults;
            boolean integral = defaults.get(0) instanceof Integer;
            value = STATS.worldRestart().define(key, defaults, candidate -> validList(candidate, defaults.size(),
                    entry -> entry instanceof Number number && number.doubleValue() >= min && number.doubleValue() <= max
                            && (!integral || number.doubleValue() == number.intValue())));
        }

        private Number get(int index) {
            return (STATS_SPEC.isLoaded() ? value.get() : defaults).get(index);
        }
    }
}
