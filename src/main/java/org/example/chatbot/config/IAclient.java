package org.example.chatbot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Cliente HTTP para la API de DeepSeek (compatible con OpenAI).</b>
 * <p>
 * Único punto de contacto con el modelo de IA. Soporta function calling nativo
 * (tools) para que el LLM pueda decidir ejecutar acciones en el sistema.
 *   <li>Se construye el body JSON con model, temperature, messages[], tools[]</li>
 *   <li>POST a {@code ia.api.url}</li>
 *   <li>Se parsea la respuesta:
 *     <ul>
 *       <li>Si finish_reason = "stop" → el LLM devolvió texto final</li>
 *       <li>Si finish_reason = "tool_calls" → el LLM quiere ejecutar tools</li>
 *     </ul>
 *   </li>
 *   <li>Se devuelve un {@link RespuestaIA} que encapsula ambos casos</li>
 */
@Component
public class IAclient {

    private static final Logger log = LoggerFactory.getLogger(IAclient.class);

    /** URL de la API (ej: https://api.deepseek.com/v1/chat/completions). */
    @Value("${ia.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    /** API key para autenticación Bearer. */
    @Value("${ia.api.key}")
    private String apiKey;

    /** Modelo a usar (ej: deepseek-v4-flash, deepseek-chat). */
    @Value("${ia.model:deepseek-chat}")
    private String model;

    /** Cliente HTTP nativo de Java (más ligero que RestTemplate). */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** Para serializar/deserializar JSON. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ═══════════════════════════════════════════════════════════════════════════
    // MÉTODO PRINCIPAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Llama al modelo con function calling habilitado (tools).
     * ¿Qué hace?
     *   Arma el body con system prompt, mensajes y definiciones de tools
     *   Hace POST a la API
     *   <li>Parsea la respuesta: extrae content y/o tool_calls</li>
     *   <li>Devuelve {@link RespuestaIA} que el orquestador procesa</li>
     * </ol>
     * <p>
     * <b>Parámetros del body:</b>
     * <ul>
     *   <li>temperature=0.2 — bajo para que sea predecible</li>
     *   <li>max_tokens=500 — suficiente para respuestas de reserva</li>
     *   <li>tool_choice="auto" — el modelo decide si usar tools o responder directo</li>
     * </ul>
     *
     * @param systemPrompt instrucciones del sistema (rol, reglas, contexto)
     * @param messages     historial + mensaje actual con roles (user/assistant/tool)
     * @param tools        definiciones de tools disponibles para el LLM
     * @return respuesta parseada (texto o tool_calls)
     */
    public RespuestaIA preguntarConTools(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 500);
        body.put("messages", construirMessages(systemPrompt, messages));
        body.put("tools", tools);
        body.put("tool_choice", "auto");

        String responseBody = llamarAPI(body);
        return parsearRespuesta(responseBody);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNOS
    // ═══════════════════════════════════════════════════════════════════════════
    /**
     * Arma el array messages agregando el system prompt al principio.
     * La API espera: [{"role": "system", "content": "..."}, {"role": "user", ...}, ...]
     */
    private List<Map<String, Object>> construirMessages(
            String systemPrompt, List<Map<String, Object>> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Map.of("role", "system", "content", systemPrompt));
        result.addAll(messages);
        return result;
    }

    /**
     * Hace el POST HTTP a la API de DeepSeek.
     * <p>
     * Serializa el body a JSON, envía con header Authorization: Bearer,
     * y devuelve el body de la respuesta como String.
     *
     * @throws RuntimeException si la API devuelve un código distinto de 200
     */
    private String llamarAPI(Map<String, Object> body) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(body);
        log.debug("[IAclient] Request: {}", jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[IAclient] Error HTTP {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error de API: HTTP " + response.statusCode());
        }

        log.debug("[IAclient] Response: {}", response.body());
        return response.body();
    }

