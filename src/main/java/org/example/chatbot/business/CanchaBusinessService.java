package org.example.chatbot.business;

import org.example.chatbot.entity.Turno;
import org.example.chatbot.repository.TurnoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <b>Reglas de negocio para consultas de canchas.</b>
 * <p>
 * Capa de consulta: disponibilidad, búsqueda y verificación.
 * Usada por {@code ToolDispatcher} para las tools de consulta.
 */
@Service
public class CanchaBusinessService {

    @Autowired
    private TurnoRepository turnoRepository;

    /**
     * Consulta los turnos <b>ocupados</b> para una fecha.
     * <p>
     * Los turnos cancelados NO se consideran ocupados (liberan el slot).
     *
     * @param fecha      día a consultar
     * @param canchaTipo tipo de cancha (5, 6 u 8). Si es null, devuelve todos
     * @return lista de turnos ocupados (sin cancelados)
     */
    public List<Turno> consultarOcupados(LocalDate fecha, Integer canchaTipo) {
        if (canchaTipo != null) {
            return turnoRepository.findByFechaAndCanchaTipoAndEstadoNot(fecha, canchaTipo, "cancelado");
        }
        return turnoRepository.findByFechaAndEstadoNot(fecha, "cancelado");
    }

    /**
     * Verifica si un horario ya está ocupado para un tipo de cancha.
     * F5, F6 y F8 no se bloquean entre sí.
     */
    public boolean estaOcupado(Integer canchaTipo, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        return turnoRepository.existeSolapamiento(canchaTipo, fecha, horaInicio, horaFin);
    }

    /**
     * Busca los turnos activos de un cliente por su número de teléfono.
     * El cliente usa esto para ver sus turnos o encontrar el ID antes de cancelar.
     *
     * @param usuarioNum número de teléfono del cliente
     * @return lista de turnos en estado "reservado"
     */
    public List<Turno> buscarPorTelefono(String usuarioNum) {
        return turnoRepository.findByUsuarioNumAndEstado(usuarioNum, "reservado");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SLOTS DISPONIBLES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calcula los horarios <b>disponibles</b> para una fecha y tipo de cancha.
     * <p>
     * Genera todos los slots de 1 hora entre 08:00 y 22:00 (último turno 22:00-23:00),
     * consulta los ocupados en la BD, y devuelve solo los libres.
     *
     * @param fecha      día a consultar
     * @param canchaTipo tipo de cancha (5, 6 u 8)
     * @return lista de horas de inicio disponibles (ej: ["08:00", "09:00", ...])
     */
    public List<String> calcularDisponibles(LocalDate fecha, Integer canchaTipo) {
        // 1. Generar todos los slots posibles (1 hora cada uno, de 8 a 22)
        List<LocalTime> todosLosSlots = new ArrayList<>();
        LocalTime cursor = LocalTime.of(8, 0);
        LocalTime fin = LocalTime.of(22, 0);
        while (!cursor.isAfter(fin)) {
            todosLosSlots.add(cursor);
            cursor = cursor.plusHours(1);
        }

        // 2. Obtener ocupados de la BD
        List<Turno> ocupados = consultarOcupados(fecha, canchaTipo);

        // 3. Extraer horas de inicio ocupadas
        List<LocalTime> horasOcupadas = ocupados.stream()
                .map(Turno::getHoraInicio)
                .collect(Collectors.toList());

        // 4. Filtrar: solo los que NO están ocupados
        return todosLosSlots.stream()
                .filter(slot -> !horasOcupadas.contains(slot))
                .map(LocalTime::toString)
                .collect(Collectors.toList());
    }
}
