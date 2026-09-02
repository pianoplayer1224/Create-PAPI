package com.papilights.compat;

import com.papilights.PapiLights;
import com.papilights.registry.PapiRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Adds an information page for the PAPI Light explaining how to build and configure an array.
 *
 * <p>JEI is an optional dependency: this class is only ever loaded by JEI's own plugin discovery,
 * so it costs nothing when JEI is absent.
 */
@JeiPlugin
public class PapiJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(PapiLights.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
                PapiRegistry.PAPI_LIGHT_ITEM.get(),
                Component.translatable("jei.papilights.info.papi_light"));
    }
}
