package nc.multiblock.computer;

import dan200.computercraft.api.ForgeComputerCraftAPI;
import net.minecraftforge.common.util.LazyOptional;
import nc.multiblock.fission.FissionPeripheral;
import nc.multiblock.hx.ExchangerPeripheral;
import nc.multiblock.machine.ProcessorPeripheral;
import nc.multiblock.turbine.TurbinePeripheral;

public final class ComputerPeripherals {
    private ComputerPeripherals() {}

    public static void register() {
        ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) -> {
            if (!(level.getBlockEntity(pos) instanceof ComputerPort port)) {
                return LazyOptional.empty();
            }
            return port.peripheral(() -> switch (port.kind()) {
                case FISSION -> new FissionPeripheral(port);
                case EXCHANGER -> new ExchangerPeripheral(port);
                case TURBINE -> new TurbinePeripheral(port);
                case MACHINE -> new ProcessorPeripheral(port);
            });
        });
    }
}
