package nc.init;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.init.NCItemProcessors.Rate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class NCProcessorAvailability {
    private static final String NUCLEAR_FURNACE = "nuclear_furnace";
    private static final List<String> IDS = Stream.concat(Stream.of(NUCLEAR_FURNACE),
            Arrays.stream(Rate.values()).map(Rate::id)).toList();
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> REGISTER_PROCESSOR;

    static {
        var builder = new ForgeConfigSpec.Builder();
        REGISTER_PROCESSOR = builder.comment("Enable each stock processor. Order: " + String.join(", ", IDS))
                .worldRestart().define("register_processor", Collections.nCopies(IDS.size(), true),
                        value -> value instanceof List<?> list && list.size() == IDS.size()
                                && list.stream().allMatch(Boolean.class::isInstance));
        SPEC = builder.build();
    }

    private NCProcessorAvailability() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "nuclearcraft-processor-availability.toml");
        context.getModEventBus().addListener(NCProcessorAvailability::registerCondition);
    }

    private static void registerCondition(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CraftingHelper.register(ProcessorEnabled.SERIALIZER));
    }

    static boolean enabled(Rate rate) {
        return enabled(rate.ordinal() + 1);
    }

    public static boolean nuclearFurnaceEnabled() {
        return enabled(0);
    }

    private static boolean enabled(int index) {
        return !SPEC.isLoaded() || REGISTER_PROCESSOR.get().get(index);
    }

    private record ProcessorEnabled(int index) implements ICondition {
        private static final ResourceLocation ID = NuclearCraft.id("processor_enabled");
        private static final IConditionSerializer<ProcessorEnabled> SERIALIZER = new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, ProcessorEnabled condition) {
                json.addProperty("processor", IDS.get(condition.index));
            }

            @Override
            public ProcessorEnabled read(JsonObject json) {
                String processor = GsonHelper.getAsString(json, "processor");
                int index = IDS.indexOf(processor);
                if (index < 0) {
                    throw new JsonSyntaxException("Unknown stock processor: " + processor);
                }
                return new ProcessorEnabled(index);
            }

            @Override
            public ResourceLocation getID() {
                return ID;
            }
        };

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public boolean test(IContext context) {
            return enabled(index);
        }
    }
}
