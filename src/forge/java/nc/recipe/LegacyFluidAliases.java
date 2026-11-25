package nc.recipe;

import nc.init.NCItemProcessors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.stream.Stream;

enum LegacyFluidAliases {
    MEKANISM("mekanism"),
    TECH_REBORN("techreborn");

    private final String namespace;

    LegacyFluidAliases(String namespace) {
        this.namespace = namespace;
    }

    boolean enabled() {
        return switch (this) {
            case MEKANISM -> NCItemProcessors.mekanismFluidRecipesEnabled();
            case TECH_REBORN -> NCItemProcessors.techRebornFluidRecipesEnabled();
        };
    }

    private List<String> sourceNames(String family) {
        return switch (this) {
            case MEKANISM -> {
                if (family.equals("helium")) {
                    yield List.of();
                }
                String legacy = switch (family) {
                    case "sulfur_dioxide", "sulfur_trioxide", "hydrogen_chloride" -> "liquid" + family.replace("_", "");
                    case "sulfuric_acid" -> "sulfuricacid";
                    default -> "liquid" + family;
                };
                yield List.of(family, legacy);
            }
            case TECH_REBORN -> {
                String name = family.equals("helium_3") ? "helium3" : family;
                yield List.of(name, "fluid" + name);
            }
        };
    }

    Stream<Fluid> sources(String family) {
        return sourceNames(family).stream()
                .map(name -> ForgeRegistries.FLUIDS.getValue(ResourceLocation.fromNamespaceAndPath(namespace, name)))
                .filter(fluid -> fluid != null && fluid != Fluids.EMPTY);
    }

    static LegacyFluidAliases matching(String family, Fluid fluid) {
        var id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        for (var source : values()) {
            if (source.namespace.equals(id.getNamespace())) {
                for (String name : source.sourceNames(family)) {
                    if (path.equals(name) || path.equals("flowing_" + name) || path.equals(name + "_flowing")) {
                        return source;
                    }
                }
                break;
            }
        }
        return null;
    }
}
