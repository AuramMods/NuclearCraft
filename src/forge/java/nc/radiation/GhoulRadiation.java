package nc.radiation;

import nc.entity.FeralGhoul;
import nc.init.NCRadiation;
import nc.init.NCWasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class GhoulRadiation {
    private GhoulRadiation() {}

    public static boolean canSpawnGlowing(ServerLevel level, BlockPos pos) {
        if (!RadiationConfig.ENABLED.get()) {
            return false;
        }
        double biome = level.getBiome(pos).unwrapKey().map(key ->
                WorldRadiation.configuredRate(RadiationConfig.BIOME_RATES.get(), key.location().toString(), 1D))
                .orElse(1D);
        double wasteland = WorldRadiation.configuredRate(RadiationConfig.WORLD_RATES.get(),
                NCWasteland.DIMENSION.location().toString(), 1D);
        return WorldRadiation.rate(level, pos) > Math.max(biome, wasteland);
    }

    public static void poison(FeralGhoul ghoul, LivingEntity target, double difficulty) {
        if (!RadiationConfig.ENABLED.get()) {
            return;
        }
        double attack = ghoul.isGlowingGhoul() ? RadiationConfig.GLOWING_GHOUL_ATTACK.get()
                : RadiationConfig.FERAL_GHOUL_ATTACK.get();
        double dose = attack * difficulty;
        if (target instanceof ServerPlayer player) {
            PlayerRadiation.addPoison(player, dose);
        } else if (target instanceof Mob mob) {
            MobRadiation.addPoison(mob, dose);
        } else {
            return;
        }
        var random = ghoul.getRandom();
        ghoul.playSound(NCRadiation.RAD_POISONING.get(), 1.5F * RadiationConfig.soundVolume(7),
                1F + 0.2F * (random.nextFloat() - random.nextFloat()));
    }
}
