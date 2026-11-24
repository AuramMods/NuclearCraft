package nc.init;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.init.NCFissionMaterials.Form;
import nc.init.NCFissionMaterials.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static nc.init.NCFissionMaterials.FLUORIDE;
import static nc.init.NCFissionMaterials.FUEL_SALT;
import static nc.init.NCFissionMaterials.nonnegative;

final class FissionMaterialResources {
    private static final Gson GSON = new Gson();
    private final Map<ResourceLocation, Map<String, ?>> recipes = new HashMap<>();
    private final Map<ResourceLocation, Map<String, ?>> models = new HashMap<>();
    private final Map<ResourceLocation, Set<String>> tags = new HashMap<>();
    private final Map<String, String> language = new HashMap<>();

    void addItem(String item, String tag, String label, String parent) {
        models.put(NuclearCraft.id("models/item/" + item + ".json"), Map.of("parent", parent));
        language.put("item.nuclearcraft." + item, label);
        tag("items/ingots/" + tag, id(item));
    }

    void addFluid(String fluid, String label) {
        models.put(NuclearCraft.id("models/item/" + fluid + "_bucket.json"),
                Map.of("parent", "forge:item/bucket", "loader", "forge:fluid_container", "fluid", id(fluid)));
        models.put(NuclearCraft.id("blockstates/" + fluid + ".json"),
                Map.of("variants", Map.of("", Map.of("model", "nuclearcraft:block/fluid/molten_still"))));
        language.put("fluid_type.nuclearcraft." + fluid, label);
        language.put("block.nuclearcraft." + fluid, label);
        language.put("item.nuclearcraft." + fluid + "_bucket", label + " Bucket");
        tag("fluids/molten_" + fluid, id(fluid), id("flowing_" + fluid));
    }

    void formRecipes(Material material, boolean molten) {
        String rawItem = material.item(Form.RAW, false);
        String rawTag = material.ingotTag(Form.RAW, false);
        Set<Form> forms = material.forms();
        if (forms.contains(Form.RAW)) {
            if (forms.contains(Form.CARBIDE)) {
                alloy(material, Form.CARBIDE, "graphite");
                separate(material, Form.CARBIDE, rawItem, "graphite_dust");
            }
            if (forms.contains(Form.ZA)) {
                alloy(material, Form.ZA, "zirconium");
                separate(material, Form.ZA, rawItem, "zirconium_dust");
            }
            if (forms.contains(Form.OXIDE)) {
                infuse(material, Form.OXIDE, rawTag, rawItem, "oxygen");
            }
            if (forms.contains(Form.NITRIDE)) {
                infuse(material, Form.NITRIDE, rawTag, rawItem, "nitrogen");
            }
            if (molten) {
                String fluid = material.name();
                recipe("melter", fluid, 800, 40, Map.of(
                        "ingredient", ingot(rawTag), "result", fluid(fluid, 144)));
                String type = "ingot_former";
                addRecipe(type, fluid, "nuclearcraft:" + type, Map.of(
                        "fluid_input", fluidTag("molten_" + fluid, 144), "ingredients", List.of(),
                        "result", item(rawItem, 1), "ticks", 400, "power_multiplier", 1));
            }
        }
        if (material.fuel() && forms.containsAll(Set.of(Form.CARBIDE, Form.TRISO))) {
            String triso = material.item(Form.TRISO, false);
            recipe("assembler", triso, 600, 10, Map.of(
                    "ingredients", List.of(counted(ingot(material.ingotTag(Form.CARBIDE, false)), 9),
                            counted(itemTag("dusts/graphite"), 1), counted(ingot("pyrolytic_carbon"), 1),
                            counted(ingot("silicon_carbide"), 1)),
                    "result", item(triso, 9)));
        }
    }

    private void alloy(Material material, Form form, String addition) {
        String result = material.item(form, false);
        String raw = material.ingotTag(Form.RAW, false);
        recipe("alloy_furnace", result, 400, 20, Map.of(
                "ingredients", List.of(counted(alloyIngredient(raw), 1),
                        counted(alloyIngredient(addition), 1)), "result", item(result, 1)));
        alloySize(result, "nuggets", "tiny_dusts", raw, addition, 400 / 9);
        alloySize(result, "storage_blocks", "storage_blocks", raw, addition, 3_600);
    }

