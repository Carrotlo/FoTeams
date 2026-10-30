package me.foesio.foTeams.api.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/** Base for FoTeams events. The async flag reflects the dispatching thread. */
public abstract class TeamEvent extends Event {
    protected TeamEvent() {
        super(Bukkit.getServer() != null && !Bukkit.isPrimaryThread());
    }
}
