package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Rate;
import nc.init.NCRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class MelterMaterialRecipes {
    private static final String MOLTEN = "molten_";
    private static final Set<String> EXCLUDED =
            Set.of("coal", "redstone", "glowstone", "prismarine", "obsidian", "silicon", "marshmallow");
    private static final List<String> LEGACY_METALS = List.of("boron_10", "boron_11", "lithium_6", "lithium_7",
            "hard_carbon", "manganese_dioxide", "alugentum", "hastelloy", "lead_platinum", "barium_oxide",
            "nickel_oxide", "polydimethylsilylene", "polymethylsilylene_methylene", "electrical_steel",
            "energetic_alloy", "vibrant_alloy", "redstone_alloy", "conductive_iron", "pulsating_iron", "dark_steel",
            "soularium", "end_steel", "construction_alloy", "crude_steel", "crystalline_alloy", "melodic_alloy",
            "stellar_alloy", "crystalline_pink_slime", "energetic_silver", "vivid_alloy", "dilithium", "manasteel",
            "terrasteel", "elementium", "refined_obsidian", "refined_glowstone", "psi", "starmetal", "base_essence",
            "inferium", "prudentium", "intermedium", "superium", "supremium", "strontium_90", "molybdenum",
            "ruthenium_106", "caesium_137", "promethium_147", "europium_155");
    private static final Map<RecipeManager, Map<Item, MelterRecipe>> SERVER_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MelterMaterialRecipes() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        var recipes = event.getServerResources().getRecipeManager();
        var context = event.getConditionContext();
        event.addListener((barrier, resources, preparation, reload, background, game) ->
                barrier.wait(Unit.INSTANCE).thenRunAsync(() -> {
                    var catalog = new Catalog(recipes, context.getAllTags(Registries.ITEM), context.getAllTags(Registries.FLUID));
                    SERVER_RECIPES.put(recipes, Map.copyOf(catalog.discover()));
                }, game));
    }

    public static @Nullable MelterRecipe find(RecipeManager recipes, ItemStack stack) {
        MelterRecipe recipe = SERVER_RECIPES.getOrDefault(recipes, Map.of()).get(stack.getItem());
        return recipe != null && enabled(recipe) ? recipe : null;
    }

    public static List<MelterRecipe> fromSyncedTags(RecipeManager recipes) {
        var explicitInputs = alwaysMatchedItems(recipes);
        var discovered = new Catalog(recipes, tags(BuiltInRegistries.ITEM), tags(BuiltInRegistries.FLUID)).discover();
        var visible = new LinkedHashMap<MelterRecipe, List<ItemStack>>();
        discovered.forEach((item, recipe) -> {
            if (enabled(recipe) && !explicitInputs.contains(item)) {
                visible.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(item.getDefaultInstance());
            }
        });
        return visible.entrySet().stream().map(entry -> {
            var recipe = entry.getKey();
            var input = new ItemProcessorRecipe.Input(Ingredient.of(entry.getValue().stream()), 1);
            return new MelterRecipe(recipe.id(), input, recipe.fluidProduct(), recipe.ticks(),
                    recipe.energyPerTick(), recipe.radiation(), recipe.defaults());
        }).toList();
    }

    private static Set<Item> alwaysMatchedItems(RecipeManager recipes) {
        var items = new HashSet<Item>();
        for (MelterRecipe recipe : recipes.getAllRecipesFor(NCRegistries.MELTING.get())) {
            var input = recipe.input();
            var alternatives = input instanceof ItemInput.Alternatives group
                    ? group.choices() : List.of((ItemProcessorRecipe.Input) input);
            for (var alternative : alternatives) {
                // A counted or NBT-sensitive recipe can leave the generated fallback reachable.
                if (alternative.count() == 1 && alternative.ingredient().isSimple()) {
                    alternative.alternatives().forEach(stack -> items.add(stack.getItem()));
                }
            }
        }
        return items;
    }

    private static boolean enabled(MelterRecipe recipe) {
        return NCItemProcessors.defaultRecipes(Rate.MELTER)
                && (!recipe.id().getPath().endsWith("/ore") || NCItemProcessors.oreProcessing());
    }

    private static <T> Map<ResourceLocation, List<Holder<T>>> tags(Registry<T> registry) {
        return registry.getTags().collect(Collectors.toMap(pair -> pair.getFirst().location(),
                pair -> pair.getSecond().stream().toList()));
    }

    private record Catalog(RecipeManager recipes, Map<ResourceLocation, ? extends Collection<Holder<Item>>> items,
                           Map<ResourceLocation, ? extends Collection<Holder<Fluid>>> fluids) {
        Map<Item, MelterRecipe> discover() {
            var result = new LinkedHashMap<Item, MelterRecipe>();
            optionalRecipes(result);
            var materials = new TreeSet<>(LEGACY_METALS);
            materials.add("silicon_carbide");
            fluids.keySet().stream().filter(id -> id.getNamespace().equals("forge") || id.getNamespace().equals("c"))
                    .map(ResourceLocation::getPath).filter(path -> path.startsWith(MOLTEN))
                    .map(path -> path.substring(MOLTEN.length())).forEach(materials::add);
            materials.removeAll(EXCLUDED);
            materials.remove("cesium_137");
            materials.forEach(material -> addMaterial(result, material));
            return result;
        }

        private void addMaterial(Map<Item, MelterRecipe> result, String material) {
            String fluidName = material.equals("silicon_carbide") ? "sic_vapor" : material;
            Fluid fluid = BuiltInRegistries.FLUID.getOptional(NuclearCraft.id(fluidName))
                    .orElseGet(() -> source(MOLTEN + fluidName));
            if (fluid == null) {
                return;
            }
            Set<Item> ingots = materialItems("ingots/", material);
            Set<Item> dusts = materialItems("dusts/", material);
            Set<Item> gems = materialItems("gems/", material);
            boolean metal = LEGACY_METALS.contains(material) || material.equals("silicon_carbide")
                    || (!ingots.isEmpty() && !dusts.isEmpty());
            if (!metal && (gems.isEmpty() || dusts.isEmpty())) {
                return;
            }
            String path = "materials/" + material + "/";
            Set<Item> units = metal ? ingots : gems;
            units.addAll(dusts);
            if (recipes.byKey(NuclearCraft.id("melter/" + fluidName)).isEmpty()) {
                add(result, path + "unit", fluid, metal ? 144 : 666, 800, 40, units);
            }
            add(result, path + "ore", fluid, metal ? 288 : 1332, 1000, 60, materialItems("ores/", material));
            Set<Item> nuggets = materialItems("nuggets/", material);
            nuggets.addAll(materialItems("tiny_dusts/", material));
            nuggets.addAll(materialItems("dusts/tiny/", material));
            add(result, path + "nugget", fluid, metal ? 16 : 74, 800D / 9D, 40, nuggets);
            if (metal) {
                add(result, path + "block", fluid, 1296, 7200, 40, materialItems("storage_blocks/", material));
            }
        }

        private void optionalRecipes(Map<Item, MelterRecipe> result) {
            add(result, "brine", source("brine"), 15, 200, 20, taggedItems("dusts/salt", "salt"));
            Fluid glass = preferredSource("tconstruct:molten_glass", "molten_glass");
            add(result, "sand", glass, 1000, 1200, 60, taggedItems("sand"));
            add(result, "glass", glass, 1000, 1200, 60, taggedItems("glass"));
            Fluid clay = preferredSource("tconstruct:molten_clay", "molten_clay");
            add(result, "clay_blocks", clay, 576, 1600, 60, Set.of(Items.CLAY, Items.BRICKS, Items.TERRACOTTA));
            add(result, "clay_items", clay, 144, 400, 60, Set.of(Items.CLAY_BALL, Items.BRICK));
            add(result, "stone", preferredSource("tconstruct:seared_stone", "molten_stone"), 72, 1200, 60,
                    taggedItems("stone", "cobblestone"));
            var dirt = taggedItems("dirt");
            dirt.add(Items.DIRT);
            dirt.add(Items.GRASS_BLOCK);
            add(result, "dirt", source("molten_dirt"), 144, 800, 40, dirt);
            for (String material : List.of("pyrotheum", "cryotheum", "aerotheum", "petrotheum")) {
                Fluid fluid = BuiltInRegistries.FLUID.getOptional(NuclearCraft.id(material))
                        .orElseGet(() -> source(material));
                add(result, material, fluid, 250, 800, 40, taggedItems("dusts/" + material));
            }
        }

        private Set<Item> materialItems(String prefix, String material) {
            return switch (material) {
                case "caesium_137" -> taggedItems(prefix + material, prefix + "cesium_137");
                case "starmetal" -> taggedItems(prefix + material, prefix + "astral_starmetal");
                default -> taggedItems(prefix + material);
            };
        }

        private Set<Item> taggedItems(String... paths) {
            Set<Item> result = new LinkedHashSet<>();
            for (String path : paths) {
                for (String namespace : List.of("forge", "c")) {
                    var entries = items.get(ResourceLocation.tryBuild(namespace, path));
                    if (entries != null) {
                        entries.stream().map(Holder::value).filter(item -> item != Items.AIR).forEach(result::add);
                    }
                }
            }
            return result;
        }

        private @Nullable Fluid preferredSource(String tag, String fallback) {
            Fluid fluid = firstSource(ResourceLocation.tryParse(tag));
            return fluid != null ? fluid : source(fallback);
        }

        private @Nullable Fluid source(String path) {
            Fluid fluid = firstSource(ResourceLocation.tryBuild("forge", path));
            return fluid != null ? fluid : firstSource(ResourceLocation.tryBuild("c", path));
        }

        private @Nullable Fluid firstSource(ResourceLocation id) {
            var entries = fluids.get(id);
            return entries == null ? null : entries.stream().map(Holder::value)
                    .filter(fluid -> fluid.isSource(fluid.defaultFluidState())).findFirst().orElse(null);
        }

        private void add(Map<Item, MelterRecipe> result, String path, @Nullable Fluid fluid,
                         int amount, double ticks, int power, Set<Item> inputs) {
            ResourceLocation id = NuclearCraft.id("melter/" + path);
            if (fluid != null && !inputs.isEmpty() && recipes.byKey(id).isEmpty()) {
                var input = new ItemProcessorRecipe.Input(Ingredient.of(inputs.stream().map(Item::getDefaultInstance)), 1);
                var recipe = new MelterRecipe(id, input, FluidProduct.fixed(new FluidStack(fluid, amount)), ticks, power, 0D, null);
                inputs.forEach(item -> result.putIfAbsent(item, recipe));
            }
        }
    }
}
