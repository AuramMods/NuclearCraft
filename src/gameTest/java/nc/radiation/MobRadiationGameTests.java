package nc.radiation;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCRadiation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobRadiationGameTests {
    private MobRadiationGameTests() {}

    @GameTest(template = "empty")
    public static void mobsRetainAbsorbedDoseAndUseTheLegacyEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        var cow = EntityType.COW.create(level);
        var husk = EntityType.HUSK.create(level);
        MobRadiation.tick(cow, 2.5, 100);
        MobRadiation.tick(husk, 5, 100);
        var passive = MobRadiation.reading(cow);
        var hostile = MobRadiation.reading(husk);
        helper.assertTrue(passive.dose() == 250 && passive.maximum() == 500 && passive.rate() == 2.5,
                "Cow exposure uses a cap of fifty times maximum health");
        helper.assertTrue(hostile.dose() == 500 && hostile.maximum() == 1000 && hostile.rate() == 5,
                "Hostile exposure uses the same dose and health scaling");
        helper.assertTrue(cow.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
                        && !cow.hasEffect(MobEffects.MOVEMENT_SPEED),
                "Passive radiation applies the default sickness tier");
        helper.assertTrue(husk.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1
                        && !husk.hasEffect(MobEffects.WEAKNESS),
                "Hostile radiation applies a buff at the same dose fraction");

        var saved = cow.saveWithoutId(new CompoundTag());
        var restored = EntityType.COW.create(level);
        restored.load(saved);
        helper.assertTrue(MobRadiation.reading(restored).equals(passive), "Mob radiation survives an entity save");
        var message = new AtomicReference<Component>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MobGeiger")) {
            @Override
            public void sendSystemMessage(Component text) {
                message.set(text);
            }
        };
        var counter = NCRadiation.GEIGER_COUNTER.get().getDefaultInstance();
        helper.assertTrue(counter.interactLivingEntity(player, restored, InteractionHand.MAIN_HAND).consumesAction()
                        && message.get() != null && message.get().getString().contains("2.5 Rad/t"),
                "The Geiger counter reports the measured mob rate");
        MobRadiation.tick(restored, 4, 100);
        helper.assertTrue(!restored.isAlive(), "A fatal passive dose kills through the radiation damage type");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobsStoreFoodRadiationAndApplyInternalResistanceAfterReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var cow = new EatingCow(level);
        cow.eatGoldenApple();
        var data = cow.getPersistentData().getCompound(MobRadiation.DATA);
        helper.assertTrue(cow.getMainHandItem().isEmpty() && data.getDouble(RadiationTreatment.RESISTANCE) == 0.1D
                        && data.getDouble(MedicineItem.Kind.RADAWAY.id()) == 20D,
                "Completing the actual use of a golden apple stores resistance and buffered treatment");

        var loaded = EntityType.COW.create(level);
        loaded.load(cow.saveWithoutId(new CompoundTag()));
        MobRadiation.tick(loaded, 1D, 10D);
        var reading = MobRadiation.reading(loaded);
        helper.assertTrue(Math.abs(reading.rate() - 1D / 1.1D) < 1E-10
                        && Math.abs(reading.dose() - 10D / 1.1D) < 1E-10,
                "The saved food resistance attenuates the applied ambient rate");
        data = loaded.getPersistentData().getCompound(MobRadiation.DATA);
        helper.assertTrue(data.getDouble(MedicineItem.Kind.RADAWAY.id()) == 20D
                        && data.getDouble(RadiationTreatment.RESISTANCE) == 0.1D,
                "Mob updates preserve the queued treatment and resistance like the legacy sampler");
        helper.succeed();
    }

    private static final class EatingCow extends Cow {
        EatingCow(Level level) {
            super(EntityType.COW, level);
            setSilent(true);
        }

        void eatGoldenApple() {
            setItemInHand(InteractionHand.MAIN_HAND, Items.GOLDEN_APPLE.getDefaultInstance());
            startUsingItem(InteractionHand.MAIN_HAND);
            completeUsingItem();
        }
    }
}
