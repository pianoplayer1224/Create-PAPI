package com.papilights;

import com.papilights.network.PapiNetwork;
import com.papilights.registry.PapiRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(PapiLights.MOD_ID)
public class PapiLights {

    public static final String MOD_ID = "papilights";

    /**
     * Create's main creative tab. Matched by registry key rather than by referencing
     * {@code AllCreativeModeTabs}, so nothing here breaks if Create shuffles its class layout.
     */
    private static final ResourceKey<CreativeModeTab> CREATE_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("create", "base"));

    public PapiLights(IEventBus modBus) {
        PapiRegistry.register(modBus);
        modBus.addListener(PapiNetwork::register);
        modBus.addListener(PapiLights::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (CREATE_TAB.equals(event.getTabKey())) {
            event.accept(PapiRegistry.PAPI_LIGHT_ITEM.get());
        }
    }
}
