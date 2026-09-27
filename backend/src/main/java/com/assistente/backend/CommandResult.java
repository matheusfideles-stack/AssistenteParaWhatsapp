package com.assistente.backend;

import java.nio.file.Path;

/** Resultado de processar uma mensagem: o texto de resposta e, opcionalmente, um arquivo a anexar (backup). */
public record CommandResult(String text, Path filePath, boolean clearActiveReminder) {

    public static CommandResult of(String text) {
        return new CommandResult(text, null, false);
    }

    public static CommandResult clearingReminder(String text) {
        return new CommandResult(text, null, true);
    }

    public static CommandResult withFile(String text, Path filePath) {
        return new CommandResult(text, filePath, false);
    }
}
