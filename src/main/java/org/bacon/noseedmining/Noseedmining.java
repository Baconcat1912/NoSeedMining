package org.bacon.noseedmining;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;

import org.bacon.noseedmining.state.OreSecretPersistentState;

public class Noseedmining implements ModInitializer {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                OreSecretPersistentState.get(overworld);
            }
        });
    }
}
