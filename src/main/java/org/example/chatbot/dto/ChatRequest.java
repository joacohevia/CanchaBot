package org.example.chatbot.dto;

import lombok.Data;
import java.util.List;

// ─── Request que llega al Controller ───────────────────────────────────────────
@Data
public class ChatRequest {
    private String mensaje;
    private String sessionId; // para identificar la conversación (puede ser chatId de Telegram)
    private List<MensajeHistorial> historial;

    @Data
    public static class MensajeHistorial {
        private String role;    // "user" o "model"
        private String content;
    }
}
