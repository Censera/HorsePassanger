package me.censera.secondpassenger;

import org.bukkit.plugin.java.JavaPlugin;

public final class SecondPassenger extends JavaPlugin {
    @Override
    public void onEnable() {
        if (!Agent.isInstalled()) {
            getLogger().severe("Bytecode transformer is not installed. Start Paper with -javaagent:path/to/second-passenger.jar");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Horse passenger transformation is active.");
    }
}
