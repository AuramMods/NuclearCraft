package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import nc.multiblock.quantum.QuantumPartEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

public final class QuantumQubitRenderer implements BlockEntityRenderer<QuantumPartEntity> {
    @Override
    public void render(QuantumPartEntity qubit, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!qubit.part().isQubit() || qubit.getLevel() == null) {
            return;
        }
        long ticks = qubit.getLevel().getGameTime();
        double phase = ((ticks % 60) + partialTick) * 6D;
        float pulse = 6F * Math.max(0F, 1F - Math.max(0F, ticks - qubit.colorTick() + partialTick) / 600F);
        float red = wave(phase) + (qubit.result() < 0 ? pulse : 0F);
        float green = wave(phase + 120D);
        float blue = wave(phase + 240D) + (qubit.result() > 0 ? pulse : 0F);
        float brightest = Math.max(red, Math.max(green, blue));
        LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                0.25, 0.25, 0.25, 0.75, 0.75, 0.75, red / brightest, green / brightest, blue / brightest, 1F);
    }

    private static float wave(double degrees) {
        double angle = degrees % 360D;
        double ramp = 2D - Math.min(angle, 360D - angle) / 60D;
        return (float) Math.max(0D, Math.min(1D, ramp));
    }
}
