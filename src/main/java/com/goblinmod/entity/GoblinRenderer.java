package com.goblinmod.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GoblinRenderer extends GeoEntityRenderer<GoblinEntity> {
    public GoblinRenderer(EntityRendererProvider.Context context) {
        super(context, new GoblinModel());
        this.shadowRadius = 0.4F;
        this.withScale(0.7F);
    }
}
