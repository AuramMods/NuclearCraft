package nc.multiblock.quantum;

import com.google.gson.JsonObject;
import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class QuantumConfig {
    private static final ForgeConfigSpec.Builder AVAILABILITY = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue REGISTER = AVAILABILITY
            .comment("Enable quantum computers and their built-in crafting recipes.")
            .worldRestart().define("register_quantum", true);
    private static final ForgeConfigSpec AVAILABILITY_SPEC = AVAILABILITY.build();

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue DEDICATED = BUILDER
            .comment("Enable quantum computers on dedicated servers. State space doubles per qubit.")
            .worldRestart().define("quantum_dedicated_server", false);
    private static final ForgeConfigSpec.IntValue MAX_QUBITS = BUILDER.worldRestart()
            .defineInRange("quantum_max_qubits", 16, 1, QuantumCircuit.MAX_QUBITS);
    private static final ForgeConfigSpec.IntValue ANGLE_PRECISION = BUILDER.worldRestart()
            .defineInRange("quantum_angle_precision", 16, 4, 1024);
    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private QuantumConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, AVAILABILITY_SPEC, "nuclearcraft-quantum-availability.toml");
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-quantum.toml");
        context.getModEventBus().addListener(QuantumConfig::registerCondition);
    }

    private static void registerCondition(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CraftingHelper.register(Enabled.SERIALIZER);
            if (ModList.get().isLoaded("computercraft")) {
                QuantumPeripheral.register();
            }
        });
    }

    public static boolean enabled() { return !AVAILABILITY_SPEC.isLoaded() || REGISTER.get(); }
    static boolean dedicated() { return SPEC.isLoaded() && DEDICATED.get(); }
    static int maxQubits() { return SPEC.isLoaded() ? MAX_QUBITS.get() : 16; }
    static int anglePrecision() { return SPEC.isLoaded() ? ANGLE_PRECISION.get() : 16; }
    private enum Enabled implements ICondition {
        INSTANCE;

        private static final ResourceLocation ID = NuclearCraft.id("quantum_enabled");
        private static final IConditionSerializer<Enabled> SERIALIZER = new IConditionSerializer<>() {
            @Override public void write(JsonObject json, Enabled value) {}
            @Override public Enabled read(JsonObject json) { return INSTANCE; }
            @Override public ResourceLocation getID() { return ID; }
        };

        @Override public ResourceLocation getID() { return ID; }
        @Override public boolean test(IContext context) { return enabled(); }
    }
}
