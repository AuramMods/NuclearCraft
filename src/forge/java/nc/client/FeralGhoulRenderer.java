package nc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import nc.NuclearCraft;
import nc.entity.FeralGhoul;
import nc.init.NCEntities;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FeralGhoulRenderer extends HumanoidMobRenderer<FeralGhoul, FeralGhoulModel> {
    private static final ResourceLocation FERAL = NuclearCraft.id("textures/entity/feral_ghoul.png");
    private static final ResourceLocation GLOWING = NuclearCraft.id("textures/entity/glowing_ghoul.png");
    private static final RenderType GLOW = RenderType.eyes(NuclearCraft.id("textures/entity/glowing_ghoul_glow.png"));

    private FeralGhoulRenderer(EntityRendererProvider.Context context, boolean glowing) {
        super(context, new FeralGhoulModel(context.bakeLayer(FeralGhoulModel.BODY)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new FeralGhoulModel(context.bakeLayer(FeralGhoulModel.INNER_ARMOR)),
                new FeralGhoulModel(context.bakeLayer(FeralGhoulModel.OUTER_ARMOR)),
                context.getModelManager()));
        if (glowing) {
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack poses, MultiBufferSource buffers, int light, FeralGhoul ghoul,
                                   float swing, float amount, float partial, float age, float yaw, float pitch) {
                    if (!ghoul.isInvisible()) {
                        getParentModel().renderToBuffer(poses, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
                    }
                }
            });
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FeralGhoul ghoul) {
        return ghoul.isGlowingGhoul() ? GLOWING : FERAL;
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NCEntities.FERAL_GHOUL.get(), context -> new FeralGhoulRenderer(context, false));
        event.registerEntityRenderer(NCEntities.GLOWING_GHOUL.get(), context -> new FeralGhoulRenderer(context, true));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FeralGhoulModel.BODY, () -> FeralGhoulModel.layer(null));
        event.registerLayerDefinition(FeralGhoulModel.INNER_ARMOR, () -> FeralGhoulModel.layer(new CubeDeformation(0.5F)));
        event.registerLayerDefinition(FeralGhoulModel.OUTER_ARMOR, () -> FeralGhoulModel.layer(new CubeDeformation(1F)));
    }
}
