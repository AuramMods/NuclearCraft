package nc.equipment;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

public class EquipmentArmor extends ArmorItem {
    public EquipmentArmor(ArmorMaterial material, Type type) {
        super(material, type, new Properties());
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return getMaterial().getDurabilityForType(getType());
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return getMaxDamage(stack) > 0;
    }

    @Override
    public int getDefense() {
        return getMaterial().getDefenseForType(getType());
    }

    @Override
    public float getToughness() {
        return getMaterial().getToughness();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        var defaults = super.getDefaultAttributeModifiers(slot);
        if (slot != getEquipmentSlot()) {
            return defaults;
        }
        var modifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder();
        defaults.forEach((attribute, modifier) -> {
            double amount = modifier.getAmount();
            if (attribute == Attributes.ARMOR) {
                amount = getDefense();
            } else if (attribute == Attributes.ARMOR_TOUGHNESS) {
                amount = getToughness();
            }
            modifiers.put(attribute, amount == modifier.getAmount() ? modifier : new AttributeModifier(
                    modifier.getId(), modifier.getName(), amount, modifier.getOperation()));
        });
        return modifiers.build();
    }
}
