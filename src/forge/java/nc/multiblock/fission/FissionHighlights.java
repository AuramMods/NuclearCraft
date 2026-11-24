package nc.multiblock.fission;

import nc.NuclearCraft;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FissionHighlights {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NuclearCraft.id("fission_highlight"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final int MAX_POSITIONS = 16_384;
    private static final Map<BlockPos, Long> SHOWN = new LinkedHashMap<>();
    private static ResourceLocation dimension;

    private FissionHighlights() {}

    public static void register() {
        CHANNEL.messageBuilder(Report.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Report::write)
                .decoder(Report::read)
                .consumerMainThread((report, context) -> {
                    if (!report.dimension().equals(dimension)) {
                        SHOWN.clear();
                        dimension = report.dimension();
                    }
                    long expiration = Util.getMillis() + 5000;
                    report.positions().forEach(pos -> SHOWN.put(pos, expiration));
                }).add();
    }

    public static void send(ServerPlayer player, Collection<BlockPos> positions) {
        if (!positions.isEmpty() && !(player instanceof FakePlayer) && player.connection != null) {
            var all = List.copyOf(positions);
            var dimension = player.serverLevel().dimension().location();
            for (int start = 0; start < all.size(); start += MAX_POSITIONS) {
                int end = Math.min(all.size(), start + MAX_POSITIONS);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Report(dimension, all.subList(start, end)));
            }
        }
    }

    public static List<BlockPos> current(ResourceLocation currentDimension) {
        if (!currentDimension.equals(dimension)) {
            return List.of();
        }
        long now = Util.getMillis();
        SHOWN.values().removeIf(expiration -> now >= expiration);
        return List.copyOf(SHOWN.keySet());
    }

    public static void clear() {
        SHOWN.clear();
        dimension = null;
    }

    private record Report(ResourceLocation dimension, List<BlockPos> positions) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeVarInt(positions.size());
            positions.forEach(buffer::writeBlockPos);
        }

        private static Report read(FriendlyByteBuf buffer) {
            ResourceLocation dimension = buffer.readResourceLocation();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_POSITIONS) {
                throw new IllegalArgumentException("Invalid fission highlight count " + count);
            }
            List<BlockPos> positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                positions.add(buffer.readBlockPos());
            }
            return new Report(dimension, List.copyOf(positions));
        }
    }
}
