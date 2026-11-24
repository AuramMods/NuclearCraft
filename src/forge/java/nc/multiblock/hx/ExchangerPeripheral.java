package nc.multiblock.hx;

import dan200.computercraft.api.lua.LuaFunction;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.MachinePeripheral;

import java.util.List;

public final class ExchangerPeripheral extends MachinePeripheral<ExchangerController> {
    public ExchangerPeripheral(ComputerPort port) {
        super(port, ExchangerController.class);
    }

    @Override
    public String getType() {
        return "nc_heat_exchanger";
    }

    @LuaFunction(mainThread = true)
    public boolean isExchangerOn() { return read(ExchangerController::computerOn, false); }

    @LuaFunction(mainThread = true)
    public int getActiveNetworkCount() { return read(ExchangerController::computerActiveCircuits, 0); }

    @LuaFunction(mainThread = true)
    public int getActiveTubeCount() { return read(ExchangerController::computerActiveTubes, 0); }

    @LuaFunction(mainThread = true)
    public double getShellSpeedMultiplier() { return read(ExchangerController::computerShellSpeed, 0D); }

    @LuaFunction(mainThread = true)
    public double getShellInputRate() { return read(ExchangerController::computerShellInputRate, 0); }

    @LuaFunction(mainThread = true)
    public double getHeatTransferRate() { return read(ExchangerController::computerHeatRate, 0D); }

    @LuaFunction(mainThread = true)
    public double getMeanTempDiff() { return read(ExchangerController::computerTemperatureDifference, 0D); }

    @LuaFunction(mainThread = true)
    public int getNumberOfNetworks() { return read(ExchangerController::computerNetworkCount, 0); }

    @LuaFunction(mainThread = true)
    public List<List<Object>> getNetworkStats() { return read(ExchangerController::computerNetworks, List.of()); }

    @LuaFunction(mainThread = true)
    public int getNumberOfTubes() { return read(ExchangerController::computerTubeCount, 0); }

    @LuaFunction(mainThread = true)
    public List<List<Object>> getTubeStats() { return read(ExchangerController::computerTubes, List.of()); }

    @LuaFunction(mainThread = true)
    public void activate() { control(controller -> controller.computerActivate(true)); }

    @LuaFunction(mainThread = true)
    public void deactivate() { control(controller -> controller.computerActivate(false)); }

    @LuaFunction(mainThread = true)
    public void clearAllMaterial() { control(ExchangerController::computerClear); }
}
