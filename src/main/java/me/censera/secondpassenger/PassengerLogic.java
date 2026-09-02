package me.censera.secondpassenger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class PassengerLogic {
    private static final String HORSE_CLASS = "net.minecraft.world.entity.animal.equine.AbstractHorse";
    private static final double SEAT_OFFSET = 0.4D;

    private static final ConcurrentHashMap<Class<?>, MethodHandle[]> ACCESS = new ConcurrentHashMap<>();

    private PassengerLogic() {
    }

    public static boolean isHorseLike(Object vehicle) {
        for (Class<?> current = vehicle.getClass(); current != null; current = current.getSuperclass()) {
            if (HORSE_CLASS.equals(current.getName())) {
                return true;
            }
        }

        return false;
    }

    public static int passengerCount(Object vehicle) {
        return passengers(vehicle).size();
    }

    public static Object position(Object vehicle, Object passenger, Object position) {
        if (!isHorseLike(vehicle) || position == null) {
            return position;
        }

        List<Object> passengers = passengers(vehicle);
        int index = passengers.indexOf(passenger);
        if (index < 0 || index > 1) {
            return position;
        }

        double offset = index == 0 ? -SEAT_OFFSET : SEAT_OFFSET;
        return offset(vehicle, position, offset);
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

            double rotatedX = xOffset * cos;
            double rotatedZ = xOffset * sin;

            return access[5].invokeExact(x + rotatedX, y, z + rotatedZ);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to offset passenger position", e);
        }
    }

    private static MethodHandle[] access(Class<?> vehicleClass) {
        return ACCESS.computeIfAbsent(vehicleClass, PassengerLogic::createAccess);
    }

    private static MethodHandle[] createAccess(Class<?> vehicleClass) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getPassengers = lookup.findVirtual(
                    vehicleClass,
                    "getPassengers",
                    MethodType.methodType(List.class)
            ).asType(MethodType.methodType(List.class, Object.class));

            MethodHandle getYRot = lookup.findVirtual(
                    vehicleClass,
                    "getYRot",
                    MethodType.methodType(float.class)
            ).asType(MethodType.methodType(float.class, Object.class));

            Class<?> vec3 = Class.forName(
                    "net.minecraft.world.phys.Vec3",
                    false,
                    vehicleClass.getClassLoader()
            );

            MethodHandle vecX = lookup.findGetter(vec3, "x", double.class)
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle vecY = lookup.findGetter(vec3, "y", double.class)
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle vecZ = lookup.findGetter(vec3, "z", double.class)
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle vecConstructor = lookup.findConstructor(
                    vec3,
                    MethodType.methodType(void.class, double.class, double.class, double.class)
            ).asType(MethodType.methodType(Object.class, double.class, double.class, double.class));

            return new MethodHandle[]{
                    getPassengers,
                    getYRot,
                    vecX,
                    vecY,
                    vecZ,
                    vecConstructor
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize passenger access for " + vehicleClass.getName(), e);
        }
    }
}
