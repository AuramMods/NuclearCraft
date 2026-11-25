package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCTurbine;
import nc.init.TurbineConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record TurbineRecipe(ResourceLocation id, FluidInput input,
                            FluidProduct product, double powerPerMb, double expansion, double spinUp,
                            ResourceLocation particle, double particleSpeed,
                            @Nullable Profile profile) implements Recipe<Container> {
    public static final ResourceLocation CLOUD = new ResourceLocation("minecraft", "cloud");

    public enum Profile {
        HIGH_PRESSURE_STEAM, LOW_PRESSURE_STEAM, STEAM, HOT_GAS
    }

    public double powerPerMb() {
        return profile != null && powerPerMb < 0D ? TurbineConfig.recipePower(profile.ordinal()) : powerPerMb;
    }

    public double expansion() {
        return profile != null && expansion < 0D ? TurbineConfig.recipeExpansion(profile.ordinal()) : expansion;
    }

    public double spinUp() {
        return profile != null && spinUp < 0D ? TurbineConfig.recipeSpinUp(profile.ordinal()) : spinUp;
    }

    public boolean accepts(FluidStack stack) {
        return input.accepts(stack);
    }


    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCTurbine.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCTurbine.RECIPES.get();
    }

    public static final class Serializer implements RecipeSerializer<TurbineRecipe> {
        @Override
        public TurbineRecipe fromJson(ResourceLocation id, JsonObject json) {
            FluidInput input = FluidInput.readRequired(json.get("input"), id);
            FluidProduct product = FluidProduct.fromJson(id, GsonHelper.getAsJsonObject(json, "output"));
            Profile profile = profile(json);
            double power = number(json, "power_per_mb", profile, 0D);
            double expansion = number(json, "expansion", profile, 1D);
            double spinUp = json.has("spin_up") ? number(json, "spin_up", profile, 0D) : profile != null ? -1D : 1D;
            ResourceLocation particle = json.has("particle") ? location(json, "particle") : CLOUD;
            double speed = GsonHelper.getAsDouble(json, "particle_speed", 5D / 116D);
            if (!Double.isFinite(speed) || speed < 0
                    || !(BuiltInRegistries.PARTICLE_TYPE.get(particle) instanceof SimpleParticleType)) {
                throw new JsonSyntaxException("Invalid turbine recipe " + id);
            }
            return new TurbineRecipe(id, input, product,
                    power, expansion, spinUp, particle, speed, profile);
        }

        private static double number(JsonObject json, String key, @Nullable Profile profile, double min) {
            if (profile != null && !json.has(key)) {
                return -1D;
            }
            double value = GsonHelper.getAsDouble(json, key);
            if (!Double.isFinite(value) || value < min) {
                throw new JsonSyntaxException("Invalid turbine " + key + " value " + value);
            }
            return value;
        }

        private static @Nullable Profile profile(JsonObject json) {
            if (!json.has("legacy_profile")) {
                return null;
            }
            String text = GsonHelper.getAsString(json, "legacy_profile");
            try {
                return Profile.valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new JsonSyntaxException("Unknown turbine legacy_profile " + text, error);
            }
        }

        private static ResourceLocation location(JsonObject json, String key) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, key));
            if (id == null) {
                throw new JsonSyntaxException("Invalid turbine fluid key " + json.get(key));
            }
            return id;
        }

        @Override
        public TurbineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new TurbineRecipe(id, FluidInput.fromNetwork(buffer),
                    FluidProduct.fromNetwork(buffer), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readResourceLocation(), buffer.readDouble(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TurbineRecipe recipe) {
            FluidInput.write(buffer, recipe.input);
            recipe.product.toNetwork(buffer);
            buffer.writeDouble(recipe.powerPerMb());
            buffer.writeDouble(recipe.expansion());
            buffer.writeDouble(recipe.spinUp());
            buffer.writeResourceLocation(recipe.particle);
            buffer.writeDouble(recipe.particleSpeed);
        }
    }
}
