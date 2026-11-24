package nc.radiation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class GeigerCounterItem extends Item {
    public GeigerCounterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!PlayerRadiation.enabled()) {
                return InteractionResultHolder.pass(stack);
            }
            PlayerRadiation.report(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
