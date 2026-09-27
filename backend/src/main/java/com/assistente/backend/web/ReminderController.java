package com.assistente.backend.web;

import com.assistente.backend.ReminderQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoint de polling: o bot Node consulta a cada 15s e recebe (e consome) os lembretes pendentes de envio. */
@RestController
public class ReminderController {

    private final ReminderQueueService reminderQueueService;

    public ReminderController(ReminderQueueService reminderQueueService) {
        this.reminderQueueService = reminderQueueService;
    }

    @GetMapping("/api/reminders/pending")
    public List<ReminderQueueService.ReminderMessage> pending() {
        return reminderQueueService.drainPending();
    }
}
