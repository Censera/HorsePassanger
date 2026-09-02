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
        GeyserEntity vehicle = event.vehicle();
        if (!isHorse(vehicle)) {
            return;
        }

        var passengers = vehicle.passengers();
        GeyserEntity addedPassenger = event.addedPassenger();

        if (passengers.size() == 1) {
            setOffset(passengers.get(0), -SEAT_OFFSET);
            setOffset(addedPassenger, SEAT_OFFSET);
        } else {
            addedPassenger.override(GeyserEntityDataTypes.SEAT_OFFSET, null);
        }
    }

    @Subscribe
    public void onDismount(ServerUpdateEntityPassengersEvent.Dismount event) {
        GeyserEntity vehicle = event.vehicle();
        if (!isHorse(vehicle)) {
            return;
        }

        event.removedPassenger().override(GeyserEntityDataTypes.SEAT_OFFSET, null);
        for (GeyserEntity passenger : vehicle.passengers()) {
            passenger.override(GeyserEntityDataTypes.SEAT_OFFSET, null);
        }
    }

    private static void setOffset(GeyserEntity passenger, float x) {
        passenger.override(
                GeyserEntityDataTypes.SEAT_OFFSET,
                Vector3f.from(x, 0.0F, 0.0F)
        );
    }

    private static boolean isHorse(GeyserEntity entity) {
        var identifier = entity.definition().identifier();
        if (!identifier.namespace().equals("minecraft")) {
            return false;
        }

        return identifier.path().equals("horse")
                || identifier.path().equals("skeleton_horse")
                || identifier.path().equals("donkey")
                || identifier.path().equals("mule");
    }
}
