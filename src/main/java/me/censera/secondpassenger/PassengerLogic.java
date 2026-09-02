package me.censera.secondpassenger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class PassengerLogic {
    private static final String HORSE_CLASS = "net.minecraft.world.entity.animal.equine.AbstractHorse";
    private static final String PLAYER_CLASS = "net.minecraft.world.entity.player.Player";
    private static final String ENTITY_CLASS = "net.minecraft.world.entity.Entity";
    private static final String INTERACTION_RESULT_CLASS = "net.minecraft.world.InteractionResult";
    private static final String INTERACTION_SUCCESS_CLASS = "net.minecraft.world.InteractionResult$Success";

    private static final double SPACE_FACTOR = 0.75D;
    private static final double MINIMUM_CENTER_DISTANCE = 1.2D;

    private static final ConcurrentHashMap<Class<?>, MethodHandle[]> ACCESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, MethodHandle[]> PASSENGER_ACCESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> HORSE_LIKE = new ConcurrentHashMap<>();

    private PassengerLogic() {
    }

    public static boolean isHorseLike(Object vehicle) {
        return HORSE_LIKE.computeIfAbsent(vehicle.getClass(), PassengerLogic::computeHorseLike);
    }

    private static boolean computeHorseLike(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (HORSE_CLASS.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    public static int passengerCount(Object vehicle) {
        return passengers(vehicle).size();
    }

    public static Object additionalPassengerResult(Object vehicle, Object player, Object hand) {
        if (!isHorseLike(vehicle) || passengerCount(vehicle) != 1 || !isPlayer(player)) {
            return null;
        }

        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle secondaryUse = lookup.findVirtual(
                    player.getClass(), "isSecondaryUseActive", MethodType.methodType(boolean.class)
            ).asType(MethodType.methodType(boolean.class, Object.class));

            if ((boolean) secondaryUse.invokeExact(player)) {
                return null;
            }

            Class<?> entity = Class.forName(ENTITY_CLASS, false, vehicle.getClass().getClassLoader());
            MethodHandle startRiding = lookup.findVirtual(
                    player.getClass(), "startRiding", MethodType.methodType(boolean.class, entity)
            ).asType(MethodType.methodType(boolean.class, Object.class, Object.class));

            if (!(boolean) startRiding.invokeExact(player, vehicle)) {
                return null;
            }

            Class<?> interactionResult = Class.forName(
                    INTERACTION_RESULT_CLASS, false, vehicle.getClass().getClassLoader()
            );
            Class<?> interactionSuccess = Class.forName(
                    INTERACTION_SUCCESS_CLASS, false, vehicle.getClass().getClassLoader()
            );
            return lookup.findStaticGetter(interactionResult, "SUCCESS_SERVER", interactionSuccess)
                    .asType(MethodType.methodType(Object.class))
                    .invoke();
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to add a second passenger to " + vehicle.getClass().getName(), e);
        }
    }

    private static boolean isPlayer(Object value) {
        for (Class<?> current = value.getClass(); current != null; current = current.getSuperclass()) {
            if (PLAYER_CLASS.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs after vanilla AbstractHorse.positionRider has placed the passenger.
     * The passenger remains a real server-side passenger, but its stored world
     * position is moved sideways along the horse's local X axis.
     */
    public static void positionRider(Object vehicle, Object passenger) {
        if (!isHorseLike(vehicle)) {
            return;
        }

        List<Object> passengers = passengers(vehicle);
        int index = passengers.indexOf(passenger);
        if (index < 0 || index > 1 || passengers.size() != 2 || !isPlayer(passenger)) {
            return;
        }

        try {
            MethodHandle[] vehicleAccess = access(vehicle.getClass());
            MethodHandle[] passengerHandle = passengerAccess(passenger.getClass());

            Object otherPassenger = passengers.get(index == 0 ? 1 : 0);
            double passengerWidth = (double) passengerHandle[4].invokeExact(passenger);
            double otherWidth = (double) passengerAccess(otherPassenger.getClass())[4].invokeExact(otherPassenger);

            double gap = ((passengerWidth + otherWidth) * 0.5D) * SPACE_FACTOR;
            double centerDistance = Math.max(
                    (passengerWidth * 0.5D) + gap + (otherWidth * 0.5D),
                    MINIMUM_CENTER_DISTANCE
            );
            double offset = index == 0 ? -centerDistance * 0.5D : centerDistance * 0.5D;

            double yaw = Math.toRadians((float) vehicleAccess[1].invokeExact(vehicle));
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);

            double x = (double) passengerHandle[0].invokeExact(passenger);
            double y = (double) passengerHandle[1].invokeExact(passenger);
            double z = (double) passengerHandle[2].invokeExact(passenger);

            passengerHandle[3].invokeExact(
                    passenger,
                    x + offset * cos,
                    y,
                    z + offset * sin
            );
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to position passenger on " + vehicle.getClass().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> passengers(Object vehicle) {
        try {
            return (List<Object>) access(vehicle.getClass())[0].invokeExact(vehicle);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to read passengers from " + vehicle.getClass().getName(), e);
        }
    }

    private static MethodHandle[] access(Class<?> vehicleClass) {
        return ACCESS.computeIfAbsent(vehicleClass, PassengerLogic::createAccess);
    }

    private static MethodHandle[] passengerAccess(Class<?> passengerClass) {
        return PASSENGER_ACCESS.computeIfAbsent(passengerClass, PassengerLogic::createPassengerAccess);
    }

    private static MethodHandle[] createAccess(Class<?> vehicleClass) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getPassengers = lookup.findVirtual(
                    vehicleClass, "getPassengers", MethodType.methodType(List.class)
            ).asType(MethodType.methodType(List.class, Object.class));
            MethodHandle getYRot = lookup.findVirtual(
                    vehicleClass, "getYRot", MethodType.methodType(float.class)
            ).asType(MethodType.methodType(float.class, Object.class));
            return new MethodHandle[]{getPassengers, getYRot};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize vehicle access for " + vehicleClass.getName(), e);
        }
    }

    private static MethodHandle[] createPassengerAccess(Class<?> passengerClass) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> entity = Class.forName(ENTITY_CLASS, false, passengerClass.getClassLoader());

            MethodHandle getX = lookup.findVirtual(
                    entity, "getX", MethodType.methodType(double.class)
            ).asType(MethodType.methodType(double.class, Object.class));
            MethodHandle getY = lookup.findVirtual(
                    entity, "getY", MethodType.methodType(double.class)
            ).asType(MethodType.methodType(double.class, Object.class));
            MethodHandle getZ = lookup.findVirtual(
                    entity, "getZ", MethodType.methodType(double.class)
            ).asType(MethodType.methodType(double.class, Object.class));
            MethodHandle getWidth = lookup.findVirtual(
                    entity, "getBbWidth", MethodType.methodType(float.class)
            ).asType(MethodType.methodType(double.class, Object.class));
            MethodHandle setPos = lookup.findVirtual(
                    entity, "setPos", MethodType.methodType(void.class, double.class, double.class, double.class)
            ).asType(MethodType.methodType(void.class, Object.class, double.class, double.class, double.class));

            return new MethodHandle[]{getX, getY, getZ, setPos, getWidth};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize passenger access for " + passengerClass.getName(), e);
        }
    }
}
