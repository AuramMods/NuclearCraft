package nc.multiblock.turbine;

import nc.recipe.TurbineRecipe;
import nc.util.ValueTracker;
import net.minecraft.nbt.CompoundTag;

final class TurbineDrive {
    private static final int MB_PER_BLADE = 100;
    private static final double TENSION_LENIENCY = 0.05D;

    private final ValueTracker rateTracker = new ValueTracker();
    private double rawPower;
    private double ratedPower;
    private double throughput;
    private double tension;

    int ratedFlow(TurbineLayout layout) {
        return MB_PER_BLADE * layout.bladeArea() * layout.bladeSets();
    }

    long tick(TurbineLayout layout, TurbineRecipe recipe, int inputRate) {
        double driveBalance = 0.5D * (Math.min(1D, (1D + 2D * layout.dynamo().inletCoils()) / layout.bearings().size())
                + Math.min(1D, (1D + 2D * layout.dynamo().outletCoils()) / layout.bearings().size()));
        if (inputRate > 0) {
            double inertia = layout.inertia() * Math.sqrt(driveBalance);
            rawPower = (inertia * rawPower + inputRate * recipe.powerPerMb() * recipe.spinUp()) / (inertia + recipe.spinUp());
        } else {
            double inertia = layout.inertia() / Math.sqrt(driveBalance);
            rawPower = inertia * rawPower / (inertia + Math.log1p(inertia) + 1D);
        }

        int rated = ratedFlow(layout);
        if (recipe != null) {
            ratedPower = finitePositive(rated * recipe.powerPerMb());
        }
        double tensionChange = inputRate <= 0 ? 0D : (double) inputRate / rated - 1D - TENSION_LENIENCY;
        tension += (tensionChange < 0D ? -Math.sqrt(-tensionChange) : Math.min(1D, tensionChange))
                / (1200D * layout.bearings().size());
        tension = Math.max(0D, tension);

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
        for (int depth = 0; depth < stageCount; depth++) {
            TurbinePartBlock.Part stage = layout.stages().get(depth);
            double intermediate = expansion * Math.sqrt(stage.expansion);
            expansion *= stage.expansion;
            if (stage.efficiency > 0) {
                double ideal = Math.pow(recipe.expansion(), (depth + 0.5D) / stageCount);
                efficiency += stage.efficiency * expansionFactor(ideal, intermediate);
                minBlade = Math.min(minBlade, stage.expansion);
                maxBlade = Math.max(maxBlade, stage.expansion);
            } else {
                minStator = Math.min(minStator, stage.expansion);
            }
        }
        efficiency /= layout.bladeSets();
        int maxLength = TurbinePartBlock.MAX_INTERIOR_LENGTH;
        double idealLength = recipe.expansion() <= 1D ? maxLength
                : Math.ceil(Math.log(recipe.expansion()) / Math.log(maxBlade));
        double fill = Math.min(0.75D, (throughput + idealLength * 4 * MB_PER_BLADE) / rated);
        double throughput = 0.5D + 0.5D * Math.sin(fill * Math.PI / 1.5D);

        double effectiveMax = maxLength;
        if (minStator < 1D) {
            effectiveMax = Math.ceil(Math.max(1D, Math.min(maxLength, (Math.log(recipe.expansion())
                    - maxLength * Math.log(minStator)) / (Math.log(minBlade) - Math.log(minStator)))));
        }
        double rate = Math.min(inputRate, rated);
        double lengthBonus = rate / (MB_PER_BLADE * layout.bladeArea() * effectiveMax);
        double areaBonus = Math.sqrt(2D * rate / (MB_PER_BLADE * stageCount * maxLength * effectiveMax));
        double bonus = 1D + Math.pow(lengthBonus * areaBonus, 2D / 3D);
        return (long) (rawPower * layout.dynamo().conductivity() * efficiency
                * expansionFactor(recipe.expansion(), expansion) * throughput * bonus);
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
