package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import nc.init.NCTurbine;
import nc.init.TurbineConfig;
import nc.multiblock.turbine.TurbineController;
import nc.multiblock.turbine.TurbinePartBlock.Part;
import nc.multiblock.turbine.TurbineVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class TurbineRenderer implements BlockEntityRenderer<TurbineController> {
    private static final float INSET = 1F / 64F;
    private final BlockRenderDispatcher blocks;
    private final Map<TurbineController, Long> lastParticleTick = new WeakHashMap<>();
    private final MachineVolumeSounds<TurbineController> sounds = new MachineVolumeSounds<>(NCTurbine.RUN_SOUND,
            turbine -> {
                var visual = turbine.visuals();
                return visual != null && visual.processing()
                        ? (1D + 2D * visual.speedRatio()) * TurbineConfig.soundVolume() / 32D : 0D;
            }, turbine -> Math.pow(2D, (4D * turbine.visuals().speedRatio() - 2D) / 12D));

    public TurbineRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(TurbineController turbine, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        TurbineVisuals visual = turbine.visuals();
        Level level = turbine.getLevel();
        if (visual == null || level == null) {
            return;
        }
        BlockPos min = turbine.getBlockPos().offset(visual.origin());
        if (visual.processing()) {
            sounds.play(turbine, () -> soundPositions(visual, min));
        }
        if (visual.glass()) {
            renderRotor(visual, visual.angleAt(level.getGameTime() + partialTick), pose, buffers, light, overlay);
            renderExhaust(visual, pose, buffers);
            emitParticles(turbine, level, visual, min);
        }
    }

    private void renderRotor(TurbineVisuals visual, double angle, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Direction flow = visual.flow();
        var axis = flow.getAxis();
        BlockPos origin = visual.origin();
        double radius = visual.diameter() / 2D;
        double scale = radius / Math.hypot(radius, visual.shaftWidth() / 2D);
        var shaft = NCTurbine.SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
        var u = crossAxis(axis);
        var v = otherCrossAxis(axis);
        int length = visual.stages().size();
        for (int stage = 0; stage < length; stage++) {
            Part material = visual.stages().get(stage);
            double longitudinal = flow.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? stage + 0.5D : length - stage - 0.5D;
            double taper = Math.pow(TurbineConfig.expansion(), (1D + stage - length) / length);
            pose.pushPose();
            pose.translate(origin.getX() + (axis == Direction.Axis.X ? longitudinal : radius),
                    origin.getY() + (axis == Direction.Axis.Y ? longitudinal : radius),
                    origin.getZ() + (axis == Direction.Axis.Z ? longitudinal : radius));
            float span = (float) (scale * taper);
            pose.scale(axis == Direction.Axis.X ? 1F : span, axis == Direction.Axis.Y ? 1F : span,
                    axis == Direction.Axis.Z ? 1F : span);
            if (material != Part.STATOR) {
                pose.mulPose(rotation(axis).rotationDegrees((float) angle));
            }
            pose.pushPose();
            int width = visual.shaftWidth();
            pose.scale(axis == Direction.Axis.X ? 1 : width, axis == Direction.Axis.Y ? 1 : width,
                    axis == Direction.Axis.Z ? 1 : width);
            draw(shaft, pose, buffers, light, overlay);
            pose.popPose();

            BlockState blade = bladeState(material);
            renderArm(blade, visual, Direction.get(Direction.AxisDirection.POSITIVE, u), pose, buffers, light, overlay);
            renderArm(blade, visual, Direction.get(Direction.AxisDirection.NEGATIVE, u), pose, buffers, light, overlay);
            renderArm(blade, visual, Direction.get(Direction.AxisDirection.POSITIVE, v), pose, buffers, light, overlay);
            renderArm(blade, visual, Direction.get(Direction.AxisDirection.NEGATIVE, v), pose, buffers, light, overlay);
            pose.popPose();
        }
    }

    private void renderArm(BlockState blade, TurbineVisuals visual, Direction radial, PoseStack pose,
                           MultiBufferSource buffers, int light, int overlay) {
        Vector3f radius = normal(radial);
        Vector3f flow = normal(visual.flow());
        Vector3f lateral = radius.cross(flow, new Vector3f());
        Quaternionf frame = new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0, lateral)
                .setColumn(1, radius).setColumn(2, flow));
        boolean fast = TurbineConfig.fastBlades();
        int strips = fast ? 1 : visual.shaftWidth();
        double distance = (visual.shaftWidth() + visual.bladeLength()) / 2D;
        float bladeWidth = (float) TurbineConfig.bladeWidth() * (fast ? visual.shaftWidth() : 1);
        for (int strip = 0; strip < strips; strip++) {
            double offset = strip - (strips - 1) / 2D;
            pose.pushPose();
            pose.translate(radius.x() * distance + lateral.x() * offset, radius.y() * distance + lateral.y() * offset,
                    radius.z() * distance + lateral.z() * offset);
            pose.mulPose(frame);
            pose.scale(bladeWidth, visual.bladeLength(), 1F);
            pose.mulPose(Axis.YP.rotationDegrees(45F));
            draw(blade, pose, buffers, light, overlay);
            pose.popPose();
        }
    }

    private void draw(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.translate(-0.5D, -0.5D, -0.5D);
        blocks.renderSingleBlock(state, pose, buffers, light, overlay);
    }

    private static BlockState bladeState(Part part) {
        return switch (part) {
            case STEEL -> NCTurbine.STEEL.get().defaultBlockState();
            case EXTREME -> NCTurbine.EXTREME.get().defaultBlockState();
            case SIC_SIC_CMC -> NCTurbine.SIC_SIC_CMC.get().defaultBlockState();
            case STATOR -> NCTurbine.STATOR.get().defaultBlockState();
            default -> throw new IllegalArgumentException("Not a rotor stage: " + part);
        };
    }

    private static Vector3f normal(Direction direction) {
        return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Axis rotation(Direction.Axis axis) {
        return switch (axis) {
            case X -> Axis.XP;
            case Y -> Axis.YP;
            case Z -> Axis.ZP;
        };
    }

    private static Direction.Axis crossAxis(Direction.Axis flow) {
        return flow == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    private static Direction.Axis otherCrossAxis(Direction.Axis flow) {
        return flow == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static List<BlockPos> soundPositions(TurbineVisuals visual, BlockPos min) {
        int[] x = soundOffsets(visual.size(Direction.Axis.X));
        int[] y = soundOffsets(visual.size(Direction.Axis.Y));
        int[] z = soundOffsets(visual.size(Direction.Axis.Z));
        var positions = new ArrayList<BlockPos>();
        for (int i : x) {
            for (int j : y) {
                for (int k : z) {
                    positions.add(min.offset(i, j, k));
                }
            }
        }
        return positions;
    }

    private static int[] soundOffsets(int interior) {
        if (interior + 2 <= 8) {
            return new int[]{(interior - 1) / 2};
        }
        int inset = (int) Math.pow(interior + 2, 0.4D);
        return new int[]{inset - 1, interior - inset};
    }

    private static void renderExhaust(TurbineVisuals visual, PoseStack pose, MultiBufferSource buffers) {
        if (visual.exhaust().isEmpty() || visual.capacity() <= 0) {
            return;
        }
        float fraction = Mth.clamp(4F * visual.exhaust().getAmount() / visual.capacity() - 3F, 0F, 1F);
        if (fraction <= 0F) {
            return;
        }
        var origin = visual.origin();
        pose.pushPose();
        pose.translate(origin.getX() + INSET, origin.getY() + INSET, origin.getZ() + INSET);
        var vertices = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        FluidVolumeRenderer.renderLayer(visual.exhaust(), pose.last(), vertices, visual.size(Direction.Axis.X) - 2 * INSET,
                visual.size(Direction.Axis.Z) - 2 * INSET, 0F, visual.size(Direction.Axis.Y) - 2 * INSET, fraction * fraction);
        pose.popPose();
    }

    private void emitParticles(TurbineController turbine, Level level, TurbineVisuals visual, BlockPos min) {
        if (!visual.processing() || visual.particle() == null || TurbineConfig.particles() == 0D
                || !(BuiltInRegistries.PARTICLE_TYPE.get(visual.particle()) instanceof SimpleParticleType type)) {
            return;
        }
        long now = level.getGameTime();
        if (lastParticleTick.getOrDefault(turbine, Long.MIN_VALUE) == now) {
            return;
        }
        lastParticleTick.put(turbine, now);
        Direction flow = visual.flow();
        var axis = flow.getAxis();
        var u = crossAxis(axis);
        int length = visual.stages().size();
        int width = visual.diameter();
        int rim = visual.bladeLength();
        double inlet = flow.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.02D : length - 0.02D;
        double spread = 1D / (4D * Math.sqrt(length));
        double velocity = (flow.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1D : -1D) * length * visual.particleSpeed();
        double chance = Math.min(1D, TurbineConfig.particles() * visual.particleRate());
        var random = level.random;
        for (int first = 0; first < width; first++) {
            if (first >= rim && first < width - rim) {
                continue;
            }
            for (int second = 0; second < width; second++) {
                if ((second >= rim && second < width - rim) || random.nextDouble() >= chance) {
                    continue;
                }
                double a = first + 0.25D + 0.5D * random.nextDouble();
                double b = second + 0.25D + 0.5D * random.nextDouble();
                level.addParticle(type, min.getX() + coordinate(Direction.Axis.X, axis, u, inlet, a, b),
                        min.getY() + coordinate(Direction.Axis.Y, axis, u, inlet, a, b),
                        min.getZ() + coordinate(Direction.Axis.Z, axis, u, inlet, a, b),
                        axis == Direction.Axis.X ? velocity : (random.nextDouble() - 0.5D) * spread,
                        axis == Direction.Axis.Y ? velocity : (random.nextDouble() - 0.5D) * spread,
                        axis == Direction.Axis.Z ? velocity : (random.nextDouble() - 0.5D) * spread);
            }
        }
    }

    private static double coordinate(Direction.Axis component, Direction.Axis flow, Direction.Axis u,
                                     double along, double first, double second) {
        return component == flow ? along : component == u ? first : second;
    }

    @Override
    public boolean shouldRenderOffScreen(TurbineController turbine) {
        return true;
    }

    @Override
    public boolean shouldRender(TurbineController turbine, Vec3 camera) {
        var visual = turbine.visuals();
        return visual != null && (visual.glass() || visual.processing())
                && FluidVolumeRenderer.visible(camera, turbine.getBlockPos().offset(visual.origin()),
                visual.size(Direction.Axis.X), visual.size(Direction.Axis.Y), visual.size(Direction.Axis.Z), getViewDistance());
    }
}
