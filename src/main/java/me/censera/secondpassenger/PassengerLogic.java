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
    private static final String GEYSER_ENTITY_CLASS = "org.geysermc.geyser.entity.type.Entity";
    private static final String GEYSER_HORSE_CLASS = "org.geysermc.geyser.entity.type.living.animal.horse.AbstractHorseEntity";
    private static final double SPACE_FACTOR = 0.75D;
    private static final double MINIMUM_CENTER_DISTANCE = 1.2D;

    private static final ConcurrentHashMap<Class<?>, MethodHandle[]> ACCESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, MethodHandle[]> PASSENGER_ACCESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, MethodHandle[]> GEYSER_ACCESS = new ConcurrentHashMap<>();
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

    public static Object passengerAttachmentPoint(Object vehicle, Object passenger, Object original) {
        if (!isHorseLike(vehicle) || original == null) {
            return original;
        }

        List<Object> passengers = passengers(vehicle);
        int index = passengers.indexOf(passenger);
        if (index < 0 || index > 1 || passengers.size() < 2) {
            return original;
        }

        try {
            MethodHandle[] passengerAccess = passengerAccess(passenger.getClass());
            double passengerWidth = (double) passengerAccess[2].invokeExact(passenger);

            Object otherPassenger = passengers.get(index == 0 ? 1 : 0);
            MethodHandle[] otherAccess = passengerAccess(otherPassenger.getClass());
            double otherPassengerWidth = (double) otherAccess[2].invokeExact(otherPassenger);

            double gap = ((passengerWidth + otherPassengerWidth) * 0.5D) * SPACE_FACTOR;
            double centerDistance = (passengerWidth * 0.5D) + gap + (otherPassengerWidth * 0.5D);
            centerDistance = Math.max(centerDistance, MINIMUM_CENTER_DISTANCE);

            double offset = index == 0 ? -centerDistance * 0.5D : centerDistance * 0.5D;
            return offset(vehicle, original, offset);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to position passenger on " + vehicle.getClass().getName(), e);
        }
    }

    /**
     * Geyser computes every Bedrock rider seat through Entity.setRiderSeatPosition().
     * At that point the passenger already knows its vehicle and the vehicle has its
     * complete passenger list. Apply the same two-seat X offsets Geyser uses for boats,
     * but for horse-like vehicles as well.
     */
    public static Object geyserSeatPosition(Object passenger, Object position) {
        if (position == null || !isGeyserEntity(passenger)) {
            return position;
        }

        try {
            MethodHandle[] access = geyserAccess(passenger.getClass());
            Object vehicle = access[0].invoke(passenger);
            if (vehicle == null || !isGeyserHorse(vehicle)) {
                return position;
            }

            @SuppressWarnings("unchecked")
            List<Object> passengers = (List<Object>) access[1].invoke(vehicle);
            if (passengers.size() != 2) {
                return position;
            }

            int index = passengers.indexOf(passenger);
            if (index < 0 || index > 1) {
                return position;
            }

            MethodHandle[] vectorAccess = geyserVectorAccess(position.getClass());
            float x = index == 0 ? 0.2F : -0.6F;
            float y = ((Number) vectorAccess[1].invoke(position)).floatValue();
            float z = ((Number) vectorAccess[2].invoke(position)).floatValue();
            return vectorAccess[3].invoke(x, y, z);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to position Geyser horse passenger", e);
        }
    }

    private static boolean isGeyserEntity(Object value) {
        for (Class<?> current = value.getClass(); current != null; current = current.getSuperclass()) {
            if (GEYSER_ENTITY_CLASS.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGeyserHorse(Object value) {
        for (Class<?> current = value.getClass(); current != null; current = current.getSuperclass()) {
            if (GEYSER_HORSE_CLASS.equals(current.getName())) {
                return true;
            }
        }
        return false;
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

    private static MethodHandle[] geyserAccess(Class<?> passengerClass) {
        return GEYSER_ACCESS.computeIfAbsent(passengerClass, PassengerLogic::createGeyserAccess);
    }

    private static MethodHandle[] geyserVectorAccess(Class<?> vectorClass) {
        return GEYSER_ACCESS.computeIfAbsent(vectorClass, PassengerLogic::createGeyserVectorAccess);
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
            MethodHandle getWidth = lookup.findVirtual(
                    passengerClass, "getBbWidth", MethodType.methodType(float.class)
            ).asType(MethodType.methodType(double.class, Object.class));
            return new MethodHandle[]{null, null, getWidth};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize passenger access for " + passengerClass.getName(), e);
        }
    }

    private static MethodHandle[] createGeyserAccess(Class<?> passengerClass) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getVehicle = lookup.findVirtual(
                    passengerClass, "getVehicle", MethodType.methodType(Class.forName(
                            "org.geysermc.geyser.entity.type.Entity", false, passengerClass.getClassLoader()))
            ).asType(MethodType.methodType(Object.class, Object.class));
            MethodHandle getPassengers = lookup.findVirtual(
                    Class.forName(GEYSER_ENTITY_CLASS, false, passengerClass.getClassLoader()),
                    "getPassengers", MethodType.methodType(List.class)
            ).asType(MethodType.methodType(List.class, Object.class));
            return new MethodHandle[]{getVehicle, getPassengers};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize Geyser passenger access", e);
        }
    }

    private static MethodHandle[] createGeyserVectorAccess(Class<?> vectorClass) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getX = lookup.findVirtual(vectorClass, "getX", MethodType.methodType(float.class))
                    .asType(MethodType.methodType(float.class, Object.class));
            MethodHandle getY = lookup.findVirtual(vectorClass, "getY", MethodType.methodType(float.class))
                    .asType(MethodType.methodType(float.class, Object.class));
            MethodHandle getZ = lookup.findVirtual(vectorClass, "getZ", MethodType.methodType(float.class))
                    .asType(MethodType.methodType(float.class, Object.class));
            MethodHandle from = lookup.findStatic(vectorClass, "from", MethodType.methodType(
                    vectorClass, float.class, float.class, float.class
            )).asType(MethodType.methodType(Object.class, float.class, float.class, float.class));
            return new MethodHandle[]{getX, getY, getZ, from};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize Geyser vector access", e);
        }
    }
}
