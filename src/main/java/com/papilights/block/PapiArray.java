package com.papilights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Soft" multiblock detection for a PAPI array.
 *
 * <p>There is no controller block and no controller item: every unit is the same block. A run is
 * valid when {@value #PAPI_UNITS} units (a full PAPI) or {@value #APAPI_UNITS} units (an abbreviated
 * PAPI, as installed on shorter runways) sit contiguously along the array axis and all of them share
 * the same {@link PapiLightBlock#FACING}. Anything else -- one, three, five, a mixed-facing row --
 * leaves every block in the run inactive and dark.
 *
 * <h2>Geometry</h2>
 * {@code FACING} is the direction the lights <em>aim</em>: down the approach, toward the aircraft.
 * A real PAPI array is a lateral row perpendicular to that, so the run is scanned along the
 * horizontal axis perpendicular to {@code FACING}.
 *
 * <h2>Unit ordering</h2>
 * Real PAPI puts the shallowest-set unit (nominally 2.5 deg for a 3 deg path) closest to the runway
 * and the steepest (3.5 deg) furthest away, which -- for the standard left-hand installation --
 * places the shallowest unit at the <em>pilot's right</em>. The pilot looks back along
 * {@code -FACING}, so their right hand points along {@code FACING.getCounterClockWise()}. Index 0
 * (shallowest, first to turn white) therefore lives at the {@code getCounterClockWise()} end and
 * indices increase toward {@code getClockWise()}. On path that reads RED RED WHITE WHITE from the
 * pilot's left to right for a full PAPI, and RED WHITE for an APAPI, exactly as in the real world.
 *
 * <p>The index-0 unit is also the run's leader: it owns the glideslope/spread configuration and the
 * other three mirror it. Ordering is derived purely from position and facing, so every block in the
 * run agrees on who the leader is without any negotiation.
 */
public final class PapiArray {

    private PapiArray() {
    }

    /** A full PAPI array is four units. */
    public static final int PAPI_UNITS = 4;

    /** An abbreviated PAPI (APAPI) is two units -- the real-world short-runway installation. */
    public static final int APAPI_UNITS = 2;

    /**
     * Hard stop for the scan walk. Only needs to exceed {@link #PAPI_UNITS} far enough to positively
     * identify an over-long run; it also bounds the work done by a neighbour update.
     */
    private static final int MAX_SCAN = 16;

    /** True for run lengths that form a working array: a full PAPI or an APAPI, nothing between. */
    public static boolean isValidUnitCount(int units) {
        return units == PAPI_UNITS || units == APAPI_UNITS;
    }

    /** Direction from any unit toward the low-angle (index 0) end of its array. */
    public static Direction towardLowEnd(BlockState state) {
        return state.getValue(PapiLightBlock.FACING).getCounterClockWise();
    }

    /** The axis the row of blocks runs along, expressed as the index-increasing direction. */
    public static Direction towardHighEnd(BlockState state) {
        return state.getValue(PapiLightBlock.FACING).getClockWise();
    }

    /**
     * Collects the contiguous run of same-facing PAPI blocks containing {@code pos}, ordered from
     * index 0 (low angle) upward. Returns {@code null} if {@code pos} is not a PAPI block.
     *
     * <p>The returned list may be any length -- callers decide with {@link #isValidUnitCount} whether
     * it forms an array. It is deliberately capped at {@link #MAX_SCAN} entries; anything that long
     * is invalid regardless.
     */
    @Nullable
    public static List<BlockPos> collectRun(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PapiLightBlock)) {
            return null;
        }
        Direction facing = state.getValue(PapiLightBlock.FACING);
        Direction toLow = facing.getCounterClockWise();

        // Walk to the low-angle end of the run first so indices come out absolute, not relative
        // to whichever block happened to trigger the rescan.
        BlockPos start = pos;
        for (int i = 0; i < MAX_SCAN; i++) {
            BlockPos next = start.relative(toLow);
            if (!isMember(level, next, facing)) {
                break;
            }
            start = next;
        }

        List<BlockPos> run = new ArrayList<>(PAPI_UNITS);
        BlockPos cursor = start;
        Direction step = toLow.getOpposite();
        for (int i = 0; i < MAX_SCAN && isMember(level, cursor, facing); i++) {
            run.add(cursor);
            cursor = cursor.relative(step);
        }
        return run;
    }

    private static boolean isMember(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PapiLightBlock
                && state.getValue(PapiLightBlock.FACING) == facing;
    }

    /**
     * Rescans the run containing {@code pos} and pushes the result to every block entity in it.
     * Server-side only; clients receive the outcome through the normal block entity sync.
     */
    public static void updateRun(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        List<BlockPos> run = collectRun(level, pos);
        if (run == null || run.isEmpty()) {
            return;
        }
        boolean valid = isValidUnitCount(run.size());

        // Carry the existing configuration across rebuilds so that breaking and replacing a unit
        // doesn't silently reset the array's glideslope. Prefer the run's previous leader, then
        // any unit that was already part of a working array, and only then fall back to defaults
        // -- a unit that was just placed still holds the defaults and must not win.
        int units = valid ? run.size() : 0;
        double glideslope = PapiLightBlockEntity.DEFAULT_GLIDESLOPE_DEG;
        double spread = PapiLightBlockEntity.defaultSpreadFor(units);
        boolean configured = false;
        PapiLightBlockEntity source = null;
        for (BlockPos member : run) {
            PapiLightBlockEntity be = entityAt(level, member);
            if (be == null) {
                continue;
            }
            if (be.wasLeader()) {
                source = be;
                break;
            }
            if (source == null || (source.getIndex() < 0 && be.getIndex() >= 0)) {
                source = be;
            }
        }
        if (source != null && source.isConfigured()) {
            // The player set these deliberately, so they stand even if the array changes size.
            glideslope = source.getGlideslopeDeg();
            spread = source.getSpreadDeg();
            configured = true;
        } else if (source != null) {
            // Untouched array: the glideslope carries over, but the spread follows whichever
            // standard now applies -- 20' per unit for a full PAPI, 15' either side for an APAPI.
            glideslope = source.getGlideslopeDeg();
        }

        BlockPos leader = run.get(0);
        for (int i = 0; i < run.size(); i++) {
            PapiLightBlockEntity be = entityAt(level, run.get(i));
            if (be != null) {
                // An invalid run keeps its remembered leader: that memory is what lets the
                // configuration survive until the array is made whole again.
                be.applyArrayState(valid ? i : -1, units, valid ? leader : be.getLeaderPos(),
                        glideslope, spread, configured);
            }
        }
    }

    /**
     * Rescans the runs on either side of a block that has just been removed. Called with the
     * removed block's old state, since its facing is what defined the axis.
     */
    public static void updateNeighboursOf(Level level, BlockPos removedPos, BlockState removedState) {
        if (level.isClientSide || !(removedState.getBlock() instanceof PapiLightBlock)) {
            return;
        }
        Direction axis = removedState.getValue(PapiLightBlock.FACING).getClockWise();
        updateRun(level, removedPos.relative(axis));
        updateRun(level, removedPos.relative(axis.getOpposite()));
    }

    @Nullable
    public static PapiLightBlockEntity entityAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PapiLightBlockEntity be ? be : null;
    }
}
