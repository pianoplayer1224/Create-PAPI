package com.papilights.network;

import com.papilights.PapiLights;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PapiNetwork {

    private PapiNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PapiLights.MOD_ID).versioned("1");
        registrar.playToServer(
                SetPapiConfigPayload.TYPE,
                SetPapiConfigPayload.STREAM_CODEC,
                SetPapiConfigPayload::handle);
    }
}
