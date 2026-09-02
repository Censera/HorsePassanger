# second-passenger

A Paper 26.2 plugin that gives horse-like entities two passengers and positions them in boat-style left/right seats.

## How it works

The normal Bukkit API does not expose horse passenger attachment behavior. The plugin therefore uses a JVM class transformer to modify the vanilla `Entity.canAddPassenger` behavior for horse-like entities and the `AbstractHorse.getPassengerAttachmentPoint` result.

Minecraft remains the source of truth for passenger state. The plugin does not create seat entities, maintain a passenger registry, teleport riders on a scheduler, or replace horses with custom entities.

The first passenger receives the left seat and the second passenger receives the right seat. The seat offset is currently `0.4` blocks from the horse center.

## Running

The plugin JAR is also a Java agent. Start Paper with it before the server JAR is loaded:

```text
java -javaagent:plugins/second-passenger.jar -jar paper.jar
```

Then install the same JAR as a normal Paper plugin.

If the agent is not installed at JVM startup, the plugin disables itself instead of silently running without the transformation.

## Constraints

This build targets Paper 26.2 and Java 26. The transformation targets the Mojang-mapped class names used by that server version.
