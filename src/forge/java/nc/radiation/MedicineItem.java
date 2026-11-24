package nc.radiation;

import nc.init.NCRadiation;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class MedicineItem extends Item {
    private final Kind kind;

    public MedicineItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!PlayerRadiation.enabled()) {
                return InteractionResultHolder.fail(stack);
            }
            if (!PlayerRadiation.canTreat(serverPlayer, kind)) {
                player.displayClientMessage(Component.translatable("message.nuclearcraft.medicine_cooldown"), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 16;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return kind == Kind.RAD_X ? UseAnim.DRINK : UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && PlayerRadiation.enabled() && PlayerRadiation.canTreat(player, kind)) {
            PlayerRadiation.treat(player, kind);
            var sound = kind == Kind.RAD_X ? NCRadiation.RAD_X_SOUND : NCRadiation.RADAWAY_SOUND;
            level.playSound(null, player.getX(), player.getY(), player.getZ(), sound.get(), SoundSource.PLAYERS, 0.5F, 1);
            player.awardStat(Stats.ITEM_USED.get(this));
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    public enum Kind {
        RADAWAY("radaway"),
        SLOW_RADAWAY("radaway_slow"),
        RAD_X("rad_x");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
