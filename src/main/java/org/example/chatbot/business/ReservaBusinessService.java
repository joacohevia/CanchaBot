package org.example.chatbot.business;

import org.example.chatbot.entity.Turno;
import org.example.chatbot.repository.TurnoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * <b>Reglas de negocio para reservas y cancelaciones de turnos.</b>
 * <p>
 * Contiene la lógica transaccional para crear y cancelar turnos.
 * Es utilizada por {@code ToolDispatcher} cuando el LLM ejecuta
 * las tools {@code reservar_turno} o {@code cancelar_turno}.
 */
@Service
public class ReservaBusinessService {

    private static final Logger log = LoggerFactory.getLogger(ReservaBusinessService.class);

    @Autowired
    private TurnoRepository turnoRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // RESERVAR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crea una nueva reserva de turno.
     * <p>
     * <b>Flujo:</b>
     * <ol>
     *   <li>Valida que el horario no esté ocupado (solapamiento en la misma fecha y hora)</li>
     *   <li>Crea la entidad con estado "reservado"</li>
     *   <li>Guarda en base de datos (transaccional)</li>
     * </ol>
     *
     * @param usuarioNum número de teléfono del cliente (identificador)
     * @param canchaTipo tipo de cancha (5, 6 u 8)
     * @param fecha      día del turno
     * @param horaInicio hora de comienzo
     * @param horaFin    hora de finalización
     * @return el turno creado con su ID
     * @throws IllegalStateException si el horario ya está ocupado
     */
    @Transactional
    public Turno reservar(String usuarioNum, Integer canchaTipo,
                          LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {

        // 1. Forzar duración de 1 hora exacta (ignorar lo que diga el LLM)
        LocalTime horaFinReal = horaInicio.plusHours(1);

        // 2. No permitir reservas en el pasado
        LocalDateTime fechaHoraReserva = LocalDateTime.of(fecha, horaInicio);
        if (fechaHoraReserva.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("No se puede reservar en el pasado. La fecha y hora ya pasaron.");
        }

        // 3. Validar que el horario esté libre para ESE tipo de cancha
        boolean ocupado = turnoRepository.existeSolapamiento(canchaTipo, fecha, horaInicio, horaFinReal);
        if (ocupado) {
            throw new IllegalStateException(
                    "El horario ya esta ocupado el " + fecha + " a las " + horaInicio
            );
        }

        // 4. Crear entidad
        Turno turno = new Turno();
        turno.setUsuarioNum(usuarioNum);
        turno.setCanchaTipo(canchaTipo);
        turno.setFecha(fecha);
        turno.setHoraInicio(horaInicio);
        turno.setHoraFin(horaFinReal);
        turno.setEstado("reservado");

        // 3. Guardar
        Turno guardado = turnoRepository.save(turno);
        log.info("Turno reservado: id={}, tipo=F{}, tel={}, fecha={}, {}-{}",
                guardado.getId(), canchaTipo, usuarioNum, fecha, horaInicio, horaFin);
        return guardado;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCELAR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancela un turno existente por ID (soft delete: cambia estado a "cancelado").
     *
     * @param turnoId ID del turno a cancelar
     * @throws IllegalArgumentException si el turno no existe
     * @throws IllegalStateException    si el turno ya estaba cancelado
     */
    @Transactional
    public void cancelar(Long turnoId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro el turno con ID " + turnoId));

        if ("cancelado".equalsIgnoreCase(turno.getEstado())) {
            throw new IllegalStateException(
                    "El turno " + turnoId + " ya estaba cancelado.");
        }

        turno.setEstado("cancelado");
        turnoRepository.save(turno);
        log.info("Turno cancelado: id={}", turnoId);
    }
}
