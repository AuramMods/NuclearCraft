package nc.init;

import nc.script.ScriptAddons;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.nio.file.Files;
import java.util.Map;

public final class NCScriptAddons {
    private NCScriptAddons() {}

    public static void register(IEventBus bus) {
        ScriptAddons.initialize();
        bus.addListener(NCScriptAddons::addPack);
    }

    private static void addPack(AddPackFindersEvent event) {
        var type = event.getPackType();
        var title = Component.translatable("pack.nuclearcraft.script_addons");
        int format = SharedConstants.getCurrentVersion().getPackVersion(type);
        var info = new Pack.Info(title, format, format, FeatureFlagSet.of(), false);
        event.addRepositorySource(accept -> accept.accept(Pack.create("nuclearcraft_script_addons", title, true,
                id -> {
                    var root = ScriptAddons.packRoot();
                    if (Files.isDirectory(root.resolve(type.getDirectory()))) {
                        return new PathPackResources(id, root, true);
                    }
                    return new GeneratedPackResources(id, type, Map.of());
                }, info, type, Pack.Position.TOP, false, PackSource.BUILT_IN)));
    }
}
