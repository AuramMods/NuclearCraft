package nc.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

public enum CustomMachineryChemicalSaves {
    GAS("gas_tanks", "gasName"),
    INFUSION("infusion_tanks", "infuseTypeName"),
    PIGMENT("pigment_tanks", "pigmentName"),
    SLURRY("slurry_tanks", "slurryName");

    public static final String LEGACY_KEY = "gases";

    private final String key;
    private final String chemicalName;

    CustomMachineryChemicalSaves(String key, String chemicalName) {
        this.key = key;
        this.chemicalName = chemicalName;
    }

    public static @Nullable CustomMachineryChemicalSaves forHandler(Object handler) {
        return switch (handler.getClass().getSimpleName()) {
            case "GasComponentHandler" -> GAS;
            case "InfusionComponentHandler" -> INFUSION;
            case "PigmentComponentHandler" -> PIGMENT;
            case "SlurryComponentHandler" -> SLURRY;
            default -> null;
        };
    }

    public String key() {
        return key;
    }

    public ListTag readLegacy(CompoundTag data, int elementType) {
        ListTag list = data.getList(LEGACY_KEY, elementType);
        boolean hasStack = false;
        // The old key held whichever chemical handler wrote last.
        for (Tag entry : list) {
            if (entry instanceof CompoundTag tank && tank.contains("stack", Tag.TAG_COMPOUND)) {
                if (tank.getCompound("stack").contains(chemicalName, Tag.TAG_STRING)) {
                    return list;
                }
                hasStack = true;
            }
        }
        return hasStack ? new ListTag() : list;
    }
}
