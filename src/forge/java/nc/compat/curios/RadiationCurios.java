package nc.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public final class RadiationCurios {
    private RadiationCurios() {}

    public static IItemHandlerModifiable equipped(Player player) {
        return ModList.get().isLoaded("curios") ? Integration.equipped(player) : null;
    }

    private static final class Integration {
        private static IItemHandlerModifiable equipped(Player player) {
            return CuriosApi.getCuriosInventory(player).map(ICuriosItemHandler::getEquippedCurios).orElse(null);
        }
    }
}
