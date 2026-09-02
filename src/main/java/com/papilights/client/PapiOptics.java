package com.papilights.client;

import com.papilights.block.PapiLightBlock;
import com.papilights.block.PapiLightBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side colour selection for a PAPI unit.
 *
 * <p>Real PAPI has no state to speak of: each unit is a lamp behind a split lens, red below the
 * cut-off and white above it, so two aircraft at different heights genuinely see different colours
 * from the same fixture at the same instant. This mirrors that exactly -- every client computes the
 * colour from its own camera and nothing about the colour is ever sent over the network.
 */
public final class PapiOptics {

    private PapiOptics() {
    }

    private static final int OFF = 0;
    private static final int RED = 1;
    private static final int WHITE = 2;

    /**
     * The elevation angle, in degrees, from a unit's bulb to the viewer. Positive means the viewer
     * is above the unit.
     */
    public static double elevationDeg(Vec3 bulb, Vec3 viewer) {
        double dx = viewer.x - bulb.x;
        double dy = viewer.y - bulb.y;
        double dz = viewer.z - bulb.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /**
     * Resolves the colour this unit shows to a viewer at {@code viewer}, or {@code null} when the
     * unit is dark -- either because the array is incomplete or because the viewer is behind it.
     * Real PAPI is directional; from behind the array you see nothing.
     */
    @Nullable
    public static DyeColor colorFor(PapiLightBlockEntity be, Vec3 viewer) {
        if (!be.isActive()) {
            return null;
        }
        int cached = be.getCachedColor(viewer);
        if (cached == Integer.MIN_VALUE) {
            cached = compute(be, viewer);
            be.putCachedColor(viewer, cached);
        }
        return switch (cached) {
            case RED -> DyeColor.RED;
            case WHITE -> DyeColor.WHITE;
            default -> null;
        };
    }

    private static int compute(PapiLightBlockEntity be, Vec3 viewer) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof PapiLightBlock)) {
            return OFF;
        }
        Vec3 bulb = be.getBulbOrigin();
        double dx = viewer.x - bulb.x;
        double dy = viewer.y - bulb.y;
        double dz = viewer.z - bulb.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // Beam cut-off: the lens only throws light forward, so anything on the back side is dark.
        // The tolerance keeps the unit lit for someone stood right on top of it, where "in front"
        // stops being meaningful.
        Direction facing = state.getValue(PapiLightBlock.FACING);
        if (horizontal > 0.05D) {
            double alongBeam = dx * facing.getStepX() + dz * facing.getStepZ();
            if (alongBeam <= 0.0D) {
                return OFF;
            }
        }

        double elevation = Math.toDegrees(Math.atan2(dy, horizontal));
        return elevation >= be.getUnitAngleDeg() ? WHITE : RED;
    }
}
