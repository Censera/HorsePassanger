package me.censera.secondpassenger.geyser;

import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.event.java.ServerUpdateEntityPassengersEvent;
import org.geysermc.geyser.api.extension.Extension;

public final class SecondPassengerGeyser implements Extension {
    private static final float SEAT_OFFSET = 0.6F;

    @Subscribe
    public void onMount(ServerUpdateEntityPassengersEvent.Mount event) {
        apply(event.vehicle());
    }

    @Subscribe
    public void onDismount(ServerUpdateEntityPassengersEvent.Dismount event) {
        apply(event.vehicle());
    }

    private void apply(GeyserEntity vehicle) {
        if (!isHorse(vehicle) || vehicle.passengers().size() != 2) {
            return;
        }

        for (int i = 0; i < vehicle.passengers().size(); i++) {
            GeyserEntity passenger = vehicle.passengers().get(i);
            float x = i == 0 ? -SEAT_OFFSET : SEAT_OFFSET;
            passenger.override(GeyserEntityDataTypes.SEAT_OFFSET, Vector3f.from(x, 0.0F, 0.0F));
        }
    }

    private static boolean isHorse(GeyserEntity entity) {
        String type = entity.definition().identifier().toString();
        return type.equals("minecraft:horse")
                || type.equals("minecraft:skeleton_horse")
                || type.equals("minecraft:donkey")
                || type.equals("minecraft:mule");
    }
}
