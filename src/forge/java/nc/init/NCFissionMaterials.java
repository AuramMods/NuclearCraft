package nc.init;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.radiation.RadiationSources;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

final class NCFissionMaterials {
    static final String FLUORIDE = "_fluoride";
    static final String FUEL_SALT = "_fluoride_flibe";
    private static final String PACK_ID = "nuclearcraft_fission_materials";

    private NCFissionMaterials() {}

    static void register(IEventBus bus) {
        var path = FMLPaths.CONFIGDIR.get().resolve("nuclearcraft-fission-materials.json");
        var resources = new FissionMaterialResources();
        try {
            JsonObject definitions;
            try (var reader = Files.newBufferedReader(path)) {
                definitions = GsonHelper.parse(reader);
            }
            for (var entry : GsonHelper.getAsJsonObject(definitions, "isotopes", new JsonObject()).entrySet()) {
                register(resources, entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey()), false);
            }
            for (var entry : GsonHelper.getAsJsonObject(definitions, "fuels", new JsonObject()).entrySet()) {
                register(resources, entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey()), true);
            }
        } catch (NoSuchFileException absent) {
            return;
        } catch (IOException | IllegalArgumentException | JsonSyntaxException error) {
            throw new IllegalArgumentException("Unable to register fission materials from " + path, error);
        }
        bus.addListener((AddPackFindersEvent event) -> {
            PackType type = event.getPackType();
            var content = resources.bake(type);
            if (content.isEmpty()) {
                return;
            }
            var title = Component.literal("NuclearCraft fission materials");
            int format = SharedConstants.getCurrentVersion().getPackVersion(type);
            var info = new Pack.Info(title, format, format, FeatureFlagSet.of(), false);
            event.addRepositorySource(packs -> packs.accept(Pack.create(PACK_ID, title, true,
                    id -> new GeneratedPackResources(id, type, content), info, type,
                    Pack.Position.BOTTOM, false, PackSource.BUILT_IN)));
        });
    }

    private static void register(FissionMaterialResources resources, String name, JsonObject json, boolean fuel) {
        validateName(name);
        String tag = GsonHelper.getAsString(json, "tag", name);
        validateName(tag);
        String label = GsonHelper.getAsString(json, "display_name", name.replace('_', ' '));
        String model = GsonHelper.getAsString(json, "model", "");
        if (!model.isEmpty() && ResourceLocation.tryParse(model) == null) {
            throw new JsonSyntaxException("Invalid model for " + name + ": " + model);
        }

        Set<Form> forms = EnumSet.noneOf(Form.class);
        if (json.has("forms")) {
            for (JsonElement value : GsonHelper.getAsJsonArray(json, "forms")) {
                var form = Form.valueOf(GsonHelper.convertToString(value, "form").toUpperCase(Locale.ROOT));
                if (!fuel && form == Form.TRISO) {
                    throw new JsonSyntaxException("TRISO is a fuel form: " + name);
                }
                forms.add(form);
            }
        }
        Integer freshColor = color(json, "fluid_color");
        Integer spentColor = color(json, "depleted_fluid_color");
        if (!fuel && spentColor != null) {
            throw new JsonSyntaxException("An isotope has no depleted fluid: " + name);
        }
        if (forms.isEmpty() && freshColor == null && spentColor == null) {
            throw new JsonSyntaxException("No forms or fluids specified for " + name);
        }

        double radiation = nonnegative(json, "radiation");
        double depletedRadiation = nonnegative(json, "depleted_radiation");
        var material = new Material(name, tag, fuel, Set.copyOf(forms));
        for (Form form : forms) {
            registerItem(resources, material, form, false, label, model, radiation);
            if (fuel && form.hasDepleted()) {
                registerItem(resources, material, form, true, label, model, depletedRadiation);
            }
        }
        if (freshColor != null || spentColor != null) {
            if (fuel) {
                registerFuelFluids(resources, name, label, freshColor == null ? 0 : freshColor, radiation);
                registerFuelFluids(resources, "depleted_" + name, "Depleted " + label,
                        spentColor == null ? 0 : spentColor, depletedRadiation);
            } else {
                NCFluids.fission(name, freshColor);
                addFluidResources(resources, name, "Molten " + label, radiation);
            }
        }
        resources.formRecipes(material, freshColor != null || spentColor != null);
        if (fuel) {
            resources.fuelRecipes(material, json, freshColor != null || spentColor != null);
        }
    }

    private static void registerItem(FissionMaterialResources resources, Material material, Form form, boolean depleted,
                                     String label, String model, double radiation) {
        String id = material.item(form, depleted);
        String tag = material.ingotTag(form, depleted);
        NCFissionFuels.registerItem(id);
        if (material.fuel()) {
            RadiationSources.registerFuelForm(id, tag, radiation);
        } else {
            RadiationSources.registerIsotopeForm(id, tag, radiation);
        }
        String parent = model.isEmpty()
                ? NuclearCraft.id("item/" + Material.itemId(material.fuel() ? "leu_235" : "uranium_235",
                        material.fuel(), form, depleted)).toString()
                : model;
        resources.addItem(id, tag, (depleted ? "Depleted " : "") + label + form.label(material.fuel()), parent);
    }

    private static void registerFuelFluids(FissionMaterialResources resources, String id, String label, int color, double rate) {
        NCFissionFluids.fuel(id, color);
        addFluidResources(resources, id, "Molten " + label, rate);
        addFluidResources(resources, id + FLUORIDE, label + " Fluoride", rate);
        addFluidResources(resources, id + FUEL_SALT, label + " Fuel Salt", rate);
    }

    private static void addFluidResources(FissionMaterialResources resources, String id, String label, double rate) {
        resources.addFluid(id, label);
        RadiationSources.registerFissionFluid(id, rate);
    }

    private static void validateName(String name) {
        if (!name.matches("[a-z0-9_]+")) {
            throw new JsonSyntaxException("Expected a lowercase fission material or tag name: " + name);
        }
    }

    private static Integer color(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        String color = GsonHelper.getAsString(json, key);
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            throw new JsonSyntaxException("Expected #RRGGBB for " + key);
        }
        return Integer.parseInt(color.substring(1), 16);
    }

    static double nonnegative(JsonObject json, String key) {
        double value = GsonHelper.getAsDouble(json, key, 0D);
        if (!Double.isFinite(value) || value < 0) {
            throw new JsonSyntaxException("Expected a finite nonnegative " + key);
        }
        return value;
    }

    enum Form {
        RAW("", ""),
        CARBIDE("_carbide", " Carbide"),
        TRISO("_triso", " TRISO"),
        OXIDE("_oxide", " Oxide"),
        NITRIDE("_nitride", " Nitride"),
        ZA("_za", " Zirconium Alloy");

        final String suffix;
        private final String label;

        Form(String suffix, String label) {
            this.suffix = suffix;
            this.label = label;
        }

        boolean hasDepleted() {
            return this != RAW && this != CARBIDE;
        }

        String label(boolean fuel) {
            return label + (!fuel ? " Ingot" : this == RAW || this == CARBIDE ? " Pellet" : "");
        }
    }

    record Material(String name, String tag, boolean fuel, Set<Form> forms) {
        String item(Form form, boolean depleted) {
            return itemId(name, fuel, form, depleted);
        }

        static String itemId(String name, boolean fuel, Form form, boolean depleted) {
            String prefix = depleted ? "depleted_" : "";
            if (!fuel) {
                return prefix + name + form.suffix + "_ingot";
            }
            return prefix + name + (form == Form.RAW || form == Form.CARBIDE ? "_pellet" : "") + form.suffix;
        }

        String ingotTag(Form form, boolean depleted) {
            return (depleted ? "depleted_" : "") + tag + form.suffix;
        }
    }
}
