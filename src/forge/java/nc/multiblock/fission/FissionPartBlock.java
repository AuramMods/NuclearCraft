package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class FissionPartBlock extends Block {
    public static final int MAX_INTERIOR_LENGTH = 24;
    private static final VoxelShape HEAT_CONTACT_SHAPE = box(0.032, 0.032, 0.032, 15.968, 15.968, 15.968);
    private static final ResourceKey<DamageType> FISSION_BURN =
            ResourceKey.create(Registries.DAMAGE_TYPE, NuclearCraft.id("fission_burn"));

    public enum Role {
        CASING, GLASS, MONITOR, POWER_PORT, CONTROLLER, PEBBLE_CONTROLLER, SALT_CONTROLLER, VENT, FUEL_PORT, CHAMBER_PORT,
        COOLER_PORT, VESSEL_PORT, HEATER_PORT, SOURCE, SOURCE_MANAGER, SHIELD_MANAGER, IRRADIATOR_PORT,
        CELL, PEBBLE_CHAMBER, VESSEL, HEAT_SINK, PEBBLE_COOLER, SALT_HEATER, MODERATOR, REFLECTOR, SHIELD, IRRADIATOR, CONDUCTOR
    }

    private final Role role;

    public FissionPartBlock(Properties properties, Role role) {
        super(properties.dynamicShape());
        this.role = role;
    }

    static Role role(BlockState state) {
        return state.getBlock() instanceof FissionPartBlock part ? part.role : null;
    }

    static boolean isClusterComponent(BlockState state) {
        Role role = role(state);
        return role != null && switch (role) {
            case CELL, PEBBLE_CHAMBER, VESSEL, HEAT_SINK, PEBBLE_COOLER, SALT_HEATER, SHIELD, IRRADIATOR, CONDUCTOR -> true;
            default -> false;
        };
    }

    static BlockPos legacyPosition(long position) {
        // Minecraft 1.12.2 placed the 12 Y bits between X and Z.
        return new BlockPos((int) (position >> 38), (int) (position << 26 >> 52), (int) (position << 38 >> 38));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return NCFission.heatDamage() ? HEAT_CONTACT_SHAPE : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (NCFission.heatDamage() && level instanceof ServerLevel server && entity instanceof LivingEntity living) {
            Cuboid.visitNearby(server, new ChunkPos(pos), MAX_INTERIOR_LENGTH + 2, SolidFissionController.class,
                    controller -> {
                        float damage = controller.contactDamage(server, pos);
                        if (damage > 0F) {
                            var source = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FISSION_BURN);
                            living.hurt(new DamageSource(source), damage);
                            if (damage > 5F) {
                                living.setSecondsOnFire((int) (damage - 4F));
                            }
                        }
                    });
        }
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server && !state.is(previous.getBlock())) {
            geometryChanged(server, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())) {
            geometryChanged(server, pos, true);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    static void geometryChanged(ServerLevel level, BlockPos pos) {
        geometryChanged(level, pos, false);
    }

    private static void geometryChanged(ServerLevel level, BlockPos pos, boolean removed) {
        Cuboid.visitNearby(level, new ChunkPos(pos), MAX_INTERIOR_LENGTH + 2, SolidFissionController.class,
                controller -> controller.partChanged(pos, removed));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            Cuboid.visitNearby(level, pos, MAX_INTERIOR_LENGTH + 2, SolidFissionController.class,
                    controller -> controller.chunkUnloaded(pos));
        }
    }
}
