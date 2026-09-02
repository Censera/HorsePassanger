package me.censera.secondpassenger;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class Agent {
    private static JarFile bootstrapJar;

    private Agent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        appendPassengerLogicToBootstrap(instrumentation);

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(named("net.minecraft.world.entity.Entity"))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(CanAddPassengerAdvice.class)
                                .on(named("canAddPassenger").and(takesArguments(1)))))
                .type(named("net.minecraft.world.entity.animal.equine.AbstractHorse"))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(PassengerInteractAdvice.class)
                                .on(named("mobInteract").and(takesArguments(2))))
                        .visit(Advice.to(PositionRiderAdvice.class)
                                .on(named("positionRider").and(takesArguments(2)))))
                .installOn(instrumentation);

        System.setProperty("second-passenger.agent", "true");
    }

    private static void appendPassengerLogicToBootstrap(Instrumentation instrumentation) {
        try {
            Path jar = Files.createTempFile("second-passenger-bootstrap-", ".jar");
            jar.toFile().deleteOnExit();

            try (InputStream input = Agent.class.getClassLoader().getResourceAsStream(
                    "me/censera/secondpassenger/PassengerLogic.class");
                 JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                if (input == null) {
                    throw new IllegalStateException("PassengerLogic.class is missing from the agent JAR");
                }

                output.putNextEntry(new JarEntry("me/censera/secondpassenger/PassengerLogic.class"));
                input.transferTo(output);
                output.closeEntry();
            }

            bootstrapJar = new JarFile(jar.toFile());
            instrumentation.appendToBootstrapClassLoaderSearch(bootstrapJar);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to add PassengerLogic to the bootstrap classloader", e);
        }
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

    public static class PassengerInteractAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static Object onEnter(
                @Advice.This Object vehicle,
                @Advice.Argument(0) Object player,
                @Advice.Argument(1) Object hand
        ) {
            return PassengerLogic.additionalPassengerResult(vehicle, player, hand);
        }

        @Advice.OnMethodExit
        static void onExit(
                @Advice.Enter Object result,
                @Advice.Return(readOnly = false, typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
                Object original
        ) {
            if (result != null) {
                original = result;
            }
        }
    }

    public static class PositionRiderAdvice {
        @Advice.OnMethodExit
        static void onExit(
                @Advice.This Object vehicle,
                @Advice.Argument(0) Object passenger
        ) {
            PassengerLogic.positionRider(vehicle, passenger);
        }
    }
}
