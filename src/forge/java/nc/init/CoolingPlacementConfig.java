package nc.init;

import com.mojang.logging.LogUtils;
import nc.multiblock.fission.FissionPlacementRules;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

final class CoolingPlacementConfig<C> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Supplier<Map<C, String>> scriptedRules;
    private final FissionPlacementRules.Part coolingPart;
    private final Function<C, String> name;
    private final Map<C, String> defaults;
    private final Map<C, ForgeConfigSpec.ConfigValue<String>> configured;
    private final FissionPlacementRules<C> defaultRules;
    private volatile FissionPlacementRules<C> rules;

    CoolingPlacementConfig(ForgeConfigSpec.Builder builder, List<C> coolants, FissionPlacementRules.Part coolingPart,
                           Function<C, String> name, String[] defaultValues) {
        this(builder, coolants, coolingPart, name, defaultValues, Map::of);
    }

    CoolingPlacementConfig(ForgeConfigSpec.Builder builder, List<C> coolants, FissionPlacementRules.Part coolingPart,
                           Function<C, String> name, String[] defaultValues, Supplier<Map<C, String>> scriptedRules) {
        this.scriptedRules = scriptedRules;
        this.coolingPart = coolingPart;
        this.name = name;
        defaults = new LinkedHashMap<>();
        configured = new LinkedHashMap<>();
        builder.comment("Placement conditions for each cooler or heater. These use the legacy fission rule syntax.")
                .push("placement_rules");
        for (int index = 0; index < coolants.size(); index++) {
            C coolant = coolants.get(index);
            String text = defaultValues[index];
            defaults.put(coolant, text);
            configured.put(coolant, builder.define(name.apply(coolant), text));
        }
        builder.pop();
        defaultRules = FissionPlacementRules.compile(coolingPart, name, defaults, Map.of(),
                message -> { throw new IllegalArgumentException(message); });
        rules = defaultRules;
    }

    void register(FMLJavaModLoadingContext context, ForgeConfigSpec spec, String filename) {
        context.getModEventBus().addListener((ModConfigEvent.Loading event) -> refresh(event, spec));
        context.getModEventBus().addListener((ModConfigEvent.Reloading event) -> refresh(event, spec));
        context.getModEventBus().addListener((ModConfigEvent.Unloading event) -> {
            if (event.getConfig().getSpec() == spec) {
                rules = defaultRules;
            }
        });
        context.registerConfig(ModConfig.Type.SERVER, spec, filename);
    }

    private void refresh(ModConfigEvent event, ForgeConfigSpec spec) {
        if (event.getConfig().getSpec() == spec) {
            var scripted = scriptedRules.get();
            var catalog = new LinkedHashMap<>(defaults);
            scripted.keySet().forEach(coolant -> catalog.put(coolant, ""));
            var overrides = new LinkedHashMap<>(scripted);
            configured.forEach((coolant, value) -> {
                String text = value.get();
                if (!defaults.get(coolant).equals(text)) {
                    overrides.put(coolant, text);
                }
            });
            rules = overrides.isEmpty() ? defaultRules : FissionPlacementRules.compile(
                    coolingPart, name, catalog, overrides, LOGGER::warn);
        }
    }

    FissionPlacementRules<C> rules() {
        return rules;
    }
}
