package me.foesio.foTeams.api;

import org.bukkit.Location;

/** A warp destination. Password contents are never exposed. */
public record WarpSnapshot(String name, Location location, boolean passwordProtected) {
    public WarpSnapshot {
        location = location.clone();
    }

    @Override
    public Location location() {
        return location.clone();
    }
}
