package nc.energy;

import nc.init.NCBatteries;
import nc.init.NCHeatExchangers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public final class BatteryBlock extends BaseEntityBlock {
    private final Definition definition;

    public BatteryBlock(Properties properties, Definition definition) {
        super(properties);
        this.definition = definition;
    }

    public static BatteryBlock createScriptedBattery(String capacity) {
        long storage = Long.parseLong(capacity);
        return scriptedBattery(storage, (int) Math.min(storage, Integer.MAX_VALUE));
    }

    public static BatteryBlock createScriptedBattery(String capacity, int transfer) {
        return scriptedBattery(Long.parseLong(capacity), transfer);
    }

    private static BatteryBlock scriptedBattery(long capacity, int transfer) {
        if (capacity <= 0 || transfer <= 0) {
            throw new IllegalArgumentException("A battery requires positive capacity and transfer");
        }
        return new BatteryBlock(Properties.copy(Blocks.IRON_BLOCK), new ScriptedBattery(capacity, transfer));
    }

    public static BlockItem createScriptedBatteryItem(String blockId) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (!(block instanceof BatteryBlock battery) || !battery.isScriptedBattery()) {
            throw new IllegalArgumentException("No scripted NuclearCraft battery block with ID " + blockId);
        }
        return new BatteryItem.BlockBattery(battery);
    }

    public boolean isScriptedBattery() {
        return definition instanceof ScriptedBattery;
    }

    Definition definition() {
        return definition;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return definition.enabled() ? super.getStateForPlacement(context) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            battery.setPlacedCharge(BatteryItem.stored(stack));
            var tag = stack.getTag();
            if (tag != null && (tag.contains(BatteryItem.STORAGE) || Mode.hasSavedModes(tag))) {
                if (placer == null || placer.isShiftKeyDown()) {
                    battery.setModes(Mode.load(tag));
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
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, definition.entity(), BatteryBlockEntity::tick);
    }

    public interface Definition {
        default boolean enabled() {
            return true;
        }

        BlockEntityType<BatteryBlockEntity> entity();

        long capacity();

        int transfer();
    }

    private record ScriptedBattery(long capacity, int transfer) implements Definition {
        @Override
        public BlockEntityType<BatteryBlockEntity> entity() {
            return NCBatteries.SCRIPTED_BATTERY_ENTITY.get();
        }
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

        private static boolean hasSavedModes(CompoundTag tag) {
            if (tag.contains("energy_sides", Tag.TAG_ANY_NUMERIC)) {
                return true;
            }
            for (Direction face : Direction.values()) {
                if (tag.contains("energyConnections" + face.ordinal(), Tag.TAG_ANY_NUMERIC)) {
                    return true;
                }
            }
            return false;
        }

        static int load(CompoundTag tag) {
            if (tag.contains("energy_sides", Tag.TAG_ANY_NUMERIC)) {
                return tag.getInt("energy_sides");
            }
            int packed = 0;
            for (Direction face : Direction.values()) {
                Mode mode = switch (tag.getInt("energyConnections" + face.ordinal())) {
                    case 1 -> OUT;
                    case 3 -> OFF;
                    default -> IN;
                };
                packed |= mode.ordinal() << (2 * face.ordinal());
            }
            return packed;
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
