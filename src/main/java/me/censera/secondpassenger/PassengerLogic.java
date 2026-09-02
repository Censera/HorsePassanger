package me.censera.secondpassenger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PassengerLogic {
    private static final String HORSE_CLASS = "net.minecraft.world.entity.animal.equine.AbstractHorse";
    private static final String PLAYER_CLASS = "net.minecraft.world.entity.player.Player";
    private static final String ENTITY_CLASS = "net.minecraft.world.entity.Entity";
    private static final String INTERACTION_HAND_CLASS = "net.minecraft.world.InteractionHand";
    private static final String ITEM_STACK_CLASS = "net.minecraft.world.item.ItemStack";
    private static final String INTERACTION_RESULT_CLASS = "net.minecraft.world.InteractionResult";
    private static final String INTERACTION_SUCCESS_CLASS = "net.minecraft.world.InteractionResult$Success";
    private static final String VEC3_CLASS = "net.minecraft.world.phys.Vec3";

    private static final double PASSENGER_OFFSET = 0.6D;

    private static final Map<Class<?>, MethodHandle[]> ACCESS = new HashMap<>();
    private static final Map<Class<?>, MethodHandle[]> PASSENGER_ACCESS = new HashMap<>();
    private static final Map<Class<?>, Boolean> HORSE_LIKE = new HashMap<>();
    private static final Map<Object, Map<UUID, Integer>> SEATS = new WeakHashMap<>();

    private PassengerLogic() {
    }

    public static boolean isHorseLike(Object vehicle) {
        Boolean cached = HORSE_LIKE.get(vehicle.getClass());
        if (cached != null) {
            return cached;
        }

        boolean result = computeHorseLike(vehicle.getClass());
        HORSE_LIKE.put(vehicle.getClass(), result);
        return result;
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

        if (!isMainHand(hand) || !isEmptyHand(player, hand)) {
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

    private static boolean isMainHand(Object hand) {
        return hand instanceof Enum<?> value && "MAIN_HAND".equals(value.name());
    }

    private static boolean isEmptyHand(Object player, Object hand) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ClassLoader loader = player.getClass().getClassLoader();
            Class<?> interactionHand = Class.forName(INTERACTION_HAND_CLASS, false, loader);
            MethodHandle getItemInHand = lookup.findVirtual(
                    player.getClass(), "getItemInHand", MethodType.methodType(
                            Class.forName(ITEM_STACK_CLASS, false, loader), interactionHand
                    )
            ).asType(MethodType.methodType(Object.class, Object.class, Object.class));
            Object item = getItemInHand.invokeExact(player, hand);

            MethodHandle isEmpty = lookup.findVirtual(
                    item.getClass(), "isEmpty", MethodType.methodType(boolean.class)
            ).asType(MethodType.methodType(boolean.class, Object.class));
            return (boolean) isEmpty.invokeExact(item);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to inspect the player's held item", e);
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
        if (!isHorseLike(vehicle) || passengerCount(vehicle) != 2 || !isPlayer(passenger)) {
            return original;
        }

        try {
            List<Object> passengers = passengers(vehicle);
            UUID passengerId = (UUID) passengerAccess(passenger.getClass())[0].invokeExact(passenger);
            Map<UUID, Integer> seats = SEATS.computeIfAbsent(vehicle, ignored -> new HashMap<>());

            for (UUID id : List.copyOf(seats.keySet())) {
                if (!containsPassengerId(passengers, id)) {
                    seats.remove(id);
                }
            }

            int seat = assignSeat(seats, passengers, passengerId);
            if (seat < 0) {
                return original;
            }

            MethodHandle[] vehicleAccess = access(vehicle.getClass());
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ClassLoader loader = vehicle.getClass().getClassLoader();
            Class<?> vec3 = Class.forName(VEC3_CLASS, false, loader);

            MethodHandle constructor = lookup.findConstructor(
                    vec3, MethodType.methodType(void.class, double.class, double.class, double.class)
            );
            MethodHandle yRot = lookup.findVirtual(
                    vec3, "yRot", MethodType.methodType(vec3, float.class)
            );
            MethodHandle add = lookup.findVirtual(
                    vec3, "add", MethodType.methodType(vec3, vec3)
            );

            double offset = seat == 0 ? -PASSENGER_OFFSET : PASSENGER_OFFSET;
            float yaw = (float) vehicleAccess[1].invokeExact(vehicle);
            Object localOffset = constructor.invoke(offset, 0.0D, 0.0D);
            Object rotatedOffset = yRot.invoke(localOffset, -yaw * (float) (Math.PI / 180.0));
            return add.invoke(original, rotatedOffset);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to calculate passenger attachment point for "
                    + vehicle.getClass().getName(), e);
        }
    }

    private static boolean containsPassengerId(List<Object> passengers, UUID id) {
        for (Object passenger : passengers) {
            try {
                UUID passengerId = (UUID) passengerAccess(passenger.getClass())[0].invokeExact(passenger);
                if (id.equals(passengerId)) {
                    return true;
                }
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to read passenger UUID", e);
            }
        }
        return false;
    }

    private static int assignSeat(Map<UUID, Integer> seats, List<Object> passengers, UUID passengerId) {
        Integer assigned = seats.get(passengerId);
        if (assigned != null) {
            return assigned;
        }

        boolean seat0 = false;
        boolean seat1 = false;
        for (Object passenger : passengers) {
            try {
                UUID id = (UUID) passengerAccess(passenger.getClass())[0].invokeExact(passenger);
                Integer seat = seats.get(id);
                if (seat != null && seat == 0) {
                    seat0 = true;
                } else if (seat != null && seat == 1) {
                    seat1 = true;
                }
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to assign passenger seat", e);
            }
        }

        if (!seat0) {
            assigned = 0;
        } else if (!seat1) {
            assigned = 1;
        } else {
            return -1;
        }

        seats.put(passengerId, assigned);
        return assigned;
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
        MethodHandle[] cached = ACCESS.get(vehicleClass);
        if (cached != null) {
            return cached;
        }

        MethodHandle[] result = createAccess(vehicleClass);
        ACCESS.put(vehicleClass, result);
        return result;
    }

    private static MethodHandle[] passengerAccess(Class<?> passengerClass) {
        MethodHandle[] cached = PASSENGER_ACCESS.get(passengerClass);
        if (cached != null) {
            return cached;
        }

        MethodHandle[] result = createPassengerAccess(passengerClass);
        PASSENGER_ACCESS.put(passengerClass, result);
        return result;
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
            MethodHandle getUUID = lookup.findVirtual(
                    entity, "getUUID", MethodType.methodType(UUID.class)
            ).asType(MethodType.methodType(UUID.class, Object.class));
            return new MethodHandle[]{getUUID};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize passenger access for " + passengerClass.getName(), e);
        }
    }
}
