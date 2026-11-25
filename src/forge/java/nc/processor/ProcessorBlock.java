package nc.processor;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Map.entry;

public class ProcessorBlock<E extends BlockEntity> extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, String> LEGACY_PARTICLES = Map.ofEntries(
            entry("depthsuspend", "mycelium"), entry("largesmoke", "large_smoke"),
            entry("suspended", "underwater"), entry("explode", "poof"),
            entry("largeexplode", "explosion"), entry("hugeexplosion", "explosion_emitter"),
            entry("fireworksSpark", "firework"), entry("wake", "fishing"),
            entry("magicCrit", "enchanted_hit"), entry("spell", "effect"),
            entry("instantSpell", "instant_effect"), entry("mobSpell", "entity_effect"),
            entry("mobSpellAmbient", "ambient_entity_effect"), entry("witchMagic", "witch"),
            entry("dripWater", "dripping_water"), entry("dripLava", "dripping_lava"),
            entry("angryVillager", "angry_villager"), entry("happyVillager", "happy_villager"),
            entry("townaura", "mycelium"), entry("enchantmenttable", "enchant"),
            entry("snowshovel", "item_snowball"), entry("snowballpoof", "item_snowball"),
            entry("slime", "item_slime"), entry("droplet", "rain"),
            entry("dragonbreath", "dragon_breath"), entry("endRod", "end_rod"),
            entry("damageIndicator", "damage_indicator"), entry("fallingdust", "falling_dust"),
            entry("totem", "totem_of_undying"));

    private final Supplier<BlockEntityType<E>> entityType;
    private final BlockEntityTicker<E> ticker;
    private final @Nullable Function<E, ItemStackHandler> inventory;
    private final List<String> particleNames;
    private final BooleanSupplier enabled;
    private List<ParticleOptions> particles;

    public ProcessorBlock(Properties properties, Supplier<BlockEntityType<E>> entityType,
                          BlockEntityTicker<E> ticker, @Nullable Function<E, ItemStackHandler> inventory) {
        this(properties, entityType, ticker, inventory, List.of());
    }

    public ProcessorBlock(Properties properties, Supplier<BlockEntityType<E>> entityType,
                          BlockEntityTicker<E> ticker, @Nullable Function<E, ItemStackHandler> inventory,
                          Rate rate) {
        this(properties, entityType, ticker, inventory, rate.particles(), rate::enabled);
    }

    public ProcessorBlock(Properties properties, Supplier<BlockEntityType<E>> entityType,
                          BlockEntityTicker<E> ticker, @Nullable Function<E, ItemStackHandler> inventory,
                          List<String> particleNames) {
        this(properties, entityType, ticker, inventory, particleNames, () -> true);
    }

    private ProcessorBlock(Properties properties, Supplier<BlockEntityType<E>> entityType,
                           BlockEntityTicker<E> ticker, @Nullable Function<E, ItemStackHandler> inventory,
                           List<String> particleNames, BooleanSupplier enabled) {
        super(properties);
        this.entityType = entityType;
        this.ticker = ticker;
        this.inventory = inventory;
        this.particleNames = List.copyOf(particleNames);
        this.enabled = enabled;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || particleNames.isEmpty() || !enabled.getAsBoolean() || !NCItemProcessors.processorParticles()) {
            return;
        }
        if (particles == null) {
            particles = resolveParticles();
        }
        var face = state.getValue(FACING);
        for (ParticleOptions particle : particles) {
            double offset = random.nextDouble() * 0.6D - 0.3D;
            double x = pos.getX() + 0.5D + face.getStepX() * 0.52D + Math.abs(face.getStepZ()) * offset;
            double z = pos.getZ() + 0.5D + face.getStepZ() * 0.52D + Math.abs(face.getStepX()) * offset;
            double y = pos.getY() + 0.125D + random.nextDouble() * 0.75D;
            level.addParticle(particle, x, y, z, 0D, 0D, 0D);
        }
    }

    private List<ParticleOptions> resolveParticles() {
        List<ParticleOptions> options = new ArrayList<>(particleNames.size());
        for (String name : particleNames) {
            if (name.equals("reddust") || name.equals("redstone")) {
                options.add(DustParticleOptions.REDSTONE);
                continue;
            }
            var reader = new StringReader(LEGACY_PARTICLES.getOrDefault(name, name));
            try {
                ParticleOptions particle = ParticleArgument.readParticle(reader, BuiltInRegistries.PARTICLE_TYPE.asLookup());
                reader.skipWhitespace();
                if (reader.canRead()) {
                    LOGGER.warn("Ignoring trailing particle arguments '{}' on {}", name, this);
                } else {
                    options.add(particle);
                }
            } catch (CommandSyntaxException error) {
                LOGGER.warn("Ignoring processor particle '{}' on {}: {}", name, this, error.getMessage());
            }
        }
        return List.copyOf(options);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return enabled.getAsBoolean() ? defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return entityType.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, entityType.get(), ticker);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MenuProvider menu)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (menu instanceof UpgradableProcessorBlockEntity processor && processor.installUpgrade(player, hand, hit.getDirection())) {
                return InteractionResult.CONSUME;
            }
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
                return InteractionResult.CONSUME;
            }
            player.openMenu(menu);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())) {
            E entity = entityType.get().getBlockEntity(level, pos);
            if (entity instanceof UpgradableProcessorBlockEntity processor) {
                processor.dropAuxiliaryInventory(level, pos);
            }
            if (entity != null && inventory != null) {
                ItemStackHandler slots = inventory.apply(entity);
                for (int slot = 0; slot < slots.getSlots(); slot++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), slots.getStackInSlot(slot));
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    static void setRunning(Level level, BlockPos pos, BlockState state, boolean running) {
        if (state.getValue(LIT) != running) {
            level.setBlock(pos, state.setValue(LIT, running), Block.UPDATE_CLIENTS);
        }
    }
}
