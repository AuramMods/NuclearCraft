package nc.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public final class RadiationCurios {
    private RadiationCurios() {}

    public static IItemHandlerModifiable equipped(LivingEntity wearer) {
        return ModList.get().isLoaded("curios") ? Integration.equipped(wearer) : null;
    }

    private static final class Integration {
        private static IItemHandlerModifiable equipped(LivingEntity wearer) {
            return CuriosApi.getCuriosInventory(wearer).map(ICuriosItemHandler::getEquippedCurios).orElse(null);
        }
    }
}
