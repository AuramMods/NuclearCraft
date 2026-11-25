package nc.mixin;

import nc.init.NCWasteland;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = ServerLevel.class, remap = false)
abstract class WastelandPrecipitation {
    @Group(name = "nuclearcraft$precipitationRoll", min = 1, max = 1)
    @Redirect(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V", require = 0,
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=iceandsnow"),
                    to = @At(value = "CONSTANT", args = "stringValue=tickBlocks")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int nuclearcraft$skipWastelandPrecipitation(RandomSource random, int bound) {
        return nuclearcraft$surfaceWeatherRoll(random, bound);
    }

    @Group(name = "nuclearcraft$precipitationRoll", min = 1, max = 1)
    @Redirect(method = "m_8714_(Lnet/minecraft/world/level/chunk/LevelChunk;I)V", require = 0,
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=iceandsnow"),
                    to = @At(value = "CONSTANT", args = "stringValue=tickBlocks")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_188503_(I)I"))
    private int nuclearcraft$skipWastelandPrecipitationSrg(RandomSource random, int bound) {
        return nuclearcraft$surfaceWeatherRoll(random, bound);
    }

    @Unique
    private int nuclearcraft$surfaceWeatherRoll(RandomSource random, int bound) {
        int roll = random.nextInt(bound);
        // Keep the weather RNG draw and leave the separate lightning and random-tick paths intact.
        return roll == 0 && ((ServerLevel) (Object) this).dimension().equals(NCWasteland.DIMENSION) ? 1 : roll;
    }
}
