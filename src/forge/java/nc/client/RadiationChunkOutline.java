package nc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import nc.NuclearCraft;
import nc.init.NCRadiation;
import nc.radiation.RadiationHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class RadiationChunkOutline {
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(256));

    private RadiationChunkOutline() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !RadiationHudConfig.CHUNK_BOUNDARIES.get()) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        var player = game.player;
        if (player == null || game.level == null) {
            return;
        }
        if (!showsOutline(player.getMainHandItem()) && !showsOutline(player.getOffhandItem())) {
            if (!(game.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            var state = game.level.getBlockState(hit.getBlockPos());
            if (!state.is(NCRadiation.GEIGER_BLOCK.get()) && !state.is(NCRadiation.SCRUBBER.get())) {
                return;
            }
        }

        var pos = player.chunkPosition();
        var chunk = game.level.getChunk(pos.x, pos.z);
        int bottom = Mth.floor(player.getY() - 2);
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                bottom = Math.min(bottom, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            }
        }
        double top = bottom + Math.max(32, player.getY() - bottom + 8);
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getMinBlockX() - camera.x, bottom + 2 - camera.y, pos.getMinBlockZ() - camera.z);
        var lines = RenderType.lines();
        LevelRenderer.renderLineBox(pose, BUFFER.getBuffer(lines), 0, 0, 0, 16, top - bottom, 16,
                1F, 0F, 0F, 0.375F);
        pose.popPose();
        BUFFER.endBatch(lines);
    }

    private static boolean showsOutline(ItemStack item) {
        return item.is(NCRadiation.GEIGER_COUNTER.get()) || item.is(NCRadiation.GEIGER_BLOCK.get().asItem())
                || item.is(NCRadiation.SCRUBBER.get().asItem());
    }
}
