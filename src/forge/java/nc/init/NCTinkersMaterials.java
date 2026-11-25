package nc.init;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NCTinkersMaterials {
    private static final List<String> MATERIALS = List.of("boron", "tough", "hard_carbon", "boron_nitride",
            "thorium", "uranium", "magnesium", "chocolate");
    private static final List<String> TOOL_STATS = List.of("head", "handle", "binding", "limb", "grip");
    private static final List<String> ARMOR_STATS = List.of("plating_helmet", "plating_chestplate",
            "plating_leggings", "plating_boots", "plating_shield", "maille");
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> TOOLS;
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> ARMOR;

    static {
        var config = new ForgeConfigSpec.Builder();
        TOOLS = defineFlags(config, "register_tic_tool", "tool and bow");
        ARMOR = defineFlags(config, "register_conarm_armor", "armor and shield");
        SPEC = config.build();
    }

    private NCTinkersMaterials() {}

    private static ForgeConfigSpec.ConfigValue<List<Boolean>> defineFlags(ForgeConfigSpec.Builder config, String key,
                                                                          String parts) {
        return config.comment("Enable Tinkers' " + parts + " parts. Order: " + String.join(", ", MATERIALS))
                .worldRestart().define(key, Collections.nCopies(MATERIALS.size(), true),
                        value -> value instanceof List<?> list && list.size() == MATERIALS.size()
                                && list.stream().allMatch(Boolean.class::isInstance));
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "nuclearcraft-tinkers-materials.toml");
        context.getModEventBus().addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> CraftingHelper.register(MaterialEnabled.SERIALIZER)));
        if (ModList.get().isLoaded("tconstruct")) {
            context.getModEventBus().addListener(NCTinkersMaterials::addStatsPack);
            StockRecipeAdjustments.register("tconstruct", (recipe, additions) -> NCTinkersSmeltery.adjustRecipe(recipe));
        }
    }

    private static void addStatsPack(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        if (type != PackType.SERVER_DATA) {
            return;
        }
        var title = Component.literal("NuclearCraft Tinkers' material settings");
        int format = SharedConstants.getCurrentVersion().getPackVersion(type);
        var info = new Pack.Info(title, format, format, FeatureFlagSet.of(), false);
        event.addRepositorySource(packs -> packs.accept(Pack.create("nuclearcraft_tinkers_settings", title, true,
                id -> new GeneratedPackResources(id, type, disabledStats()), info, type, Pack.Position.TOP, true,
                PackSource.BUILT_IN)));
    }

    private static Map<ResourceLocation, byte[]> disabledStats() {
        Map<ResourceLocation, byte[]> result = new HashMap<>();
        for (int index = 0; index < MATERIALS.size(); index++) {
            var stats = new JsonObject();
            if (!enabled(TOOLS, index)) {
                TOOL_STATS.forEach(stat -> stats.add("tconstruct:" + stat, JsonNull.INSTANCE));
            }
            if (!enabled(ARMOR, index)) {
                ARMOR_STATS.forEach(stat -> stats.add("tconstruct:" + stat, JsonNull.INSTANCE));
            }
            if (stats.size() > 0) {
                var overlay = new JsonObject();
                // Tinkers' merges these files and removes inherited stats explicitly set to null.
                overlay.add("stats", stats);
                result.put(NuclearCraft.id("tinkering/materials/stats/" + MATERIALS.get(index) + ".json"),
                        overlay.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return Map.copyOf(result);
    }

    private static boolean enabled(ForgeConfigSpec.ConfigValue<List<Boolean>> config, int index) {
        return !SPEC.isLoaded() || config.get().get(index);
    }

    private record MaterialEnabled(int index) implements ICondition {
        private static final ResourceLocation ID = NuclearCraft.id("tinkers_material_enabled");
        private static final IConditionSerializer<MaterialEnabled> SERIALIZER = new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, MaterialEnabled condition) {
                json.addProperty("material", MATERIALS.get(condition.index));
            }

            @Override
            public MaterialEnabled read(JsonObject json) {
                String material = GsonHelper.getAsString(json, "material");
                int index = MATERIALS.indexOf(material);
                if (index < 0) {
                    throw new JsonSyntaxException("Unknown stock Tinkers' material: " + material);
                }
                return new MaterialEnabled(index);
            }

            @Override
            public ResourceLocation getID() {
                return ID;
            }
        };

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public boolean test(IContext context) {
            return enabled(TOOLS, index) || enabled(ARMOR, index);
        }
    }
}
