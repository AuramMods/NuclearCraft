package nc.init;

import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class NCFluids {
    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, NuclearCraft.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, NuclearCraft.MOD_ID);
    private static final ResourceLocation MOLTEN_STILL = NuclearCraft.id("block/fluids/molten_still");
    private static final ResourceLocation MOLTEN_FLOW = NuclearCraft.id("block/fluids/molten_flow");

    private static final ResourceLocation STEAM = NuclearCraft.id("block/fluids/steam");
    private static final ResourceLocation GAS = NuclearCraft.id("block/fluids/gas");

    public static final RegisteredFluid SULFUR = molten("sulfur", 0xDEDE7A);
    public static final RegisteredFluid CORIUM = new RegisteredFluid("corium", 10,
            () -> tintedFluid(FluidType.Properties.create().density(6000).viscosity(10000).temperature(2400).lightLevel(10),
                    MOLTEN_STILL, MOLTEN_FLOW, 0xFF7C7C6F));
    public static final RegisteredFluid HIGH_PRESSURE_STEAM = steam("high_pressure_steam", 600, 0xFFBDBDBD);
    public static final RegisteredFluid EXHAUST_STEAM = steam("exhaust_steam", 400, 0xFF7E7E7E);
    public static final RegisteredFluid LOW_PRESSURE_STEAM = steam("low_pressure_steam", 550, 0xFFA8A8A8);
    public static final RegisteredFluid LOW_QUALITY_STEAM = steam("low_quality_steam", 350, 0xFF828282);
    public static final RegisteredFluid STANDARD_STEAM = steam("steam", 550, 0xFF929292);
    public static final RegisteredFluid OXYGEN = gas("oxygen", 0xFF7E8CC8);
    public static final RegisteredFluid NITROGEN = gas("nitrogen", 0xFF7CC37B);
    public static final RegisteredFluid HYDROGEN = gas("hydrogen", 0xFFB37AC4);
    public static final RegisteredFluid DEUTERIUM = gas("deuterium", 0xFF9E6FEF);
    public static final RegisteredFluid FLUORINE = gas("fluorine", 0xFFD3C75D);
    public static final RegisteredFluid EMERGENCY_COOLANT = liquid("emergency_coolant", "liquid", 2000, 0x6DD0E7);
    public static final RegisteredFluid HEATED_EMERGENCY_COOLANT = liquid("emergency_coolant_heated", "liquid", 2000, 0xCDBEE7);
    public static final RegisteredFluid LOW_ENRICHED_WATER = liquid("le_water", "le_water", 1100, 0xFFFFFF);
    public static final RegisteredFluid HIGH_ENRICHED_WATER = liquid("he_water", "he_water", 1100, 0xFFFFFF);
    public static final RegisteredFluid HEAVY_WATER = liquid("heavy_water", "heavy_water", 1100, 0xFFFFFF);

    static void registerElectrolyzerFluids() {
        liquid("hydrofluoric_acid", "liquid", 1000, 0x004C05);
        liquid("ammonium_bisulfate_solution", "salt_solution", 1000, 0x4763A2);
        liquid("ammonium_persulfate_solution", "salt_solution", 1000, 0x5173AC);
        gas("hodybef_vapor", 0xFFA18A72, 1000);
        molten("naoh", 0xC2B7BB);
        molten("koh", 0xB8C6B0);
        molten("sodium", 0xC1898C);
        molten("potassium", 0xB8C503);
        molten("alumina", 0x919880);
        molten("aluminum", 0xB5ECD5);
        molten("holmium", 0xDCB49C);
        molten("dysprosium", 0xC381E4);
        molten("beryllium", 0xD4DBC2);
        NCFissionFluids.registerFuels();
    }

    static RegisteredFluid molten(String name, int tint) {
        return new RegisteredFluid(name, 10, () -> tintedFluid(
                FluidType.Properties.create().density(5000).viscosity(8000).temperature(1200).lightLevel(10)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA),
                MOLTEN_STILL, MOLTEN_FLOW, 0xFF000000 | tint));
    }

    private static RegisteredFluid liquid(String name, String texture, int density, int tint) {
        return new RegisteredFluid(name, () -> tintedFluid(
                FluidType.Properties.create().density(density).viscosity(1000)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY),
                NuclearCraft.id("block/fluids/" + texture + "_still"),
                NuclearCraft.id("block/fluids/" + texture + "_flow"), 0xFF000000 | tint));
    }

    private static RegisteredFluid steam(String name, int temperature, int tint) {
        return new RegisteredFluid(name, () ->
                tintedFluid(FluidType.Properties.create().density(-10).viscosity(40).temperature(temperature)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.FIRE_EXTINGUISH)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.FIRE_EXTINGUISH), STEAM, STEAM, tint));
    }

    private static RegisteredFluid gas(String name, int tint) {
        return gas(name, tint, 300);
    }

    static RegisteredFluid gas(String name, int tint, int temperature) {
        return new RegisteredFluid(name, () -> tintedFluid(
                FluidType.Properties.create().density(-10).viscosity(40).temperature(temperature).canDrown(false).canSwim(false)
                        .canPushEntity(false).sound(SoundActions.BUCKET_FILL, SoundEvents.FIRE_EXTINGUISH)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.FIRE_EXTINGUISH), GAS, GAS, tint));
    }

    private NCFluids() {}

    public static void register(IEventBus bus) {
        registerElectrolyzerFluids();
        NCGasCoolants.registerFluids();
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private static FluidType tintedFluid(FluidType.Properties properties, ResourceLocation still,
                                         ResourceLocation flowing, int tint) {
        return new FluidType(properties) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return still;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flowing;
                    }

                    @Override
                    public int getTintColor() {
                        return tint;
                    }
                });
            }
        };
    }

    public static final class RegisteredFluid {
        private final RegistryObject<FluidType> type;
        private final RegistryObject<FlowingFluid> source;
        private final RegistryObject<FlowingFluid> flowing;
        private final RegistryObject<LiquidBlock> block;
        private final RegistryObject<Item> bucket;

        private RegisteredFluid(String name, Supplier<FluidType> fluidType) {
            this(name, 0, fluidType);
        }

        private RegisteredFluid(String name, int light, Supplier<FluidType> fluidType) {
            type = TYPES.register(name, fluidType);
            source = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(properties()));
            flowing = FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(properties()));
            block = NCRegistries.BLOCKS.register(name, () -> new LiquidBlock(source,
                    BlockBehaviour.Properties.copy(light == 0 ? Blocks.WATER : Blocks.LAVA).lightLevel(state -> light)));
            bucket = NCRegistries.ITEMS.register(name + "_bucket",
                    () -> new BucketItem(source, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        }

        private ForgeFlowingFluid.Properties properties() {
            return new ForgeFlowingFluid.Properties(type, source, flowing)
                    .block(block).bucket(bucket).slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(30);
        }

        public RegistryObject<FlowingFluid> source() {
            return source;
        }

        public RegistryObject<LiquidBlock> block() {
            return block;
        }

        public RegistryObject<Item> bucket() {
            return bucket;
        }
    }
}
