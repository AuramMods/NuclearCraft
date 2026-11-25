package nc.radiation;

import nc.equipment.EquipmentConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public enum HazmatMaterial implements ArmorMaterial {
    INSTANCE;

    @Override public int getDurabilityForType(ArmorItem.Type type) { return EquipmentConfig.hazmatDurability(type); }
    @Override public int getDefenseForType(ArmorItem.Type type) { return EquipmentConfig.hazmatDefense(type); }
    @Override public int getEnchantmentValue() { return EquipmentConfig.hazmatEnchantability(); }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.LEATHER); }
    @Override public String getName() { return "nuclearcraft:hazmat"; }
    @Override public float getToughness() { return EquipmentConfig.hazmatToughness(); }
    @Override public float getKnockbackResistance() { return 0; }
}
