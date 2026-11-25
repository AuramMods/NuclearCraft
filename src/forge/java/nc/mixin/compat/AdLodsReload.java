package nc.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {
        "com.endertech.minecraft.mods.adlods.init.Features",
        "com.endertech.minecraft.mods.adlods.target.AbstractTargetGenerator"
}, remap = false)
abstract class AdLodsReload {}
