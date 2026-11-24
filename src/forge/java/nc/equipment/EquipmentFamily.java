package nc.equipment;

import nc.NuclearCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.Locale;
import java.util.Objects;

public enum EquipmentFamily implements ArmorMaterial {
    BORON("ingots/boron", "ores/boron"),
    TOUGH("ingots/tough", "ingots/tough"),
    HARD_CARBON("ingots/hard_carbon", "ingots/hard_carbon"),
    BORON_NITRIDE("gems/boron_nitride", "gems/boron_nitride");

    private final TagKey<Item> toolRepair;
    private final TagKey<Item> armorRepair;
    private final ToolTier tools = new ToolTier(false);
    private final ToolTier spaxelhoe = new ToolTier(true);

    EquipmentFamily(String toolRepair, String armorRepair) {
        this.toolRepair = forgeTag(toolRepair);
        this.armorRepair = forgeTag(armorRepair);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    ToolTier tools(boolean combined) {
        return combined ? spaxelhoe : tools;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case BOOTS -> 13;
            case LEGGINGS -> 15;
            case CHESTPLATE -> 16;
            case HELMET -> 11;
        };
        return base * EquipmentConfig.armorDurability(ordinal());
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return EquipmentConfig.armorDefense(ordinal(), type.getSlot().getIndex());
    }

    @Override public int getEnchantmentValue() { return EquipmentConfig.armorEnchantability(ordinal()); }
    @Override public SoundEvent getEquipSound() {
        return this == BORON_NITRIDE ? SoundEvents.ARMOR_EQUIP_DIAMOND : SoundEvents.ARMOR_EQUIP_IRON;
    }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(armorRepair); }
    @Override public String getName() { return NuclearCraft.id(id()).toString(); }
    @Override public float getToughness() { return EquipmentConfig.armorToughness(ordinal()); }
    @Override public float getKnockbackResistance() { return 0F; }

    private static TagKey<Item> forgeTag(String path) {
        return TagKey.create(Registries.ITEM, Objects.requireNonNull(ResourceLocation.tryBuild("forge", path), path));
    }

    final class ToolTier implements Tier {
        private final int index;

        private ToolTier(boolean spaxel) {
            index = 2 * ordinal() + (spaxel ? 1 : 0);
        }

        @Override public int getUses() { return EquipmentConfig.toolDurability(index); }
        @Override public float getSpeed() { return EquipmentConfig.toolSpeed(index); }
        @Override public float getAttackDamageBonus() { return EquipmentConfig.toolDamage(index); }
        @Override public int getLevel() { return EquipmentConfig.miningLevel(index); }
        @Override public int getEnchantmentValue() { return EquipmentConfig.toolEnchantability(index); }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(toolRepair); }

        boolean canHarvest(BlockState state) {
            int level = getLevel();
            Tier comparable = switch (level) {
                case 0 -> Tiers.WOOD;
                case 1 -> Tiers.STONE;
                case 2 -> Tiers.IRON;
                case 3 -> Tiers.DIAMOND;
                default -> Tiers.NETHERITE;
            };
            if (level > Tiers.NETHERITE.getLevel()) {
                for (Tier tier : TierSortingRegistry.getSortedTiers()) {
                    if (tier.getLevel() > Tiers.NETHERITE.getLevel() && tier.getLevel() <= level) {
                        comparable = tier;
                    }
                }
            }
            return TierSortingRegistry.isCorrectTierForDrops(comparable, state);
        }
    }
}