    private void alloySize(String result, String size, String dustSize, String raw, String addition, int ticks) {
        String first = size + "/" + raw;
        String second = size + "/" + addition;
        String firstDust = dustSize + "/" + raw;
        String secondDust = dustSize + "/" + addition;
        recipe("alloy_furnace", result + "_from_" + size, ticks, 20, Map.of(
                "ingredients", List.of(counted(List.of(itemTag(first), itemTag(firstDust)), 1),
                        counted(List.of(itemTag(second), itemTag(secondDust)), 1)),
                "conditions", List.of(anyTag(first, firstDust), anyTag(second, secondDust)),
                "result", item(result, 1)));
    }

    private static Map<String, ?> anyTag(String first, String second) {
        return Map.of("type", "forge:not", "value", Map.of("type", "forge:and", "values", List.of(
                Map.of("type", "forge:tag_empty", "tag", "forge:" + first),
                Map.of("type", "forge:tag_empty", "tag", "forge:" + second))));
    }

    private void separate(Material material, Form form, String result, String dust) {
        recipe("separator", material.item(form, false) + "_split", 800, 10, Map.of(
                "ingredients", List.of(counted(ingot(material.ingotTag(form, false)), 1)),
                "results", List.of(item(result, 1), item(dust, 1))));
    }

    private void infuse(Material material, Form form, String rawTag, String rawItem, String gas) {
        String result = material.item(form, false);
        recipe("infuser", result, 600, 10, Map.of(
                "ingredients", List.of(counted(ingot(rawTag), 1)),
                "fluid_input", fluidTag(gas, 1_000), "result", item(result, 1)));
        addRecipe("materials/reduction", result, "minecraft:smelting", Map.of(
                "category", "misc", "ingredient", ingot(material.ingotTag(form, false)),
                "result", id(rawItem), "experience", 0, "cookingtime", 200));
    }

    void fuelRecipes(Material material, JsonObject json, boolean molten) {
        if (!molten && material.forms().stream().noneMatch(Form::hasDepleted)) {
            return;
        }
        JsonObject stats = GsonHelper.getAsJsonObject(json, "fission");
        int ticks = whole(stats, "ticks");
        if (ticks <= 0) {
            throw new JsonSyntaxException("Fission ticks must be positive for " + material.name());
        }
        int heat = whole(stats, "heat");
        int criticality = whole(stats, "criticality");
        int intrinsicFlux = whole(stats, "intrinsic_flux");
        var common = Map.of("efficiency", nonnegative(stats, "efficiency"),
                "decay_factor", nonnegative(stats, "decay_factor"), "radiation", nonnegative(stats, "radiation"),
                "self_priming", GsonHelper.getAsBoolean(stats, "self_priming", false));
        for (Form form : material.forms()) {
            switch (form) {
                case TRISO -> burn(material, form, common, ticks * 0.9D, heat / 0.9D, criticality * 0.9D, intrinsicFlux);
                case OXIDE -> burn(material, form, common, ticks, heat, criticality, 0);
                case NITRIDE -> burn(material, form, common, ticks * 1.25D, heat * 0.8D, criticality * 1.25D, 0);
                case ZA -> burn(material, form, common, ticks * 0.8D, heat * 1.25D, criticality * 0.85D, 0);
                default -> { }
            }
        }
        if (molten) {
            String name = material.name();
            Map<String, Object> salt = new HashMap<>(common);
            salt.put("fuel", fluid(name + FUEL_SALT, 1));
            salt.put("result", fluid("depleted_" + name + FUEL_SALT, 1));
            salt.put("ticks", ticks * 1.25D / 144D);
            salt.put("heat", (int) (heat * 0.8D));
            salt.put("criticality", criticality);
            salt.put("intrinsic_flux", 0);
            addRecipe("salt_fission", name, "nuclearcraft:salt_fission", salt);
            saltProcessing(name);
        }
    }

    private void burn(Material material, Form form, Map<String, ?> common,
                      double ticks, double heat, double criticality, int flux) {
        String type = form == Form.TRISO ? "pebble_fission" : "solid_fission";
        Map<String, Object> fields = new HashMap<>(common);
        fields.put("fuel", ingot(material.ingotTag(form, false)));
        fields.put("result", item(material.item(form, true), 1));
        fields.put("ticks", Math.max(1, (int) ticks));
        fields.put("heat", (int) heat);
        fields.put("criticality", (int) criticality);
        fields.put("intrinsic_flux", flux);
        addRecipe(type, material.item(form, false), "nuclearcraft:" + type, fields);
    }

