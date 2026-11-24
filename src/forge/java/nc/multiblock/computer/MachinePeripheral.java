package nc.multiblock.computer;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.lua.LuaFunction;
import nc.multiblock.Cuboid;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class MachinePeripheral<T extends BlockEntity & ComputerPort.Controller> implements IPeripheral {
    private final ComputerPort port;
    private final Class<T> controllerType;

    protected MachinePeripheral(ComputerPort port, Class<T> controllerType) {
        this.port = port;
        this.controllerType = controllerType;
    }

    protected final @Nullable T controller() {
        return port.controller(controllerType);
    }

    protected final <R> R read(Function<T, R> query, R fallback) {
        T controller = controller();
        return controller == null ? fallback : query.apply(controller);
    }

    protected final void control(Consumer<T> operation) {
        T controller = controller();
        if (controller != null) {
            operation.accept(controller);
        }
    }

    private @Nullable Cuboid bounds() {
        T controller = controller();
        return controller == null ? null : controller.computerBounds();
    }

    @LuaFunction(mainThread = true)
    public final boolean isComplete() {
        return controller() != null;
    }

    @LuaFunction(mainThread = true)
    public final int getLengthX() {
        var bounds = bounds();
        return bounds == null ? 0 : bounds.xLength() - 2;
    }

    @LuaFunction(mainThread = true)
    public final int getLengthY() {
        var bounds = bounds();
        return bounds == null ? 0 : bounds.yLength() - 2;
    }

    @LuaFunction(mainThread = true)
    public final int getLengthZ() {
        var bounds = bounds();
        return bounds == null ? 0 : bounds.zLength() - 2;
    }

    @Override
    public final ComputerPort getTarget() {
        return port;
    }

    @Override
    public final boolean equals(@Nullable IPeripheral other) {
        return other instanceof MachinePeripheral<?> peripheral && peripheral.port == port;
    }
}
