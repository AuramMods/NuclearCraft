package nc.compat.gamestages;

import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.List;

public final class RadiationGameStages {
    private RadiationGameStages() {}

    public static boolean grantsImmunity(Player player, boolean defaultImmunity, List<String> stages) {
        return ModList.get().isLoaded("gamestages")
                && (defaultImmunity ^ (!stages.isEmpty() && Integration.hasAny(player, stages)));
    }

    private static final class Integration {
        private static boolean hasAny(Player player, List<String> stages) {
            return GameStageHelper.hasAnyOf(player, stages);
        }
    }
}
