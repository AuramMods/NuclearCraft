package nc.client;

import nc.NuclearCraft;
import nc.init.NCRadiation;
import nc.radiation.RadiationDisplay;
import nc.radiation.RadiationHudConfig;
import nc.radiation.RadiationSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
public final class RadiationHud {
    private static final ResourceLocation TEXTURE = NuclearCraft.id("textures/hud/rads_bar.png");
    private static final int BAR_WIDTH = 104;
    private static final int INSET = 3;

    private RadiationHud() {}

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft game = Minecraft.getInstance();
        var player = game.player;
        var reading = RadiationSync.clientReading();
        if (player == null || game.options.hideGui || !reading.enabled()
                || (reading.requireCounter() && !RadiationDisplay.hasCounter(player))) {
            return;
        }

        boolean immune = reading.permanentlyImmune() || reading.immuneTicks() > 0;
        Component rate = Component.literal(reading.format(reading.effectiveRate(), "Rad/t"));
        Component info = reading.permanentlyImmune() ? Component.translatable("hud.nuclearcraft.rad_immune")
                : immune ? Component.translatable("hud.nuclearcraft.rad_immune_for", immunityTime(reading.immuneTicks()))
                : rate;
        int color = immune ? 0x55FF55 : RadiationDisplay.rateColor(reading.effectiveRate()).getColor();
        var font = game.font;
        int infoWidth = font.width(info);
        float scale = RadiationHudConfig.SIZE.get().floatValue();
        double angle = RadiationHudConfig.ANGLE.get();
        double radians = Math.toRadians(angle - 45);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int edgeX = angle >= 45 && angle <= 135 ? screenWidth : angle >= 225 && angle <= 315 ? 0
                : (int) Math.round(0.5 * screenWidth * (1 + cos * Math.abs(cos) + sin * Math.abs(sin)));
        int edgeY = angle >= 315 || angle <= 45 ? 0 : angle >= 135 && angle <= 225 ? screenHeight
                : (int) Math.round(0.5 * screenHeight * (1 - cos * Math.abs(cos) + sin * Math.abs(sin)));
        int x = Mth.clamp(edgeX, INSET,
                Math.max(INSET, screenWidth - Math.round(Math.max(BAR_WIDTH, infoWidth) * scale) - INSET));
        int y = Mth.clamp(edgeY, INSET,
                Math.max(INSET, screenHeight - Math.round((12 + font.lineHeight) * scale) - INSET));
        var coordinates = RadiationHudConfig.CARTESIAN.get();
        if (coordinates.size() >= 2) {
            x = (int) Math.round(coordinates.get(0).doubleValue() * screenWidth);
            y = (int) Math.round(coordinates.get(1).doubleValue() * screenHeight);
        }

        int filled = Mth.clamp((int) reading.dosePercent(), 0, 100);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.blit(TEXTURE, 0, 0, 0, 0, BAR_WIDTH, 10);
        if (filled > 0) {
            graphics.blit(TEXTURE, 102 - filled, 2, 100 - filled, 10, filled, 6);
        }
        int infoX = (BAR_WIDTH - infoWidth) / 2;
        if (RadiationHudConfig.TEXT_OUTLINE.get()) {
            graphics.drawString(font, info, infoX - 1, 12, 0, false);
            graphics.drawString(font, info, infoX + 1, 12, 0, false);
            graphics.drawString(font, info, infoX, 11, 0, false);
            graphics.drawString(font, info, infoX, 13, 0, false);
        }
        graphics.drawString(font, info, infoX, 12, color, false);
        graphics.pose().popPose();
    }

    private static String immunityTime(double ticks) {
        double seconds = ticks / 20;
        return seconds >= 60 ? Math.round(seconds / 6) / 10D + " min" : (long) Math.ceil(seconds) + " s";
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        var player = game.player;
        var reading = RadiationSync.clientReading();
        if (player == null || game.isPaused() || !reading.enabled() || reading.clickVolume() <= 0
                || reading.rawRate() <= 0 || reading.rawRate() < reading.minimumRate()) {
            return;
        }
        if (reading.requireCounter()) {
            boolean counterOnHotbar = false;
            for (int slot = 0; slot < Inventory.getSelectionSize(); ++slot) {
                if (player.getInventory().getItem(slot).is(NCRadiation.GEIGER_COUNTER.get())) {
                    counterOnHotbar = true;
                    break;
                }
            }
            if (!counterOnHotbar) {
                return;
            }
        }
        var random = player.getRandom();
        int attempts = Math.min(4, (int) Math.log(Math.E + reading.rawRate()));
        double chance = Math.cbrt(reading.rawRate() / 200D);
        double volume = Mth.clamp(8 * chance, 0.55, 1.1);
        for (int attempt = 0; attempt < attempts; ++attempt) {
            if (random.nextDouble() < chance) {
                player.playSound(NCRadiation.GEIGER_TICK.get(), (float) ((volume + random.nextDouble() * 0.12)
                        * reading.clickVolume()), 0.92F + random.nextFloat() * 0.16F);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        RadiationSync.clearClientReading();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RadiationSync.clearClientReading();
    }
}
