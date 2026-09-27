package com.assistente.backend.web;

import com.assistente.backend.CommandHandler;
import com.assistente.backend.CommandResult;
import com.assistente.backend.web.dto.MessageRequest;
import com.assistente.backend.web.dto.MessageResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Unico ponto de entrada consumido pelo bot do WhatsApp: recebe o texto que
 * o usuario digitou e devolve a resposta ja formatada, pronta para reenviar
 * pelo WhatsApp. Toda a logica de comandos/parsing fica no backend Java -
 * o bot Node so repassa mensagens.
 */
@RestController
public class MessageController {

    private final CommandHandler commandHandler;

    public MessageController(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @PostMapping("/api/message")
    public MessageResponse handle(@RequestBody MessageRequest request) {
        CommandResult result = commandHandler.handle(request.text(), request.activeReminderId());

        if (result.filePath() == null) {
            return MessageResponse.text(result.text(), result.clearActiveReminder());
        }

        try {
            byte[] bytes = Files.readAllBytes(result.filePath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return new MessageResponse(result.text(), result.clearActiveReminder(),
                    result.filePath().getFileName().toString(), base64);
        } catch (IOException e) {
            return MessageResponse.text("Backup gerado, mas houve erro ao ler o arquivo: " + e.getMessage(),
                    result.clearActiveReminder());
        }
    }
}
