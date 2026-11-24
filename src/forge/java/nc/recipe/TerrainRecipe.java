package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCTerrainRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TerrainRecipe(ResourceLocation id, Kind kind, Input input, BlockState result, double threshold, int priority)
        implements Recipe<Container> {
    public enum Kind {
        MUTATION("radiation_block_mutation"),
        PURIFICATION("radiation_block_purification"),
        WASTELAND("wasteland_block_mapping");

        private final String path;

        Kind(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    public record Input(Block block, TagKey<Block> tag, Map<String, String> properties) {
        public Input {
            properties = Map.copyOf(properties);
        }

        public boolean matches(BlockState state) {
            if (tag == null ? !state.is(block) : !state.is(tag)) {
                return false;
            }
            for (var entry : properties.entrySet()) {
                var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null || !propertyMatches(state, property, entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static <T extends Comparable<T>> boolean propertyMatches(BlockState state, Property<T> property, String value) {
            return property.getName(state.getValue(property)).equals(value);
        }
    }

    public static List<TerrainRecipe> ordered(Level level, Kind kind) {
        return level.getRecipeManager().getAllRecipesFor(NCTerrainRecipes.type(kind)).stream()
                .sorted(Comparator.comparingInt(TerrainRecipe::priority).reversed().thenComparing(TerrainRecipe::id))
                .toList();
    }

    @Override public boolean matches(Container input, Level level) { return false; }
    @Override public ItemStack assemble(Container input, RegistryAccess access) { return getResultItem(access); }
    @Override public ItemStack getResultItem(RegistryAccess access) { return new ItemStack(result.getBlock()); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCTerrainRecipes.serializer(kind); }
    @Override public RecipeType<?> getType() { return NCTerrainRecipes.type(kind); }

    public static final class Serializer implements RecipeSerializer<TerrainRecipe> {
        private final Kind kind;

        public Serializer(Kind kind) {
            this.kind = kind;
        }

        @Override
        public TerrainRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject source = GsonHelper.getAsJsonObject(json, "input");
            if (source.has("tag") == source.has("block")) {
                throw new JsonSyntaxException("Expected one block or block tag for " + id);
            }
            Map<String, String> filters = properties(source);
            Input input;
            if (source.has("tag")) {
                input = new Input(null, TagKey.create(Registries.BLOCK, readId(source, "tag")), filters);
            } else {
                Block block = readBlock(source);
                withProperties(block.defaultBlockState(), filters);
                input = new Input(block, null, filters);
            }

            JsonObject product = GsonHelper.getAsJsonObject(json, "result");
            BlockState state = withProperties(readBlock(product).defaultBlockState(), properties(product));
            double defaultThreshold = kind == Kind.MUTATION ? Double.MAX_VALUE : 0D;
            double threshold = GsonHelper.getAsDouble(json, "radiation_threshold", defaultThreshold);
            if (!Double.isFinite(threshold) || threshold < 0D) {
                throw new JsonSyntaxException("Invalid radiation threshold for " + id);
            }
            return new TerrainRecipe(id, kind, input, state, threshold, GsonHelper.getAsInt(json, "priority", 0));
        }

        private static Map<String, String> properties(JsonObject json) {
            Map<String, String> result = new HashMap<>();
            if (json.has("properties")) {
                GsonHelper.getAsJsonObject(json, "properties").entrySet().forEach(entry ->
                        result.put(entry.getKey(), GsonHelper.convertToString(entry.getValue(), entry.getKey())));
            }
            return result;
        }

        private static Block readBlock(JsonObject json) {
            ResourceLocation id = readId(json, "block");
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
                throw new JsonSyntaxException("Unknown block " + id);
            }
            return block;
        }

        private static ResourceLocation readId(JsonObject json, String field) {
            String value = GsonHelper.getAsString(json, field);
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                throw new JsonSyntaxException("Invalid block or tag ID " + value);
            }
            return id;
        }

        private static BlockState withProperties(BlockState state, Map<String, String> properties) {
            for (var entry : properties.entrySet()) {
                var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null) {
                    throw new JsonSyntaxException("Unknown property " + entry.getKey() + " for " + state.getBlock());
                }
                state = withProperty(state, property, entry.getValue());
            }
            return state;
        }

        private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
            T parsed = property.getValue(value).orElseThrow(() ->
                    new JsonSyntaxException("Invalid value " + value + " for " + property.getName()));
            return state.setValue(property, parsed);
        }

        @Override
        public TerrainRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            boolean tagged = buffer.readBoolean();
            ResourceLocation key = buffer.readResourceLocation();
            Map<String, String> properties = buffer.readMap(buf -> buf.readUtf(128), buf -> buf.readUtf(128));
            Input input = tagged
                    ? new Input(null, TagKey.create(Registries.BLOCK, key), properties)
                    : new Input(ForgeRegistries.BLOCKS.getValue(key), null, properties);
            return new TerrainRecipe(id, kind, input, Block.stateById(buffer.readVarInt()), buffer.readDouble(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TerrainRecipe recipe) {
            Input input = recipe.input();
            buffer.writeBoolean(input.tag() != null);
            buffer.writeResourceLocation(input.tag() == null ? ForgeRegistries.BLOCKS.getKey(input.block()) : input.tag().location());
            buffer.writeMap(input.properties(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
            buffer.writeVarInt(Block.getId(recipe.result()));
            buffer.writeDouble(recipe.threshold());
            buffer.writeVarInt(recipe.priority());
        }
    }
}
