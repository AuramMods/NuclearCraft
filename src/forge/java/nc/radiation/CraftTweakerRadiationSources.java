package nc.radiation;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.base.IUndoableAction;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.blamejared.crafttweaker.api.util.Many;
import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.openzen.zencode.java.ZenCodeType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@ZenRegister
@ZenCodeType.Name("mods.nuclearcraft.Radiation")
public final class CraftTweakerRadiationSources {
    private static final Map<String, String> PREFIX_ALIASES = Map.ofEntries(
            Map.entry("ingot", "ingots"), Map.entry("dust", "dusts"), Map.entry("dustdirty", "dirty_dusts"),
            Map.entry("clump", "clumps"), Map.entry("shard", "shards"), Map.entry("crystal", "crystals"),
            Map.entry("dustimpure", "impure_dusts"), Map.entry("dustpure", "pure_dusts"),
            Map.entry("plate", "plates"), Map.entry("blocksheetmetal", "sheetmetals"),
            Map.entry("tinydust", "tiny_dusts"), Map.entry("dusttiny", "tiny_dusts"), Map.entry("nugget", "nuggets"),
            Map.entry("rod", "rods"), Map.entry("stick", "rods"), Map.entry("slabsheetmetal", "sheetmetal_slabs"),
            Map.entry("coin", "coins"), Map.entry("smalldust", "small_dusts"), Map.entry("dustsmall", "small_dusts"),
            Map.entry("ore", "ores"), Map.entry("oregravel", "gravel_ores"), Map.entry("crushed", "crushed"),
            Map.entry("gear", "gears"), Map.entry("block", "storage_blocks"), Map.entry("platedense", "dense_plates"),
            Map.entry("slab", "slabs"));
    private static final List<String> ISOTOPE_FORMS = List.of("", "_carbide", "_oxide", "_nitride", "_za");
    private static final List<String> FUEL_FORMS = List.of("", "_triso", "_carbide", "_oxide", "_nitride", "_za");

    private CraftTweakerRadiationSources() {}

    @ZenCodeType.Method
    public static double getRadiationLevel(IItemStack stack) {
        return RadiationSources.itemRate(stack.getInternal());
    }

    @ZenCodeType.Method
    public static double getRadiationLevel(IFluidStack stack) {
        return stack.isEmpty() ? 0 : RadiationSources.fluidUnitRate(stack.getFluid()) * (stack.getAmount() / 1000D);
    }

    @ZenCodeType.Method
    public static double getRadiationLevel(KnownTag<Item> tag) {
        return RadiationSources.itemTagRate(tag.id());
    }

    @ZenCodeType.Method
    public static double getRadiationLevel(Many<KnownTag<Item>> tag) {
        return getRadiationLevel(tag.getData()) * tag.getAmount();
    }

    @ZenCodeType.Method
    public static void setRadiationLevel(IItemStack stack, double radiation) {
        setRate(stack.getRegistryName(), radiation, rule -> rule.items.put(stack.getRegistryName(), radiation));
    }

    @ZenCodeType.Method
    public static void setRadiationLevel(IFluidStack stack, double radiation) {
        setRate(stack.getRegistryName(), radiation, rule -> rule.fluids.put(stack.getRegistryName(), radiation));
    }

    @ZenCodeType.Method
    public static void setRadiationLevel(KnownTag<Item> tag, double radiation) {
        setRate(tag.id(), radiation, rule -> rule.itemTags.put(tag.id(), radiation));
    }

    @ZenCodeType.Method
    public static void setRadiationLevel(Many<KnownTag<Item>> tag, double radiation) {
        setRadiationLevel(tag.getData(), radiation);
    }

    @ZenCodeType.Method
    public static void addToRadiationBlacklist(IItemStack stack) {
        var id = stack.getRegistryName();
        apply("Exclude " + id + " from item radiation", rule -> rule.excludedItems.add(id));
    }

    @ZenCodeType.Method
    public static void addToRadiationBlacklist(IFluidStack stack) {
        var id = stack.getRegistryName();
        apply("Exclude " + id + " from fluid radiation", rule -> rule.excludedFluids.add(id));
    }

    @ZenCodeType.Method
    public static void addToRadiationBlacklist(KnownTag<Item> tag) {
        apply("Exclude #" + tag.id() + " from radiation", rule -> rule.excludedTags.add(tag.id()));
    }

    @ZenCodeType.Method
    public static void addToRadiationBlacklist(Many<KnownTag<Item>> tag) {
        addToRadiationBlacklist(tag.getData());
    }

    @ZenCodeType.Method
    public static void setMaterialPrefixRadiationMultiplier(String prefix, double multiplier) {
        String form = PREFIX_ALIASES.getOrDefault(prefix.toLowerCase(Locale.ROOT), materialName(prefix));
        requireRate(multiplier);
        apply("Set radiation multiplier for " + form, rule -> rule.materialPrefixes.put(form, multiplier));
    }

