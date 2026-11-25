package nc.script;

import com.mojang.logging.LogUtils;
import nc.NuclearCraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class ScriptAddonCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "commands.nuclearcraft.reconstruct_script_addons.";

    private ScriptAddonCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nc_reconstruct_script_addons")
                .requires(source -> source.hasPermission(2))
                .executes(context -> reconstruct(context.getSource(), false))
                .then(Commands.literal("syntax").executes(context -> reconstruct(context.getSource(), true))));
    }

    private static int reconstruct(CommandSourceStack source, boolean syntax) {
        int count;
        try {
            count = ScriptAddons.rebuild(FMLPaths.GAMEDIR.get());
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not reconstruct NuclearCraft script addons", failure);
            source.sendFailure(Component.translatable(KEY + "fail"));
            return 0;
        }
        int rebuilt = count;
        source.sendSuccess(() -> Component.translatable(KEY + "success", rebuilt), false);
        if (syntax && ModList.get().isLoaded("crafttweaker")) {
            try {
                CraftTweakerScriptAddons.checkSyntax();
                source.sendSuccess(() -> Component.translatable(KEY + "syntax_finished"), false);
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError fatal) {
                    throw fatal;
                }
                LOGGER.error("CraftTweaker could not check the reconstructed addon scripts", failure);
                source.sendFailure(Component.translatable(KEY + "syntax_failed"));
                return 0;
            }
        } else if (syntax) {
            source.sendSuccess(() -> Component.translatable(KEY + "syntax_unavailable"), false);
        }
        return 1;
    }
}
