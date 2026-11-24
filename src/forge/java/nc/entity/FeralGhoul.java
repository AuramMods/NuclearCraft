package nc.entity;

import nc.init.NCEntities;
import nc.radiation.GhoulRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.EnumSet;

public class FeralGhoul extends Zombie {
    private static final EntityDataAccessor<Boolean> CLIMBING =
            SynchedEntityData.defineId(FeralGhoul.class, EntityDataSerializers.BOOLEAN);

    public FeralGhoul(EntityType<? extends FeralGhoul> type, Level level) {
        super(type, level);
    }

    public static final class Glowing extends FeralGhoul {
        public Glowing(EntityType<? extends FeralGhoul> type, Level level) {
            super(type, level);
        }

        @Override
        public boolean isGlowingGhoul() {
            return true;
        }
    }

    public boolean isGlowingGhoul() {
        return false;
    }

    public static AttributeSupplier.Builder attributes(boolean glowing) {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, glowing ? 32D : 16D)
                .add(Attributes.ARMOR, glowing ? 8D : 4D)
                .add(Attributes.ATTACK_DAMAGE, glowing ? 4D : 2D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, glowing ? 0.75D : 0.5D)
                .add(Attributes.FOLLOW_RANGE, glowing ? 32D : 16D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (!isGlowingGhoul()) {
            goalSelector.addGoal(1, new AttackLeap());
        }
        goalSelector.addGoal(2, new ZombieAttackGoal(this, 0.5D, false));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.5D));
        goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 0.5D, false, 4, this::canBreakDoors));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, isGlowingGhoul() ? 32F : 16F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CLIMBING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !NCEntities.ghoulEnabled(isGlowingGhoul())) {
            discard();
            return;
        }
        super.tick();
        if (!level().isClientSide) {
            entityData.set(CLIMBING, horizontalCollision);
        }
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING) || super.onClimbable();
    }

    public static boolean canSpawn(EntityType<FeralGhoul> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        return NCEntities.ghoulEnabled(false) && (level.canSeeSky(pos)
                ? checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)
                : checkMonsterSpawnRules(type, level, reason, pos, random));
    }

    public static boolean canSpawnGlowing(EntityType<FeralGhoul> type, ServerLevelAccessor level, MobSpawnType reason,
                                          BlockPos pos, RandomSource random) {
        return NCEntities.ghoulEnabled(true) && !level.canSeeSky(pos)
                && checkMonsterSpawnRules(type, level, reason, pos, random)
                && GhoulRadiation.canSpawnGlowing(level.getLevel(), pos);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        float special = difficulty.getSpecialMultiplier();
        getAttribute(Attributes.FOLLOW_RANGE).addPermanentModifier(new AttributeModifier(
                "Random spawn bonus", random.nextGaussian() * 0.05D, AttributeModifier.Operation.MULTIPLY_BASE));
        setLeftHanded(random.nextFloat() < 0.05F);
        setCanPickUpLoot(random.nextFloat() < 0.55F * special);
        populateDefaultEquipmentSlots(random, difficulty);
        populateDefaultEquipmentEnchantments(random, difficulty);
        LocalDate today = LocalDate.now();
        if (getItemBySlot(EquipmentSlot.HEAD).isEmpty() && today.getMonthValue() == 10
                && today.getDayOfMonth() == 31 && random.nextFloat() < 0.25F) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(
                    random.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
            setDropChance(EquipmentSlot.HEAD, 0F);
        }
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).addPermanentModifier(new AttributeModifier(
                "Random spawn bonus", random.nextDouble() * 0.05D, AttributeModifier.Operation.ADDITION));
        double followBonus = random.nextDouble() * 1.5D * special;
        if (followBonus > 1D) {
            getAttribute(Attributes.FOLLOW_RANGE).addPermanentModifier(new AttributeModifier(
                    "Random zombie-spawn bonus", followBonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (random.nextFloat() < special * 0.05F) {
            getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(new AttributeModifier(
                    "Leader zombie bonus", random.nextDouble() * 0.25D + 0.5D, AttributeModifier.Operation.ADDITION));
            getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(
                    "Leader zombie bonus", random.nextDouble() * 3D + 1D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        setHealth(getMaxHealth());
        return data;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean hit = super.doHurtTarget(entity);
        if (hit && entity instanceof LivingEntity target && !(target instanceof Enemy) && !level().isClientSide) {
            float scale = Mth.clamp(level().getCurrentDifficultyAt(blockPosition()).getEffectiveDifficulty(), 1F, 2F);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (25F * scale)), this);
            GhoulRadiation.poison(this, target, scale);
        }
        return hit;
    }

    @Override
    protected float getJumpPower() {
        return 0.5F;
    }

    @Override
    protected void jumpFromGround() {
        leap(1D);
    }

    private void leap(double boost) {
        var velocity = getDeltaMovement();
        setDeltaMovement(velocity.x * boost, getJumpPower() + getJumpBoostPower(), velocity.z * boost);
        hasImpulse = true;
        ForgeHooks.onLivingJump(this);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return 255;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {}

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return EntityType.HUSK.getDefaultLootTable();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return NCEntities.CHARGE.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return NCEntities.DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    public float getVoicePitch() {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + (isGlowingGhoul() ? 0F : 1F);
    }

    private final class AttackLeap extends Goal {
        private AttackLeap() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            var target = getTarget();
            if (!onGround() || target == null) {
                return false;
            }
            double distance = distanceToSqr(target);
            return distance > 16D && distance < 64D && random.nextInt(4) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !onGround();
        }

        @Override
        public void start() {
            leap(1.25D);
            playSound(NCEntities.CHARGE.get(), 1F, 1F + 0.2F * (random.nextFloat() - random.nextFloat()));
        }
    }
}
