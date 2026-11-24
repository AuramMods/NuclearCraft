package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.LegacyFissionFuelConfig;
import nc.init.LegacyFissionFuelConfig.Profile;
import nc.init.LegacyFissionFuelConfig.Selection;
import nc.init.LegacyFissionFuelConfig.Setting;
import nc.init.NCFission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public interface FissionFuelStats {
    Parameters parameters();
    double runtimeTicks();

    default int heat() {
        return (int) parameters().number(Setting.HEAT_GENERATION, parameters().heat);
    }

    default double efficiency() {
        return parameters().number(Setting.EFFICIENCY, parameters().efficiency);
    }

    default double radiation() {
        return parameters().number(Setting.RADIATION, parameters().radiation);
    }

    default int criticality() {
        return (int) parameters().number(Setting.CRITICALITY, parameters().criticality);
    }

    default int intrinsicFlux() {
        return (int) parameters().number(Setting.INTRINSIC_FLUX, parameters().intrinsicFlux);
    }

    default boolean selfPriming() {
        var data = parameters();
        return data.legacy == null ? data.selfPriming : data.legacy.selfPriming(data.selfPriming);
    }

    default double decayFactor() {
        return parameters().number(Setting.DECAY_FACTOR, parameters().decayFactor);
    }

    default int runtimeHeat() {
        return (int) (heat() * NCFission.fuelHeatMultiplier());
    }

    default double runtimeEfficiency() {
        return Math.min(Double.MAX_VALUE, efficiency() * NCFission.fuelEfficiencyMultiplier());
    }

    default double runtimeRadiation() {
        return Math.min(Double.MAX_VALUE, radiation() * NCFission.fuelRadiationMultiplier());
    }

    record Parameters(int heat, double efficiency, int criticality, int intrinsicFlux, boolean selfPriming,
                      double decayFactor, double radiation, @Nullable Selection legacy) {
        double number(Setting setting, double fallback) {
            return legacy == null ? fallback : legacy.number(setting, fallback);
        }

        static Parameters fromJson(ResourceLocation id, JsonObject json) {
            int heat = GsonHelper.getAsInt(json, "heat");
            double efficiency = GsonHelper.getAsDouble(json, "efficiency");
            int criticality = GsonHelper.getAsInt(json, "criticality");
            int intrinsicFlux = GsonHelper.getAsInt(json, "intrinsic_flux", 0);
            double decay = GsonHelper.getAsDouble(json, "decay_factor", 0);
            double radiation = GsonHelper.getAsDouble(json, "radiation", 0);
            if (heat < 0 || !nonnegative(efficiency) || criticality < 0 || intrinsicFlux < 0
                    || !nonnegative(decay) || !nonnegative(radiation)) {
                throw new JsonSyntaxException("Invalid fission fuel values for " + id);
            }
            return new Parameters(heat, efficiency, criticality, intrinsicFlux,
                    GsonHelper.getAsBoolean(json, "self_priming", false), decay, radiation, readLegacy(id, json));
        }

        private static boolean nonnegative(double value) {
            return Double.isFinite(value) && value >= 0D;
        }

        private static @Nullable Selection readLegacy(ResourceLocation id, JsonObject json) {
            if (!json.has("legacy_fuel")) {
                return null;
            }
            JsonObject binding = GsonHelper.getAsJsonObject(json, "legacy_fuel");
            String family = GsonHelper.getAsString(binding, "family");
            double index = GsonHelper.getAsDouble(binding, "index");
            if (index != (int) index) {
                throw new JsonSyntaxException("Expected a whole legacy_fuel index for " + id);
            }
            try {
                var profile = Profile.valueOf(GsonHelper.getAsString(binding, "profile", "standard").toUpperCase(Locale.ROOT));
                return new Selection(LegacyFissionFuelConfig.Family.valueOf(family.toUpperCase(Locale.ROOT)), (int) index, profile);
            } catch (IllegalArgumentException error) {
                throw new JsonSyntaxException("Invalid legacy_fuel binding for " + id, error);
            }
        }

        static Parameters fromNetwork(FriendlyByteBuf buffer) {
            return new Parameters(buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readNullable(input -> new Selection(input.readEnum(LegacyFissionFuelConfig.Family.class),
                            input.readVarInt(), input.readEnum(Profile.class))));
        }

        void toNetwork(FriendlyByteBuf buffer) {
            buffer.writeVarInt(heat);
            buffer.writeDouble(efficiency);
            buffer.writeVarInt(criticality);
            buffer.writeVarInt(intrinsicFlux);
            buffer.writeBoolean(selfPriming);
            buffer.writeDouble(decayFactor);
            buffer.writeDouble(radiation);
            buffer.writeNullable(legacy, (output, selection) -> {
                output.writeEnum(selection.family());
                output.writeVarInt(selection.index());
                output.writeEnum(selection.profile());
            });
        }
    }
}
