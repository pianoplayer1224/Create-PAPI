package com.papilights.block;

import com.papilights.registry.PapiRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-authoritative state for one PAPI unit.
 *
 * <p>Deliberately <em>not</em> stored here: the colour. Red-vs-white is a property of the viewing
 * angle, not of the light, so each client works it out for itself from its own camera position --
 * see {@code PapiOptics}. The server only owns and syncs the array geometry (index, leader) and the
 * configuration (glideslope, per-unit spread).
 */
public class PapiLightBlockEntity extends BlockEntity {

    /** ICAO/FAA nominal approach path angle. */
    public static final double DEFAULT_GLIDESLOPE_DEG = 3.0D;

    /**
     * Angular step between adjacent units of a full 4-box PAPI: 20 arcminutes, putting the units at
     * 2.5 / 2.8333 / 3.1667 / 3.5 degrees for a 3 degree path -- a 1 degree total spread.
     */
    public static final double DEFAULT_PAPI_SPREAD_DEG = 1.0D / 3.0D;

    /**
     * Step for a 2-box APAPI: the units sit 15 arcminutes either side of the path, so 2.75 / 3.25
     * degrees for a 3 degree path. A wider step than full PAPI, because two units have to cover the
     * same job as four.
     */
    public static final double DEFAULT_APAPI_SPREAD_DEG = 0.5D;

    public static final double MIN_GLIDESLOPE_DEG = 0.5D;
    public static final double MAX_GLIDESLOPE_DEG = 20.0D;
    public static final double MIN_SPREAD_DEG = 0.01D;
    public static final double MAX_SPREAD_DEG = 5.0D;

    /**
     * Height of the middle of the tubes within the block, in blocks. Create's nixie tube model runs
     * its glass from y=3 to y=12; the glowing element sits about the middle of that.
     */
    private static final double BULB_HEIGHT = 7.5D / 16.0D;

    /** Index within a valid array, or -1 when this unit is not part of a run of a workable length. */
    private int index = -1;

    /** How many units the array has: 4 for a full PAPI, 2 for an APAPI, 0 while inactive. */
    private int unitCount;

    /** Position of the run's index-0 unit, or null while inactive. */
    @Nullable
    private BlockPos leaderPos;

    private double glideslopeDeg = DEFAULT_GLIDESLOPE_DEG;
    private double spreadDeg = DEFAULT_PAPI_SPREAD_DEG;

    /**
     * Set once the player applies settings from the config screen. Until then the spread tracks
     * whichever standard the array's current size calls for, so an untouched array is always showing
     * the textbook angles for what it actually is.
     */
    private boolean configured;

    /** Client-side memo of the last computed colour, keyed on the camera position that produced it. */
    @Nullable
    private Vec3 cachedViewer;
    private int cachedColor = -1;

    public PapiLightBlockEntity(BlockPos pos, BlockState state) {
        super(PapiRegistry.PAPI_LIGHT_BE.get(), pos, state);
    }

    /** The standard per-unit spread for an array of the given size. */
    public static double defaultSpreadFor(int units) {
        return units == PapiArray.APAPI_UNITS ? DEFAULT_APAPI_SPREAD_DEG : DEFAULT_PAPI_SPREAD_DEG;
    }

    // --- Array state ----------------------------------------------------------------------

    public boolean isActive() {
        return index >= 0;
    }

    public int getIndex() {
        return index;
    }

