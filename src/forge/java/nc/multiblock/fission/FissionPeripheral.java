package nc.multiblock.fission;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.ComputerValues;
import nc.multiblock.computer.MachinePeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class FissionPeripheral extends MachinePeripheral<SolidFissionController> {
    public FissionPeripheral(ComputerPort port) {
        super(port, SolidFissionController.class);
    }

    @Override
    public String getType() {
        return "nc_salt_fission_reactor";
    }

    @LuaFunction(mainThread = true)
    public boolean isReactorOn() { return read(SolidFissionController::computerOn, false); }

    @LuaFunction(mainThread = true)
    public long getHeatStored() { return read(SolidFissionController::computerHeat, 0L); }

    @LuaFunction(mainThread = true)
    public long getHeatCapacity() { return read(SolidFissionController::heatCapacity, 0L); }

    @LuaFunction(mainThread = true)
    public double getTemperature() { return read(SolidFissionController::computerTemperature, 0D); }

    @LuaFunction(mainThread = true)
    public long getCoolingRate() { return read(c -> c.computerCore().heatStats().cooling(), 0L); }

    @LuaFunction(mainThread = true)
    public long getRawHeatingRate() { return read(c -> c.computerCore().heatStats().heating(), 0L); }

    @LuaFunction(mainThread = true)
    public double getMeanEfficiency() { return read(c -> c.computerCore().heatStats().efficiency(), 0D); }

    @LuaFunction(mainThread = true)
    public double getMeanHeatMultiplier() { return read(c -> c.computerCore().heatStats().heatMultiplier(), 0D); }

    @LuaFunction(mainThread = true)
    public int getNumberOfIrradiators() { return count(SolidFissionLayout::irradiators); }

    @LuaFunction(mainThread = true)
    public int getNumberOfCells() { return count(parts(FissionVariant.SOLID, SolidFissionLayout::cells)); }

    @LuaFunction(mainThread = true)
    public int getNumberOfSinks() { return count(parts(FissionVariant.SOLID, SolidFissionLayout::sinks)); }

    @LuaFunction(mainThread = true)
    public int getNumberOfChambers() { return count(parts(FissionVariant.PEBBLE, SolidFissionLayout::cells)); }

    @LuaFunction(mainThread = true)
    public int getNumberOfCoolers() { return count(parts(FissionVariant.PEBBLE, SolidFissionLayout::sinks)); }

    @LuaFunction(mainThread = true)
    public int getNumberOfVessels() { return count(parts(FissionVariant.SALT, SolidFissionLayout::cells)); }

    @LuaFunction(mainThread = true)
    public int getNumberOfHeaters() { return count(parts(FissionVariant.SALT, SolidFissionLayout::sinks)); }

    @LuaFunction(mainThread = true)
    public int getNumberOfShields() { return count(SolidFissionLayout::shields); }

    @LuaFunction(mainThread = true)
    public MethodResult getIrradiatorStats() { return stats(SolidFissionLayout::irradiators); }

    @LuaFunction(mainThread = true)
    public MethodResult getCellStats() { return stats(parts(FissionVariant.SOLID, SolidFissionLayout::cells)); }

    @LuaFunction(mainThread = true)
    public MethodResult getSinkStats() { return stats(parts(FissionVariant.SOLID, SolidFissionLayout::sinks)); }

    @LuaFunction(mainThread = true)
    public MethodResult getChamberStats() { return stats(parts(FissionVariant.PEBBLE, SolidFissionLayout::cells)); }

    @LuaFunction(mainThread = true)
    public MethodResult getCoolerStats() { return stats(parts(FissionVariant.PEBBLE, SolidFissionLayout::sinks)); }

    @LuaFunction(mainThread = true)
    public MethodResult getVesselStats() { return stats(parts(FissionVariant.SALT, SolidFissionLayout::cells)); }

    @LuaFunction(mainThread = true)
    public MethodResult getHeaterStats() { return stats(parts(FissionVariant.SALT, SolidFissionLayout::sinks)); }

    @LuaFunction(mainThread = true)
    public MethodResult getShieldStats() { return stats(SolidFissionLayout::shields); }

    @LuaFunction(mainThread = true)
    public int getNumberOfClusters() { return read(c -> c.computerCore().clusterStats().size(), 0); }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getClusterInfo(int id) {
        return read(c -> cluster(c, id), Map.of());
    }

    @LuaFunction(mainThread = true)
    public void clearAllMaterial() { control(SolidFissionController::computerClear); }

    private int count(Function<SolidFissionLayout, List<BlockPos>> parts) {
        return read(c -> parts.apply(c.computerLayout()).size(), 0);
    }

    private MethodResult stats(Function<SolidFissionLayout, List<BlockPos>> parts) {
        return read(c -> MethodResult.of(parts.apply(c.computerLayout()).stream()
                .map(pos -> component(c, pos)).toArray()), MethodResult.of());
    }

    private static Function<SolidFissionLayout, List<BlockPos>> parts(FissionVariant variant,
                                                                      Function<SolidFissionLayout, List<BlockPos>> select) {
        return layout -> layout.variant() == variant ? select.apply(layout) : List.of();
    }

    private static Map<String, Object> cluster(SolidFissionController controller, int id) {
        var clusters = controller.computerCore().clusterStats();
        if (id < 0 || id >= clusters.size()) {
            return Map.of();
        }
        var cluster = clusters.get(id);
        Map<String, Map<List<Integer>, Map<String, Object>>> components = new LinkedHashMap<>();
        for (BlockPos pos : cluster.positions()) {
            var role = FissionPartBlock.role(controller.getLevel().getBlockState(pos));
            String key = switch (role) {
                case HEAT_SINK -> "sink";
                case PEBBLE_CHAMBER -> "chamber";
                case PEBBLE_COOLER -> "cooler";
                case SALT_HEATER -> "heater";
                default -> role.name().toLowerCase(Locale.ROOT);
            };
            components.computeIfAbsent(key, unused -> new LinkedHashMap<>())
                    .put(ComputerValues.position(pos), component(controller, pos));
        }
        return Map.of("heat_capacity", cluster.heatCapacity(), "heat_stored", cluster.heatStored(),
                "cooling", cluster.cooling(), "raw_heating", cluster.heating(), "components", components);
    }

    private static Map<String, Object> component(SolidFissionController controller, BlockPos pos) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        SolidFissionCore core = controller.computerCore();
        var entity = level.getBlockEntity(pos);
        if (entity instanceof FissionFuelCell cell) {
            var items = controller.inventory();
            var fuel = cell.reservedFuel().isEmpty() ? items.getStackInSlot(0) : cell.reservedFuel();
            return fuelStats(core.fuelStats(pos), ComputerValues.item(fuel),
                    ComputerValues.item(items.getStackInSlot(1)), cell.computerProgress());
        }
        if (entity instanceof SaltVessel vessel) {
            var fuel = vessel.reservedForInspection();
            if (fuel.isEmpty()) {
                fuel = fluid(controller.existingSaltFuelTank(vessel.filter(), false));
            }
            return fuelStats(core.fuelStats(pos), ComputerValues.fluid(fuel),
                    ComputerValues.fluid(fluid(controller.existingSaltFuelTank(vessel.outputCircuit(), true))),
                    vessel.computerProgress());
        }
        if (entity instanceof FissionIrradiator irradiator) {
            return irradiator.computerStats(level);
        }
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof SolidHeatSinkBlock sink) {
            boolean valid = core.isCooling(pos);
            return Map.of("type", sink.material().name().toLowerCase(Locale.ROOT),
                    "cooling", valid ? NCFission.cooling(sink.material()) : 0, "is_valid", valid);
        }
        if (state.getBlock() instanceof SaltHeaterBlock heater) {
            var coolant = heater.coolant();
            return coolerStats(core.coolerStats(pos), coolant.name().toLowerCase(Locale.ROOT),
                    controller.existingSaltCoolantTank(coolant, false), controller.existingSaltCoolantTank(coolant, true),
                    controller.heaterProgress(pos, coolant), NCSaltFission.cooling(coolant), core.isCooling(pos));
        }
        if (state.getBlock() instanceof PebbleCoolerBlock cooler) {
            var gas = cooler.gas();
            return coolerStats(core.coolerStats(pos), gas.name().toLowerCase(Locale.ROOT),
                    controller.existingGasTank(gas, false), controller.existingGasTank(gas, true),
                    controller.gasProgress(pos), NCPebbleFission.cooling(gas), core.isCooling(pos));
        }
        if (state.getBlock() instanceof FissionShieldBlock) {
            var shield = core.shieldStats(pos);
            return Map.of("effective_heating", shield == null ? 0D : shield.heating(),
                    "is_shielding", state.getValue(FissionWallBlock.ACTIVE), "flux", shield == null ? 0L : shield.flux());
        }
        return Map.of();
    }

    private static Map<String, Object> fuelStats(@Nullable SolidFissionCore.FuelStats stats,
                                                 List<Object> fuel, List<Object> depleted, double process) {
        var info = new LinkedHashMap<String, Object>();
        info.put("fuel", fuel);
        info.put("depleted_fuel", depleted);
        info.put("current_time", process);
        info.put("effective_heating", stats == null ? 0D : stats.heating());
        info.put("heat_multiplier", stats == null ? 0 : stats.heatMultiplier());
        info.put("is_processing", stats != null && stats.processing());
        info.put("base_process_time", stats == null ? 1D : stats.ticks());
        info.put("base_process_heat", stats == null ? 0 : stats.baseHeat());
        info.put("base_process_efficiency", stats == null ? 0D : stats.baseEfficiency());
        info.put("base_process_criticality", stats == null ? 0 : stats.recipe().criticality());
        info.put("intrinsic_flux", stats == null ? 0 : stats.recipe().intrinsicFlux());
        info.put("base_process_decay_factor", stats == null ? 0D : stats.recipe().decayFactor());
        info.put("is_self_priming", stats != null && stats.recipe().selfPriming());
        info.put("base_process_radiation", stats == null ? 0D : stats.radiation());
        info.put("is_primed", stats != null && stats.primed());
        info.put("efficiency", stats == null ? 0D : stats.efficiency());
        info.put("flux", stats == null ? 0L : stats.flux());
        return info;
    }

    private static Map<String, Object> coolerStats(@Nullable SolidFissionCore.CoolerStats stats,
                                                   String type, @Nullable FluidTank in, @Nullable FluidTank out,
                                                   double progress, int cooling, boolean valid) {
        return Map.of("coolant", ComputerValues.fluid(fluid(in)), "hot_coolant", ComputerValues.fluid(fluid(out)),
                "type", type, "cooling", valid ? cooling : 0, "speed_multiplier", stats == null ? 0D : stats.speed(),
                "is_processing", stats != null && stats.processing(), "current_time", progress, "base_process_time", 1D);
    }

    private static FluidStack fluid(@Nullable FluidTank tank) {
        return tank == null ? FluidStack.EMPTY : tank.getFluid();
    }
}
