package me.censera.secondpassenger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

final class PassengerLogic {
    private static final String HORSE_CLASS = "net.minecraft.world.entity.animal.equine.AbstractHorse";
    private static final double SEAT_OFFSET = 0.4D;

    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> type) {
            return Access.create(type);
        }
    };

    private static final ClassValue<Boolean> HORSE_LIKE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (HORSE_CLASS.equals(current.getName())) {
                    return true;
                }
            }

            return false;
        }
    };

    private PassengerLogic() {
    }

    static boolean isHorseLike(Object vehicle) {
        return HORSE_LIKE.get(vehicle.getClass());
    }

    static int passengerCount(Object vehicle) {
        return ACCESS.get(vehicle.getClass()).passengerCount(vehicle);
    }

    static Object position(Object vehicle, Object passenger, Object position) {
        if (!isHorseLike(vehicle) || position == null) {
            return position;
        }

        Access access = ACCESS.get(vehicle.getClass());
        int index = access.passengers(vehicle).indexOf(passenger);
        if (index < 0 || index > 1) {
            return position;
        }

        double offset = index == 0 ? -SEAT_OFFSET : SEAT_OFFSET;
        return access.offset(vehicle, position, offset);
    }

    private record Access(
            MethodHandle getPassengers,
            MethodHandle getYRot,
            MethodHandle vecX,
            MethodHandle vecY,
            MethodHandle vecZ,
            MethodHandle vecConstructor
    ) {
        private static Access create(Class<?> vehicleClass) {
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

                return new Access(getPassengers, getYRot, vecX, vecY, vecZ, vecConstructor);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to initialize passenger access for " + vehicleClass.getName(), e);
            }
        }

        @SuppressWarnings("unchecked")
        private List<Object> passengers(Object vehicle) {
            try {
                return (List<Object>) getPassengers.invokeExact(vehicle);
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to read passengers from " + vehicle.getClass().getName(), e);
            }
        }

        private int passengerCount(Object vehicle) {
            return passengers(vehicle).size();
        }

        private Object offset(Object vehicle, Object position, double xOffset) {
            try {
                double yaw = Math.toRadians(-((float) getYRot.invokeExact(vehicle)));
                double sin = Math.sin(yaw);
                double cos = Math.cos(yaw);

                double x = (double) vecX.invokeExact(position);
                double y = (double) vecY.invokeExact(position);
                double z = (double) vecZ.invokeExact(position);

                double rotatedX = xOffset * cos;
                double rotatedZ = -xOffset * sin;

                return vecConstructor.invokeExact(x + rotatedX, y, z + rotatedZ);
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to offset passenger position", e);
            }
        }
    }
}
