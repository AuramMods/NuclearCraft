package nc.init;

import com.google.gson.JsonObject;
import nc.NuclearCraft;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Map;

public final class NCAdvancements {
    private static final Map<ResourceLocation, AssemblyTrigger> ASSEMBLY_TRIGGERS = Map.ofEntries(
            assembly("electrolyzer_controller", "electrolyzer_assembled"),
            assembly("distiller_controller", "distiller_assembled"),
            assembly("infiltrator_controller", "infiltrator_assembled"),
            assembly("decay_pool_controller", "decay_pool_assembled"),
            assembly("pebble_fission_controller", "pebble_fission_reactor_assembled"),
            assembly("solid_fission_controller", "solid_fission_reactor_assembled"),
            assembly("salt_fission_controller", "salt_fission_reactor_assembled"),
            assembly("heat_exchanger_controller", "heat_exchanger_assembled"),
            assembly("condenser_controller", "condenser_assembled"),
            assembly("turbine_controller", "turbine_assembled")
    );

    private NCAdvancements() {}

    private static Map.Entry<ResourceLocation, AssemblyTrigger> assembly(String controller, String criterion) {
        return Map.entry(NuclearCraft.id(controller), new AssemblyTrigger(NuclearCraft.id(criterion)));
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ASSEMBLY_TRIGGERS.values().forEach(CriteriaTriggers::register));
    }

    public static void inspectAssembly(Player player, InteractionHand hand, BlockState controller) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown()) {
            var trigger = ASSEMBLY_TRIGGERS.get(BuiltInRegistries.BLOCK.getKey(controller.getBlock()));
            if (trigger != null) {
                trigger.award(serverPlayer);
            }
        }
    }

    private static final class AssemblyTrigger extends SimpleCriterionTrigger<AssemblyInstance> {
        private final ResourceLocation id;

        private AssemblyTrigger(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        protected AssemblyInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
            return new AssemblyInstance(id, player);
        }

        private void award(ServerPlayer player) {
            trigger(player, instance -> true);
        }
    }

    private static final class AssemblyInstance extends AbstractCriterionTriggerInstance {
        private AssemblyInstance(ResourceLocation criterion, ContextAwarePredicate player) {
            super(criterion, player);
        }
    }
}