    private void saltProcessing(String fresh) {
        String fluoride = fresh + FLUORIDE;
        recipe("chemical_reactor", fluoride, 400, 5, Map.of(
                "fluid_inputs", List.of(fluid(fresh, 72), fluidTag("fluorine", 500)),
                "results", List.of(fluid(fluoride, 72))));
        recipe("salt_mixer", fresh + FUEL_SALT, 300, 20, Map.of(
                "fluid_inputs", List.of(fluid(fluoride, 72), fluid("flibe", 72)),
                "result", fluid(fresh + FUEL_SALT, 72)));
        for (String name : List.of(fresh, "depleted_" + fresh)) {
            recipe("electrolyzer", name + FLUORIDE, 1_600, 40, Map.of(
                    "fluid_input", fluid(name + FLUORIDE, 72),
                    "results", List.of(fluid(name, 72), fluid("fluorine", 500))));
            recipe("centrifuge", name + FUEL_SALT, 600, 10, Map.of(
                    "fluid_input", fluid(name + FUEL_SALT, 72),
                    "results", List.of(fluid(name + FLUORIDE, 72), fluid("flibe", 72))));
        }
    }

    private void recipe(String type, String name, int ticks, int energyPerTick, Map<String, ?> fields) {
        Map<String, Object> process = new HashMap<>(fields);
        process.put("ticks", ticks);
        process.put("energy_per_tick", energyPerTick);
        addRecipe(type, name, "nuclearcraft:" + type, process);
    }

    private void addRecipe(String folder, String name, String type, Map<String, ?> fields) {
        Map<String, Object> json = new HashMap<>(fields);
        json.put("type", type);
        recipes.put(NuclearCraft.id("recipes/" + folder + "/" + name + ".json"), json);
    }

    private void tag(String path, String... ids) {
        var location = ResourceLocation.tryBuild("forge", "tags/" + path + ".json");
        tags.computeIfAbsent(location, id -> new LinkedHashSet<>()).addAll(List.of(ids));
    }

    private static int whole(JsonObject json, String key) {
        double value = nonnegative(json, key);
        if (value != (int) value) {
            throw new JsonSyntaxException("Expected a nonnegative integer for fission " + key);
        }
        return (int) value;
    }

    private static Map<String, ?> item(String item, int count) {
        return Map.of("item", id(item), "count", count);
    }

    private static List<Map<String, String>> alloyIngredient(String material) {
        return List.of(ingot(material), itemTag("dusts/" + material));
    }

    private static Map<String, String> ingot(String material) {
        return itemTag("ingots/" + material);
    }

    private static Map<String, String> itemTag(String path) {
        return Map.of("tag", "forge:" + path);
    }

    private static Map<String, ?> counted(Object ingredient, int count) {
        return Map.of("ingredient", ingredient, "count", count);
    }

    private static Map<String, ?> fluid(String fluid, int amount) {
        return Map.of("fluid", id(fluid), "amount", amount);
    }

    private static Map<String, ?> fluidTag(String tag, int amount) {
        return Map.of("tag", "forge:" + tag, "amount", amount);
    }

    private static String id(String path) {
        return NuclearCraft.id(path).toString();
    }

    Map<ResourceLocation, byte[]> bake(PackType type) {
        Map<ResourceLocation, byte[]> output = new HashMap<>();
        if (type == PackType.SERVER_DATA) {
            recipes.forEach((path, json) -> output.put(path, bytes(json)));
            tags.forEach((path, entries) -> output.put(path, bytes(Map.of("values", entries))));
        } else {
            models.forEach((path, json) -> output.put(path, bytes(json)));
            if (!language.isEmpty()) {
                output.put(NuclearCraft.id("lang/en_us.json"), bytes(language));
            }
        }
        return Map.copyOf(output);
    }

    private static byte[] bytes(Object object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }

    static final class PackContent extends AbstractPackResources {
        private final PackType type;
        private final Map<ResourceLocation, byte[]> resources;
        private final Set<String> namespaces;

        PackContent(String id, PackType type, Map<ResourceLocation, byte[]> resources) {
            super(id, true);
            this.type = type;
            this.resources = resources;
            namespaces = resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            byte[] bytes = type == this.type ? resources.get(id) : null;
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != this.type) {
                return;
            }
            String prefix = path.isEmpty() ? "" : path + "/";
            resources.forEach((id, bytes) -> {
                if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                    output.accept(id, () -> new ByteArrayInputStream(bytes));
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == this.type ? namespaces : Set.of();
        }

        @Override
        public void close() {}
    }
}
