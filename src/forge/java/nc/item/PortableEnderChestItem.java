package nc.item;

import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PortableEnderChestItem extends Item {
    private static final Component TITLE = Component.translatable("container.enderchest");

    public PortableEnderChestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            var storage = player.getEnderChestInventory();
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, owner) -> ChestMenu.threeRows(id, inventory, storage), TITLE));
            player.awardStat(Stats.OPEN_ENDERCHEST);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
