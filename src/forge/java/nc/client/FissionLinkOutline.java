package nc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import nc.NuclearCraft;
import nc.multiblock.fission.FissionHighlights;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class FissionLinkOutline {
    private static final BufferBuilder BUFFER = new BufferBuilder(256);
    private static final int[] EDGES = {
            0, 1, 2, 3, 4, 5, 6, 7,
            0, 2, 1, 3, 4, 6, 5, 7,
            0, 4, 1, 5, 2, 6, 3, 7
    };

    private FissionLinkOutline() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || level == null) {
            return;
        }
        var positions = FissionHighlights.current(level.dimension().location());
        if (positions.isEmpty()) {
            return;
        }

        var camera = event.getCamera().getPosition();
        var matrix = event.getPoseStack().last().pose();
        double angle = Util.getMillis() * 0.18D % 360D;
        float red = color(angle);
        float green = color(angle + 120D);
        float blue = color(angle + 240D);
        BUFFER.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (var pos : positions) {
            float x = (float) (pos.getX() - camera.x);
            float y = (float) (pos.getY() - camera.y);
            float z = (float) (pos.getZ() - camera.z);
            for (int corner : EDGES) {
                vertex(matrix, x, y, z, corner, red, green, blue);
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            BufferUploader.drawWithShader(BUFFER.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void vertex(Matrix4f matrix, float x, float y, float z, int corner, float red, float green, float blue) {
        BUFFER.vertex(matrix, x + ((corner & 1) == 0 ? -0.005F : 1.005F),
                        y + ((corner & 2) == 0 ? -0.005F : 1.005F),
                        z + ((corner & 4) == 0 ? -0.005F : 1.005F))
                .color(red, green, blue, 1F).endVertex();
    }

    private static float color(double angle) {
        angle %= 360D;
        return (float) Math.max(0D, Math.min(1D, Math.max((120D - angle) / 60D, (angle - 240D) / 60D)));
    }

    @SubscribeEvent
    public static void clear(ClientPlayerNetworkEvent.LoggingOut event) {
        FissionHighlights.clear();
    }
}