    /**
     * Convierte el JSON de respuesta de la API en un objeto {@link RespuestaIA}.
     * <p>
     * <b>Estructura esperada de la API:</b>
     * <pre>
     * {
     *   "choices": [{
     *     "finish_reason": "stop" | "tool_calls",
     *     "message": {
     *       "content": "texto de respuesta...",
     *       "reasoning_content": "...",          // solo en modo thinking
     *       "tool_calls": [{                     // solo si finish_reason = tool_calls
     *         "id": "call_xxx",
     *         "type": "function",
     *         "function": { "name": "...", "arguments": "{...}" }
     *       }]
     *     }
     *   }]
     * }
     * </pre>
     */
    private RespuestaIA parsearRespuesta(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").get(0).path("message");
        String finishReason = root.path("choices").get(0).path("finish_reason").asText();

        // El content puede ser null cuando el LLM solo devuelve tool_calls
        String content = message.path("content").isNull()
                ? "" : message.path("content").asText("");

        // Parsear tool_calls si existen
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                toolCalls.add(new ToolCall(
                        tc.path("id").asText(),
                        tc.path("function").path("name").asText(),
                        tc.path("function").path("arguments").asText()
                ));
            }
        }

        return new RespuestaIA(content, finishReason, toolCalls, message);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TIPOS INTERNOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Respuesta completa del modelo de IA.
     * <p>
     * Puede ser de dos tipos:
     * <ul>
     *   <li><b>Texto:</b> finish_reason = "stop", content contiene la respuesta</li>
     *   <li><b>Tool calls:</b> finish_reason = "tool_calls", toolCalls no vacío</li>
     * </ul>
     * <p>
     * Guarda el {@code rawMessage} (JSON original) para preservar campos
     * como {@code reasoning_content} que DeepSeek requiere al reenviar
     * mensajes del assistant en el historial (thinking mode).
     */
    public static class RespuestaIA {
        private final String content;          // texto de respuesta (puede ser "")
        private final String finishReason;     // "stop" o "tool_calls"
        private final List<ToolCall> toolCalls; // tools a ejecutar (vacío si es texto)
        private final JsonNode rawMessage;     // JSON original para preservar campos

        public RespuestaIA(String content, String finishReason,
                           List<ToolCall> toolCalls, JsonNode rawMessage) {
            this.content = content;
            this.finishReason = finishReason;
            this.toolCalls = toolCalls;
            this.rawMessage = rawMessage;
        }

        public String getContent()          { return content; }
        public String getFinishReason()     { return finishReason; }
        public List<ToolCall> getToolCalls() { return toolCalls; }
        public boolean hasToolCalls()       { return toolCalls != null && !toolCalls.isEmpty(); }

        /**
         * Convierte el mensaje del assistant en un Map para agregarlo al historial.
         * <p>
         * <b>Importante:</b> preserva {@code reasoning_content} si existe porque
         * DeepSeek en modo thinking lo exige al reenviar mensajes del assistant.
         * También incluye los {@code tool_calls} si los hay, para que el modelo
         * pueda ver qué tools se ejecutaron en turnos anteriores.
         *
         * @return Map listo para agregar al array messages del siguiente request
         */
        public Map<String, Object> toAssistantMessage() {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", "assistant");

            if (content != null && !content.isEmpty()) {
                msg.put("content", content);
            }

            // Requerido por DeepSeek thinking mode
            if (rawMessage != null && rawMessage.has("reasoning_content")) {
                msg.put("reasoning_content", rawMessage.get("reasoning_content").asText());
            }

            // Si el LLM pidió ejecutar tools, las incluimos en el mensaje
            if (hasToolCalls()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc : toolCalls) {
                    tcs.add(Map.of(
                            "id", tc.getId(),
                            "type", "function",
                            "function", Map.of(
                                    "name", tc.getName(),
                                    "arguments", tc.getArgumentsRaw()
                            )
                    ));
                }
                msg.put("tool_calls", tcs);
            }

            return msg;
        }
    }

    /**
     * Representa una tool call que el LLM decidió ejecutar.
     * <p>
     * Ejemplo:
     * <pre>
     *   name: "reservar_turno"
     *   argumentsRaw: "{\"cancha_id\":1,\"fecha\":\"2026-05-27\",\"hora_inicio\":\"14:00\"}"
     * </pre>
     */
    public static class ToolCall {
        private final String id;            // identificador único del tool_call
        private final String name;          // nombre de la tool (ej: "reservar_turno")
        private final String argumentsRaw;  // JSON string con los argumentos

        public ToolCall(String id, String name, String argumentsRaw) {
            this.id = id;
            this.name = name;
            this.argumentsRaw = argumentsRaw;
        }

        public String getId()           { return id; }
        public String getName()         { return name; }
        public String getArgumentsRaw() { return argumentsRaw; }
    }
}
