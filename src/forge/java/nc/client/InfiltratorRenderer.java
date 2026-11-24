package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import nc.init.NCMachines;
import nc.multiblock.machine.Distiller;
import nc.multiblock.machine.Infiltrator;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;

public final class InfiltratorRenderer implements BlockEntityRenderer<Infiltrator> {
    private static final float INSET = 1F / 64F;
    private final MachineVolumeSounds<Infiltrator> sounds = new MachineVolumeSounds<>(NCMachines.INFILTRATOR_RUN,
            machine -> machine.renderVolume() == null ? 0 : machine.renderVolume().soundVolume());

    @Override public void render(Infiltrator machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var volume = machine.renderVolume();
        if (volume == null || volume.capacity() <= 0) return;
        sounds.play(machine, machine.getBlockPos().offset(volume.origin()), volume.width(), volume.height(), volume.depth());
        var pressure = volume.pressure();
        if (pressure.isEmpty()) return;
        boolean gas = Distiller.isGas(pressure);
        float fraction = Math.min(1, (float) pressure.getAmount() / volume.capacity());
        float height = volume.height() - 2 * INSET;
        pose.pushPose();
        pose.translate(volume.origin().getX() + INSET, volume.origin().getY() + INSET, volume.origin().getZ() + INSET);
        var vertices = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        FluidVolumeRenderer.renderLayer(pressure, pose.last(), vertices, volume.width() - 2 * INSET,
                volume.depth() - 2 * INSET, 0, gas ? height : height * fraction, gas ? Math.min(0.55F, fraction) : 1);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(Infiltrator machine) { return true; }

    @Override public boolean shouldRender(Infiltrator machine, Vec3 camera) {
        var volume = machine.renderVolume();
        return volume != null && FluidVolumeRenderer.visible(camera, machine.getBlockPos().offset(volume.origin()),
                volume.width(), volume.height(), volume.depth(), getViewDistance());
    }
}
