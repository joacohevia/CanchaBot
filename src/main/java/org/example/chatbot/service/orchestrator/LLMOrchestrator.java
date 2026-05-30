package org.example.chatbot.service.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.chatbot.config.IAclient;
import org.example.chatbot.service.tool.ToolExecutor;
import org.example.chatbot.tools.ToolDefinitions;
import org.example.chatbot.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquesta la interacción con el LLM: envía mensajes, tools, y ejecuta
 * el agentic loop (llamar LLM → ejecutar tools → repetir).
 */
@Service
public class LLMOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LLMOrchestrator.class);
    private static final int MAX_LOOPS = 3;

    @Autowired
    private IAclient iAclient;

    @Autowired
    private ToolExecutor toolExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Ejecuta el agentic loop completo.
     *
     * @param systemPrompt instrucciones del sistema
     * @param messages     historial + mensaje actual con roles
     * @param sessionId    para logging
     * @return respuesta final en lenguaje natural
     */
    public String ejecutar(String systemPrompt, List<Map<String, Object>> messages, String sessionId) {
        int loops = 0;

        while (loops < MAX_LOOPS) {
            loops++;
            log.info("[Orquestador] Loop {} para sessionId={}", loops, sessionId);

            try {
                IAclient.RespuestaIA respuesta = iAclient.preguntarConTools(
                        systemPrompt, messages, ToolDefinitions.getTools()
                );

                log.info("[Orquestador] finish_reason={}", respuesta.getFinishReason());

                // ¿El modelo quiere ejecutar tools?
                if (respuesta.hasToolCalls()) {
                    messages = procesarToolCalls(respuesta, messages);
                    continue;
                }

                // Respuesta final en texto
                return respuesta.getContent();

            } catch (Exception e) {
                log.error("[Orquestador] Error en loop {} para sessionId={}", loops, sessionId, e);
                return "Ocurrió un error. Intentá de nuevo.";
            }
        }

        log.warn("[Orquestador] Máximo de loops alcanzado para sessionId={}", sessionId);
        return "No pude completar la operación. Intentá de nuevo más tarde.";
    }

    /**
     * Ejecuta las tools pedidas por el modelo y agrega los resultados al historial.
     */
    private List<Map<String, Object>> procesarToolCalls(
            IAclient.RespuestaIA respuesta,
            List<Map<String, Object>> messages) {

        // Agregar mensaje del assistant con sus tool_calls
        messages.add(respuesta.toAssistantMessage());

        for (IAclient.ToolCall tc : respuesta.getToolCalls()) {
            ToolResult resultado = toolExecutor.ejecutar(tc.getName(), tc.getArgumentsRaw());
            log.info("[Orquestador] Tool {} → éxito={}", tc.getName(), resultado.isExito());

            // Agregar tool_result al historial
            messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", tc.getId(),
                    "content", resultado.toString()
            ));
        }

        return messages;
    }
}
