package nc.client;

import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import nc.multiblock.hx.ExchangerTubeBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class ExchangerTubeOutline {
    private static final double INSET = 2D / 16D;
    private static final double EDGE = 1D - INSET;

    private ExchangerTubeOutline() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        var game = Minecraft.getInstance();
        var player = game.player;
        if (game.level == null || player == null || game.screen != null || game.options.hideGui
                || !player.getMainHandItem().is(NCHeatExchangers.MULTITOOL.get())
                || !(game.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var pos = hit.getBlockPos();
        if (!(game.level.getBlockState(pos).getBlock() instanceof ExchangerTubeBlock)) {
            return;
        }

        Direction side = ExchangerTubeBlock.multitoolFace(player, hit.getDirection());
        AABB face = bounds(side);
        var camera = event.getCamera().getPosition();
        double x = pos.getX() - camera.x;
        double y = pos.getY() - camera.y;
        double z = pos.getZ() - camera.z;
        WorldOutline.begin();
        WorldOutline.box(WorldOutline.viewRotation(), x + face.minX, y + face.minY, z + face.minZ,
                x + face.maxX, y + face.maxY, z + face.maxZ, 1F, 0F, 0F);
        WorldOutline.draw();
    }

    private static AABB bounds(Direction side) {
        return switch (side) {
            case DOWN -> new AABB(INSET, 0, INSET, EDGE, INSET, EDGE);
            case UP -> new AABB(INSET, EDGE, INSET, EDGE, 1, EDGE);
            case NORTH -> new AABB(INSET, INSET, 0, EDGE, EDGE, INSET);
            case SOUTH -> new AABB(INSET, INSET, EDGE, EDGE, EDGE, 1);
            case WEST -> new AABB(0, INSET, INSET, INSET, EDGE, EDGE);
            case EAST -> new AABB(EDGE, INSET, INSET, 1, EDGE, EDGE);
        };
    }
}
