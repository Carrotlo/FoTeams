package me.foesio.foTeams.service;

import me.foesio.foTeams.config.FileConfig;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SwearFilterService {
    private final FileConfig file;
    private volatile List<String> blockedWords = List.of();

    public SwearFilterService(FileConfig file) {
        this.file = Objects.requireNonNull(file, "file");
        reload();
    }

    public void reload() {
        blockedWords = file.config().getStringList("words").stream()
                .map(SwearFilterService::normalize)
                .filter(word -> !word.isBlank())
                .distinct()
                .toList();
    }

    public boolean containsBlockedWord(String input) {
        if (blockedWords.isEmpty()) {
            return false;
        }
        String normalized = normalize(input);
        if (normalized.isBlank()) {
            return false;
        }
        for (String blockedWord : blockedWords) {
            if (normalized.contains(blockedWord)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder(input.length());
        input.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
