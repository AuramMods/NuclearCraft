package nc.mixin.compat;

import nc.compat.CustomMachineryChemicalSaves;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "fr.frinn.custommachinerymekanism.common.component.handler.ChemicalComponentHandler", remap = false)
abstract class CustomMachineryChemicalPersistence {
    @ModifyConstant(method = "serialize", constant = @Constant(stringValue = CustomMachineryChemicalSaves.LEGACY_KEY))
    private String saveChemicalTanks(String originalKey) {
        var kind = CustomMachineryChemicalSaves.forHandler(this);
        return kind == null ? originalKey : kind.key();
    }

    @Redirect(method = "deserialize", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;m_128425_(Ljava/lang/String;I)Z"))
    private boolean containsChemicalTanks(CompoundTag data, String originalKey, int type) {
        var kind = CustomMachineryChemicalSaves.forHandler(this);
        return kind == null ? data.contains(originalKey, type)
                : data.contains(kind.key(), type) || data.contains(CustomMachineryChemicalSaves.LEGACY_KEY, type);
    }

    @Redirect(method = "deserialize", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;m_128437_(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;"))
    private ListTag loadChemicalTanks(CompoundTag data, String originalKey, int elementType) {
        var kind = CustomMachineryChemicalSaves.forHandler(this);
        if (kind == null) {
            return data.getList(originalKey, elementType);
        }
        return data.contains(kind.key(), Tag.TAG_LIST) ? data.getList(kind.key(), elementType)
                : kind.readLegacy(data, elementType);
    }
}
