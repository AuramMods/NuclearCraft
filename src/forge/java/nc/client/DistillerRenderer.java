package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import nc.init.NCMachines;
import nc.multiblock.machine.Distiller;
import nc.multiblock.machine.Distiller.RenderVolume;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.List;

public final class DistillerRenderer implements BlockEntityRenderer<Distiller> {
    private static final float PIXEL = 1F / 16F;
    private final MachineVolumeSounds<Distiller> sounds = new MachineVolumeSounds<>(NCMachines.DISTILLER_RUN,
            tower -> tower.renderVolume() == null ? 0 : tower.renderVolume().soundVolume());

    @Override public void render(Distiller tower, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var volume = tower.renderVolume();
        if (volume == null || volume.capacity() <= 0) return;
        sounds.play(tower, tower.getBlockPos().offset(volume.origin()), volume.width(), volume.height(), volume.depth());
        var contents = List.of(volume.first(), volume.second());
        if (contents.stream().allMatch(FluidStack::isEmpty)) return;

        pose.pushPose();
        pose.translate(volume.origin().getX(), volume.origin().getY(), volume.origin().getZ());
        var vertices = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        var liquids = contents.stream().filter(stack -> !stack.isEmpty() && !Distiller.isGas(stack))
                .sorted(Comparator.comparingInt(stack -> -stack.getFluid().getFluidType().getDensity(stack))).toList();
        if (!liquids.isEmpty()) renderLiquid(volume, liquids, pose, vertices);
        for (var gas : contents) {
            if (gas.isEmpty() || !Distiller.isGas(gas)) continue;
            box(gas, pose, vertices, PIXEL / 4, PIXEL / 4, PIXEL / 4,
                    volume.width() - PIXEL / 2, volume.height() - PIXEL / 2, volume.depth() - PIXEL / 2,
                    (float) Math.min(0.55, fraction(gas, volume)));
        }
        pose.popPose();
    }

    private static void renderLiquid(RenderVolume volume, List<FluidStack> liquids, PoseStack pose, VertexConsumer vertices) {
        float width = volume.width() - PIXEL;
        float depth = volume.depth() - PIXEL;
        var trays = volume.trays();
        var floor = liquids.get(0);
        box(floor, pose, vertices, PIXEL / 2, 0, PIXEL / 2, width, 0.25 * fraction(floor, volume), depth, 1);
        for (int i = 0; i < trays.size(); i++) {
            var liquid = liquids.get(liquids.size() * i / trays.size());
            double fraction = fraction(liquid, volume);
            double trayY = trays.get(i) + 1.5 - PIXEL / 4;
            double fill = Math.min(1, 2 * fraction);
            box(liquid, pose, vertices, PIXEL / 2 + (1 - fill) * width / 2, trayY, PIXEL / 2 + (1 - fill) * depth / 2,
                    fill * width, 0.5 - PIXEL / 2, fill * depth, 1);
            if (fraction > 0.5) {
                double fall = i == 0 ? trayY : trays.get(i) - trays.get(i - 1);
                boolean fromLow = (i & 1) == 0;
                double spread = Math.min(1, 2 * (fraction - 0.5));
                box(liquid, pose, vertices, fromLow ? PIXEL / 2 : width - PIXEL / 2, trayY - fall,
                        PIXEL / 2 + (1 - spread) * depth / 2, PIXEL, fall, spread * depth, 1);
                box(liquid, pose, vertices, PIXEL / 2 + (1 - spread) * width / 2, trayY - fall,
                        fromLow ? PIXEL / 2 : depth - PIXEL / 2, spread * width, fall, PIXEL, 1);
            }
        }
        var top = liquids.get(liquids.size() - 1);
        double stream = Math.max(0, 2 * (fraction(top, volume) - 0.5)) * (1 - PIXEL * 4);
        double lower = trays.isEmpty() ? 0 : trays.get(trays.size() - 1) + 1.5;
        if (stream <= 0) return;
        for (var distributor : volume.distributors()) {
            box(top, pose, vertices, distributor.getX() + (1 - stream) / 2, lower,
                    distributor.getZ() + (1 - stream) / 2, stream, volume.height() - lower, stream, 1);
        }
    }

    private static double fraction(FluidStack fluid, RenderVolume volume) {
        return Math.min(1, (double) fluid.getAmount() / volume.capacity());
    }

    private static void box(FluidStack fluid, PoseStack pose, VertexConsumer vertices, double x, double y, double z,
                            double width, double height, double depth, float opacity) {
        if (width <= 0 || height <= 0 || depth <= 0 || opacity <= 0) return;
        pose.pushPose();
        pose.translate(x, y, z);
        FluidVolumeRenderer.renderLayer(fluid, pose.last(), vertices, (float) width, (float) depth, 0, (float) height, opacity);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(Distiller tower) { return true; }

    @Override public boolean shouldRender(Distiller tower, Vec3 camera) {
        var volume = tower.renderVolume();
        if (volume == null) return false;
        var min = tower.getBlockPos().offset(volume.origin());
        return FluidVolumeRenderer.visible(camera, min, volume.width(), volume.height(), volume.depth(), getViewDistance());
    }
}
