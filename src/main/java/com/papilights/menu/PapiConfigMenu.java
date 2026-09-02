package com.papilights.menu;

import com.papilights.block.PapiArray;
import com.papilights.block.PapiLightBlockEntity;
import com.papilights.registry.PapiRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Slot-less menu backing the PAPI configuration screen.
 *
 * <p>It exists so the screen is opened and validated the standard way -- the server decides when it
 * may open, closes it when the player walks away, and the initial values ride along in the menu's
 * open payload. It is always bound to the array's leader, whichever of the four units was clicked.
 */
public class PapiConfigMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos leaderPos;
    private final double initialGlideslope;
    private final double initialSpread;
    private final int unitCount;

    /** Server-side constructor. */
    public PapiConfigMenu(int containerId, Inventory inventory, PapiLightBlockEntity leader) {
        super(PapiRegistry.PAPI_CONFIG_MENU.get(), containerId);
        this.leaderPos = leader.getBlockPos();
        this.initialGlideslope = leader.getGlideslopeDeg();
        this.initialSpread = leader.getSpreadDeg();
        this.unitCount = leader.getUnitCount();
        this.access = leader.getLevel() == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(leader.getLevel(), leaderPos);
    }

    /** Client-side constructor, fed by the payload written in {@link #open}. */
    public PapiConfigMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(PapiRegistry.PAPI_CONFIG_MENU.get(), containerId);
        this.leaderPos = buf.readBlockPos();
        this.initialGlideslope = buf.readDouble();
        this.initialSpread = buf.readDouble();
        this.unitCount = buf.readVarInt();
        this.access = ContainerLevelAccess.NULL;
    }

    public static void open(ServerPlayer player, PapiLightBlockEntity leader) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable(leader.getUnitCount() == PapiArray.APAPI_UNITS
                        ? "gui.papilights.config.title.apapi"
                        : "gui.papilights.config.title.papi");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return new PapiConfigMenu(containerId, inventory, leader);
            }
        }, buf -> {
            buf.writeBlockPos(leader.getBlockPos());
            buf.writeDouble(leader.getGlideslopeDeg());
            buf.writeDouble(leader.getSpreadDeg());
            buf.writeVarInt(leader.getUnitCount());
        });
    }

    public BlockPos getLeaderPos() {
        return leaderPos;
    }

    public double getInitialGlideslope() {
        return initialGlideslope;
    }

    public double getInitialSpread() {
        return initialSpread;
    }

    /** 4 for a full PAPI, 2 for an APAPI. */
    public int getUnitCount() {
        return unitCount;
    }

    /** Resolves the leader block entity on the server for a config write. */
    @Nullable
    public PapiLightBlockEntity resolveLeader(Player player) {
        if (player.level().isClientSide) {
            return null;
        }
        return player.level().getBlockEntity(leaderPos) instanceof PapiLightBlockEntity be ? be : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PapiRegistry.block());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
