package nc.multiblock.turbine;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.MachinePeripheral;

import java.util.List;

public final class TurbinePeripheral extends MachinePeripheral<TurbineController> {
    public TurbinePeripheral(ComputerPort port) {
        super(port, TurbineController.class);
    }

    @Override public String getType() { return "nc_turbine"; }

    @LuaFunction(mainThread = true) public final boolean isTurbineOn() { return read(TurbineController::computerOn, false); }
    @LuaFunction(mainThread = true) public final boolean isProcessing() { return read(TurbineController::computerProcessing, false); }
    @LuaFunction(mainThread = true) public final long getEnergyStored() { return read(TurbineController::computerEnergy, 0L); }
    @LuaFunction(mainThread = true) public final long getEnergyCapacity() { return read(TurbineController::computerCapacity, 0L); }
    @LuaFunction(mainThread = true) public final long getPower() { return read(TurbineController::computerPower, 0L); }
    @LuaFunction(mainThread = true) public final double getCoilConductivity() { return read(TurbineController::computerConductivity, 0D); }
    @LuaFunction(mainThread = true) public final String getFlowDirection() { return read(TurbineController::computerFlow, "null"); }
    @LuaFunction(mainThread = true) public final double getTotalExpansionLevel() { return read(TurbineController::computerTotalExpansion, 0D); }
    @LuaFunction(mainThread = true) public final double getIdealTotalExpansionLevel() { return read(TurbineController::computerIdealExpansion, 0D); }
    @LuaFunction(mainThread = true) public final int getInputRate() { return read(TurbineController::computerInputRate, 0); }
    @LuaFunction(mainThread = true) public final int getNumberOfDynamoParts() { return read(TurbineController::computerDynamoCount, 0); }
    @LuaFunction(mainThread = true) public final List<List<Object>> getDynamoPartStats() {
        return read(TurbineController::computerDynamoParts, List.of());
    }

    @LuaFunction(mainThread = true) public final MethodResult getExpansionLevels() {
        return MethodResult.of(read(TurbineController::computerExpansionLevels, List.of()).toArray());
    }

    @LuaFunction(mainThread = true) public final MethodResult getIdealExpansionLevels() {
        return MethodResult.of(read(TurbineController::computerIdealLevels, List.of()).toArray());
    }

    @LuaFunction(mainThread = true) public final MethodResult getBladeEfficiencies() {
        return MethodResult.of(read(TurbineController::computerBladeEfficiencies, List.of()).toArray());
    }

    @LuaFunction(mainThread = true) public final void activate() { control(turbine -> turbine.computerActivate(true)); }
    @LuaFunction(mainThread = true) public final void deactivate() { control(turbine -> turbine.computerActivate(false)); }
    @LuaFunction(mainThread = true) public final void clearAllMaterial() { control(TurbineController::computerClear); }
}
