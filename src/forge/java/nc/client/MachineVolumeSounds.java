package nc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

final class MachineVolumeSounds<T extends BlockEntity> {
    private final Map<T, List<CornerSound<T>>> active = new WeakHashMap<>();
    private final Supplier<SoundEvent> sound;
    private final ToDoubleFunction<T> volume;

    MachineVolumeSounds(Supplier<SoundEvent> sound, ToDoubleFunction<T> volume) {
        this.sound = sound;
        this.volume = volume;
    }

    void play(T machine, BlockPos min, int width, int height, int depth) {
        play(machine, () -> corners(min, width, height, depth));
    }

    void play(T machine, Collection<BlockPos> positions) {
        play(machine, () -> positions);
    }

    private void play(T machine, Supplier<? extends Collection<BlockPos>> locations) {
        var minecraft = Minecraft.getInstance();
        float currentVolume = (float) volume.applyAsDouble(machine);
        if (currentVolume <= 0 || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                || minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS) == 0) return;
        var running = active.get(machine);
        if (running != null && running.stream().anyMatch(loop -> !loop.isStopped())) return;
        var positions = locations.get();
        var loops = new ArrayList<CornerSound<T>>(positions.size());
        for (var position : positions) {
            var loop = new CornerSound<>(sound.get(), machine, position, currentVolume, volume);
            minecraft.getSoundManager().play(loop);
            loops.add(loop);
        }
        active.put(machine, loops);
    }

    private static Collection<BlockPos> corners(BlockPos min, int width, int height, int depth) {
        var positions = new HashSet<BlockPos>();
        for (int i = 0; i < 8; i++) {
            positions.add(min.offset((i & 1) == 0 ? 0 : width - 1,
                    (i & 2) == 0 ? 0 : height - 1, (i & 4) == 0 ? 0 : depth - 1));
        }
        return positions;
    }

    private static final class CornerSound<T extends BlockEntity> extends AbstractTickableSoundInstance {
        private final WeakReference<T> machine;
        private final ToDoubleFunction<T> volumeSource;

        CornerSound(SoundEvent sound, T machine, BlockPos corner, float volume, ToDoubleFunction<T> volumeSource) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.machine = new WeakReference<>(machine);
            this.volumeSource = volumeSource;
            this.volume = volume;
            x = corner.getX() + 0.5;
            y = corner.getY() + 0.5;
            z = corner.getZ() + 0.5;
            looping = true;
        }

        @Override public void tick() {
            var owner = machine.get();
            float current = owner == null || owner.isRemoved() ? 0 : (float) volumeSource.applyAsDouble(owner);
            if (current <= 0) stop();
            else volume = current;
        }
    }
}
