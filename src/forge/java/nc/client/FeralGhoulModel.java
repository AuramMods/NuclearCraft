package nc.client;

import nc.NuclearCraft;
import nc.entity.FeralGhoul;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

public final class FeralGhoulModel extends HumanoidModel<FeralGhoul> {
    static final ModelLayerLocation BODY = new ModelLayerLocation(NuclearCraft.id("feral_ghoul"), "main");
    static final ModelLayerLocation INNER_ARMOR = new ModelLayerLocation(NuclearCraft.id("feral_ghoul"), "inner_armor");
    static final ModelLayerLocation OUTER_ARMOR = new ModelLayerLocation(NuclearCraft.id("feral_ghoul"), "outer_armor");

    private final ModelPart root;

    FeralGhoulModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    static LayerDefinition layer(CubeDeformation armor) {
        var mesh = new MeshDefinition();
        var parts = mesh.getRoot();
        boolean wearingArmor = armor != null;
        CubeDeformation headSize = wearingArmor ? armor : new CubeDeformation(-0.1F);
        CubeDeformation torsoSize = wearingArmor ? armor : new CubeDeformation(-0.2F);
        CubeDeformation armSize = wearingArmor ? armor : new CubeDeformation(-0.5F);
        int armLength = wearingArmor ? 12 : 16;
        parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4, -8, -4, 8, 8, 8, headSize),
                PartPose.offsetAndRotation(0, 3.5F, -2.2F, 0.1134464F, 0, 0));
        parts.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4, -8, -4, 8, 8, 8, headSize.extend(0.5F)),
                PartPose.offsetAndRotation(0, 3.5F, -2.2F, 0.1134464F, 0, 0));
        parts.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4, 0, -2, 8, 12, 4, torsoSize),
                PartPose.offsetAndRotation(0, 2.4F, -2.7F, 0.43633232F, 0, 0));
        parts.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2, 0, -2, 4, 12, 4, torsoSize),
                PartPose.offsetAndRotation(-1.9F, 12, 2.1F, -0.017453292F, 0, 0.02617994F));
        parts.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror()
                        .addBox(-2, 0, -2, 4, 12, 4, torsoSize),
                PartPose.offsetAndRotation(1.9F, 12, 2.1F, -0.017453292F, 0, -0.02617994F));
        parts.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-3, -2, -2, 4, armLength, 4, armSize),
                PartPose.offsetAndRotation(-4.2F, 4.4F, -1.7F, -0.34906584F, -0.10000737F, 0.10000737F));
        parts.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
                        .addBox(-1, -2, -2, 4, armLength, 4, armSize),
                PartPose.offsetAndRotation(4.2F, 4.4F, -1.7F, -0.34906584F, 0.10000737F, -0.10000737F));
        return LayerDefinition.create(mesh, 64, wearingArmor ? 32 : 64);
    }

    @Override
    public void setupAnim(FeralGhoul ghoul, float limbSwing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boolean flying = ghoul.getFallFlyingTicks() > 4;
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot += flying ? -Mth.PI / 4F : pitch * Mth.DEG_TO_RAD;
        float dampening = 1F;
        if (flying) {
            float speed = (float) ghoul.getDeltaMovement().lengthSqr() / 0.2F;
            dampening = Math.max(1F, speed * speed * speed);
        }
        float steps = limbSwing * 0.6662F;
        rightArm.xRot += Mth.cos(steps + Mth.PI) * amount / dampening;
        leftArm.xRot += Mth.cos(steps) * amount / dampening;
        rightLeg.xRot += Mth.cos(steps) * 1.4F * amount / dampening;
        leftLeg.xRot += Mth.cos(steps + Mth.PI) * 1.4F * amount / dampening;
        if (riding) {
            rightArm.xRot -= 0.34906584F + Mth.PI / 5F;
            leftArm.xRot -= Mth.PI / 5F;
            rightLeg.setRotation(-1.4137167F, Mth.PI / 10F, 0.07853982F);
            leftLeg.setRotation(-1.4137167F, -Mth.PI / 10F, -0.07853982F);
        }
        setupAttackAnimation(ghoul, age);
        AnimationUtils.bobArms(rightArm, leftArm, age);
        AnimationUtils.bobArms(rightArm, leftArm, age);
        hat.copyFrom(head);
    }
}
