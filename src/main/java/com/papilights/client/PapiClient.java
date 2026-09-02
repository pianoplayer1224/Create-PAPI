package com.papilights.client;

import com.papilights.PapiLights;
import com.papilights.registry.PapiRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only wiring. Nothing here is touched on a dedicated server: the block, the multiblock scan
 * and the config sync are all plain server-authoritative state, and only the colour is client-side.
 */
@EventBusSubscriber(modid = PapiLights.MOD_ID, value = Dist.CLIENT)
public final class PapiClient {

    private PapiClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PapiRegistry.PAPI_LIGHT_BE.get(), PapiLightRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PapiRegistry.PAPI_CONFIG_MENU.get(), PapiConfigScreen::new);
    }
}
