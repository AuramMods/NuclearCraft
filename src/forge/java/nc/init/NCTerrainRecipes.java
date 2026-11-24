package nc.init;

import nc.NuclearCraft;
import nc.recipe.TerrainRecipe;
import nc.recipe.TerrainRecipe.Kind;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class NCTerrainRecipes {
    private static final Map<Kind, RegistryObject<RecipeType<TerrainRecipe>>> TYPES = new EnumMap<>(Kind.class);
    private static final Map<Kind, RegistryObject<RecipeSerializer<TerrainRecipe>>> SERIALIZERS = new EnumMap<>(Kind.class);

    private NCTerrainRecipes() {}

    public static void register() {
        for (Kind kind : Kind.values()) {
            TYPES.put(kind, NCRegistries.RECIPE_TYPES.register(kind.path(), () -> RecipeType.simple(NuclearCraft.id(kind.path()))));
            SERIALIZERS.put(kind, NCRegistries.RECIPE_SERIALIZERS.register(kind.path(), () -> new TerrainRecipe.Serializer(kind)));
        }
    }

    public static RecipeType<TerrainRecipe> type(Kind kind) {
        return TYPES.get(kind).get();
    }

    public static RecipeSerializer<TerrainRecipe> serializer(Kind kind) {
        return SERIALIZERS.get(kind).get();
    }
}
