package nc.compat.tconstruct;

import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

public final class TinkersHarvest {
    private TinkersHarvest() {}

    public static int getLevel(ItemStack stack) {
        if (stack.getItem() instanceof IModifiable) {
            return ToolStack.from(stack).getStats().get(ToolStats.HARVEST_TIER).getLevel();
        }
        return 0;
    }
}
