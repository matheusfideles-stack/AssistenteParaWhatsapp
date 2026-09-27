package com.assistente.backend.web.dto;

/** Corpo de POST /api/message: o texto recebido no WhatsApp e o id do ultimo lembrete ativo, se houver. */
public record MessageRequest(String text, Long activeReminderId) {
}
