package me.foesio.foTeams.input;

import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.foTeams.FoTeams;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CoreInputGuiService implements InputGuiService {
    private static final String DISPLAY_RESTORE_TOKEN = "{theme}";
    private static final long CLOSE_SUPPRESSION_TICKS = 5L;
    private static final Set<String> PUBLIC_DIALOGS = Set.of(
            "team-name",
            "team-tag",
            "team-description",
            "tag-color",
            "warp-create",
            "team-search"
    );

    private final FoTeams plugin;
    private final ChatPromptInputGuiService chatFallback;
    private final TeamTextDialogs configuredDialogs;
    private final DialogService dialogs;
    private final Map<UUID, InputPrompt> activeFallbackPrompts = new ConcurrentHashMap<>();
    private final Set<UUID> fallbackCancels = ConcurrentHashMap.newKeySet();

    public CoreInputGuiService(FoTeams plugin, FoCoreContext core) {
        this.plugin = plugin;
        this.chatFallback = new ChatPromptInputGuiService(plugin);
        this.configuredDialogs = new TeamTextDialogs(plugin);
        this.dialogs = core.createDialogService(new FallbackDialogService(
                core.nativeDialogs(),
                (player, request, onClose) -> run(onClose),
                (player, request, onConfirm, onCancel) -> run(onCancel),
                this::openChatFallback
        ));
    }

    public static boolean validateInstalled(FoTeams plugin) {
        return TeamTextDialogs.validateInstalled(plugin);
    }

    @Override
    public void openInput(Player player, InputPrompt prompt, Consumer<String> onSubmit, Runnable onCancel) {
        UUID playerId = player.getUniqueId();
        TextDialogRequest request = requestFor(prompt);
        if (request == null) {
            chatFallback.openInput(player, prompt, onSubmit, onCancel);
            return;
        }
        Consumer<String> submit = value -> {
            fallbackCancels.remove(playerId);
            onSubmit.accept(value);
        };
        Runnable cancel = () -> {
            if (!fallbackCancels.remove(playerId)) {
                plugin.sendMessage(player, "prompt-cancelled");
            }
            onCancel.run();
        };

        boolean nativeAttempt = dialogs.support().canUseNativeDialogs(player);
        boolean suppressedClose = false;
        if (nativeAttempt) {
            suppressedClose = plugin.getGuiService().suppressNextInventoryClose(player);
            player.closeInventory();
        }

        activeFallbackPrompts.put(playerId, prompt);
        boolean openedNative = false;
        try {
            openedNative = dialogs.openTextInput(player, request, submit, cancel);
        } finally {
            activeFallbackPrompts.remove(playerId);
            if (suppressedClose) {
                if (openedNative) {
                    plugin.getGuiService().clearNativeDialogCloseSuppressionLater(player, CLOSE_SUPPRESSION_TICKS);
                } else {
                    plugin.getGuiService().clearNativeDialogCloseSuppression(player);
                }
            }
        }
    }

    @Override
    public void reload() {
        configuredDialogs.reload();
    }

    @Override
    public void close() {
        chatFallback.close();
        activeFallbackPrompts.clear();
        fallbackCancels.clear();
    }

    @Override
    public boolean usesNativeDialogs() {
        return dialogs.support().canUseNativeDialogs();
    }

    private void openChatFallback(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        InputPrompt prompt = activeFallbackPrompts.get(player.getUniqueId());
        if (prompt == null) {
            prompt = new InputPrompt("text-input", request.title(), String.join(" ", request.body()),
                    request.fieldLabel(), request.initialValue(), request.placeholder(), request.fieldLabel(), request.maxLength());
        }
        fallbackCancels.add(player.getUniqueId());
        chatFallback.openInput(player, prompt, onSubmit, onCancel);
    }

    private TextDialogRequest requestFor(InputPrompt prompt) {
        TextDialogRequest fallback = fallbackRequest(prompt);
        Map<String, String> replacements = displayReplacements(prompt);
        Map<String, String> rawReplacements = rawReplacements(prompt);
        TextDialogRequest request = PUBLIC_DIALOGS.contains(prompt.dialogId())
                ? configuredDialogs.request(prompt.dialogId())
                : fallback;
        if (request == null) {
            return null;
        }
        return render(request, replacements, rawReplacements);
    }

    private TextDialogRequest fallbackRequest(InputPrompt prompt) {
        if ("team-search".equals(prompt.dialogId())) {
            return new TextDialogRequest(
                    "Search",
                    lines(prompt.body()),
                    prompt.fieldLabel(),
                    prompt.currentValue(),
                    prompt.format(),
                    DialogButton.search(),
                    DialogButton.cancel(),
                    320,
                    300,
                    Math.max(1, prompt.maxLength()),
                    true,
                    false,
                    false
            );
        }
        if ("editor-number-input".equals(prompt.dialogId()) || "editor-money-input".equals(prompt.dialogId())) {
            return TextDialogRequest.number(lines(prompt.body()), prompt.currentValue(), prompt.format());
        }
        return new TextDialogRequest(
                prompt.title(),
                lines(prompt.body()),
                prompt.fieldLabel(),
                prompt.currentValue(),
                prompt.format(),
                DialogButton.save(),
                DialogButton.cancel(),
                320,
                300,
                Math.max(1, prompt.maxLength()),
                true,
                false,
                false
        );
    }

    private TextDialogRequest render(TextDialogRequest request, Map<String, String> replacements, Map<String, String> rawReplacements) {
        return new TextDialogRequest(
                render(request.title(), replacements),
                request.body().stream().map(line -> render(line, replacements)).toList(),
                render(request.fieldLabel(), replacements),
                replacePlain(request.initialValue(), rawReplacements),
                render(request.placeholder(), replacements),
                render(request.submitButton(), replacements),
                render(request.cancelButton(), replacements),
                request.bodyWidth(),
                request.inputWidth(),
                request.maxLength(),
                request.labelVisible(),
                request.canCloseWithEscape(),
                request.pause()
        );
    }

    private DialogButton render(DialogButton button, Map<String, String> replacements) {
        return new DialogButton(render(button.label(), replacements), render(button.tooltip(), replacements), button.width(), button.icon());
    }

    private String render(String template, Map<String, String> replacements) {
        return plugin.getMessages().renderTemplate(template, replacements);
    }

    private String replacePlain(String template, Map<String, String> replacements) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private Map<String, String> displayReplacements(InputPrompt prompt) {
        return Map.of(
                "current", displayLiteral(prompt.currentValue()),
                "format", prompt.format() == null ? "" : prompt.format(),
                "setting", prompt.title() == null ? "" : prompt.title()
        );
    }

    private Map<String, String> rawReplacements(InputPrompt prompt) {
        return Map.of(
                "current", prompt.currentValue() == null ? "" : prompt.currentValue(),
                "format", prompt.format() == null ? "" : prompt.format(),
                "setting", prompt.title() == null ? "" : prompt.title()
        );
    }

    private String displayLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character == '&' || character == '§') && index + 1 < value.length() && isLegacyCode(value.charAt(index + 1))) {
                char code = value.charAt(++index);
                builder.append("&&").append(code).append(DISPLAY_RESTORE_TOKEN).append(code);
                continue;
            }
            if (character == '#' && hasHexColor(value, index + 1)) {
                String hex = value.substring(index + 1, index + 7);
                builder.append("##").append(hex).append(DISPLAY_RESTORE_TOKEN).append(hex);
                index += 6;
                continue;
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private boolean hasHexColor(String value, int start) {
        if (start + 6 > value.length()) {
            return false;
        }
        for (int index = start; index < start + 6; index++) {
            if (!isHexDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isHexDigit(char character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private boolean isLegacyCode(char character) {
        return "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(character) >= 0;
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\R", -1));
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
