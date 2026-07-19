package me.foesio.foTeams.input;

import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foTeams.FoTeams;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

public final class BalanceDialogServices {
    private static final String PAPER_SERVICE_CLASS = "me.foesio.foTeams.input.paper.PaperBalanceDialogService";
    private static final BalanceDialogService UNAVAILABLE = new BalanceDialogService() {
        @Override
        public boolean canOpenNative() {
            return false;
        }

        @Override
        public boolean open(Player player, BalanceDialogRequest request, Consumer<BalanceDialogAction> onAction) {
            return false;
        }
    };

    private BalanceDialogServices() {
    }

    public static BalanceDialogService create(FoTeams plugin, FoCoreContext core) {
        NativeDialogSupport support = core.nativeDialogs();
        if (!support.serverSupportsNativeDialogs()) {
            return UNAVAILABLE;
        }
        try {
            Class<?> serviceClass = Class.forName(PAPER_SERVICE_CLASS, true, plugin.getClass().getClassLoader());
            Constructor<?> constructor = serviceClass.getConstructor(FoTeams.class, NativeDialogSupport.class, FoScheduler.class);
            return (BalanceDialogService) constructor.newInstance(plugin, support, core.scheduler());
        } catch (ReflectiveOperationException | LinkageError exception) {
            support.disableForSession("Team balance dialog unavailable: " + exception.getClass().getSimpleName());
            plugin.getLogger().warning("Native team balance dialog unavailable; using chat fallback: " + exception.getMessage());
            return UNAVAILABLE;
        }
    }
}
