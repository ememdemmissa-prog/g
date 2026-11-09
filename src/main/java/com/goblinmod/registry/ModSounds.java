package com.goblinmod.registry;

import com.goblinmod.GoblinMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GoblinMod.MODID);

    public static final RegistryObject<SoundEvent> GOBLIN_IDLE = register("goblin_idle");
    public static final RegistryObject<SoundEvent> GOBLIN_HURT = register("goblin_hurt");
    public static final RegistryObject<SoundEvent> GOBLIN_DEATH = register("goblin_death");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GoblinMod.MODID, name)));
    }
}
