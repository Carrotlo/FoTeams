package me.foesio.foTeams.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class LocationUtil {
    private LocationUtil() {
    }

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return String.join(";",
                location.getWorld().getName(),
                String.valueOf(location.getX()),
                String.valueOf(location.getY()),
                String.valueOf(location.getZ()),
                String.valueOf(location.getYaw()),
                String.valueOf(location.getPitch()));
    }

    public static Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] split = raw.split(";");
        if (split.length < 6) {
            return null;
        }
        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(split[1]);
            double y = Double.parseDouble(split[2]);
            double z = Double.parseDouble(split[3]);
            float yaw = Float.parseFloat(split[4]);
            float pitch = Float.parseFloat(split[5]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                return null;
            }
            return new Location(world,
                    x,
                    y,
                    z,
                    yaw,
                    pitch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
