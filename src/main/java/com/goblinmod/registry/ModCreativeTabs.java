package com.goblinmod.registry;

import com.goblinmod.GoblinMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GoblinMod.MODID);

    public static final RegistryObject<CreativeModeTab> GOBLIN_TAB = CREATIVE_MODE_TABS.register("goblin",
            () -> CreativeModeTab.builder()
                    .icon(() -> ModItems.GOBLIN_SPAWN_EGG.get().getDefaultInstance())
                    .title(Component.translatable("itemGroup.goblinmod.goblin"))
                    .displayItems((params, output) -> output.accept(ModItems.GOBLIN_SPAWN_EGG.get()))
                    .build());
}
