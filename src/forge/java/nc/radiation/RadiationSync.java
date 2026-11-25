package nc.radiation;

import nc.NuclearCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class RadiationSync {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NuclearCraft.id("radiation"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<ServerPlayer, Reading> LAST_SENT = new WeakHashMap<>();
    private static Reading clientReading = Reading.HIDDEN;
    private static Map<ResourceLocation, FoodRadiation.Effects> clientFoods = Map.of();

    private RadiationSync() {}

    public static void register() {
        CHANNEL.messageBuilder(Reading.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Reading::write)
                .decoder(Reading::read)
                .consumerMainThread((reading, context) -> clientReading = reading)
                .add();
        CHANNEL.messageBuilder(ScriptedFoods.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScriptedFoods::write)
                .decoder(ScriptedFoods::read)
                .consumerMainThread((foods, context) -> clientFoods = foods.entries())
                .add();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        var foods = new ScriptedFoods(RadiationScriptRules.active().foods());
        if (event.getPlayer() != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), foods);
        } else {
            event.getPlayerList().getPlayers().forEach(player -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), foods));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RadiationScriptRules.clear();
    }

    static FoodRadiation.Effects clientFood(ResourceLocation id) {
        return clientFoods.get(id);
    }

    public static void clearClientFoods() {
        clientFoods = Map.of();
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

    private record ScriptedFoods(Map<ResourceLocation, FoodRadiation.Effects> entries) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeMap(entries, FriendlyByteBuf::writeResourceLocation, (output, food) -> {
                output.writeDouble(food.dose());
                output.writeDouble(food.resistance());
            });
        }

        private static ScriptedFoods read(FriendlyByteBuf buffer) {
            return new ScriptedFoods(buffer.readMap(FriendlyByteBuf::readResourceLocation,
                    input -> new FoodRadiation.Effects(input.readDouble(), input.readDouble())));
        }
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
