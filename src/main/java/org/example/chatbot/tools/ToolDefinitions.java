package org.example.chatbot.tools;

import java.util.List;
import java.util.Map;

/**
 * Define las tools que el modelo puede invocar.
 * Formato compatible con DeepSeek / OpenAI function calling.
 */
public class ToolDefinitions {

    public static List<Map<String, Object>> getTools() {
        return List.of(
            consultarDisponibilidad(),
            reservarTurno(),
            cancelarTurno(),
            consultarTurnosPorNumero()
        );
    }

    /** Tool 1: consultar_disponibilidad */
    private static Map<String, Object> consultarDisponibilidad() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "consultar_disponibilidad",
                "description", "Consulta los turnos ocupados para una fecha. Llamar cuando el usuario pregunta que horarios hay libres.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "fecha", Map.of(
                            "type", "string",
                            "description", "Fecha en formato YYYY-MM-DD. Calcular a partir de referencias como 'hoy', 'mañana'."
                        ),
                        "cancha_tipo", Map.of(
                            "type", "integer",
                            "enum", List.of(5, 6, 8),
                            "description", "Tipo de cancha (5, 6 u 8). Opcional: si el usuario no especifica, omitir para ver todas."
                        )
                    ),
                    "required", List.of("fecha")
                )
            )
        );
    }

    /** Tool 2: reservar_turno */
    private static Map<String, Object> reservarTurno() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "reservar_turno",
                "description", "Reserva un turno. SOLO llamar cuando tengas TODOS los datos: usuario_num (telefono), cancha_tipo, fecha, hora_inicio y hora_fin. Si falta alguno, pregunta al usuario primero.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "usuario_num", Map.of(
                            "type", "string",
                            "description", "Numero de telefono del cliente (sin guiones ni espacios)."
                        ),
                        "cancha_tipo", Map.of(
                            "type", "integer",
                            "enum", List.of(5, 6, 8),
                            "description", "Tipo de cancha: 5, 6 u 8."
                        ),
                        "fecha", Map.of(
                            "type", "string",
                            "description", "Fecha del turno en formato YYYY-MM-DD."
                        ),
                        "hora_inicio", Map.of(
                            "type", "string",
                            "description", "Hora de inicio en formato HH:MM (24hs). Ej: 14:00"
                        ),
                        "hora_fin", Map.of(
                            "type", "string",
                            "description", "Hora de fin en formato HH:MM (24hs). Ej: 15:00"
                        )
                    ),
                    "required", List.of("usuario_num", "cancha_tipo", "fecha", "hora_inicio", "hora_fin")
                )
            )
        );
    }

    /** Tool 3: cancelar_turno */
    private static Map<String, Object> cancelarTurno() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "cancelar_turno",
                "description", "Cancela un turno por ID. Si el usuario no tiene el ID, usar primero 'consultar_turnos_por_numero' con su telefono.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "turno_id", Map.of(
                            "type", "integer",
                            "description", "ID numerico del turno a cancelar."
                        )
                    ),
                    "required", List.of("turno_id")
                )
            )
        );
    }

    /** Tool 4: consultar_turnos_por_numero */
    private static Map<String, Object> consultarTurnosPorNumero() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "consultar_turnos_por_numero",
                "description", "Busca los turnos activos de un cliente por su numero de telefono. Usar cuando el cliente quiere ver sus turnos o necesita el ID para cancelar.",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "usuario_num", Map.of(
                            "type", "string",
                            "description", "Numero de telefono del cliente, sin guiones ni espacios. Ej: 3511234567"
                        )
                    ),
                    "required", List.of("usuario_num")
                )
            )
        );
    }
}
