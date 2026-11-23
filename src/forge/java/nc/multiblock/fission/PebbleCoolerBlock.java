package nc.multiblock.fission;

import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PebbleCoolerBlock extends FissionPartBlock {
    private final NCGasCoolants gas;

    public PebbleCoolerBlock(Properties properties, NCGasCoolants gas) {
        super(properties, Role.PEBBLE_COOLER);
        this.gas = gas;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.placement_rule",
                NCPebbleFission.placementRules().text(gas)).withStyle(ChatFormatting.GRAY));
    }

    public NCGasCoolants gas() {
        return gas;
    }
}
