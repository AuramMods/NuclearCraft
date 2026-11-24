package nc.multiblock.machine;

import dan200.computercraft.api.lua.LuaFunction;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.ComputerValues;
import nc.multiblock.computer.MachinePeripheral;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class ProcessorPeripheral extends MachinePeripheral<MachineController> {
    public ProcessorPeripheral(ComputerPort port) {
        super(port, MachineController.class);
    }

    @Override public String getType() { return "nc_machine"; }

    @LuaFunction(mainThread = true)
    public final boolean isMachineOn() {
        return getIsProcessing();
    }

    @LuaFunction(mainThread = true)
    public final boolean getIsProcessing() {
        return process(MachineController.ProcessStatus::processing, false);
    }

    @LuaFunction(mainThread = true)
    public final double getCurrentTime() {
        return process(MachineController.ProcessStatus::progress, 0D);
    }

    @LuaFunction(mainThread = true)
    public final double getBaseProcessTime() {
        return process(MachineController.ProcessStatus::duration, 1D);
    }

    @LuaFunction(mainThread = true)
    public final long getBaseProcessPower() {
        return process(MachineController.ProcessStatus::power, 0L);
    }

    @LuaFunction(mainThread = true) public final List<List<Object>> getItemInputs() { return items(false); }
    @LuaFunction(mainThread = true) public final List<List<Object>> getItemOutputs() { return items(true); }
    @LuaFunction(mainThread = true) public final List<List<Object>> getFluidInputs() { return fluids(false); }
    @LuaFunction(mainThread = true) public final List<List<Object>> getFluidOutputs() { return fluids(true); }

    @LuaFunction(mainThread = true) public final void haltProcess() { control(machine -> machine.setHalted(true)); }
    @LuaFunction(mainThread = true) public final void resumeProcess() { control(machine -> machine.setHalted(false)); }
    @LuaFunction(mainThread = true) public final void clearAllMaterial() { control(MachineController::clear); }

    private <T> T process(Function<MachineController.ProcessStatus, T> query, T fallback) {
        return read(machine -> query.apply(machine.computerProcess()), fallback);
    }

    private List<List<Object>> items(boolean output) {
        return read(machine -> {
            var stacks = machine.processItems();
            if (stacks == null) {
                return List.of();
            }
            int start = output ? machine.itemCount(false) : 0;
            return IntStream.range(0, machine.itemCount(output)).mapToObj(slot ->
                    ComputerValues.item(stacks.getStackInSlot(start + slot))).toList();
        }, List.of());
    }

    private List<List<Object>> fluids(boolean output) {
        return read(machine -> IntStream.range(0, machine.tankCount(output))
                .mapToObj(slot -> ComputerValues.fluid(machine.tankFor(output, slot).getFluid())).toList(), List.of());
    }
}
