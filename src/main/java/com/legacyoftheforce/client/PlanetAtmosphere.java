package com.legacyoftheforce.client;

import com.legacyoftheforce.planets.PlanetId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Optional steady forest haze; native fluid/status fog remains at least as restrictive. */
public final class PlanetAtmosphere {
    @SubscribeEvent public void fog(ViewportEvent.RenderFog event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !ClientConfig.PLANET_ATMOSPHERE.get() || !level.dimension().equals(PlanetId.ENDOR.dimension())
                || event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = (float)Math.min(event.getFarPlaneDistance(), ClientConfig.FOREST_FOG_DISTANCE.get());
        if (!Float.isFinite(far) || far <= 0) return;
        event.setFarPlaneDistance(far); event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * .5f)); event.setCanceled(true);
    }
}
