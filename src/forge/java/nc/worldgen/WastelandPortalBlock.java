package nc.worldgen;

import nc.init.NCFluidProducts;
import nc.init.NCWasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.ITeleporter;

import java.util.function.Function;

public final class WastelandPortalBlock extends Block {
    private static final VoxelShape COLLISION = Block.box(0.032D, 0.032D, 0.032D, 15.968D, 15.968D, 15.968D);
    private static final ITeleporter TELEPORTER = new ITeleporter() {
        @Override
        public PortalInfo getPortalInfo(Entity entity, ServerLevel destination,
                                        Function<ServerLevel, PortalInfo> vanillaInfo) {
            double scale = DimensionType.getTeleportationScale(entity.level().dimensionType(), destination.dimensionType());
            BlockPos center = destination.getWorldBorder().clampToBounds(entity.getX() * scale, entity.getY(),
                    entity.getZ() * scale);
            BlockPos landing = findLanding(destination, entity, center);
            if (landing == null) {
                return null;
            }
            destination.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(landing), 3, landing);
            return new PortalInfo(Vec3.atBottomCenterOf(landing), Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }

        @Override
        public Entity placeEntity(Entity entity, ServerLevel origin, ServerLevel destination, float yaw,
                                  Function<Boolean, Entity> reposition) {
            Entity arriving = reposition.apply(false);
            if (arriving != null) {
                arriving.resetFallDistance();
            }
            return arriving;
        }

        @Override
        public boolean playTeleportSound(ServerPlayer player, ServerLevel origin,
                                         ServerLevel destination) {
            return false;
        }
    };

    public WastelandPortalBlock() {
        super(Properties.copy(Blocks.BEDROCK).strength(-1F, 6_000_000F).noLootTable()
                .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !NCWasteland.enabled() || !entity.isAlive()
                || entity.isOnPortalCooldown() || !entity.canChangeDimensions() || entity.isVehicle() || entity.isPassenger()
                || entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ENDER_DRAGON) {
            return;
        }
        var target = server.dimension().equals(NCWasteland.DIMENSION) ? Level.OVERWORLD : NCWasteland.DIMENSION;
        ServerLevel destination = server.getServer().getLevel(target);
        if (destination != null) {
            entity.setPortalCooldown();
            entity.changeDimension(destination, TELEPORTER);
        }
    }

    private static BlockPos findLanding(ServerLevel level, Entity entity, BlockPos center) {
        BlockPos fallback = null;
        for (BlockPos column : BlockPos.spiralAround(center, 6, Direction.EAST,
                Direction.SOUTH)) {
            if (!level.getWorldBorder().isWithinBounds(column)) {
                continue;
            }
            BlockPos landing = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (landing.getY() >= level.getMaxBuildHeight() - 2) {
                continue;
            }
            BlockPos floor = landing.below();
            BlockState support = level.getBlockState(floor);
            Vec3 offset = Vec3.atBottomCenterOf(landing).subtract(entity.position());
            if (!level.noCollision(entity, entity.getBoundingBox().move(offset)) || !level.getFluidState(landing).isEmpty()) {
                continue;
            }
            if (!support.is(NCWasteland.PORTAL.get()) && support.getFluidState().isEmpty()
                    && support.isFaceSturdy(level, floor, Direction.UP)
                    && !support.is(Blocks.MAGMA_BLOCK) && !support.is(NCFluidProducts.SOLIDIFIED_CORIUM.get())) {
                return landing;
            }
            if (fallback == null && level.isEmptyBlock(landing)) {
                fallback = landing;
            }
        }
        if (fallback != null) {
            BlockPos landing = fallback.above();
            Vec3 offset = Vec3.atBottomCenterOf(landing).subtract(entity.position());
            if (level.noCollision(entity, entity.getBoundingBox().move(offset))
                    && level.setBlockAndUpdate(fallback, NCFluidProducts.WASTELAND_EARTH.get().defaultBlockState())) {
                return landing;
            }
        }
        return null;
    }
}
