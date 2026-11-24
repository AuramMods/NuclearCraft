package nc.radiation;

import nc.init.NCRadiation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public final class ShieldingItem extends Item {
    private final Tier tier;

    public ShieldingItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public double resistance() {
        return tier.resistance();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || !ShieldingSettings.tileShielding()) {
            return InteractionResult.PASS;
        }
        var level = context.getLevel();
        if (RadiationConfig.CONTAINER_SOURCES.get() <= 0) {
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.translatable("message.nuclearcraft.shielding.containers_disabled"));
            }
            return InteractionResult.PASS;
        }
        var pos = context.getClickedPos();
        var side = context.getClickedFace();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, side, context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        var entity = level.getBlockEntity(pos);
        if (entity == null || (!entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent()
                && !entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent())) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }

        double old = RadiationShielding.containerResistance(entity);
        double installed = resistance();
        if (old >= installed) {
            server.sendSystemMessage(Component.translatable("message.nuclearcraft.shielding.already_installed",
                    RadiationShielding.format(old)));
            return InteractionResult.CONSUME;
        }
        RadiationShielding.install(entity, installed);
        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
            ItemStack recovered = recover(old);
            if (!recovered.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, recovered);
            }
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
        server.sendSystemMessage(Component.translatable("message.nuclearcraft.shielding.installed",
                RadiationShielding.format(installed)));
        return InteractionResult.CONSUME;
    }

    static ItemStack recover(double resistance) {
        var tiers = Tier.values();
        for (int index = tiers.length - 1; index >= 0; --index) {
            Tier tier = tiers[index];
            if (resistance >= tier.resistance()) {
                return NCRadiation.shielding(tier).getDefaultInstance();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("tooltip.nuclearcraft.shielding.install",
                RadiationShielding.format(resistance())).withStyle(ChatFormatting.GRAY));
        if (ShieldingSettings.tileShielding() && RadiationConfig.CONTAINER_SOURCES.get() > 0) {
            lines.add(Component.translatable("tooltip.nuclearcraft.shielding.container").withStyle(ChatFormatting.GRAY));
        }
    }

    public enum Tier {
        LIGHT("rad_shielding_light", 0.01D),
        MEDIUM("rad_shielding_medium", 0.1D),
        HEAVY("rad_shielding_heavy", 1D);

        private final String id;
        private final double fallback;

        Tier(String id, double fallback) {
            this.id = id;
            this.fallback = fallback;
        }

        public String id() {
            return id;
        }

        double resistance() {
            return ShieldingSettings.level(ordinal(), fallback);
        }
    }
}
