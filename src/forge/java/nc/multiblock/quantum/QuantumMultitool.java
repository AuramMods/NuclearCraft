package nc.multiblock.quantum;

import nc.multiblock.fission.FissionHighlights;
import nc.multiblock.quantum.QuantumPart.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public final class QuantumMultitool {
    private static final String TOOL = "ncMultitool";
    private static final String SESSION = "quantum";

    private QuantumMultitool() {}

    public static InteractionResult useOn(ItemStack stack, UseOnContext context) {
        var level = context.getLevel();
        if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof QuantumBlock)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && context.getPlayer() instanceof ServerPlayer player
                && server.getBlockEntity(context.getClickedPos()) instanceof QuantumPartEntity part) {
            player.displayClientMessage(part.configure(server, player, stack), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean setAngle(Player player, ItemStack stack) {
        CompoundTag session = session(stack);
        if (player.isShiftKeyDown() || !session.getString("setting").equals(Setting.ANGLE.name())) {
            return false;
        }
        if (!player.level().isClientSide) {
            double step = 360D / QuantumConfig.anglePrecision();
            double angle = (Math.round(player.getYRot() / step) * step % 360D + 360D) % 360D;
            session.putDouble("angle", angle);
            player.displayClientMessage(Component.translatable("message.nuclearcraft.quantum.angle", Double.toString(angle)), false);
        }
        return true;
    }

    static Component label(Setting setting) {
        return Component.translatable("message.nuclearcraft.quantum.setting." + setting.name().toLowerCase(java.util.Locale.ROOT));
    }

    static CompoundTag session(ItemStack stack) {
        var tag = stack.getTagElement(TOOL);
        return tag == null ? new CompoundTag() : tag.getCompound(SESSION);
    }

    static void clear(ItemStack stack) {
        var tag = stack.getTagElement(TOOL);
        if (tag != null) {
            tag.remove(SESSION);
        }
    }

    static void start(ServerLevel level, ItemStack stack, BlockPos gate, Setting setting) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("gate", gate.asLong());
        tag.putString("dimension", level.dimension().location().toString());
        tag.putString("setting", setting.name());
        stack.getOrCreateTagElement(TOOL).put(SESSION, tag);
    }

    static boolean matches(CompoundTag session, ServerLevel level, BlockPos pos, Setting setting) {
        return session.getString("dimension").equals(level.dimension().location().toString())
                && session.getLong("gate") == pos.asLong() && session.getString("setting").equals(setting.name());
    }

    static Component selectQubit(ServerLevel level, QuantumNetworks.Network network, ItemStack stack, QuantumPartEntity qubit) {
        CompoundTag session = session(stack);
        if (session.getString("setting").isEmpty() || session.getString("setting").equals(Setting.ANGLE.name())) {
            return Component.translatable("message.nuclearcraft.quantum.qubit_id", qubit.qubitId());
        }
        if (network == null || network.computer() == null || qubit.qubitId() < 0
                || !session.getString("dimension").equals(level.dimension().location().toString())
                || QuantumNetworks.at(level, BlockPos.of(session.getLong("gate"))) != network) {
            return Component.translatable("message.nuclearcraft.quantum.wrong_network");
        }
        int id = qubit.qubitId();
        int[] ids = session.getIntArray("selection");
        boolean ordered = session.getString("setting").equals(Setting.FIRST.name())
                || session.getString("setting").equals(Setting.SECOND.name());
        if (ordered || Arrays.stream(ids).noneMatch(existing -> existing == id)) {
            ids = Arrays.copyOf(ids, ids.length + 1);
            ids[ids.length - 1] = id;
            if (!ordered) {
                Arrays.sort(ids);
            }
            session.putIntArray("selection", ids);
        }
        return Component.translatable("message.nuclearcraft.quantum.selected", id, Arrays.toString(ids));
    }

    static void highlight(ServerPlayer player, QuantumNetworks.Network network, Collection<int[]> selections) {
        if (network == null || selections.isEmpty()) {
            return;
        }
        var ids = new HashSet<Integer>();
        for (int[] selection : selections) {
            for (int id : selection) {
                ids.add(id);
            }
        }
        FissionHighlights.send(player, network.qubits().stream().filter(qubit -> ids.contains(qubit.qubitId()))
                .map(QuantumPartEntity::getBlockPos).toList());
    }
}
