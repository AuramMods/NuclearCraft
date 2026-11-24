package nc.multiblock.fission;

import nc.init.NCFission;
import nc.recipe.FissionFuelStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

final class FissionFuelDecay {
    private final BlockEntity part;
    private @Nullable SolidFissionController controller;
    private long lastUpdate = Long.MIN_VALUE;
    private double baseHeat;
    private double factor;
    private double heat;
    private double iodine;
    private double poison;

    FissionFuelDecay(BlockEntity part) {
        this.part = part;
    }

    static @Nullable FissionFuelDecay of(@Nullable BlockEntity entity) {
        if (entity instanceof FissionFuelCell cell) {
            return cell.decay();
        }
        return entity instanceof SaltVessel vessel ? vessel.decay() : null;
    }

    void bind(SolidFissionController controller) {
        this.controller = controller;
    }

    void unbind(SolidFissionController controller) {
        if (this.controller == controller) {
            this.controller = null;
        }
    }

    void remember(FissionFuelStats recipe, int effectiveBaseHeat) {
        double nextFactor = Math.min(1D, recipe.decayFactor());
        if (baseHeat != effectiveBaseHeat || factor != nextFactor) {
            baseHeat = effectiveBaseHeat;
            factor = nextFactor;
            part.setChanged();
        }
    }

    double baseHeat() {
        return baseHeat;
    }

    double heating() {
        return baseHeat * heat;
    }

    double criticality(FissionFuelStats recipe) {
        return recipe.criticality() * (1D - Math.min(1D, recipe.decayFactor()) + poison);
    }

    boolean hasFractions() {
        return heat > 0 || iodine > 0 || poison > 0;
    }

    void tickUnmanaged(ServerLevel level) {
        if (!hasFractions()) {
            return;
        }
        if (!NCFission.fuelDecayEnabled() || controller == null || controller.isRemoved()) {
            tick(level.getGameTime(), NCFission.decaySettings(), false);
        }
    }

    void tick(long gameTime, @Nullable FissionDecaySettings settings, boolean processing) {
        if (gameTime == lastUpdate || (!processing && !hasFractions())) {
            return;
        }
        lastUpdate = gameTime;
        double oldHeat = heat, oldIodine = iodine, oldPoison = poison;
        if (settings == null) {
            heat = iodine = poison = 0;
        } else {
            double heatLimit = factor * settings.heat().equilibrium();
            double iodineLimit = factor * settings.iodine().equilibrium();
            double poisonLimit = factor * settings.poison().equilibrium();
            boolean growHeat = processing && heat <= heatLimit;
            boolean growIodine = processing && iodine <= iodineLimit;
            boolean growPoison = processing && poison <= poisonLimit;
            double heatLoss = growHeat ? 0 : loss(settings, settings.heat(), heat, heatLimit);
            double iodineLoss = growIodine ? 0 : loss(settings, settings.iodine(), iodine, iodineLimit);
            heat = growHeat ? grow(settings, settings.heat(), heat, heatLimit) : heat - heatLoss;
            iodine = growIodine ? grow(settings, settings.iodine(), iodine, iodineLimit)
                    : iodine - iodineLoss + settings.heat().daughterMultiplier() * heatLoss;
            poison = growPoison ? grow(settings, settings.poison(), poison, poisonLimit)
                    : poison - loss(settings, settings.poison(), poison, poisonLimit)
                        + settings.iodine().daughterMultiplier() * iodineLoss;
        }
        if (oldHeat != heat || oldIodine != iodine || oldPoison != poison) {
            part.setChanged();
        }
    }

    private static double grow(FissionDecaySettings settings, FissionDecaySettings.Step step,
                               double fraction, double limit) {
        double production = settings.exponential() * (limit - fraction) + settings.linear() * limit;
        return production == 0 ? fraction : Math.min(limit, fraction + production / step.buildupTicks());
    }

    private static double loss(FissionDecaySettings settings, FissionDecaySettings.Step step,
                               double fraction, double limit) {
        double reduction = settings.exponential() * fraction + settings.linear() * limit;
        return reduction == 0 ? 0 : Math.min(fraction, reduction / step.lifetimeTicks());
    }

    void save(CompoundTag tag) {
        if (baseHeat > 0 || factor > 0 || hasFractions()) {
            tag.putDouble("decayProcessHeat", baseHeat);
            tag.putDouble("baseProcessDecayFactor", factor);
            tag.putDouble("decayHeatFraction", heat);
            tag.putDouble("iodineFraction", iodine);
            tag.putDouble("poisonFraction", poison);
        }
    }

    void load(CompoundTag tag) {
        baseHeat = Math.min(Integer.MAX_VALUE, fraction(tag, "decayProcessHeat"));
        factor = Math.min(1D, fraction(tag, "baseProcessDecayFactor"));
        heat = fraction(tag, "decayHeatFraction");
        iodine = fraction(tag, "iodineFraction");
        poison = fraction(tag, "poisonFraction");
    }

    private static double fraction(CompoundTag tag, String key) {
        double value = tag.getDouble(key);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
