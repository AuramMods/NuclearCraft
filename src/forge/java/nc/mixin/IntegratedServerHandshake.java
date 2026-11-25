package nc.mixin;

import net.minecraft.network.Connection;
import net.minecraftforge.network.HandshakeHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = HandshakeHandler.class, remap = false)
abstract class IntegratedServerHandshake {
    @Shadow @Final private Connection manager;

    // Older Forge builds can finish one packet early. Match only the subtraction in the
    // completion check so builds that already wait for the whole queue need no injection.
    @ModifyConstant(method = "tickServer()Z", constant = @Constant(intValue = 1), require = 0,
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 1),
                    to = @At(value = "JUMP", opcode = Opcodes.IF_ICMPLT)))
    private int waitForLastLocalLoginPacket(int remaining) {
        return manager.isMemoryConnection() ? 0 : remaining;
    }
}
