package org.example.chatbot.tools;

import org.example.chatbot.business.CanchaBusinessService;
import org.example.chatbot.business.ReservaBusinessService;
import org.example.chatbot.entity.Turno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Capa de ejecución de herramientas (tools).
 * <p>
 * Router: parsea argumentos del LLM y delega en los Business Services.
 * No tiene lógica de negocio propia.
 */
@Component
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    @Autowired
    private CanchaBusinessService canchaBusiness;

    @Autowired
    private ReservaBusinessService reservaBusiness;

    /**
     * Entry point: LLMOrchestrator llama acá con el nombre de la tool y los args.
     */
    public ToolResult dispatch(String toolName, Map<String, Object> args) {
        log.info("[Tool] Ejecutando: {} | args: {}", toolName, args);

        return switch (toolName) {
            case "consultar_disponibilidad"  -> consultarDisponibilidad(args);
            case "reservar_turno"            -> reservarTurno(args);
            case "cancelar_turno"            -> cancelarTurno(args);
            case "consultar_turnos_por_numero" -> consultarTurnosPorNumero(args);
            default -> {
                log.warn("[Tool] Tool no reconocida: {}", toolName);
                yield ToolResult.error("Accion no reconocida: " + toolName);
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // consultar_disponibilidad
    // ─────────────────────────────────────────────────────────────────────────

        /**
     * Consulta turnos ocupados para una fecha y tipo de cancha.
     * Ahora devuelve los slots LIBRES en vez de los ocupados.
     */
        private ToolResult consultarDisponibilidad(Map<String, Object> args) {
            try {
                String fechaStr = (String) args.get("fecha");
                if (fechaStr == null) return ToolResult.error("Falta el parametro 'fecha'.");

                LocalDate fecha = LocalDate.parse(fechaStr);
                Integer canchaTipo = args.get("cancha_tipo") != null
                        ? ((Number) args.get("cancha_tipo")).intValue()
                        : null;

                if (canchaTipo != null) {
                    // Slots libres para un tipo específico de cancha
                    List<String> libres = canchaBusiness.calcularDisponibles(fecha, canchaTipo);
                    if (libres.isEmpty()) {
                        return ToolResult.ok("No hay horarios disponibles para F" + canchaTipo + " el " + fechaStr + ".");
                    }
                    return ToolResult.ok("Horarios disponibles para F" + canchaTipo + " el " + fechaStr
                            + ": " + String.join(", ", libres), libres.size());
                } else {
                    // Mostrar disponibilidad por cada tipo de cancha
                    List<String> libres5 = canchaBusiness.calcularDisponibles(fecha, 5);
                    List<String> libres6 = canchaBusiness.calcularDisponibles(fecha, 6);
                    List<String> libres8 = canchaBusiness.calcularDisponibles(fecha, 8);
                    return ToolResult.ok(String.format(
                            "Disponibilidad para el %s:\nF5: %s\nF6: %s\nF8: %s",
                            fechaStr,
                            libres5.isEmpty() ? "sin turnos libres" : String.join(", ", libres5),
                            libres6.isEmpty() ? "sin turnos libres" : String.join(", ", libres6),
                            libres8.isEmpty() ? "sin turnos libres" : String.join(", ", libres8)
                    ));
                }

        } catch (DateTimeParseException e) {
            return ToolResult.error("Formato de fecha invalido. Usar YYYY-MM-DD.");
        } catch (Exception e) {
            log.error("[Tool] Error en consultar_disponibilidad", e);
            return ToolResult.error("Error al consultar disponibilidad.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // reservar_turno
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reserva un turno. Delega en ReservaBusinessService.
     * El LLM ya recolectó: usuario_num, cancha_tipo, fecha, hora_inicio, hora_fin.
     */
    private ToolResult reservarTurno(Map<String, Object> args) {
        try {
            String usuarioNum  = (String) args.get("usuario_num");
            String fechaStr    = (String) args.get("fecha");
            String horaInicio  = (String) args.get("hora_inicio");
            String horaFin     = (String) args.get("hora_fin");

            Integer canchaTipo = args.get("cancha_tipo") instanceof Number n
                    ? n.intValue()
                    : null;

            if (usuarioNum == null || fechaStr == null || horaInicio == null || horaFin == null || canchaTipo == null) {
                return ToolResult.error("Faltan datos. Necesito: usuario_num, cancha_tipo, fecha, hora_inicio, hora_fin.");
            }

            // Validar formato de teléfono: solo dígitos, 8 a 12 caracteres
            if (!usuarioNum.matches("\\d{8,12}")) {
                return ToolResult.error("El numero de telefono debe tener entre 8 y 12 digitos, sin espacios ni guiones.");
            }

            LocalDate fecha = LocalDate.parse(fechaStr);
            LocalTime inicio = LocalTime.parse(horaInicio);
            LocalTime fin = LocalTime.parse(horaFin);

            Turno guardado = reservaBusiness.reservar(usuarioNum, canchaTipo, fecha, inicio, fin);

            log.info("[Tool] Turno reservado id={}", guardado.getId());
            return ToolResult.ok(String.format(
                    "Turno reservado. ID: %d | F%d | Fecha: %s | %s - %s | Tel: %s.",
                    guardado.getId(), canchaTipo, fechaStr, horaInicio, horaFin, usuarioNum));

        } catch (DateTimeParseException e) {
            return ToolResult.error("Formato de fecha/hora invalido.");
        } catch (IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] Error en reservar_turno", e);
            return ToolResult.error("Error al guardar el turno.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // cancelar_turno
    // ─────────────────────────────────────────────────────────────────────────

    private ToolResult cancelarTurno(Map<String, Object> args) {
        try {
            Object idObj = args.get("turno_id");
            if (idObj == null) return ToolResult.error("Falta el ID del turno.");
            String fechaStr    = (String) args.get("fecha");
            String horaInicio  = (String) args.get("hora_inicio");
            LocalDate fecha = LocalDate.parse(fechaStr);
            LocalTime inicio = LocalTime.parse(horaInicio);

            long turnoId = ((Number) idObj).longValue();
            reservaBusiness.cancelar(turnoId,fecha,inicio);

            log.info("[Tool] Turno cancelado id={}", turnoId);
            return ToolResult.ok("Turno " + turnoId + " cancelado correctamente.");

        }catch (DateTimeParseException e) {
            return ToolResult.error("Formato de fecha/hora invalido.");
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] Error en cancelar_turno", e);
            return ToolResult.error("Error al cancelar el turno.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // consultar_turnos_por_numero
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Busca turnos activos por número de teléfono.
     * El usuario usa esto para ver sus turnos o encontrar el ID antes de cancelar.
     */
    private ToolResult consultarTurnosPorNumero(Map<String, Object> args) {
        try {
            String telefono = (String) args.get("usuario_num");
            if (telefono == null || telefono.isBlank()) return ToolResult.error("Falta el numero de telefono.");

            List<Turno> turnos = canchaBusiness.buscarPorTelefono(telefono);

            if (turnos.isEmpty()) {
                return ToolResult.ok("No se encontraron turnos activos para el telefono " + telefono + ".");
            }

            String resumen = turnos.stream()
                    .map(t -> String.format("ID %d | F%d | %s %s-%s",
                            t.getId(), t.getCanchaTipo(),
                            t.getFecha(), t.getHoraInicio(), t.getHoraFin()))
                    .collect(Collectors.joining("\n"));

            return ToolResult.ok("Turnos activos para " + telefono + ":\n" + resumen);

        } catch (Exception e) {
            log.error("[Tool] Error en consultar_turnos_por_numero", e);
            return ToolResult.error("Error al consultar los turnos.");
        }
    }
}
