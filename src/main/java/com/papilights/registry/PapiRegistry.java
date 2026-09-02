package com.papilights.registry;

import com.papilights.PapiLights;
import com.papilights.block.PapiLightBlock;
import com.papilights.block.PapiLightBlockEntity;
import com.papilights.menu.PapiConfigMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class PapiRegistry {

    private PapiRegistry() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PapiLights.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PapiLights.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PapiLights.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PapiLights.MOD_ID);

    public static final Supplier<PapiLightBlock> PAPI_LIGHT = BLOCKS.register("papi_light",
            () -> new PapiLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    // The tubes are lit glass; a little block light sells the effect and
                    // keeps the array visible from the air at night.
                    .lightLevel(state -> 5)));

    public static final Supplier<Item> PAPI_LIGHT_ITEM = ITEMS.register("papi_light",
            () -> new BlockItem(PAPI_LIGHT.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<PapiLightBlockEntity>> PAPI_LIGHT_BE =
            BLOCK_ENTITIES.register("papi_light", () -> BlockEntityType.Builder
                    .of(PapiLightBlockEntity::new, PAPI_LIGHT.get())
                    .build(null));

    public static final Supplier<MenuType<PapiConfigMenu>> PAPI_CONFIG_MENU =
            MENUS.register("papi_config", () -> IMenuTypeExtension.create(PapiConfigMenu::new));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    /** Convenience for the many places that just need the block instance. */
    public static Block block() {
        return PAPI_LIGHT.get();
    }
}
