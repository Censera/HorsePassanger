package me.censera.secondpassenger;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class Agent {
    private Agent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        System.setProperty("second-passenger.agent", "true");

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(named("net.minecraft.world.entity.Entity"))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(CanAddPassengerAdvice.class)
                                .on(named("canAddPassenger").and(takesArguments(1)))))
                .type(named("net.minecraft.world.entity.animal.equine.AbstractHorse"))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(PassengerPositionAdvice.class)
                                .on(named("getPassengerAttachmentPoint").and(takesArguments(3)))))
                .installOn(instrumentation);
    }

    public static class CanAddPassengerAdvice {
        @Advice.OnMethodExit
        static void onExit(
                @Advice.This Object vehicle,
                @Advice.Return(readOnly = false) boolean result
        ) {
            if (PassengerLogic.isHorseLike(vehicle) && PassengerLogic.passengerCount(vehicle) < 2) {
                result = true;
            }
        }
    }

    public static class PassengerPositionAdvice {
        @Advice.OnMethodExit
        static void onExit(
                @Advice.This Object vehicle,
                @Advice.Argument(0) Object passenger,
                @Advice.Return(readOnly = false, typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
                Object position
        ) {
            position = PassengerLogic.position(vehicle, passenger, position);
        }
    }
}
