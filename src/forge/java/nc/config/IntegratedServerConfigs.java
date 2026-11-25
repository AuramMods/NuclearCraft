package nc.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IntegratedServerConfigs {
    private static final Map<ModConfig, WorldConfig> WORLD_CONFIGS = new ConcurrentHashMap<>();
    private static final Method SET_CONFIG_DATA = ObfuscationReflectionHelper.findMethod(
            ModConfig.class, "setConfigData", CommentedConfig.class);

    private IntegratedServerConfigs() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.HIGHEST, IntegratedServerConfigs::onLoading);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                WORLD_CONFIGS.values().removeIf(saved -> saved.server() == event.getServer()));
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (config.getType() != ModConfig.Type.SERVER) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.isDedicatedServer()) {
            return;
        }
        if (config.getConfigData() instanceof CommentedFileConfig file) {
            WORLD_CONFIGS.put(config, new WorldConfig(server, file));
            return;
        }
        WorldConfig saved = WORLD_CONFIGS.get(config);
        if (saved != null && saved.server() == server) {
            // Forge's vanilla login fallback loads in-memory defaults. Preserve the local world's
            // file and restore the spec before other NuclearCraft config listeners read it.
            try {
                SET_CONFIG_DATA.invoke(config, saved.file());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not restore world config " + config.getFileName(), failure);
            }
        }
    }

    private record WorldConfig(MinecraftServer server, CommentedFileConfig file) {}
}
