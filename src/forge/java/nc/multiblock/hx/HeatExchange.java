package nc.multiblock.hx;

import nc.init.HeatExchangerConfig;
import nc.multiblock.hx.ExchangerController.Circuit;
import nc.recipe.HeatExchangerRecipe;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class HeatExchange {
    record Activity(int circuits, int tubes, double heatRate, double shellSpeed, double temperatureDifference) {
        static final Activity IDLE = new Activity(0, 0, 0D, 0D, 0D);
    }

    private record Batch(Circuit circuit, HeatExchangerRecipe recipe, FluidStack product, double heat, int batches) {
        static Batch find(Circuit circuit, Function<FluidStack, HeatExchangerRecipe> findRecipe) {
            HeatExchangerRecipe recipe = findRecipe.apply(circuit.input.getFluid());
            if (recipe == null) {
                return null;
            }
            FluidStack product = recipe.output();
            int batches = circuit.capacity(recipe.input().amount(), product);
            double heat = recipe.heatRequired();
            if (batches <= 0 || heat <= 0D || !Double.isFinite(heat)) {
                return null;
            }
            return new Batch(circuit, recipe, product, heat, batches);
        }

        double demand() {
            return batches * heat - circuit.credit(recipe.id(), heat);
        }

        void exchange(double rate) {
            circuit.process(recipe.id(), recipe.input().amount(), product, heat, batches, rate);
        }
    }

    private record Proposal(ExchangerLayout.Network network, Batch tube, double shared,
                            double tubeMultiplier, double shellMultiplier, double temperatureDifference) {}

    private HeatExchange() {}

    static Activity tick(List<ExchangerLayout.Network> networks, Circuit shell, Map<BlockPos, Circuit> circuits,
                         Function<FluidStack, HeatExchangerRecipe> findRecipe) {
        Batch shellBatch = Batch.find(shell, findRecipe);
        if (shellBatch == null) {
            return Activity.IDLE;
        }
        List<Proposal> proposals = new ArrayList<>();
        double shellDemand = 0D;
        for (ExchangerLayout.Network network : networks) {
            Batch tube = Batch.find(circuits.get(network.tubeAnchor()), findRecipe);
            if (tube == null || tube.recipe().heating() == shellBatch.recipe().heating()) {
                continue;
            }
            double difference = meanDifference(network, tube.recipe(), shellBatch.recipe());
            double shared = difference * tube.recipe().flowMultiplier(network.tubeFlow())
                    * shellBatch.recipe().flowMultiplier(network.shellFlow());
            double tubeMultiplier = tube.recipe().heating() ? network.retainedConductivity() : network.conductivity();
            double shellMultiplier = tube.recipe().heating() ? network.conductivity() : network.retainedConductivity();
            if (shared <= 0D || tubeMultiplier <= 0D || shellMultiplier <= 0D) {
                continue;
            }
            double offered = Math.min(shared, tube.demand() / tubeMultiplier);
            if (offered > 0D) {
                proposals.add(new Proposal(network, tube, offered, tubeMultiplier, shellMultiplier, difference));
                shellDemand += offered * shellMultiplier;
            }
        }
        if (shellDemand <= 0D) {
            return Activity.IDLE;
        }

        // All tube circuits draw from the same shell budget. Scale their proposals before changing any tanks.
        double shellRate = Math.min(1D, shellBatch.demand() / shellDemand);
        double heatRate = 0D;
        double shellExchange = 0D;
        double totalDifference = 0D;
        int usefulTubes = 0;
        for (Proposal proposal : proposals) {
            double exchanged = proposal.shared() * shellRate;
            proposal.tube().exchange(exchanged * proposal.tubeMultiplier());
            shellExchange += exchanged * proposal.shellMultiplier();
            heatRate += exchanged * proposal.network().conductivity();
            usefulTubes += proposal.network().usefulTubes();
            totalDifference += proposal.temperatureDifference() * proposal.network().usefulTubes();
        }
        shellBatch.exchange(shellExchange);
        return new Activity(proposals.size(), usefulTubes, heatRate, shellExchange / shellBatch.heat(),
                usefulTubes == 0 ? 0D : totalDifference / usefulTubes);
    }

    private static double meanDifference(ExchangerLayout.Network network, HeatExchangerRecipe tube, HeatExchangerRecipe shell) {
        int shellStart = network.counterflow() ? shell.outputTemperature() : shell.inputTemperature();
        int shellEnd = network.counterflow() ? shell.inputTemperature() : shell.outputTemperature();
        double start = (double) tube.inputTemperature() - shellStart;
        double end = (double) tube.outputTemperature() - shellEnd;
        double sum = start + end;
        if (sum == 0D || tube.heating() != (sum < 0D) || start * end < 0D) {
            return 0D;
        }
        return HeatExchangerConfig.meanDifference(start, end);
    }
}
