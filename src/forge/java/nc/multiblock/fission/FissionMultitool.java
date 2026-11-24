package nc.multiblock.fission;

import nc.multiblock.fission.FissionPartBlock.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FissionMultitool {
    private static final String LINK = "nuclearcraft_fission_link";

    private FissionMultitool() {}

    public static InteractionResult useOn(ItemStack tool, UseOnContext context) {
        var level = context.getLevel();
        if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof FissionControlBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server && context.getPlayer() instanceof ServerPlayer player
                && server.getBlockEntity(context.getClickedPos()) instanceof FissionControlBlockEntity part) {
            interact(server, player, part, tool);
            play(server, player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    static void interact(ServerLevel level, ServerPlayer player, FissionControlBlockEntity part, ItemStack tool) {
        CompoundTag data = tool.getOrCreateTag();
        CompoundTag selection = data.getCompound(LINK);
        SolidFissionController reactor = part.controller();
        BlockPos pos = part.getBlockPos();
        Component name = part.getBlockState().getBlock().getName();
        String dimension = level.dimension().location().toString();

        if (FissionControlBlock.isManager(part.role())) {
            if (player.isShiftKeyDown()) {
                selection = new CompoundTag();
                selection.putString("dimension", dimension);
                selection.putString("type", part.role().name());
                selection.putLong("manager", pos.asLong());
                data.put(LINK, selection);
                message(player, "start", name);
            } else if (reactor == null) {
                message(player, "requires_reactor");
            } else if (selection.isEmpty()) {
                Set<BlockPos> linked = part.listeners();
                FissionHighlights.send(player, linked);
                message(player, "show", name, linked.size());
            } else if (selection.getString("dimension").equals(dimension) && selection.contains("manager", Tag.TAG_LONG)
                    && selection.getLong("manager") == pos.asLong() && selection.getString("type").equals(part.role().name())) {
                Set<BlockPos> linked = reactor.linkControls(level, part, positions(selection));
                data.remove(LINK);
                FissionHighlights.send(player, linked);
                message(player, "finish", name, linked.size());
            } else {
                message(player, "wrong_manager");
            }
        } else if (player.isShiftKeyDown()) {
            Role managerRole = part.role() == Role.SOURCE ? Role.SOURCE_MANAGER : Role.SHIELD_MANAGER;
            if (!selection.getString("dimension").equals(dimension) || !selection.contains("manager", Tag.TAG_LONG)
                    || !selection.getString("type").equals(managerRole.name())) {
                message(player, "wrong_listener");
                return;
            }
            Set<BlockPos> selected = positions(selection);
            if (selected.add(pos)) {
                selection.putLongArray("listeners", selected.stream().mapToLong(BlockPos::asLong).toArray());
            }
            message(player, "append", name, selected.size());
        } else if (part.role() == Role.SOURCE) {
            BlockPos target = reactor == null ? null : reactor.primingTarget(level, pos);
            if (target != null) {
                FissionHighlights.send(player, List.of(target));
                message(player, "target", target.getX(), target.getY(), target.getZ(), level.getBlockState(target).getBlock().getName());
            } else {
                message(player, "no_target");
            }
        } else if (reactor != null) {
            var heat = reactor.partHeat(pos);
            message(player, "heat", heat.amount(), heat.capacity());
        } else {
            message(player, "requires_reactor");
        }
    }

    private static Set<BlockPos> positions(CompoundTag selection) {
        Set<BlockPos> selected = new LinkedHashSet<>();
        for (long position : selection.getLongArray("listeners")) {
            selected.add(BlockPos.of(position));
        }
        return selected;
    }

    public static boolean clear(ServerLevel level, ServerPlayer player, ItemStack tool) {
        CompoundTag tag = tool.getTag();
        if (tag == null || !tag.contains(LINK)) {
            return false;
        }
        tag.remove(LINK);
        play(level, player);
        message(player, "clear");
        return true;
    }

    private static void play(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1F);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.nuclearcraft.fission.link." + key, args), true);
    }
}
