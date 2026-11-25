package nc.script;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.CraftTweakerConstants;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.zencode.IScriptLoadSource;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister(loaders = {CraftTweakerConstants.INIT_LOADER_NAME,
        CraftTweakerConstants.TAGS_LOADER_NAME, CraftTweakerConstants.DEFAULT_LOADER_NAME})
@ZenCodeType.Name("mods.nuclearcraft.CTHelper")
public final class CraftTweakerScriptAddons {
    private CraftTweakerScriptAddons() {}

    @ZenCodeType.Method
    public static boolean isScriptAddonLoaded(String addonId) {
        return ScriptAddons.isLoaded(addonId);
    }

    static void checkSyntax() throws Throwable {
        var source = IScriptLoadSource.find(CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID);
        for (var loader : CraftTweakerAPI.getRegistry().getAllLoaders()) {
            var config = new ScriptRunConfiguration(loader, source, ScriptRunConfiguration.RunKind.SYNTAX_CHECK);
            CraftTweakerAPI.getScriptRunManager().createScriptRun(config).execute();
        }
    }
}
