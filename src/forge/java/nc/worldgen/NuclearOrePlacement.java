package nc.worldgen;

import com.mojang.serialization.Codec;
import nc.init.NCOres;
import nc.init.NCOres.Ore;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public final class NuclearOrePlacement extends PlacementFilter {
    public static final Codec<NuclearOrePlacement> CODEC =
            Ore.CODEC.fieldOf("ore").xmap(NuclearOrePlacement::new, placement -> placement.ore).codec();

    private final Ore ore;

    private NuclearOrePlacement(Ore ore) {
        this.ore = ore;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        return ore.enabled() && NCOres.allowsDimension(context.getLevel().getLevel().dimension().location());
    }

    @Override
    public PlacementModifierType<?> type() {
        return NCOres.PLACEMENT.get();
    }
}
