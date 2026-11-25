package nc.init;

import nc.NuclearCraft;
import nc.fluid.FluidConfig;
import nc.fluid.FluidHazard.Kind;
import nc.fluid.NCFlowingFluid;
import nc.fluid.NCLiquidBlock;
import nc.fluid.NCLiquidBucket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
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
    private static final ResourceLocation SOUL = NuclearCraft.id("block/fluids/soul");

    public static final RegisteredFluid SULFUR = molten("sulfur", 0xDEDE7A);
    public static final RegisteredFluid CORIUM = corium("corium", 0xFF7C7C6F);
    public static final RegisteredFluid HIGH_PRESSURE_STEAM = steam("high_pressure_steam", 600, 0xFFBDBDBD);
    public static final RegisteredFluid EXHAUST_STEAM = steam("exhaust_steam", 400, 0xFF7E7E7E);
    public static final RegisteredFluid LOW_PRESSURE_STEAM = steam("low_pressure_steam", 550, 0xFFA8A8A8);
    public static final RegisteredFluid LOW_QUALITY_STEAM = steam("low_quality_steam", 350, 0xFF828282);
    public static final RegisteredFluid STANDARD_STEAM = steam("steam", 550, 0xFF929292);
    public static final RegisteredFluid OXYGEN = gas("oxygen", 0xFF7E8CC8);
    public static final RegisteredFluid NITROGEN = gas("nitrogen", 0xFF7CC37B);
    public static final RegisteredFluid HYDROGEN = gas("hydrogen", 0xFFB37AC4);
    public static final RegisteredFluid HELIUM_3 = gas("helium_3", 0xFFCBBB67);
    public static final RegisteredFluid DEUTERIUM = gas("deuterium", 0xFF9E6FEF);
    public static final RegisteredFluid FLUORINE = gas("fluorine", 0xFFD3C75D);
    public static final RegisteredFluid EMERGENCY_COOLANT = liquid("emergency_coolant", "liquid", 2000, 0x6DD0E7, 250, 2000);
    public static final RegisteredFluid HEATED_EMERGENCY_COOLANT =
            liquid("emergency_coolant_heated", "liquid", 2000, 0xCDBEE7, 400, 1500, 7);
    public static final RegisteredFluid PREHEATED_WATER = liquid("preheated_water", "liquid", 1000, 0x4D85D6, 400);
    public static final RegisteredFluid CONDENSATE_WATER = liquid("condensate_water", "liquid", 1000, 0x4B95DB, 350);
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
        coolantMaterial("aluminum", 0xB5ECD5);
        molten("holmium", 0xDCB49C);
        molten("dysprosium", 0xC381E4);
        molten("beryllium", 0xD4DBC2);
        NCFissionFluids.registerFuels();
    }

    static RegisteredFluid molten(String name, int tint) {
        return molten(name, tint, Kind.MOLTEN, false);
    }

    static RegisteredFluid coolantMaterial(String name, int tint) {
        return molten(name, tint, name.equals("glowstone") ? Kind.GLOWSTONE : Kind.MOLTEN, true);
    }

    static RegisteredFluid fission(String name, int tint) {
        return molten(name, tint, Kind.FISSION, false);
    }

    static RegisteredFluid optionalFission(String name, int tint) {
        return molten(name, tint, Kind.FISSION, true);
    }

    private static RegisteredFluid corium(String name, int color) {
        return new RegisteredFluid(name, 10, Kind.CORIUM, () -> tintedFluid(
                FluidType.Properties.create().density(6000).viscosity(10000).temperature(2400).lightLevel(10),
                MOLTEN_STILL, MOLTEN_FLOW, color));
    }

    private static RegisteredFluid molten(String name, int tint, Kind kind, boolean optionalBlock) {
        return new RegisteredFluid(name, 10, kind, optionalBlock, () -> tintedFluid(
                FluidType.Properties.create().density(5000).viscosity(8000).temperature(1200).lightLevel(10)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA),
                MOLTEN_STILL, MOLTEN_FLOW, 0xFF000000 | tint, optionalBlock));
    }

    private static RegisteredFluid liquid(String name, String texture, int density, int tint) {
        return liquid(name, texture, density, tint, 300);
    }

    private static RegisteredFluid liquid(String name, String texture, int density, int tint, int temperature) {
        return liquid(name, texture, density, tint, temperature, 1000);
    }

    static RegisteredFluid liquid(String name, String texture, int density, int tint, int temperature, int viscosity) {
        return liquid(name, texture, density, tint, temperature, viscosity, 0);
    }

    static RegisteredFluid liquid(String name, String texture, int density, int tint, int temperature, int viscosity, int light) {
        Kind kind = switch (name) {
            case "hydrofluoric_acid", "boric_acid", "sulfuric_acid", "orthosilicic_acid" -> Kind.ACID;
            case "ethanol", "methanol", "benzene", "phenol", "fluorobenzene", "difluorobenzene",
                    "dimethyldifluorosilane" -> Kind.FLAMMABLE;
            case "chocolate_liquor", "cocoa_butter", "unsweetened_chocolate", "dark_chocolate", "milk_chocolate",
                    "marshmallow" -> Kind.CHOCOLATE;
            case "sugar", "gelatin", "hydrated_gelatin" -> Kind.STICKY;
            case "cryotheum" -> Kind.CRYOTHEUM;
            case "liquid_helium" -> Kind.SUPERFLUID;
            default -> Kind.LIQUID;
        };
        return new RegisteredFluid(name, light, kind, () -> tintedFluid(
                FluidType.Properties.create().density(density).viscosity(viscosity).temperature(temperature).lightLevel(light)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY),
                NuclearCraft.id("block/fluids/" + texture + "_still"),
                NuclearCraft.id("block/fluids/" + texture + "_flow"), 0xFF000000 | tint));
    }

    static RegisteredFluid saltCoolant(String name, int tint, boolean hot, boolean optionalBlock) {
        int light = hot ? 7 : 0;
        return new RegisteredFluid(name, light, hot ? Kind.HOT_COOLANT : Kind.COOLANT, optionalBlock,
                () -> tintedFluid(FluidType.Properties.create()
                .density(hot ? 4000 : 5000).viscosity(hot ? 10000 : 15000).temperature(hot ? 800 : 400)
                .lightLevel(light).sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA), MOLTEN_STILL, MOLTEN_FLOW,
                0xFF000000 | tint, optionalBlock));
    }

    private static RegisteredFluid steam(String name, int temperature, int tint) {
        return new RegisteredFluid(name, 0, Kind.STEAM, () ->
                tintedFluid(FluidType.Properties.create().density(-10).viscosity(40).temperature(temperature)
                        .canDrown(false).canSwim(false).canPushEntity(false)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.FIRE_EXTINGUISH)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.FIRE_EXTINGUISH), STEAM, STEAM, tint));
    }

    private static RegisteredFluid gas(String name, int tint) {
        return gas(name, tint, 300);
    }

    static RegisteredFluid gas(String name, int tint, int temperature) {
        return new RegisteredFluid(name, 0, temperature > 350 ? Kind.HOT_GAS : Kind.GAS, () -> tintedFluid(
                FluidType.Properties.create().density(-10).viscosity(40).temperature(temperature).canDrown(false).canSwim(false)
                        .canPushEntity(false).sound(SoundActions.BUCKET_FILL, SoundEvents.FIRE_EXTINGUISH)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.FIRE_EXTINGUISH), GAS, GAS, tint));
    }

    static RegisteredFluid soul(String name, int tint, SoundEvent emptySound, SoundEvent fillSound) {
        return new RegisteredFluid(name, 0, Kind.GAS, () -> tintedFluid(
                FluidType.Properties.create().density(-10).viscosity(40).canDrown(false).canSwim(false)
                        .canPushEntity(false).sound(SoundActions.BUCKET_FILL, fillSound)
                        .sound(SoundActions.BUCKET_EMPTY, emptySound), SOUL, SOUL, tint));
    }

    private NCFluids() {}

    public static void register(IEventBus bus) {
        NCFluidProducts.register();
        gas("tritium", 0xFF5DBBD6);
        corium("roentgen_josh", 0xFFCC4B07);
        ResourceLocation plasma = NuclearCraft.id("block/fluids/plasma");
        new RegisteredFluid("plasma", 15, Kind.PLASMA, () -> tintedFluid(
                FluidType.Properties.create().density(50).viscosity(100).temperature(1_000_000).lightLevel(15)
                        .canDrown(false).canSwim(false).canPushEntity(false)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.FIRE_EXTINGUISH)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.FIRE_EXTINGUISH), plasma, plasma, 0xFFFFFFFF));
        registerElectrolyzerFluids();
        NCGasCoolants.registerFluids();
        NCSaltCoolants.registerFluids();
        NCSaltMixerFluids.register();
        NCChemicalFluids.register();
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private static FluidType tintedFluid(FluidType.Properties properties, ResourceLocation still,
                                         ResourceLocation flowing, int tint) {
        return tintedFluid(properties, still, flowing, tint, false);
    }

    private static FluidType tintedFluid(FluidType.Properties properties, ResourceLocation still,
                                         ResourceLocation flowing, int tint, boolean optionalBlock) {
        return new FluidType(properties) {
            @Override
            public BlockState getBlockForFluidState(BlockAndTintGetter level, BlockPos pos, FluidState state) {
                return optionalBlock && !FluidConfig.optionalFluidBlocksEnabled() ? Blocks.AIR.defaultBlockState()
                        : super.getBlockForFluidState(level, pos, state);
            }

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

        private RegisteredFluid(String name, int light, Kind kind, Supplier<FluidType> fluidType) {
            this(name, light, kind, false, fluidType);
        }

        private RegisteredFluid(String name, int light, Kind kind, boolean optionalBlock, Supplier<FluidType> fluidType) {
            type = TYPES.register(name, fluidType);
            source = FLUIDS.register(name, () -> new NCFlowingFluid.Source(properties(kind), kind));
            flowing = FLUIDS.register("flowing_" + name, () -> new NCFlowingFluid.Flowing(properties(kind), kind));
            block = NCRegistries.BLOCKS.register(name, () -> new NCLiquidBlock(source,
                    BlockBehaviour.Properties.copy(light == 0 ? Blocks.WATER : Blocks.LAVA).lightLevel(state -> light), kind));
            bucket = NCRegistries.ITEMS.register(name + "_bucket", () -> {
                var properties = new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1);
                return optionalBlock ? new NCLiquidBucket(source, properties) : new BucketItem(source, properties);
            });
        }

        private ForgeFlowingFluid.Properties properties(Kind kind) {
            int tickRate = switch (kind) {
                case GAS, HOT_GAS, STEAM, PLASMA, SUPERFLUID -> 5;
                default -> 30;
            };
            return new ForgeFlowingFluid.Properties(type, source, flowing)
                    .block(block).bucket(bucket).slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(tickRate);
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
