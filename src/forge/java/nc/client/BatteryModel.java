package nc.client;

import nc.NuclearCraft;
import nc.energy.BatteryBlock;
import nc.energy.BatteryBlock.Mode;
import nc.energy.BatteryBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BatteryModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel output;
    private final BakedModel disabled;

    private BatteryModel(BakedModel input, BakedModel output, BakedModel disabled) {
        super(input);
        this.output = output;
        this.disabled = disabled;
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        batteries().forEach(block -> {
            event.register(variant(block, Mode.OUT));
            event.register(variant(block, Mode.OFF));
        });
    }

    @SubscribeEvent
    public static void installModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        var missing = models.get(ModelBakery.MISSING_MODEL_LOCATION);
        batteries().forEach(block -> {
            var key = BlockModelShaper.stateToModelLocation(block.defaultBlockState());
            BakedModel input = models.get(key);
            if (input != null && input != missing) {
                var output = models.getOrDefault(variant(block, Mode.OUT), input);
                var disabled = models.getOrDefault(variant(block, Mode.OFF), input);
                models.put(key, new BatteryModel(input, output == missing ? input : output, disabled == missing ? input : disabled));
            }
        });
    }

    private static Stream<BatteryBlock> batteries() {
        return ForgeRegistries.BLOCKS.getValues().stream().filter(BatteryBlock.class::isInstance).map(BatteryBlock.class::cast);
    }

    private static ResourceLocation variant(BatteryBlock block, Mode mode) {
        var id = ForgeRegistries.BLOCKS.getKey(block);
        return new ResourceLocation(id.getNamespace(), "block/" + id.getPath() + "_" + mode.getSerializedName());
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                             @NotNull RandomSource random, @NotNull ModelData data,
                                             @Nullable RenderType renderType) {
        Integer modes = data.get(BatteryBlockEntity.FACE_MODES);
        BakedModel model = side == null || modes == null ? originalModel : switch (Mode.read(modes, side)) {
            case IN -> originalModel;
            case OUT -> output;
            case OFF -> disabled;
        };
        return model.getQuads(state, side, random, data, renderType);
    }
}
