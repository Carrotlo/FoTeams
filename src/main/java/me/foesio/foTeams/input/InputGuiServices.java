package me.foesio.foTeams.input;

import me.foesio.core.FoCoreContext;
import me.foesio.foTeams.FoTeams;

public final class InputGuiServices {
    private InputGuiServices() {
    }

    public static InputGuiService create(FoTeams plugin, FoCoreContext core) {
        return new CoreInputGuiService(plugin, core);
    }
}