    @ZenCodeType.Method
    public static void setMaterialRadiationLevel(String material, double radiation) {
        requireRate(radiation);
        var definition = RadiationScriptRules.MaterialRule.named(materialName(material), radiation);
        apply("Set material radiation for " + material, rule -> rule.materials.add(definition));
    }

    @ZenCodeType.Method
    public static void setIsotopeRadiationLevel(String isotope, double radiation) {
        setIsotopeRadiationLevel(isotope, "", radiation);
    }

    @ZenCodeType.Method
    public static void setIsotopeRadiationLevel(String isotope, String fluid, double radiation) {
        String material = materialName(isotope);
        requireRate(radiation);
        var forms = ISOTOPE_FORMS.stream()
                .map(form -> RadiationScriptRules.MaterialRule.named(material + form, radiation)).toList();
        var fluidId = parseFluid(fluid);
        apply("Set isotope radiation for " + isotope, rule -> {
            rule.materials.addAll(forms);
            addFluidForms(rule, fluidId, "", radiation);
        });
    }

    @ZenCodeType.Method
    public static void setFuelRadiationLevel(String fuel, double fresh, double depleted) {
        setFuelRadiationLevel(fuel, "", fresh, depleted);
    }

    @ZenCodeType.Method
    public static void setFuelRadiationLevel(String fuel, String fluid, double fresh, double depleted) {
        String material = materialName(fuel);
        requireRate(fresh);
        requireRate(depleted);
        var fluidId = parseFluid(fluid);
        apply("Set fuel radiation for " + fuel, rule -> {
            for (String form : FUEL_FORMS) {
                for (String namespace : List.of("forge", "c")) {
                    rule.itemTags.put(new ResourceLocation(namespace, "ingots/" + material + form), fresh);
                    rule.itemTags.put(new ResourceLocation(namespace, "ingots/depleted_" + material + form), depleted);
                }
            }
            addFluidForms(rule, fluidId, "", fresh);
            addFluidForms(rule, fluidId, "depleted_", depleted);
        });
    }

    @ZenCodeType.Method
    public static void setFoodRadiationStats(IItemStack food, double radiation, double resistance) {
        if (!Double.isFinite(radiation) || !Double.isFinite(resistance)) {
            throw new IllegalArgumentException("Food radiation and resistance must be finite");
        }
        var id = food.getRegistryName();
        apply("Set food radiation for " + id,
                rule -> rule.foods.put(id, new FoodRadiation.Effects(radiation, resistance)));
    }

    @ZenCodeType.Method
    public static void setRadiationImmunityGameStages(boolean defaultImmunity, String... stages) {
        if (List.of(stages).stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("GameStages IDs must not be blank");
        }
        var settings = new RadiationScriptRules.StageRule(defaultImmunity, List.of(stages));
        apply("Set GameStages radiation immunity", rule -> rule.stages = settings);
    }

    private static void addFluidForms(RadiationScriptRules.Builder rule, ResourceLocation fluid, String prefix, double rate) {
        if (fluid == null) {
            return;
        }
        double perBucket = Math.min(Double.MAX_VALUE, rate * RadiationSources.FLUID_MULTIPLIER);
        for (String form : RadiationSources.SALT_FORMS) {
            rule.fluids.put(new ResourceLocation(fluid.getNamespace(), prefix + fluid.getPath() + form), perBucket);
        }
    }

    private static ResourceLocation parseFluid(String fluid) {
        if (fluid.isEmpty()) {
            return null;
        }
        return fluid.indexOf(':') < 0 ? NuclearCraft.id(fluid) : new ResourceLocation(fluid);
    }

    private static String materialName(String name) {
        String converted = name.replaceAll("(?<=[a-z])(?=[A-Z])|(?<=[a-zA-Z])(?=[0-9])", "_")
                .toLowerCase(Locale.ROOT);
        if (converted.isBlank()) {
            throw new IllegalArgumentException("Radiation material names must not be blank");
        }
        return converted;
    }

    private static void setRate(ResourceLocation id, double rate, Consumer<RadiationScriptRules.Builder> rule) {
        requireRate(rate);
        apply("Set radiation for " + id + " to " + rate, rule);
    }

    private static void requireRate(double rate) {
        if (!Double.isFinite(rate) || rate < 0) {
            throw new IllegalArgumentException("Radiation source rates must be finite and nonnegative");
        }
    }

    private static void apply(String description, Consumer<RadiationScriptRules.Builder> rule) {
        CraftTweakerAPI.apply(new RuleAction(description, rule));
    }

    private static final class RuleAction implements IUndoableAction {
        private final String description;
        private final Consumer<RadiationScriptRules.Builder> rule;

        private RuleAction(String description, Consumer<RadiationScriptRules.Builder> rule) {
            this.description = description;
            this.rule = rule;
        }

        @Override
        public void apply() {
            RadiationScriptRules.add(this, rule);
        }

        @Override
        public void undo() {
            RadiationScriptRules.remove(this);
        }

        @Override
        public String describe() {
            return description;
        }

        @Override
        public String describeUndo() {
            return "Undo " + description;
        }

        @Override
        public String systemName() {
            return NuclearCraft.MOD_ID;
        }
    }
}
