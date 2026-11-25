package nc.mixin;

import nc.entity.FeralGhoul;
import nc.init.NCEntities;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ChunkMap.TrackedEntity.class, remap = false)
abstract class GhoulEntityTracking {
    // Forge's type range is in chunks. Apply the configured block range before server scaling.
    @ModifyVariable(method = {"scaledRange(I)I", "m_140483_(I)I"}, at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 1)
    private int nuclearcraft$applyGhoulRange(int original) {
        int ghoulRange = NCEntities.ghoulTrackingRange();
        if (ghoulRange == NCEntities.DEFAULT_GHOUL_TRACKING_RANGE) {
            return original;
        }
        var tracked = (ChunkMap.TrackedEntity) (Object) this;
        Entity entity = tracked.entity;
        boolean hasGhoul = entity instanceof FeralGhoul;
        if (!entity.isVehicle()) {
            return hasGhoul ? ghoulRange : original;
        }

        // A vehicle uses the greatest tracking range of its own type and all its passengers.
        int longest = hasGhoul ? ghoulRange : tracked.range;
        for (Entity passenger : entity.getIndirectPassengers()) {
            boolean ghoul = passenger instanceof FeralGhoul;
            hasGhoul |= ghoul;
            longest = Math.max(longest, ghoul ? ghoulRange : passenger.getType().clientTrackingRange() * 16);
        }
        return hasGhoul ? longest : original;
    }
}
