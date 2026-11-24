package nc.multiblock.turbine;

import nc.init.TurbineConfig;
import nc.recipe.TurbineRecipe;
import nc.util.ValueTracker;
import net.minecraft.nbt.CompoundTag;

final class TurbineDrive {
    private final ValueTracker rateTracker = new ValueTracker();
    private double rawPower;
    private double ratedPower;
    private double throughput;
    private double tension;

    long ratedFlow(TurbineLayout layout) {
        return (long) TurbineConfig.mbPerBlade() * layout.bladeArea() * layout.bladeSets();
    }

    long tick(TurbineLayout layout, TurbineRecipe recipe, int inputRate) {
        double driveBalance = 0.5D * (Math.min(1D, (1D + 2D * layout.dynamo().inletCoils()) / layout.bearings().size())
                + Math.min(1D, (1D + 2D * layout.dynamo().outletCoils()) / layout.bearings().size()));
        if (inputRate > 0) {
            double inertia = layout.inertia() * Math.sqrt(driveBalance);
            double spinUp = Math.min(Double.MAX_VALUE, recipe.spinUp() * TurbineConfig.globalSpinUp());
            double targetPower = Math.min(Double.MAX_VALUE, inputRate * recipe.powerPerMb());
            double weight = spinUp / (inertia + spinUp);
            rawPower = Math.min(Double.MAX_VALUE, rawPower * (1D - weight) + targetPower * weight);
        } else {
            double inertia = layout.inertia() / Math.sqrt(driveBalance);
            double coast = inertia / (TurbineConfig.spinDown() * (inertia + Math.log1p(inertia) + 1D));
            rawPower = Math.min(Double.MAX_VALUE, rawPower * coast);
        }

        long rated = ratedFlow(layout);
        if (recipe != null) {
            ratedPower = Math.min(Double.MAX_VALUE, rated * recipe.powerPerMb());
        }
        double tensionChange = inputRate <= 0 ? 0D : (double) inputRate / rated - 1D - TurbineConfig.tensionLeniency();
        tensionChange = tensionChange < 0D ? -Math.sqrt(-tensionChange)
                : tensionChange / Math.max(1D, TurbineConfig.tensionThroughputFactor() - 1D);
        tension += Math.min(1D, tensionChange)
                / (1200D * layout.bearings().size());
        tension = Math.max(0D, tension);

        double previousThroughput = throughput;
        throughput = rateTracker.update(inputRate);
        if (recipe == null) {
            return 0;
        }
        int stageCount = layout.stages().size();
        double expansion = 1D;
        double efficiency = 0D;
        double minBlade = Double.POSITIVE_INFINITY;
        double maxBlade = 1D;
        double minStator = 1D;
        double idealExpansion = recipe.expansion();
        for (int depth = 0; depth < stageCount; depth++) {
            TurbineRotorBlock stage = layout.stages().get(depth);
            double coefficient = stage.expansion();
            double intermediate = expansion * Math.sqrt(coefficient);
            expansion *= coefficient;
            if (stage.blade()) {
                double ideal = Math.pow(idealExpansion, (depth + 0.5D) / stageCount);
                efficiency += stage.efficiency() * expansionFactor(ideal, intermediate);
                minBlade = Math.min(minBlade, coefficient);
                maxBlade = Math.max(maxBlade, coefficient);
            } else {
                minStator = Math.min(minStator, coefficient);
            }
        }
        efficiency /= layout.bladeSets();
        int maxLength = TurbineConfig.maxSize();
        int mbPerBlade = TurbineConfig.mbPerBlade();
        int minimumBladeArea = 4 * Math.max(1, TurbineConfig.minSize() - 2);
        double idealLength = idealExpansion <= 1D || maxBlade <= 1D ? maxLength
                : Math.ceil(Math.log(idealExpansion) / Math.log(maxBlade));
        double throughputRatio = Math.min(1D, (previousThroughput + idealLength * minimumBladeArea * mbPerBlade) / rated);
        double threshold = TurbineConfig.throughputThreshold();
        double floor = TurbineConfig.throughputFloor();
        double throughputEfficiency = throughputRatio >= threshold ? 1D
                : floor + (1D - floor) * Math.sin(throughputRatio * Math.PI / (2D * threshold));

        double effectiveMax = maxLength;
        if (minBlade > 1D && minStator < 1D) {
            effectiveMax = Math.ceil(Math.max(1D, Math.min(maxLength, (Math.log(idealExpansion)
                    - maxLength * Math.log(minStator)) / (Math.log(minBlade) - Math.log(minStator)))));
        }
        double rate = Math.min(inputRate, rated);
        double lengthBonus = rate / ((double) mbPerBlade * layout.bladeArea() * effectiveMax);
        double areaBonus = Math.sqrt(2D * rate / ((double) mbPerBlade * stageCount * maxLength * effectiveMax));
        double bonus = 1D + TurbineConfig.powerBonus() * Math.pow(lengthBonus * areaBonus, 2D / 3D);
        double multiplier = layout.dynamo().conductivity() * efficiency * expansionFactor(idealExpansion, expansion)
                * throughputEfficiency * bonus;
        return (long) (rawPower * multiplier);
    }

    private static double expansionFactor(double ideal, double actual) {
        return Math.min(ideal / actual, actual / ideal);
    }

    double tension() {
        return tension;
    }

    double speedRatio() {
        return ratedPower > 0 ? rawPower / ratedPower : 0D;
    }

    double throughput() {
        return throughput;
    }

    void clearRotation() {
        rawPower = ratedPower = throughput = 0D;
        rateTracker.reset();
    }

    void save(CompoundTag tag) {
        tag.putDouble("raw_power", rawPower);
        tag.putDouble("rated_power", ratedPower);
        tag.putDouble("tension", tension);
    }

    void load(CompoundTag tag) {
        rawPower = finitePositive(tag.getDouble("raw_power"));
        ratedPower = finitePositive(tag.getDouble("rated_power"));
        tension = finitePositive(tag.getDouble("tension"));
    }

    private static double finitePositive(double value) {
        return Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }
}
