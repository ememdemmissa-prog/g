package com.goblinmod.entity;

import com.goblinmod.GoblinMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GoblinModel extends GeoModel<GoblinEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(GoblinMod.MODID, "geo/goblin.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoblinMod.MODID, "textures/entity/goblin.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(GoblinMod.MODID, "animations/goblin.animation.json");

    @Override
    public ResourceLocation getModelResource(GoblinEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GoblinEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(GoblinEntity animatable) {
        return ANIMATION;
    }
}
