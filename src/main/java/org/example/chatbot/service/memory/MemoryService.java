package org.example.chatbot.service.memory;

import org.example.chatbot.dto.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administra el historial de conversación por sesión.
 * Cada sesión se identifica por sessionId (telegram chat id).
 */
@Service
public class MemoryService {

    private static final int MAX_HISTORIAL = 12;

    private final Map<String, List<ChatRequest.MensajeHistorial>> historiales = new ConcurrentHashMap<>();

    /**
     * Obtiene o crea el historial para una sesión.
     */
    public List<ChatRequest.MensajeHistorial> obtenerHistorial(String sessionId) {
        return historiales.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    /**
     * Agrega un mensaje al historial y trunca si excede el máximo.
     */
    public void agregarMensaje(String sessionId, ChatRequest.MensajeHistorial mensaje) {
        List<ChatRequest.MensajeHistorial> historial = obtenerHistorial(sessionId);
        historial.add(mensaje);
        if (historial.size() > MAX_HISTORIAL) {
            // Podar los más viejos
            List<ChatRequest.MensajeHistorial> truncado = new ArrayList<>(
                    historial.subList(historial.size() - MAX_HISTORIAL, historial.size())
            );
            historiales.put(sessionId, truncado);
        }
    }

    /**
     * Agrega un par usuario-bot al historial en una sola operación.
     */
    public void agregarIntercambio(String sessionId, String textoUsuario, String textoBot) {
        ChatRequest.MensajeHistorial userMsg = new ChatRequest.MensajeHistorial();
        userMsg.setRole("user");
        userMsg.setContent(textoUsuario);

        ChatRequest.MensajeHistorial botMsg = new ChatRequest.MensajeHistorial();
        botMsg.setRole("assistant");
        botMsg.setContent(textoBot);

        List<ChatRequest.MensajeHistorial> historial = obtenerHistorial(sessionId);
        historial.add(userMsg);
        historial.add(botMsg);
        if (historial.size() > MAX_HISTORIAL) {
            List<ChatRequest.MensajeHistorial> truncado = new ArrayList<>(
                    historial.subList(historial.size() - MAX_HISTORIAL, historial.size())
            );
            historiales.put(sessionId, truncado);
        }
    }

    /**
     * Limpia el historial de una sesión (ej: /start).
     */
    public void limpiarHistorial(String sessionId) {
        historiales.remove(sessionId);
    }

    /**
     * Devuelve el tamaño del historial para debugging.
     */
    public int tamanio(String sessionId) {
        List<ChatRequest.MensajeHistorial> h = historiales.get(sessionId);
        return h != null ? h.size() : 0;
    }
}
