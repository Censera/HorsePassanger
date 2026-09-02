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
    private static final double SPACE_FACTOR = 0.75D;

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
            return lookup.findStaticGetter(interactionResult, "SUCCESS", interactionResult).invoke();
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

    public static void positionRider(Object vehicle, Object passenger) {
        if (!isHorseLike(vehicle)) {
            return;
        }

        List<Object> passengers = passengers(vehicle);
        int index = passengers.indexOf(passenger);
        if (index < 0 || index > 1) {
            return;
        }

        try {
            MethodHandle[] passengerAccess = passengerAccess(passenger.getClass());
            Object position = passengerAccess[0].invokeExact(passenger);

            double passengerWidth = (double) passengerAccess[2].invokeExact(passenger);
            double otherPassengerWidth = passengerWidth;
            if (passengers.size() > 1) {
                Object otherPassenger = passengers.get(index == 0 ? 1 : 0);
                MethodHandle[] otherAccess = passengerAccess(otherPassenger.getClass());
                otherPassengerWidth = (double) otherAccess[2].invokeExact(otherPassenger);
            }

            double gap = ((passengerWidth + otherPassengerWidth) * 0.5D) * SPACE_FACTOR;
            double centerDistance = (passengerWidth * 0.5D) + gap + (otherPassengerWidth * 0.5D);
            double offset = index == 0 ? -centerDistance * 0.5D : centerDistance * 0.5D;
            Object shifted = offset(vehicle, position, offset);

            MethodHandle[] vehicleAccess = access(vehicle.getClass());
            double x = (double) vehicleAccess[2].invokeExact(shifted);
            double y = (double) vehicleAccess[3].invokeExact(shifted);
            double z = (double) vehicleAccess[4].invokeExact(shifted);
            passengerAccess[1].invokeExact(passenger, x, y, z);
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

    private static Object offset(Object vehicle, Object position, double xOffset) {
        try {
            MethodHandle[] access = access(vehicle.getClass());
            double yaw = Math.toRadians((float) access[1].invokeExact(vehicle));
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);

            double x = (double) access[2].invokeExact(position);
            double y = (double) access[3].invokeExact(position);
            double z = (double) access[4].invokeExact(position);

            return access[5].invokeExact(x + xOffset * cos, y, z + xOffset * sin);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to offset passenger position", e);
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

            Class<?> vec3 = Class.forName("net.minecraft.world.phys.Vec3", false, vehicleClass.getClassLoader());
            MethodHandle vecX = lookup.findGetter(vec3, "x", double.class)
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle vecY = lookup.findGetter(vec3, "y", double.class)
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle vecZ = lookup.findGetter(vec3, "z", double.class)
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle vecConstructor = lookup.findConstructor(
                    vec3, MethodType.methodType(void.class, double.class, double.class, double.class)
            ).asType(MethodType.methodType(Object.class, double.class, double.class, double.class));

            return new MethodHandle[]{getPassengers, getYRot, vecX, vecY, vecZ, vecConstructor};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize passenger access for " + vehicleClass.getName(), e);
        }
    }

    private static MethodHandle[] createPassengerAccess(Class<?> passengerClass) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> vec3 = Class.forName("net.minecraft.world.phys.Vec3", false, passengerClass.getClassLoader());

            MethodHandle getPosition = lookup.findVirtual(
                    passengerClass, "position", MethodType.methodType(vec3)
            ).asType(MethodType.methodType(Object.class, Object.class));
            MethodHandle setPos = lookup.findVirtual(
                    passengerClass, "setPos", MethodType.methodType(void.class, double.class, double.class, double.class)
            ).asType(MethodType.methodType(void.class, Object.class, double.class, double.class, double.class));
            MethodHandle getWidth = lookup.findVirtual(
                    passengerClass, "getBbWidth", MethodType.methodType(float.class)
            ).asType(MethodType.methodType(double.class, Object.class));

            return new MethodHandle[]{getPosition, setPos, getWidth};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize passenger access for " + passengerClass.getName(), e);
        }
    }
}
