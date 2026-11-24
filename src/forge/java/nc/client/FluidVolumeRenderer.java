package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

final class FluidVolumeRenderer {
    private FluidVolumeRenderer() {}

    static void renderLayer(FluidStack fluid, PoseStack.Pose pose, VertexConsumer output,
                            float width, float depth, float bottom, float top, float opacity) {
        if (fluid.isEmpty()) return;
        var extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        var texture = extensions.getStillTexture(fluid);
        if (texture == null) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        int color = extensions.getTintColor(fluid);
        color = (color & 0xffffff) | ((int) ((color >>> 24) * opacity) << 24);
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

    static boolean visible(Vec3 camera, BlockPos min, int width, int height, int depth, int viewDistance) {
        double x = Mth.clamp(camera.x, min.getX(), min.getX() + width);
        double y = Mth.clamp(camera.y, min.getY(), min.getY() + height);
        double z = Mth.clamp(camera.z, min.getZ(), min.getZ() + depth);
        return camera.distanceToSqr(x, y, z) <= (double) viewDistance * viewDistance;
    }
}
