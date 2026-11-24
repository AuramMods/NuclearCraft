package nc.client;

import nc.NuclearCraft;
import nc.init.NCWasteland;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class WastelandAmbience {
    private static final RandomSource RANDOM = RandomSource.create();
    private static SoundInstance current;
    private static int delay = 200;

    private WastelandAmbience() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var game = Minecraft.getInstance();
        var sounds = game.getSoundManager();
        if (game.level == null || !game.level.dimension().equals(NCWasteland.DIMENSION)) {
            if (current != null) {
                sounds.stop(current);
                current = null;
            }
            delay = 200;
        } else if (!game.isPaused() && (current == null || !sounds.isActive(current)) && delay-- <= 0) {
            var tracks = NCWasteland.AMBIENCE;
            current = SimpleSoundInstance.forMusic(tracks.get(RANDOM.nextInt(tracks.size())).get());
            sounds.play(current);
            delay = 200 + RANDOM.nextInt(200);
        }
    }
}
