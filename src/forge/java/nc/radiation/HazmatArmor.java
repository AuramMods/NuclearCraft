package nc.radiation;

import nc.equipment.EquipmentArmor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class HazmatArmor extends EquipmentArmor {
    public HazmatArmor(Type type) {
        super(HazmatMaterial.INSTANCE, type);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return 0;
    }
}
