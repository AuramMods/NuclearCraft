package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import nc.multiblock.hx.ExchangerController;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

public final class ExchangerRenderer implements BlockEntityRenderer<ExchangerController> {
    private static final float INSET = 1F / 64F;

    @Override
    public void render(ExchangerController exchanger, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        var volume = exchanger.renderVolume();
        if (volume == null) {
            return;
        }
        renderFluid(volume, volume.input(), volume.inputCapacity(), INSET, !volume.condenser(), pose, buffers);
        renderFluid(volume, volume.output(), volume.outputCapacity(), 2 * INSET, true, pose, buffers);
    }

    private static void renderFluid(ExchangerController.RenderVolume volume, FluidStack fluid, int capacity,
                                    float inset, boolean gas, PoseStack pose, MultiBufferSource buffers) {
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        gas |= fluid.getFluid().getFluidType().getDensity(fluid) < 0;
        float fraction = Mth.clamp((float) fluid.getAmount() / capacity, 0, 1);
        float height = (volume.height() - 2 * inset) * (gas ? 1F : fraction);
        float opacity = gas ? fraction * fraction : 1F;
        pose.pushPose();
        var origin = volume.origin();
        pose.translate(origin.getX() + inset, origin.getY() + inset, origin.getZ() + inset);
        var vertices = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        FluidVolumeRenderer.renderLayer(fluid, pose.last(), vertices, volume.width() - 2 * inset,
                volume.depth() - 2 * inset, 0, height, opacity);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ExchangerController exchanger) {
        return true;
    }

    @Override
    public boolean shouldRender(ExchangerController exchanger, Vec3 camera) {
        var volume = exchanger.renderVolume();
        return volume != null && (!volume.input().isEmpty() || !volume.output().isEmpty())
                && FluidVolumeRenderer.visible(camera, exchanger.getBlockPos().offset(volume.origin()),
                volume.width(), volume.height(), volume.depth(), getViewDistance());
    }
}
