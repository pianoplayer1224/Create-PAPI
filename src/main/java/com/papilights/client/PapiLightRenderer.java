package com.papilights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.papilights.block.PapiLightBlock;
import com.papilights.block.PapiLightBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;

/**
 * Draws the two glowing bulbs of a PAPI unit.
 *
 * <p>The housing itself is not drawn here: the block's model is a single-bulb housing built from
 * one of Create's nixie tube cuboid pairs and textured from {@code create:block/nixie_tube}, so the
 * chunk mesh handles it and Sodium sees nothing unusual. All this renderer adds is the lit element,
 * and it does so by calling
 * Create's {@link NixieTubeRenderer#drawTube} -- a public static entry point that takes the colour
 * as a parameter. That is the whole integration: no mixins, no reflection, no subclassing of
 * Create's block entity, and no dependence on Create's colour sync. We simply hand it the colour
 * this client just computed.
 *
 * <p>The transform below reproduces the frame {@code NixieTubeRenderer.renderSafe} sets up before
 * its own {@code drawTube} calls, minus the wall/ceiling cases we do not support.
 */
public class PapiLightRenderer implements BlockEntityRenderer<PapiLightBlockEntity> {

    /**
     * U+2588 FULL BLOCK. Present in vanilla's {@code nonlatin_european} bitmap font, so it renders
     * as a solid lit rectangle filling the tube rather than as a digit.
     */
    private static final String LAMP_GLYPH = "█";

    /** Create passes 3.0 for floor-mounted tubes; it is the glyph's vertical offset in tube space. */
    private static final float GLYPH_HEIGHT = 3.0F;

    /** Text is drawn at font scale; Create shrinks it to tube size with this factor. */
    private static final float TUBE_SCALE = 0.05F;

    /**
     * Beyond this the bulbs stop being drawn. PAPI is meant to be read from a long way out, so this
     * is generous compared to Create's own nixie tubes.
     */
    private static final int VIEW_DISTANCE = 256;

    public PapiLightRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PapiLightBlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.isActive() || be.getLevel() == null) {
            return;
        }

        // The viewer is the camera, not the player entity: what the array shows should match the
        // eye that is actually looking at it, including in third person and spectator.
        Vec3 viewer = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        DyeColor color = PapiOptics.colorFor(be, viewer);
        if (color == null) {
            return;
        }

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(PapiLightBlock.FACING);

        // The glyph frame is the model frame turned a further -90 degrees, which is the frame
        // Create's own renderer draws in. Its local +Z ends up pointing along FACING, so the bright
        // side of the bulb aims down the approach. See PapiArray for why the row direction is
        // facing.getCounterClockWise().
        float modelYaw = -facing.getCounterClockWise().toYRot();

        RandomSource random = be.getLevel().getRandom();

        ms.pushPose();
        ms.translate(0.5D, 0.5D, 0.5D);
        ms.mulPose(Axis.YP.rotationDegrees(modelYaw - 90.0F));

        // One bulb per unit: the housing has a single glass column, centred.
        ms.scale(TUBE_SCALE, -TUBE_SCALE, TUBE_SCALE);
        NixieTubeRenderer.drawTube(ms, buffer, LAMP_GLYPH, GLYPH_HEIGHT, color, random);

        ms.popPose();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRenderOffScreen(PapiLightBlockEntity be) {
        return false;
    }
}
