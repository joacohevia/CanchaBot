package org.example.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private String respuesta;
    private String sessionId;
    private boolean turnoGuardado; // indica si se guardó un turno en esta interacción
}
