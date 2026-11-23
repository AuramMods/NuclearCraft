package nc.init;

import com.mojang.logging.LogUtils;
import nc.multiblock.fission.FissionPlacementRules;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

final class CoolingPlacementConfig<C extends Enum<C>> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Class<C> type;
    private final FissionPlacementRules.Part coolingPart;
    private final Function<C, String> name;
    private final Map<C, String> defaults;
    private final Map<C, ForgeConfigSpec.ConfigValue<String>> configured;
    private final FissionPlacementRules<C> defaultRules;
    private volatile FissionPlacementRules<C> rules;

    CoolingPlacementConfig(ForgeConfigSpec.Builder builder, Class<C> type, FissionPlacementRules.Part coolingPart,
                           Function<C, String> name, String[] defaultValues) {
        this.type = type;
        this.coolingPart = coolingPart;
        this.name = name;
        defaults = new EnumMap<>(type);
        configured = new EnumMap<>(type);
        builder.comment("Placement conditions for each cooler or heater. These use the legacy fission rule syntax.")
                .push("placement_rules");
        for (C coolant : type.getEnumConstants()) {
            String text = defaultValues[coolant.ordinal()];
            defaults.put(coolant, text);
            configured.put(coolant, builder.define(name.apply(coolant), text));
        }
        builder.pop();
        defaultRules = FissionPlacementRules.compile(type, coolingPart, name, defaults, Map.of(),
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
            var overrides = new EnumMap<C, String>(type);
            configured.forEach((coolant, value) -> {
                String text = value.get();
                if (!defaults.get(coolant).equals(text)) {
                    overrides.put(coolant, text);
                }
            });
            rules = overrides.isEmpty() ? defaultRules : FissionPlacementRules.compile(
                    type, coolingPart, name, defaults, overrides, LOGGER::warn);
        }
    }

    FissionPlacementRules<C> rules() {
        return rules;
    }
}
