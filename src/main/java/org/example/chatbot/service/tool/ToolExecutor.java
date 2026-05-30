package org.example.chatbot.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.chatbot.tools.ToolDispatcher;
import org.example.chatbot.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Ejecuta tools llamadas por el LLM y devuelve resultados estructurados.
 * Es el puente entre el LLM Orchestrator y el ToolDispatcher.
 */
@Service
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    @Autowired
    private ToolDispatcher toolDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Ejecuta una tool a partir de su nombre y argumentos JSON.
     */
    public ToolResult ejecutar(String toolName, String argumentsJson) {
        try {
            Map<String, Object> args = objectMapper.readValue(
                    argumentsJson,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)
            );
            log.info("Ejecutando tool: {} | args: {}", toolName, args);
            return toolDispatcher.dispatch(toolName, args);
        } catch (Exception e) {
            log.error("Error ejecutando tool {}: {}", toolName, e.getMessage(), e);
            return ToolResult.error("Error al ejecutar la acción: " + e.getMessage());
        }
    }
}
