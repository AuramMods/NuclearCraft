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

    public static final RegisteredFluid SULFUR = new RegisteredFluid("sulfur",
            () -> tintedFluid(FluidType.Properties.create().density(5000).viscosity(8000).temperature(1200).lightLevel(10)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA), MOLTEN_STILL, MOLTEN_FLOW, 0xFFDEDE7A));
    public static final RegisteredFluid CORIUM = new RegisteredFluid("corium",
            () -> tintedFluid(FluidType.Properties.create().density(6000).viscosity(10000).temperature(2400).lightLevel(10),
                    MOLTEN_STILL, MOLTEN_FLOW, 0xFF7C7C6F));
    public static final RegisteredFluid HIGH_PRESSURE_STEAM = steam("high_pressure_steam", 600, 0xFFBDBDBD);
    public static final RegisteredFluid EXHAUST_STEAM = steam("exhaust_steam", 400, 0xFF7E7E7E);
    public static final RegisteredFluid LOW_PRESSURE_STEAM = steam("low_pressure_steam", 550, 0xFFA8A8A8);
    public static final RegisteredFluid LOW_QUALITY_STEAM = steam("low_quality_steam", 350, 0xFF828282);
    public static final RegisteredFluid STANDARD_STEAM = steam("steam", 550, 0xFF929292);

    private static RegisteredFluid steam(String name, int temperature, int tint) {
        return new RegisteredFluid(name, () ->
                tintedFluid(FluidType.Properties.create().density(-10).viscosity(40).temperature(temperature)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.FIRE_EXTINGUISH)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.FIRE_EXTINGUISH), STEAM, STEAM, tint));
    }

    private NCFluids() {}

    public static void register(IEventBus bus) {
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
            type = TYPES.register(name, fluidType);
            source = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(properties()));
            flowing = FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(properties()));
            block = NCRegistries.BLOCKS.register(name,
                    () -> new LiquidBlock(source, BlockBehaviour.Properties.copy(Blocks.LAVA)));
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
