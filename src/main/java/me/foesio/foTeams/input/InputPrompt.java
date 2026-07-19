package me.foesio.foTeams.input;

public record InputPrompt(
        String dialogId,
        String title,
        String body,
        String fieldLabel,
        String currentValue,
        String format,
        String chatPrompt,
        int maxLength
) {
}
