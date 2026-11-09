package com.goblinmod.registry;

import com.goblinmod.GoblinMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GoblinMod.MODID);

    public static final RegistryObject<Item> GOBLIN_SPAWN_EGG = ITEMS.register("goblin_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GOBLIN, 0x3E7D32, 0x1B3D1B, new Item.Properties()));
}
