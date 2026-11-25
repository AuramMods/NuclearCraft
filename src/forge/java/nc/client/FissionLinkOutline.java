package nc.client;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionHighlights;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class FissionLinkOutline {
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
        var matrix = WorldOutline.viewRotation();
        double angle = Util.getMillis() * 0.18D % 360D;
        float red = color(angle);
        float green = color(angle + 120D);
        float blue = color(angle + 240D);
        WorldOutline.begin();
        for (var pos : positions) {
            double x = pos.getX() - camera.x;
            double y = pos.getY() - camera.y;
            double z = pos.getZ() - camera.z;
            WorldOutline.box(matrix, x - 0.005D, y - 0.005D, z - 0.005D,
                    x + 1.005D, y + 1.005D, z + 1.005D, red, green, blue);
        }
        WorldOutline.draw();
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
