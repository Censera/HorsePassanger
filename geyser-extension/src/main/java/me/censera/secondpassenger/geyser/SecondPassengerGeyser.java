package me.censera.secondpassenger.geyser;

import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.event.java.ServerUpdateEntityPassengersEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.entity.type.Entity;

public final class SecondPassengerGeyser implements Extension {
    private static final float SEAT_OFFSET = 0.6F;

    @Subscribe
    public void onMount(ServerUpdateEntityPassengersEvent.Mount event) {
        GeyserEntity vehicle = event.vehicle();
        if (!isHorse(vehicle)) {
            return;
        }

        var passengers = vehicle.passengers();
        if (passengers.size() != 1) {
            return;
        }

        setOffset(passengers.get(0), -SEAT_OFFSET);
        setOffset(event.addedPassenger(), SEAT_OFFSET);
    }

    @Subscribe
    public void onDismount(ServerUpdateEntityPassengersEvent.Dismount event) {
        GeyserEntity vehicle = event.vehicle();
        if (!isHorse(vehicle)) {
            return;
        }

        setOffset(event.removedPassenger(), 0.0F);
        for (GeyserEntity passenger : vehicle.passengers()) {
            setOffset(passenger, 0.0F);
        }
    }

    private static void setOffset(GeyserEntity passenger, float x) {
        if (!(passenger instanceof Entity entity)) {
            return;
        }

        entity.setRiderSeatPosition(Vector3f.from(x, 0.0F, 0.0F));
        entity.updateBedrockMetadata();
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
