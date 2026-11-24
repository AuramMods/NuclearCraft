package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import nc.multiblock.machine.DecayPool;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;

public final class DecayPoolRenderer implements BlockEntityRenderer<DecayPool> {
    private static final float INSET = 1F / 64F;

    @Override
    public void render(DecayPool pool, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var volume = pool.renderVolume();
        if (volume == null || volume.capacity() == 0) return;
        int feed = volume.feed().getAmount();
        int product = volume.product().getAmount();
        long total = (long) feed + product;
        if (total == 0) return;
        float scale = (volume.height() - 2 * INSET) / Math.max(volume.capacity(), total);
        pose.pushPose();
        pose.translate(volume.origin().getX() + INSET, volume.origin().getY() + INSET, volume.origin().getZ() + INSET);
        var vertices = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        float width = volume.width() - 2 * INSET;
        float depth = volume.depth() - 2 * INSET;
        FluidVolumeRenderer.renderLayer(volume.feed(), pose.last(), vertices, width, depth, 0, feed * scale, 1);
        FluidVolumeRenderer.renderLayer(volume.product(), pose.last(), vertices, width, depth, feed * scale, total * scale, 1);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(DecayPool pool) { return true; }

    @Override
    public boolean shouldRender(DecayPool pool, Vec3 camera) {
        var volume = pool.renderVolume();
        if (volume == null) return false;
        var min = pool.getBlockPos().offset(volume.origin());
        return FluidVolumeRenderer.visible(camera, min, volume.width(), volume.height(), volume.depth(), getViewDistance());
    }
}
