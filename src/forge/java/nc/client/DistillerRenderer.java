package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import nc.init.NCMachines;
import nc.multiblock.machine.Distiller;
import nc.multiblock.machine.Distiller.RenderVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class DistillerRenderer implements BlockEntityRenderer<Distiller> {
    private static final float PIXEL = 1F / 16F;
    private final Map<Distiller, List<CornerSound>> sounds = new WeakHashMap<>();

    @Override public void render(Distiller tower, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var volume = tower.renderVolume();
        if (volume == null || volume.capacity() <= 0) return;
        playSounds(tower, volume);
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
        DecayPoolRenderer.renderLayer(fluid, pose.last(), vertices, (float) width, (float) depth, 0, (float) height, opacity);
        pose.popPose();
    }

    private void playSounds(Distiller tower, RenderVolume volume) {
        var minecraft = Minecraft.getInstance();
        if (volume.soundVolume() <= 0 || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                || minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS) == 0) return;
        var running = sounds.get(tower);
        if (running != null && running.stream().anyMatch(sound -> !sound.isStopped())) return;
        var min = tower.getBlockPos().offset(volume.origin());
        var corners = new HashSet<BlockPos>();
        for (int i = 0; i < 8; i++) {
            corners.add(min.offset((i & 1) == 0 ? 0 : volume.width() - 1,
                    (i & 2) == 0 ? 0 : volume.height() - 1, (i & 4) == 0 ? 0 : volume.depth() - 1));
        }
        var next = new ArrayList<CornerSound>(corners.size());
        for (var corner : corners) {
            var sound = new CornerSound(tower, corner, volume.soundVolume());
            minecraft.getSoundManager().play(sound);
            next.add(sound);
        }
        sounds.put(tower, next);
    }

    @Override public boolean shouldRenderOffScreen(Distiller tower) { return true; }

    @Override public boolean shouldRender(Distiller tower, Vec3 camera) {
        var volume = tower.renderVolume();
        if (volume == null) return false;
        var min = tower.getBlockPos().offset(volume.origin());
        double x = Mth.clamp(camera.x, min.getX(), min.getX() + volume.width());
        double y = Mth.clamp(camera.y, min.getY(), min.getY() + volume.height());
        double z = Mth.clamp(camera.z, min.getZ(), min.getZ() + volume.depth());
        return camera.distanceToSqr(x, y, z) <= (double) getViewDistance() * getViewDistance();
    }

    private static final class CornerSound extends AbstractTickableSoundInstance {
        private final WeakReference<Distiller> tower;

        CornerSound(Distiller tower, BlockPos corner, float volume) {
            super(NCMachines.DISTILLER_RUN.get(), SoundSource.BLOCKS, RandomSource.create());
            this.tower = new WeakReference<>(tower);
            this.volume = volume;
            x = corner.getX() + 0.5;
            y = corner.getY() + 0.5;
            z = corner.getZ() + 0.5;
            looping = true;
        }

        @Override public void tick() {
            var owner = tower.get();
            var state = owner == null || owner.isRemoved() ? null : owner.renderVolume();
            if (state == null || state.soundVolume() <= 0) stop();
            else volume = state.soundVolume();
        }
    }
}
