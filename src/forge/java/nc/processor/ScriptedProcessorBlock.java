package nc.processor;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import nc.init.NCItemProcessors;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public final class ScriptedProcessorBlock extends ProcessorBlock<ItemProcessorBlockEntity> {
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

    private final List<String> particleNames;
    private List<ParticleOptions> particles;

    public ScriptedProcessorBlock(Properties properties, ProcessorProfile machine, List<String> particles) {
        super(properties, machine::entityType, ItemProcessorBlockEntity::tick, ItemProcessorBlockEntity::inventory);
        particleNames = List.copyOf(particles);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || particleNames.isEmpty() || !NCItemProcessors.processorParticles()) {
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
}
