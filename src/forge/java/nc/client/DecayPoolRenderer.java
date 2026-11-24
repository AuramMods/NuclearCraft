package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import nc.multiblock.machine.DecayPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

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
        renderLayer(volume.feed(), pose.last(), vertices, width, depth, 0, feed * scale);
        renderLayer(volume.product(), pose.last(), vertices, width, depth, feed * scale, total * scale);
        pose.popPose();
    }

    private static void renderLayer(FluidStack fluid, PoseStack.Pose pose, VertexConsumer output,
                                    float width, float depth, float bottom, float top) {
        if (fluid.isEmpty()) return;
        var extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        var texture = extensions.getStillTexture(fluid);
        if (texture == null) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        int color = extensions.getTintColor(fluid);
        quad(output, pose, sprite, color, 0, 1, 0, 0, top, depth, width, top, depth, width, top, 0, 0, top, 0);
        quad(output, pose, sprite, color, 0, -1, 0, 0, bottom, 0, width, bottom, 0, width, bottom, depth, 0, bottom, depth);
        quad(output, pose, sprite, color, 0, 0, 1, 0, bottom, depth, width, bottom, depth, width, top, depth, 0, top, depth);
        quad(output, pose, sprite, color, 0, 0, -1, width, bottom, 0, 0, bottom, 0, 0, top, 0, width, top, 0);
        quad(output, pose, sprite, color, -1, 0, 0, 0, bottom, 0, 0, bottom, depth, 0, top, depth, 0, top, 0);
        quad(output, pose, sprite, color, 1, 0, 0, width, bottom, depth, width, bottom, 0, width, top, 0, width, top, depth);
    }

    private static void quad(VertexConsumer output, PoseStack.Pose pose, TextureAtlasSprite sprite, int color,
                             int nx, int ny, int nz, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(output, pose, color, nx, ny, nz, ax, ay, az, sprite.getU0(), sprite.getV1());
        vertex(output, pose, color, nx, ny, nz, bx, by, bz, sprite.getU1(), sprite.getV1());
        vertex(output, pose, color, nx, ny, nz, cx, cy, cz, sprite.getU1(), sprite.getV0());
        vertex(output, pose, color, nx, ny, nz, dx, dy, dz, sprite.getU0(), sprite.getV0());
    }

    private static void vertex(VertexConsumer output, PoseStack.Pose pose, int color, int nx, int ny, int nz,
                               float x, float y, float z, float u, float v) {
        output.vertex(pose.pose(), x, y, z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, (color >>> 24) * 3 / 4)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), nx, ny, nz).endVertex();
    }

    @Override public boolean shouldRenderOffScreen(DecayPool pool) { return true; }

    @Override
    public boolean shouldRender(DecayPool pool, Vec3 camera) {
        var volume = pool.renderVolume();
        if (volume == null) return false;
        var min = pool.getBlockPos().offset(volume.origin());
        double x = Mth.clamp(camera.x, min.getX(), min.getX() + volume.width());
        double y = Mth.clamp(camera.y, min.getY(), min.getY() + volume.height());
        double z = Mth.clamp(camera.z, min.getZ(), min.getZ() + volume.depth());
        return camera.distanceToSqr(x, y, z) <= (double) getViewDistance() * getViewDistance();
    }
}
