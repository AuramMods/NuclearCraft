package nc.energy;

import nc.init.NCBatteries.Tier;
import nc.init.NCHeatExchangers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class BatteryBlock extends BaseEntityBlock {
    static final Direction[] FACES = Direction.values();
    private final Tier tier;

    public BatteryBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            battery.setPlacedCharge(BatteryItem.stored(stack));
            var tag = stack.getTag();
            if (tag != null && tag.contains(BatteryItem.STORAGE)) {
                if (placer == null || placer.isShiftKeyDown()) {
                    battery.setModes(tag.getInt("energy_sides"));
                } else {
                    battery.setMode(Direction.orderedByNearest(placer)[0].getOpposite(), Mode.OUT);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            if (player.getItemInHand(hand).is(NCHeatExchangers.MULTITOOL.get())) {
                Direction face = player.isShiftKeyDown() ? hit.getDirection().getOpposite() : hit.getDirection();
                Mode next = battery.mode(face).next();
                battery.setMode(face, next);
                player.displayClientMessage(Component.translatable("block.nuclearcraft.battery.side",
                        Component.translatable("gui.nuclearcraft.face." + face.getName()), next.label()), true);
            } else {
                player.displayClientMessage(BatteryItem.chargeLabel(battery.stored(), battery.capacity()), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BatteryBlockEntity battery ? battery.comparator() : 0;
    }

    @Override
    public BatteryBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state, tier);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, tier.entity(), BatteryBlockEntity::tick);
    }

    public enum Mode implements StringRepresentable {
        IN("in"), OUT("out"), OFF("off");

        private static final Mode[] ALL = values();
        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public static Mode read(int modes, Direction face) {
            int id = modes >>> (2 * face.ordinal()) & 3;
            return id < ALL.length ? ALL[id] : IN;
        }

        Mode next() {
            return ALL[(ordinal() + 1) % ALL.length];
        }

        Component label() {
            return Component.translatable("block.nuclearcraft.battery." + id);
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
