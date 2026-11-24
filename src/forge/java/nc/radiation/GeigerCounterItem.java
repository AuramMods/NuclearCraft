package nc.radiation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob) && !(target instanceof Player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer viewer) {
            if (!PlayerRadiation.enabled()) {
                return InteractionResult.PASS;
            }
            if (target instanceof Mob mob) {
                var reading = MobRadiation.reading(mob);
                RadiationDisplay.report(viewer, reading.dose(), reading.maximum(), reading.rate());
            } else if (target instanceof ServerPlayer serverPlayer) {
                var reading = PlayerRadiation.reading(serverPlayer);
                RadiationDisplay.report(viewer, reading.dose(), reading.maximum(), reading.effectiveRate());
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
