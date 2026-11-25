package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCFission;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.stream.Stream;

public record FissionMaterialRecipe(ResourceLocation id, Kind kind, ResourceLocation blockId, boolean tag,
                                    int priority, int flux, double efficiency, double reflectivity,
                                    @Nullable Profile profile) implements Recipe<Container> {
    public enum Kind {
        MODERATOR("fission_moderator"),
        REFLECTOR("fission_reflector");

        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
    }

    public enum Profile {
        GRAPHITE(Kind.MODERATOR), BERYLLIUM(Kind.MODERATOR), HEAVY_WATER(Kind.MODERATOR),
        BERYLLIUM_CARBON(Kind.REFLECTOR), LEAD_STEEL(Kind.REFLECTOR);

        private final Kind kind;
        Profile(Kind kind) { this.kind = kind; }
    }

    public int flux() {
        return profile == null || kind != Kind.MODERATOR ? flux : NCFission.moderatorProfile(profile).flux();
    }

    public double efficiency() {
        if (profile == null) {
            return efficiency;
        }
        return kind == Kind.MODERATOR ? NCFission.moderatorProfile(profile).efficiency()
                : NCFission.reflectorProfile(profile).efficiency();
    }

    public double reflectivity() {
        return profile == null || kind != Kind.REFLECTOR ? reflectivity : NCFission.reflectorProfile(profile).reflectivity();
    }

    public Stream<Block> blocks() {
        return tag ? BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, blockId)).stream()
                .flatMap(holders -> holders.stream().map(Holder::value))
                : Stream.ofNullable(ForgeRegistries.BLOCKS.getValue(blockId));
    }

    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, Ingredient.of(blocks().map(ItemStack::new)
                .filter(stack -> !stack.isEmpty())));
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCFission.materialSerializer(kind); }
    @Override public RecipeType<?> getType() { return NCFission.materialRecipes(kind); }

    public record Serializer(Kind kind) implements RecipeSerializer<FissionMaterialRecipe> {
        @Override
        public FissionMaterialRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject input = GsonHelper.getAsJsonObject(json, "input");
            boolean tag = input.has("tag");
            ResourceLocation blockId = ResourceLocation.tryParse(GsonHelper.getAsString(input, tag ? "tag" : "block"));
            if (blockId == null || tag == input.has("block")
                    || (!tag && (!ForgeRegistries.BLOCKS.containsKey(blockId)
                            || ForgeRegistries.BLOCKS.getValue(blockId).defaultBlockState().isAir()))) {
                throw new JsonSyntaxException("Invalid fission material block or tag for " + id);
            }
            int priority = GsonHelper.getAsInt(json, "priority", 0);
            if (json.has("legacy_profile")) {
                String name = GsonHelper.getAsString(json, "legacy_profile");
                Profile profile;
                try {
                    profile = Profile.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException error) {
                    throw new JsonSyntaxException("Unknown fission material profile for " + id, error);
                }
                if (profile.kind != kind || json.has("flux") || json.has("efficiency") || json.has("reflectivity")) {
                    throw new JsonSyntaxException("A legacy fission material profile replaces the explicit stats for " + id);
                }
                return new FissionMaterialRecipe(id, kind, blockId, tag, priority, 0, 0D, 0D, profile);
            }
            int flux = kind == Kind.MODERATOR ? GsonHelper.getAsInt(json, "flux", 0) : 0;
            double efficiency = GsonHelper.getAsDouble(json, "efficiency", kind == Kind.MODERATOR ? 1D : 0D);
            double reflectivity = kind == Kind.REFLECTOR ? GsonHelper.getAsDouble(json, "reflectivity", 0D) : 0D;
            if (flux < 0 || !Double.isFinite(efficiency) || efficiency < 0D || efficiency > 255D
                    || !Double.isFinite(reflectivity) || reflectivity < 0D || reflectivity > 1D
                    || (kind == Kind.MODERATOR ? json.has("reflectivity") : json.has("flux"))) {
                throw new JsonSyntaxException("Invalid fission material stats for " + id);
            }
            return new FissionMaterialRecipe(id, kind, blockId, tag, priority, flux, efficiency, reflectivity, null);
        }

        @Override
        public FissionMaterialRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FissionMaterialRecipe(id, kind, buffer.readResourceLocation(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readNullable(input -> input.readEnum(Profile.class)));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FissionMaterialRecipe recipe) {
            buffer.writeResourceLocation(recipe.blockId);
            buffer.writeBoolean(recipe.tag);
            buffer.writeVarInt(recipe.priority);
            buffer.writeVarInt(recipe.flux);
            buffer.writeDouble(recipe.efficiency);
            buffer.writeDouble(recipe.reflectivity);
            buffer.writeNullable(recipe.profile, FriendlyByteBuf::writeEnum);
        }
    }
}
