package org.example.chatbot.service;

import org.example.chatbot.business.CanchaBusinessService;
import org.example.chatbot.dto.ChatRequest;
import org.example.chatbot.dto.ChatResponse;
import org.example.chatbot.entity.Turno;
import org.example.chatbot.service.intent.Intencion;
import org.example.chatbot.service.intent.IntentDetector;
import org.example.chatbot.service.memory.MemoryService;
import org.example.chatbot.service.orchestrator.LLMOrchestrator;
import org.example.chatbot.service.state.EstadoConversacion;
import org.example.chatbot.service.state.StateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ChatService — Orquestador principal.
 * Flujo:
 *   Telegram → Controller → ChatService
 *                             ├── IntentDetector   (intención local)
 *                             ├── MemoryService     (historial)
 *                             ├── StateManager      (estado de conversación)
 *                             └── LLMOrchestrator   (agentic loop + tools)
 *                                        ↓
 *                                  ToolExecutor
 *                                        ↓
 *                                  ToolDispatcher
 *                                        ↓
 *                                  Business Services
 *                                        ↓
 *                                  Database
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private IntentDetector intentDetector;

    @Autowired
    private MemoryService memory;

    @Autowired
    private StateManager state;

    @Autowired
    private LLMOrchestrator llmOrchestrator;

    @Autowired
    private CanchaBusinessService canchaBusiness;

    /**
     * System prompt del complejo deportivo.
     */
    private static final String SYSTEM_PROMPT = """
            Sos el asistente del Complejo Deportivo. Tu tarea es ayudar a reservar, consultar y cancelar turnos de canchas.
            
            CANCHAS DISPONIBLES:
            - Futbol 5 — $15.000/hora
            - Futbol 6 — $18.000/hora
            - Futbol 8 — $22.000/hora
            
            HORARIOS: Lunes a domingo de 8:00 a 23:00. Turnos de 1 hora.
            
            REGLAS:
            - Habla en espanol, sin emojis, sin Markdown, sin presentarte en cada mensaje.
            - Se breve: maximo 2-3 oraciones por respuesta.
            - NUNCA inventes datos de disponibilidad. Usa la herramienta consultar_disponibilidad.
            - Para reservar necesitas: tipo de cancha (5, 6 u 8), fecha, hora y numero de telefono.
            - Pregunta los datos de a uno, no todos juntos.
            - Cuando tengas todos los datos: usuario_num, cancha_tipo, fecha, hora_inicio y hora_fin, llama a reservar_turno.
            - Si el usuario quiere cancelar pero no tiene el ID, usa primero consultar_turnos_por_numero con su telefono.
            - El usuario_id es el chat de Telegram, NO lo confundas con el numero de telefono del cliente.
            - Da la opcion siempre de MENU o AYUDA para volver al comienzo
            """;

    /**
     * Punto de entrada principal.
     */
    public ChatResponse consultarConIA(String promptUsuario, String sessionId) {
        List<ChatRequest.MensajeHistorial> historial = memory.obtenerHistorial(sessionId);

        try {
            Intencion intencion = intentDetector.detectar(promptUsuario);
            log.info("[Chat] sessionId={} | intencion={} | mensaje={}", sessionId, intencion, promptUsuario);

            String respuestaSimple = manejarIntencionSimple(intencion, sessionId);
            if (respuestaSimple != null) {
                memory.agregarIntercambio(sessionId, promptUsuario, respuestaSimple);
                return new ChatResponse(respuestaSimple, sessionId, false);
            }

            // 2b. Estados que se resuelven directo sin LLM
            EstadoConversacion estado = state.obtenerEstado(sessionId);
            if (estado == EstadoConversacion.ESPERANDO_TELEFONO_CONSULTA) {
                String respuestaTelefono = buscarTurnosPorTelefono(promptUsuario, sessionId);
                memory.agregarIntercambio(sessionId, promptUsuario, respuestaTelefono);
                return new ChatResponse(respuestaTelefono, sessionId, false);
            }

            actualizarEstadoPorIntencion(intencion, sessionId);
            String systemPromptConContexto = construirSystemPromptConContexto(sessionId);
            List<Map<String, Object>> messages = construirMessages(historial, promptUsuario, sessionId);

            log.info("[Chat] Enviando al LLM | estado={} | mensajes={}",
                    state.obtenerEstado(sessionId), messages.size());

            String respuesta = llmOrchestrator.ejecutar(systemPromptConContexto, messages, sessionId);

            memory.agregarIntercambio(sessionId, promptUsuario, respuesta);
            return new ChatResponse(respuesta, sessionId, false);

        } catch (Exception e) {
            log.error("[Chat] Error para sessionId={}", sessionId, e);
            return new ChatResponse("Ocurrio un error. Intenta de nuevo.", sessionId, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTENCIONES SIMPLES (sin LLM)
    // ═══════════════════════════════════════════════════════════════════════════

    private String manejarIntencionSimple(Intencion intencion, String sessionId) {
        return switch (intencion) {
            case SALUDO -> {
                state.reiniciar(sessionId);
                memory.limpiarHistorial(sessionId);
                yield """
                        Hola! Soy CanchaBot, el asistente del Complejo Deportivo.
                        
                        Elegi una opcion:
                        A) Ver turnos disponibles
                        B) Reservar una cancha
                        C) Cancelar un turno
                        D) Ver mis turnos
                        Para volver al comienzo ingresa MENU""";
            }
            case PRECIOS -> """
                    Precios del Complejo Deportivo:
                    - Futbol 5 (cancha 1): $15.000/hora
                    - Futbol 6 (cancha 2): $18.000/hora
                    - Futbol 8 (cancha 3): $22.000/hora
                    Para volver al comienzo ingresa MENU""";
            case HORARIOS -> "Estamos abiertos todos los dias de 8:00 a 23:00. Turnos de 1 hora." +
                    "Para volver al comienzo ingresa MENU";
            case UBICACION -> "Estamos en Av. Del Libertador 4500, Palermo, CABA." +
                    " Para volver al comienzo ingresa MENU";
            case AYUDA -> """
                    Podes pedirme:
                    - Ver turnos disponibles (opcion A)
                    - Reservar una cancha (opcion B)
                    - Cancelar un turno (opcion C)
                    - Ver tus turnos (opcion D)
                    
                    Para reservar necesito tu telefono, tipo de cancha (5, 6 u 8), fecha y hora.""";
            case VER_MIS_TURNOS -> {
                state.cambiarEstado(sessionId, EstadoConversacion.ESPERANDO_TELEFONO_CONSULTA);
                yield "Para buscar tus turnos necesito tu numero de telefono (sin espacios ni guiones).";
            }
            default -> null; // va al LLM
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ESTADO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Actualiza el estado de la conversación según la intención detectada.
     */
    private void actualizarEstadoPorIntencion(Intencion intencion, String sessionId) {
        switch (intencion) {
            case RESERVAR_TURNO -> state.cambiarEstado(sessionId, EstadoConversacion.ESPERANDO_TIPO_CANCHA);
            case CANCELAR_TURNO -> state.cambiarEstado(sessionId, EstadoConversacion.ESPERANDO_TELEFONO_CANCELAR);
            default -> { /* mantener estado actual */ }
        }
    }

    /**
     * Agrega contexto del estado actual al system prompt.
     */
    private String construirSystemPromptConContexto(String sessionId) {
        EstadoConversacion estado = state.obtenerEstado(sessionId);
        StateManager.DatosReserva datos = state.obtenerDatosReserva(sessionId);

        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);

        // Fecha actual para que el LLM sepa qué día es
        LocalDate hoy = LocalDate.now();
        String diaSemana = hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es"));
        sb.append("\n\nCONTEXTO: hoy es ").append(diaSemana).append(" ").append(hoy)
                .append(". El usuario_id es: ").append(sessionId).append(".");

        // Estado actual de la conversación
        sb.append("\nESTADO_ACTUAL: ").append(estado);

        // Datos ya recolectados para no repetir preguntas
        if (datos.tipoCancha != null || datos.fecha != null || datos.hora != null || datos.usuarioNum != null) {
            sb.append("\nDATOS_YA_RECOLECTADOS: ");
            if (datos.tipoCancha != null) sb.append("cancha_tipo=").append(datos.tipoCancha).append(" ");
            if (datos.fecha != null) sb.append("fecha=").append(datos.fecha).append(" ");
            if (datos.hora != null) sb.append("hora=").append(datos.hora).append(" ");
            if (datos.usuarioNum != null) sb.append("telefono=").append(datos.usuarioNum).append(" ");
            sb.append("\nNO PREGUNTES de nuevo los datos que ya tenes. Solo pregunta lo que falta.");
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MENSAJES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Arma el array de mensajes para enviar a la API.
     */
    private List<Map<String, Object>> construirMessages(
            List<ChatRequest.MensajeHistorial> historial,
            String promptUsuario,
            String sessionId) {

        List<Map<String, Object>> messages = new ArrayList<>();

        // Contexto de fecha como primer exchange (solo al inicio)
        boolean esNuevaSesion = historial == null || historial.isEmpty();
        if (esNuevaSesion) {
            LocalDate hoy = LocalDate.now();
            String diaSemana = hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es"));
            String contexto = String.format(
                    "Contexto del sistema: hoy es %s %s. El usuario_id es: %s.",
                    diaSemana, hoy, sessionId
            );
            messages.add(Map.of("role", "user", "content", contexto));
            messages.add(Map.of("role", "assistant", "content", "Entendido."));
        }

        // Historial previo
        if (historial != null && !historial.isEmpty()) {
            for (ChatRequest.MensajeHistorial m : historial) {
                messages.add(Map.of(
                        "role", normalizarRol(m.getRole()),
                        "content", m.getContent() != null ? m.getContent() : ""
                ));
            }
        }

        // Mensaje actual
        messages.add(Map.of("role", "user", "content", promptUsuario));

        return messages;
    }

    private String normalizarRol(String rol) {
        if (rol == null) return "user";
        return switch (rol.toLowerCase().trim()) {
            case "assistant", "bot", "barberbot", "canchabot" -> "assistant";
            default -> "user";
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HANDLER DIRECTO: /mis_turnos (sin LLM)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Busca turnos por teléfono sin pasar por el LLM.
     * Se ejecuta cuando el estado es ESPERANDO_TELEFONO_CONSULTA.
     */
    private String buscarTurnosPorTelefono(String texto, String sessionId) {
        String telefono = texto.replaceAll("[^0-9]", "");

        if (!telefono.matches("\\d{8,12}")) {
            return "El formato de telefono no es valido. Ingresa solo numeros (8 a 12 digitos).";
        }

        List<Turno> turnos = canchaBusiness.buscarPorTelefono(telefono);

        if (turnos.isEmpty()) {
            state.limpiarDatosReserva(sessionId);
            return "No encontre turnos activos para el telefono " + telefono + ".";
        }

        StringBuilder sb = new StringBuilder("Turnos activos para " + telefono + ":\n\n");
        for (Turno t : turnos) {
            sb.append(String.format("ID %d | F%d | %s | %s-%s\n",
                    t.getId(), t.getCanchaTipo(),
                    t.getFecha(), t.getHoraInicio(), t.getHoraFin()));
        }
        sb.append("\nPara cancelar un turno, usa el ID correspondiente.");

        state.limpiarDatosReserva(sessionId);
        return sb.toString();
    }
}
