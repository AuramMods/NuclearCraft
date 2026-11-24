package nc.radiation;

import nc.NuclearCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.WeakHashMap;

public final class RadiationSync {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NuclearCraft.id("radiation"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<ServerPlayer, Reading> LAST_SENT = new WeakHashMap<>();
    private static Reading clientReading = Reading.HIDDEN;

    private RadiationSync() {}

    public static void register() {
        CHANNEL.messageBuilder(Reading.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Reading::write)
                .decoder(Reading::read)
                .consumerMainThread((reading, context) -> clientReading = reading)
                .add();
    }

    static void send(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        Reading reading = PlayerRadiation.reading(player);
        if (!reading.equals(LAST_SENT.get(player))) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reading);
            LAST_SENT.put(player, reading);
        }
    }

    public static Reading clientReading() {
        return clientReading;
    }

    public static void clearClientReading() {
        clientReading = Reading.HIDDEN;
    }

    public record Reading(boolean enabled, boolean requireCounter, double dose, double maximum, double effectiveRate,
                          double rawRate, boolean permanentlyImmune, double immuneTicks, double minimumRate,
                          int units, float clickVolume) {
        static final Reading HIDDEN = new Reading(false, true, 0, 1, 0, 0, false, 0, 0, 0, 0);

        private void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(enabled);
            if (enabled) {
                buffer.writeBoolean(requireCounter);
                buffer.writeDouble(dose);
                buffer.writeDouble(maximum);
                buffer.writeDouble(effectiveRate);
                buffer.writeDouble(rawRate);
                buffer.writeBoolean(permanentlyImmune);
                buffer.writeDouble(immuneTicks);
                buffer.writeDouble(minimumRate);
                buffer.writeVarInt(units);
                buffer.writeFloat(clickVolume);
            }
        }

        private static Reading read(FriendlyByteBuf buffer) {
            if (!buffer.readBoolean()) {
                return HIDDEN;
            }
            return new Reading(true, buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readVarInt(), buffer.readFloat());
        }

        public double dosePercent() {
            return Math.min(100, 100 * dose / maximum);
        }

        public String format(double value, String unit) {
            return RadiationDisplay.format(value, minimumRate, units, unit);
        }
    }
}
