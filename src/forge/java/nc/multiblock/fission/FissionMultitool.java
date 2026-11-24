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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FissionMultitool {
    private static final String LINK = "nuclearcraft_fission_link";
    private static final String COMPONENT = "nuclearcraft_fission_component";

    private FissionMultitool() {}

    public static boolean handles(BlockState state) {
        return state.getBlock() instanceof FissionControlBlock || FissionPartBlock.role(state) == Role.MONITOR
                || FissionPartBlock.isClusterComponent(state);
    }

    public static InteractionResult useOn(ItemStack tool, UseOnContext context) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        boolean sneaking = context.getPlayer().isShiftKeyDown();
        Role role = FissionPartBlock.role(state);
        boolean control = state.getBlock() instanceof FissionControlBlock;
        boolean monitor = role == Role.MONITOR && !sneaking;
        boolean component = sneaking && FissionPartBlock.isClusterComponent(state);
        if (!control && !monitor && !component) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (monitor && server.getBlockEntity(pos) instanceof FissionMonitor part) {
            useMonitor(server, player, tool, part);
        } else if (component && (!control || !tool.getOrCreateTag().contains(LINK, Tag.TAG_COMPOUND))) {
            var selected = new CompoundTag();
            selected.putString("dimension", server.dimension().location().toString());
            selected.putLong("pos", pos.asLong());
            tool.getOrCreateTag().put(COMPONENT, selected);
            monitorMessage(player, "select", state.getBlock().getName());
        } else if (control && server.getBlockEntity(pos) instanceof FissionControlBlockEntity part) {
            interact(server, player, part, tool);
        } else {
            return InteractionResult.PASS;
        }
        play(server, player);
        return InteractionResult.CONSUME;
    }

    static void inspectCluster(ServerPlayer player, FissionMonitor monitor) {
        var cluster = monitor.cluster();
        if (cluster.isEmpty()) {
            monitorMessage(player, monitor.target() == null ? "no_target" : "no_cluster");
        } else {
            FissionHighlights.send(player, cluster);
        }
    }

    private static void useMonitor(ServerLevel level, ServerPlayer player, ItemStack tool, FissionMonitor monitor) {
        CompoundTag data = tool.getOrCreateTag();
        CompoundTag legacy = data.getCompound("ncMultitool");
        if (data.contains(COMPONENT, Tag.TAG_COMPOUND) || legacy.contains("fissionComponentInfo", Tag.TAG_COMPOUND)) {
            BlockPos target = selectedComponent(level, data, legacy);
            if (target == null || !monitor.link(target)) {
                monitorMessage(player, "wrong_target");
                return;
            }
            data.remove(COMPONENT);
            legacy.remove("fissionComponentInfo");
            monitorMessage(player, "linked", target.getX(), target.getY(), target.getZ());
            FissionHighlights.send(player, List.of(target));
        } else {
            BlockPos target = monitor.target();
            if (target == null) {
                monitorMessage(player, "no_target");
            } else {
                monitorMessage(player, "target", level.getBlockState(target).getBlock().getName(),
                        target.getX(), target.getY(), target.getZ());
                FissionHighlights.send(player, List.of(target));
            }
        }
    }

    private static @Nullable BlockPos selectedComponent(ServerLevel level, CompoundTag data, CompoundTag legacy) {
        if (data.contains(COMPONENT, Tag.TAG_COMPOUND)) {
            CompoundTag info = data.getCompound(COMPONENT);
            return info.contains("pos", Tag.TAG_LONG) && info.getString("dimension").equals(level.dimension().location().toString())
                    ? BlockPos.of(info.getLong("pos")) : null;
        }
        CompoundTag info = legacy.getCompound("fissionComponentInfo");
        return info.contains("componentPos", Tag.TAG_LONG)
                ? FissionPartBlock.legacyPosition(info.getLong("componentPos")) : null;
    }

    private static void monitorMessage(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.nuclearcraft.fission.monitor." + key, args), true);
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
        if (tag == null) {
            return false;
        }
        var legacy = tag.getCompound("ncMultitool");
        if (!tag.contains(LINK) && !tag.contains(COMPONENT) && !legacy.contains("fissionComponentInfo")) {
            return false;
        }
        tag.remove(LINK);
        tag.remove(COMPONENT);
        legacy.remove("fissionComponentInfo");
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
