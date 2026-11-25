package nc.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class NCLiquidBucket extends BucketItem {
    public NCLiquidBucket(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult hit,
                                 @Nullable ItemStack container) {
        Fluid fluid = getFluid();
        return fluid.getFluidType().canBePlacedInLevel(level, pos, fluid.defaultFluidState())
                && super.emptyContents(player, level, pos, hit, container);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        // Forge's default BucketItem provider is limited to the exact vanilla class.
        return new FluidBucketWrapper(stack);
    }
}
