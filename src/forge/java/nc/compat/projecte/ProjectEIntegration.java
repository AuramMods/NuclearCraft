package nc.compat.projecte;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import nc.init.NCFluidProducts;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Map;

import static java.util.Map.entry;

public final class ProjectEIntegration {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue REGISTER_EMC;

    static {
        var builder = new ForgeConfigSpec.Builder();
        REGISTER_EMC = builder.comment("Supply NuclearCraft's legacy material EMC values when ProjectE is installed.")
                .worldRestart().define("register_projecte_emc", true);
        SPEC = builder.build();
    }

    private ProjectEIntegration() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "nuclearcraft-projecte.toml");
    }

    // ProjectE runs higher-priority mappers first, so its custom values can replace these defaults.
    @EMCMapper(priority = 100, requiredMods = "projecte")
    public static final class MaterialMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
        private static final Map<String, Integer> METALS = Map.ofEntries(
                entry("iron", 256), entry("gold", 2048), entry("copper", 128), entry("tin", 256),
                entry("lead", 512), entry("thorium", 2048), entry("uranium", 4096), entry("boron", 512),
                entry("lithium", 512), entry("magnesium", 512), entry("graphite", 64), entry("beryllium", 128),
                entry("zirconium", 128), entry("manganese", 128), entry("aluminum", 128), entry("silver", 512),
                entry("manganese_oxide", 128), entry("manganese_dioxide", 128), entry("zirconia", 128),
                entry("palladium", 8192), entry("tin_oxide", 256), entry("nickel", 1024),
                entry("nickel_oxide", 1024), entry("cobalt", 4096), entry("cobalt_oxide", 4096),
                entry("ruthenium", 8192), entry("ruthenium_oxide", 8192), entry("iridium", 8192),
                entry("iridium_oxide", 8192), entry("holmium", 2048), entry("dysprosium", 2048),
                entry("gadolinium", 2048), entry("samarium", 2048), entry("copper_oxide", 128),
                entry("platinum", 8192), entry("platinum_oxide", 8192), entry("tough", 464),
                entry("hard_carbon", 4160), entry("magnesium_diboride", 512),
                entry("lithium_manganese_dioxide", 320), entry("steel", 320), entry("ferroboron", 416),
                entry("shibuichi", 448), entry("tin_silver", 640), entry("lead_platinum", 2432),
                entry("extreme", 4624), entry("thermoconducting", 2600), entry("zircaloy", 144),
                entry("silicon_carbide", 32), entry("hsla_steel", 246));
        private static final Map<String, Integer> GEMS = Map.of(
                "diamond", 8192, "rhodochrosite", 64, "boron_nitride", 256, "fluorite", 64,
                "villiaumite", 64, "carobbiite", 64, "arsenic", 64, "boron_arsenide", 576);
        private static final Map<String, Integer> DUSTS = Map.ofEntries(
                entry("calcium_sulfate", 96), entry("crystal_binder", 344), entry("energetic", 224),
                entry("sodium_fluoride", 64), entry("potassium_fluoride", 64), entry("sodium_hydroxide", 64),
                entry("potassium_hydroxide", 64), entry("borax", 1152), entry("dimensional", 32),
                entry("carbon_manganese", 96), entry("alugentum", 1024),
                entry("obsidian", 16), entry("end_stone", 1));

        @Override
        public String getName() {
            return "NuclearCraftMaterialMapper";
        }

        @Override
        public String getDescription() {
            return "Apply the legacy NuclearCraft EMC values to ingots, gems, dusts, and storage blocks.";
        }

        @Override
        public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> collector, CommentedFileConfig config,
                                ReloadableServerResources resources, RegistryAccess registries,
                                ResourceManager resourceManager) {
            if (!REGISTER_EMC.get()) {
                return;
            }
            METALS.forEach((name, emc) -> addMaterial(collector, "ingots/", name, emc));
            GEMS.forEach((name, emc) -> addMaterial(collector, "gems/", name, emc));
            DUSTS.forEach((name, emc) -> addTag(collector, "dusts/" + name, emc));
            for (String quartz : new String[]{"quartz", "nether_quartz"}) {
                addTag(collector, "gems/" + quartz, 256);
                addTag(collector, "dusts/" + quartz, 256);
            }
            addTag(collector, "silicon", 1);
            addTag(collector, "gems/silicon", 1);
            addTag(collector, "ingots/silicon", 1);
            collector.setValueBefore(NSSItem.createItem(NCFluidProducts.WASTELAND_EARTH.getId()), 1L);
        }

        private static void addMaterial(IMappingCollector<NormalizedSimpleStack, Long> collector,
                                        String form, String name, long emc) {
            addTag(collector, form + name, emc);
            addTag(collector, "dusts/" + name, emc);
            addTag(collector, "storage_blocks/" + name, 9 * emc);
        }

        private static void addTag(IMappingCollector<NormalizedSimpleStack, Long> collector, String path, long emc) {
            var tag = NSSItem.createTag(ResourceLocation.fromNamespaceAndPath("forge", path));
            collector.setValueBefore(tag, emc);
            tag.forEachElement(item -> collector.setValueBefore(item, emc));
        }
    }
}