    /** 4 for a full PAPI, 2 for an APAPI, 0 while inactive. */
    public int getUnitCount() {
        return unitCount;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** True if this unit was the leader of its run the last time the array was scanned. */
    public boolean wasLeader() {
        return leaderPos != null && leaderPos.equals(worldPosition);
    }

    @Nullable
    public BlockPos getLeaderPos() {
        return leaderPos;
    }

    /** Resolves the block entity that owns this array's configuration, loading nothing off-thread. */
    @Nullable
    public PapiLightBlockEntity getLeader() {
        if (level == null || leaderPos == null) {
            return null;
        }
        if (leaderPos.equals(worldPosition)) {
            return this;
        }
        return PapiArray.entityAt(level, leaderPos);
    }

    public double getGlideslopeDeg() {
        return glideslopeDeg;
    }

    public double getSpreadDeg() {
        return spreadDeg;
    }

    /**
     * The angle at which <em>this</em> unit flips between red and white. Index 0 is set shallowest
     * and the last index steepest, spaced evenly and symmetric about the configured centre angle --
     * so a 4-unit array lands on +/-1.5 and +/-0.5 steps, and a 2-unit APAPI on +/-0.5.
     */
    public double getUnitAngleDeg() {
        return unitAngleDeg(glideslopeDeg, spreadDeg, index, unitCount);
    }

    /** Shared with the config screen so its preview and the lights can never disagree. */
    public static double unitAngleDeg(double glideslope, double spread, int index, int units) {
        return glideslope + (index - (units - 1) / 2.0D) * spread;
    }

    /** Centre of the glowing element, used as the origin for the viewing-angle calculation. */
    public Vec3 getBulbOrigin() {
        return new Vec3(worldPosition.getX() + 0.5D,
                worldPosition.getY() + BULB_HEIGHT,
                worldPosition.getZ() + 0.5D);
    }

    /** Called by {@link PapiArray} after a rescan. Server-side. */
    public void applyArrayState(int newIndex, int newUnitCount, @Nullable BlockPos newLeader,
                                double newGlideslope, double newSpread, boolean newConfigured) {
        boolean changed = newIndex != index
                || newUnitCount != unitCount
                || !java.util.Objects.equals(newLeader, leaderPos)
                || newGlideslope != glideslopeDeg
                || newSpread != spreadDeg
                || newConfigured != configured;
        index = newIndex;
        unitCount = newUnitCount;
        leaderPos = newLeader;
        glideslopeDeg = newGlideslope;
        spreadDeg = newSpread;
        configured = newConfigured;
        if (changed) {
            sync();
        }
    }

    /**
     * Writes new configuration onto the leader and mirrors it across the rest of the run. Values are
     * clamped here rather than trusted from the client.
     */
    public void configureArray(double newGlideslope, double newSpread) {
        if (level == null || level.isClientSide) {
            return;
        }
        double glideslope = clamp(newGlideslope, MIN_GLIDESLOPE_DEG, MAX_GLIDESLOPE_DEG);
        double spread = clamp(newSpread, MIN_SPREAD_DEG, MAX_SPREAD_DEG);

        List<BlockPos> run = PapiArray.collectRun(level, worldPosition);
        if (run == null || !PapiArray.isValidUnitCount(run.size())) {
            // Array was broken between opening the screen and pressing apply; keep our own copy so
            // it survives, but there is nothing to mirror to.
            glideslopeDeg = glideslope;
            spreadDeg = spread;
            configured = true;
            sync();
            return;
        }
        for (BlockPos member : run) {
            PapiLightBlockEntity be = PapiArray.entityAt(level, member);
            if (be != null) {
                be.glideslopeDeg = glideslope;
                be.spreadDeg = spread;
                be.configured = true;
                be.sync();
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.min(max, Math.max(min, value)) : min;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- Client colour cache --------------------------------------------------------------

    /**
     * Returns the previously computed colour when the camera has not meaningfully moved. Recomputing
     * is only a handful of flops plus one atan2, but this keeps a screen full of arrays free even
     * on very high frame rates.
     */
    public int getCachedColor(Vec3 viewer) {
        if (cachedViewer != null && cachedViewer.distanceToSqr(viewer) < 1.0E-6D) {
            return cachedColor;
        }
        return Integer.MIN_VALUE;
    }

    public void putCachedColor(Vec3 viewer, int color) {
        cachedViewer = viewer;
        cachedColor = color;
    }

    private void invalidateColorCache() {
        cachedViewer = null;
    }

    // --- Persistence and sync -------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Index", index);
        tag.putInt("Units", unitCount);
        tag.putDouble("Glideslope", glideslopeDeg);
        tag.putDouble("Spread", spreadDeg);
        tag.putBoolean("Configured", configured);
        if (leaderPos != null) {
            tag.putLong("Leader", leaderPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        index = tag.contains("Index") ? tag.getInt("Index") : -1;
        // Arrays saved before APAPI support existed were always four units.
        unitCount = tag.contains("Units") ? tag.getInt("Units")
                : (index >= 0 ? PapiArray.PAPI_UNITS : 0);
        glideslopeDeg = tag.contains("Glideslope") ? tag.getDouble("Glideslope") : DEFAULT_GLIDESLOPE_DEG;
        spreadDeg = tag.contains("Spread") ? tag.getDouble("Spread") : defaultSpreadFor(unitCount);
        configured = tag.getBoolean("Configured");
        leaderPos = tag.contains("Leader") ? BlockPos.of(tag.getLong("Leader")) : null;
        invalidateColorCache();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
