package com.shadowsamurai.client;

import com.shadowsamurai.ModEntities;
import com.shadowsamurai.entity.ShadowSamuraiEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/** Humanoid base + kabuto helmet, curved demon horns, flowing cape, pauldrons and a kusazuri skirt. 128x128 skin. */
public class ShadowSamuraiModel extends HumanoidModel<ShadowSamuraiEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModEntities.id("shadow_samurai"), "main");
    private final ModelPart cape, capeTail;

    public ShadowSamuraiModel(ModelPart root) {
        super(root);
        this.cape = root.getChild("body").getChild("cape");
        this.capeTail = this.cape.getChild("cape_tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("kabuto", CubeListBuilder.create().texOffs(64, 0).addBox(-5F, -9.5F, -5F, 10, 2, 10), PartPose.ZERO);
        PartDefinition hr = head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(104, 0).addBox(-1F, -6F, -1F, 2, 6, 2), PartPose.offsetAndRotation(-3.5F, -8.5F, -1F, 0F, 0F, -0.6F));
        hr.addOrReplaceChild("horn_r_tip", CubeListBuilder.create().texOffs(104, 12).addBox(-1F, -6F, -1F, 2, 6, 2), PartPose.offsetAndRotation(0F, -5.5F, 0F, 0F, 0F, 0.95F));
        PartDefinition hl = head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(112, 0).addBox(-1F, -6F, -1F, 2, 6, 2), PartPose.offsetAndRotation(3.5F, -8.5F, -1F, 0F, 0F, 0.6F));
        hl.addOrReplaceChild("horn_l_tip", CubeListBuilder.create().texOffs(112, 12).addBox(-1F, -6F, -1F, 2, 6, 2), PartPose.offsetAndRotation(0F, -5.5F, 0F, 0F, 0F, -0.95F));

        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("kusazuri", CubeListBuilder.create().texOffs(0, 64).addBox(-5.5F, 11.5F, -3.5F, 11, 5, 7), PartPose.ZERO);
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(64, 16).addBox(-6F, 0F, 0F, 12, 22, 1), PartPose.offset(0F, 0.5F, 2.3F));
        cape.addOrReplaceChild("cape_tail", CubeListBuilder.create().texOffs(64, 40).addBox(-5F, 0F, 0F, 10, 6, 1), PartPose.offset(0F, 22F, 0F));

        root.getChild("right_arm").addOrReplaceChild("pauldron_r", CubeListBuilder.create().texOffs(64, 48).addBox(-4.5F, -3.5F, -3.5F, 7, 3, 7), PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("pauldron_l", CubeListBuilder.create().texOffs(96, 48).addBox(-2.5F, -3.5F, -3.5F, 7, 3, 7), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(ShadowSamuraiEntity e, float limb, float limbAmt, float age, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limb, limbAmt, age, netHeadYaw, headPitch);
        int tick = e.getAttackTick();
        float flare = 0F;
        switch (e.getAttackType()) {
            case ShadowSamuraiEntity.THROW -> {
                rightArm.xRot = tick < 14 ? -2.7F : -1.5F;
                rightArm.zRot = 0.2F;
                leftArm.xRot = -0.6F;
            }
            case ShadowSamuraiEntity.SPIRAL -> {
                rightArm.xRot = 0F; leftArm.xRot = 0F;
                rightArm.zRot = 1.5F; leftArm.zRot = -1.5F;
                flare = 1F;
            }
            case ShadowSamuraiEntity.HOOK -> {
                leftArm.xRot = -1.6F; leftArm.yRot = 0.3F;
                rightArm.xRot = -0.4F;
            }
            case ShadowSamuraiEntity.MOON -> {
                float v = tick < 32 ? -2.9F : -0.7F;
                rightArm.xRot = v; leftArm.xRot = v;
                rightArm.yRot = -0.3F; leftArm.yRot = 0.3F;
                flare = tick < 32 ? 0.3F : 0.9F;
            }
            case ShadowSamuraiEntity.STORM -> {
                float w = Mth.sin(age * 0.5F) * 0.12F;
                rightArm.xRot = -2.8F + w; leftArm.xRot = -2.8F - w;
                rightArm.zRot = 0.5F; leftArm.zRot = -0.5F;
                flare = 0.5F;
            }
            case ShadowSamuraiEntity.INTRO -> {
                float p = Math.min(tick / 80F, 1F);
                rightArm.zRot = 0.5F * p; leftArm.zRot = -0.5F * p;
                rightArm.xRot = -0.8F * p; leftArm.xRot = -0.8F * p;
                head.xRot = -0.45F * p;
                hat.copyFrom(head);
            }
            default -> { if (e.isEnraged()) flare = 0.2F; }
        }
        float speed = Mth.clamp(limbAmt, 0F, 1F);
        cape.xRot = 0.12F + speed * 0.7F + flare * 0.9F + Mth.sin(age * 0.07F) * 0.05F;
        cape.zRot = Mth.sin(age * 0.05F) * 0.04F;
        capeTail.xRot = 0.1F + speed * 0.4F + Mth.sin(age * 0.15F + 1F) * 0.12F;
    }
}
