package com.goblinmod.registry;

import com.goblinmod.GoblinMod;
import com.goblinmod.entity.GoblinEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GoblinMod.MODID);

    public static final RegistryObject<EntityType<GoblinEntity>> GOBLIN = ENTITIES.register("goblin",
            () -> EntityType.Builder.of(GoblinEntity::new, MobCategory.MONSTER).sized(0.6F, 1.4F).build("goblin"));
}
