package me.foesio.foTeams.input.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.input.BalanceDialogAction;
import me.foesio.foTeams.input.BalanceDialogConfig;
import me.foesio.foTeams.input.BalanceDialogRequest;
import me.foesio.foTeams.input.BalanceDialogService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class PaperBalanceDialogService implements BalanceDialogService {
    private static final String INPUT_KEY = "amount";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private final FoTeams plugin;
    private final NativeDialogSupport support;
    private final FoScheduler scheduler;

    public PaperBalanceDialogService(FoTeams plugin, NativeDialogSupport support, FoScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.support = Objects.requireNonNull(support, "support");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean canOpenNative(Player player) {
        return support.canUseNativeDialogs(player);
    }

    @Override
    public boolean open(Player player, BalanceDialogRequest request, Consumer<BalanceDialogAction> onAction) {
        if (!canOpenNative(player) || player == null || !player.isOnline() || request == null) {
            return false;
        }

        try {
            BalanceDialogConfig config = BalanceDialogConfig.load(plugin);
            Map<String, String> placeholders = placeholders(request);
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base(player, config, placeholders))
                    .type(DialogType.multiAction(actions(player, config, request, placeholders, onAction), null, config.columns())));
            ((Audience) player).showDialog(dialog);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            support.disableForSession("Team balance dialog failed: " + exception.getClass().getSimpleName());
            plugin.getLogger().warning("Native team balance dialog failed; using chat fallback: " + exception.getMessage());
            return false;
        }
    }

    private DialogBase base(Player player, BalanceDialogConfig config, Map<String, String> placeholders) {
        Component title = component(player, config.title(), placeholders);
        return DialogBase.builder(title)
                .externalTitle(title)
                .canCloseWithEscape(config.canCloseWithEscape())
                .pause(config.pause())
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body(player, config.body(), config.bodyWidth(), placeholders))
                .inputs(List.of(DialogInput.text(INPUT_KEY, component(player, config.fieldLabel(), placeholders))
                        .width(config.inputWidth())
                        .labelVisible(config.labelVisible())
                        .initial(truncate(replace(config.initialValue(), placeholders), config.maxLength()))
                        .maxLength(config.maxLength())
                        .build()))
                .build();
    }

    private List<DialogBody> body(Player player, List<String> lines, int width, Map<String, String> placeholders) {
        return lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(line -> body(player, line, width, placeholders))
                .toList();
    }

    private DialogBody body(Player player, String line, int width, Map<String, String> placeholders) {
        return DialogBody.plainMessage(component(player, line, placeholders), width);
    }

    private List<ActionButton> actions(Player player,
                                       BalanceDialogConfig config,
                                       BalanceDialogRequest request,
                                       Map<String, String> placeholders,
                                       Consumer<BalanceDialogAction> onAction) {
        List<ActionButton> buttons = new ArrayList<>();
        if (request.canDeposit()) {
            buttons.add(button(player, config.depositButton(), placeholders, submitAction(player, onAction, BalanceDialogAction.Type.DEPOSIT)));
        }
        if (request.canWithdraw()) {
            buttons.add(button(player, config.withdrawButton(), placeholders, submitAction(player, onAction, BalanceDialogAction.Type.WITHDRAW)));
        }
        buttons.add(button(player, config.backButton(), placeholders, action(player, () -> {
            if (onAction != null) {
                onAction.accept(BalanceDialogAction.back());
            }
        })));
        return List.copyOf(buttons);
    }

    private ActionButton button(Player player, BalanceDialogConfig.Button button, Map<String, String> placeholders, DialogAction action) {
        String label = replace(button.label(), placeholders);
        if (button.icon() != null && !button.icon().isBlank()) {
            label = DialogIcons.withIcon(label, button.icon());
        }
        return ActionButton.create(
                component(player, label, Map.of()),
                tooltip(player, button.tooltip(), placeholders),
                Math.max(1, button.width()),
                action
        );
    }

    private DialogAction submitAction(Player player, Consumer<BalanceDialogAction> onAction, BalanceDialogAction.Type type) {
        UUID playerId = player.getUniqueId();
        return DialogAction.customClick((view, audience) -> {
            if (onAction == null) {
                return;
            }
            String amount = readText(view);
            runForPlayer(playerId, audience, () -> onAction.accept(new BalanceDialogAction(type, amount)));
        }, CALLBACK_OPTIONS);
    }

    private DialogAction action(Player player, Runnable runnable) {
        UUID playerId = player.getUniqueId();
        return DialogAction.customClick((view, audience) -> runForPlayer(playerId, audience, runnable), CALLBACK_OPTIONS);
    }

    private void runForPlayer(UUID expectedPlayerId, Audience audience, Runnable action) {
        if (action == null || !(audience instanceof Player player) || !player.getUniqueId().equals(expectedPlayerId)) {
            return;
        }
        scheduler.runForPlayer(player, () -> {
            if (plugin.isEnabled() && player.isOnline() && player.getUniqueId().equals(expectedPlayerId)) {
                action.run();
            }
        });
    }

    private String readText(DialogResponseView view) {
        if (view == null) {
            return "";
        }
        String value = view.getText(INPUT_KEY);
        return value == null ? "" : value;
    }

    private Component component(Player player, String text, Map<String, String> placeholders) {
        return DialogIcons.inlineTokens(player, LEGACY.deserialize(
                plugin.getMessages().renderTemplateForViewer(player, text, placeholders)));
    }

    private Component tooltip(Player player, String text, Map<String, String> placeholders) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return component(player, text, placeholders);
    }

    private Map<String, String> placeholders(BalanceDialogRequest request) {
        return Map.of(
                "team_balance", safe(request.teamBalance()),
                "player_balance", safe(request.playerBalance())
        );
    }

    private String replace(String text, Map<String, String> placeholders) {
        String result = safe(text);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", safe(entry.getValue()));
        }
        return result;
    }

    private String truncate(String value, int maxLength) {
        String safeValue = safe(value);
        int safeMax = Math.max(1, maxLength);
        return safeValue.length() <= safeMax ? safeValue : safeValue.substring(0, safeMax);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
