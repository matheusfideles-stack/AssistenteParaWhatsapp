package com.assistente.backend.web.dto;

/**
 * Resposta de POST /api/message. Quando ha um arquivo de backup para enviar,
 * ele vai em base64 (backupFileBase64/backupFileName) - simples de
 * transportar em JSON, sem precisar de multipart nem de disco compartilhado
 * entre o backend Java e o bot Node.
 */
public record MessageResponse(String text, boolean clearActiveReminder, String backupFileName, String backupFileBase64) {

    public static MessageResponse text(String text, boolean clearActiveReminder) {
        return new MessageResponse(text, clearActiveReminder, null, null);
    }
}
