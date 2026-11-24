package nc.equipment;

import nc.equipment.EquipmentFamily.ToolTier;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.extensions.IForgeItem;
import net.minecraftforge.event.ItemAttributeModifierEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class EquipmentTools {
    private EquipmentTools() {}

    public enum Kind {
        SWORD(3F, -2.4F), PICKAXE(1F, -2.8F), SHOVEL(1.5F, -3F), AXE(0F, -3F),
        HOE(0F, -3F), SPAXELHOE(3F, -2.4F);

        private final float damage;
        private final float speed;

        Kind(float damage, float speed) {
            this.damage = damage;
            this.speed = speed;
        }

        public String id(EquipmentFamily family) {
            return name().toLowerCase(Locale.ROOT) + "_" + family.id();
        }

        public Item create(EquipmentFamily family) {
            ToolTier tier = family.tools(this == SPAXELHOE);
            return switch (this) {
                case SWORD -> new Sword(tier);
                case PICKAXE -> new Pickaxe(tier);
                case SHOVEL -> new Shovel(tier);
                case AXE -> new Axe(tier);
                case HOE -> new Hoe(tier);
                case SPAXELHOE -> new Spaxelhoe(tier);
            };
        }

        private float damage(ToolTier tier) {
            return this == HOE ? 0F : damage + tier.getAttackDamageBonus();
        }

        private float speed(ToolTier tier) {
            return this == HOE ? speed + tier.getAttackDamageBonus() : speed;
        }
    }

    public static void updateCombatAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (event.getSlotType() != EquipmentSlot.MAINHAND || !(stack.getItem() instanceof Configured tool)
                || (stack.getTag() != null && stack.getTag().contains("AttributeModifiers", Tag.TAG_LIST))) {
            return;
        }
        Kind kind = tool.kind();
        replaceModifier(event, Attributes.ATTACK_DAMAGE, kind.damage(tool.tier()));
        replaceModifier(event, Attributes.ATTACK_SPEED, kind.speed(tool.tier()));
    }

    private static void replaceModifier(ItemAttributeModifierEvent event, Attribute attribute, float amount) {
        for (AttributeModifier modifier : event.getOriginalModifiers().get(attribute)) {
            if (modifier.getAmount() != amount && event.removeModifier(attribute, modifier)) {
                event.addModifier(attribute, new AttributeModifier(modifier.getId(), modifier.getName(),
                        amount, modifier.getOperation()));
            }
        }
    }

    private interface Configured extends IForgeItem {
        Kind kind();
        ToolTier tier();

        @Override
        default int getMaxDamage(ItemStack stack) {
            return tier().getUses();
        }
    }

    private static final class Sword extends SwordItem implements Configured {
        private Sword(ToolTier tier) {
            super(tier, 3, Kind.SWORD.speed, new Item.Properties());
        }

        @Override public Kind kind() { return Kind.SWORD; }
        @Override public ToolTier tier() { return (ToolTier) getTier(); }
    }

    private static final class Pickaxe extends PickaxeItem implements Configured {
        private Pickaxe(ToolTier tier) {
            super(tier, 1, Kind.PICKAXE.speed, new Item.Properties());
        }

        @Override public Kind kind() { return Kind.PICKAXE; }
        @Override public ToolTier tier() { return (ToolTier) getTier(); }

        @Override
        public float getDestroySpeed(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? tier().getSpeed() : 1F;
        }

        @Override
        public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_PICKAXE) && tier().canHarvest(state);
        }
    }

    private static final class Shovel extends ShovelItem implements Configured {
        private Shovel(ToolTier tier) {
            super(tier, Kind.SHOVEL.damage, Kind.SHOVEL.speed, new Item.Properties());
        }

        @Override public Kind kind() { return Kind.SHOVEL; }
        @Override public ToolTier tier() { return (ToolTier) getTier(); }

        @Override
        public float getDestroySpeed(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? tier().getSpeed() : 1F;
        }

        @Override
        public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_SHOVEL) && tier().canHarvest(state);
        }
    }

    private static final class Axe extends AxeItem implements Configured {
        private Axe(ToolTier tier) {
            super(tier, Kind.AXE.damage, Kind.AXE.speed, new Item.Properties());
        }

        @Override public Kind kind() { return Kind.AXE; }
        @Override public ToolTier tier() { return (ToolTier) getTier(); }

        @Override
        public float getDestroySpeed(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_AXE) ? tier().getSpeed() : 1F;
        }

        @Override
        public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_AXE) && tier().canHarvest(state);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            return wearOnHit(stack, attacker);
        }
    }

    private static class Hoe extends HoeItem implements Configured {
        private Hoe(ToolTier tier) {
            this(tier, Kind.HOE);
        }

        private Hoe(ToolTier tier, Kind kind) {
            super(tier, kind == Kind.HOE ? 0 : 3, kind.speed(tier), new Item.Properties());
        }

        @Override public Kind kind() { return Kind.HOE; }
        @Override public ToolTier tier() { return (ToolTier) getTier(); }

        @Override
        public float getDestroySpeed(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_HOE) ? tier().getSpeed() : 1F;
        }

        @Override
        public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
            return state.is(BlockTags.MINEABLE_WITH_HOE) && tier().canHarvest(state);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            return wearOnHit(stack, attacker);
        }
    }

    private static final class Spaxelhoe extends Hoe {
        private static final Set<ToolAction> ACTIONS = Set.of(ToolActions.AXE_DIG, ToolActions.PICKAXE_DIG,
                ToolActions.SHOVEL_DIG, ToolActions.HOE_DIG, ToolActions.HOE_TILL, ToolActions.SWORD_DIG);

        private Spaxelhoe(ToolTier tier) {
            super(tier, Kind.SPAXELHOE);
        }

        @Override public Kind kind() { return Kind.SPAXELHOE; }

        @Override
        public float getDestroySpeed(ItemStack stack, BlockState state) {
            return tier().getSpeed();
        }

        @Override
        public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
            return tier().canHarvest(state);
        }

        @Override
        public boolean canPerformAction(ItemStack stack, ToolAction action) {
            return ACTIONS.contains(action);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
            lines.add(Component.translatable(getDescriptionId() + ".desc"));
        }
    }

    private static boolean wearOnHit(ItemStack stack, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }
}
