package org.example.chatbot.controller;

import org.example.chatbot.dto.ChatRequest;
import org.example.chatbot.dto.ChatResponse;
import org.example.chatbot.dto.RespuestaApi;
import org.example.chatbot.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/prompt")
    public ResponseEntity<RespuestaApi<ChatResponse>> enviarPrompt(@RequestBody ChatRequest request) {
        try {
            ChatResponse response = chatService.consultarConIA(
                request.getMensaje(),
                request.getSessionId()
            );
            return ResponseEntity.ok(new RespuestaApi<>(true, "OK", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RespuestaApi<>(false, "Error interno del servidor", null));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("CanchaBot corriendo OK");
    }
}
