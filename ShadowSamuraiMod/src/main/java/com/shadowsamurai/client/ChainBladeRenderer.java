package com.shadowsamurai.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shadowsamurai.entity.ChainBladeProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the spinning blade/ring AND the real 3D chain (sagging, wiggling links) back to the wielder's hand. */
public class ChainBladeRenderer extends EntityRenderer<ChainBladeProjectile> {
    private static final int FULL_BRIGHT = 15728880;
    private final ItemRenderer itemRenderer;

    public ChainBladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override public boolean shouldRender(ChainBladeProjectile e, Frustum f, double x, double y, double z) { return true; }
    @Override public ResourceLocation getTextureLocation(ChainBladeProjectile e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(ChainBladeProjectile e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Entity owner = e.getOwnerEntity();
        if (owner != null) renderChain(e, owner, pt, ps, buf);

        ps.pushPose();
        ps.translate(0, e.getBbHeight() / 2, 0);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180F));
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + pt) * 48F));
        float s = e.isHook() ? 1.7F : 3.2F;
        ps.scale(s, s, s);
        itemRenderer.renderStatic(e.getRenderStack(), ItemDisplayContext.GROUND, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private void renderChain(ChainBladeProjectile e, Entity owner, float pt, PoseStack ps, MultiBufferSource buf) {
        Vec3 from = new Vec3(0, e.getBbHeight() / 2, 0);
        Vec3 hand = ChainBladeProjectile.handPos(owner, pt).subtract(e.getPosition(pt));
        Vec3 delta = hand.subtract(from);
        double len = delta.length();
        if (len < 0.3) return;
        int n = Math.max(2, (int) (len / 0.45));
        float time = e.tickCount + pt;
        double sag = Math.min(1.4, len * 0.09);
        Vec3 prev = from;
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n, arc = Math.sin(Math.PI * t);
            Vec3 p = from.add(delta.scale(t)).add(0, -arc * sag + Math.sin(time * 0.6 + t * 7) * 0.05 * arc, 0)
                    .add(Math.cos(time * 0.5 + t * 5) * 0.04 * arc, 0, 0);
            Vec3 seg = p.subtract(prev);
            double L = seg.length();
            if (L > 1e-4) {
                Vec3 mid = prev.add(seg.scale(0.5));
                ps.pushPose();
                ps.translate(mid.x, mid.y, mid.z);
                ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) seg.x, (float) seg.y, (float) seg.z).normalize()));
                if (i % 2 == 0) ps.mulPose(Axis.YP.rotationDegrees(90F));
                ps.scale(1.3F, (float) L * 1.08F, 1.3F);
                ps.translate(-0.5, -0.5, -0.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.CHAIN.defaultBlockState(), ps, buf, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            prev = p;
        }
    }
}
