package nc.compat.tconstruct;

import nc.NuclearCraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.armor.OnAttackedModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.mining.BlockBreakModifierHook;
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public final class TinkersModifiers {
    private TinkersModifiers() {}

    public static void register(IEventBus bus) {
        var modifiers = ModifierDeferredRegister.create(NuclearCraft.MOD_ID);
        modifiers.register("uplifting", Uplifting::new);
        modifiers.register("withering", () -> new RetaliatingEffect(MobEffects.WITHER, 81));
        modifiers.register("poisonous", () -> new RetaliatingEffect(MobEffects.POISON, 101));
        modifiers.register(bus);
    }

    private static final class Uplifting extends Modifier implements BlockBreakModifierHook, MeleeHitModifierHook,
            OnAttackedModifierHook {
        @Override
        protected void registerHooks(ModuleHookMap.Builder hooks) {
            hooks.addHook(this, ModifierHooks.BLOCK_BREAK, ModifierHooks.MELEE_HIT, ModifierHooks.ON_ATTACKED);
        }

        @Override
        public void afterBlockBreak(IToolStackView tool, ModifierEntry modifier, ToolHarvestContext context) {
            uplift(context.getLiving());
        }

        @Override
        public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
            uplift(context.getAttacker());
        }

        @Override
        public void onAttacked(IToolStackView tool, ModifierEntry modifier, EquipmentContext context, EquipmentSlot slot,
                               DamageSource source, float damage, boolean directDamage) {
            uplift(context.getEntity());
        }

        private static void uplift(LivingEntity user) {
            if (user != null && !user.level().isClientSide && user.getRandom().nextBoolean()) {
                user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 81, 2, false, false));
            }
        }
    }

    private static final class RetaliatingEffect extends Modifier implements MeleeHitModifierHook, OnAttackedModifierHook {
        private final MobEffect effect;
        private final int hitDuration;

        RetaliatingEffect(MobEffect effect, int hitDuration) {
            this.effect = effect;
            this.hitDuration = hitDuration;
        }

        @Override
        protected void registerHooks(ModuleHookMap.Builder hooks) {
            hooks.addHook(this, ModifierHooks.MELEE_HIT, ModifierHooks.ON_ATTACKED);
        }

        @Override
        public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
            LivingEntity target = context.getLivingTarget();
            if (target != null && target.isAlive() && !target.level().isClientSide) {
                target.addEffect(new MobEffectInstance(effect, hitDuration));
            }
        }

        @Override
        public void onAttacked(IToolStackView tool, ModifierEntry modifier, EquipmentContext context, EquipmentSlot slot,
                               DamageSource source, float damage, boolean directDamage) {
            if (source.getDirectEntity() instanceof LivingEntity target && !target.level().isClientSide) {
                target.addEffect(new MobEffectInstance(effect, 81));
            }
        }
    }
}
