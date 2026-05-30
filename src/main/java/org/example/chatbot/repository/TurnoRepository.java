package org.example.chatbot.repository;

import org.example.chatbot.entity.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {

    // ─────────────────────────────────────────────────────────────────────────
    // DISPONIBILIDAD
    // ─────────────────────────────────────────────────────────────────────────

    /** Turnos ocupados en una fecha (todos los tipos de cancha). Excluye cancelados. */
    List<Turno> findByFechaAndEstadoNot(LocalDate fecha, String estado);

    /** Turnos ocupados en una fecha para un tipo específico de cancha. */
    List<Turno> findByFechaAndCanchaTipoAndEstadoNot(LocalDate fecha, Integer canchaTipo, String estado);

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFICACIÓN DE SOLAPAMIENTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ¿Ya hay un turno activo del mismo tipo de cancha que se solape
     * con esta fecha y horario?
     * <p>
     * <b>Cómo funciona el solapamiento:</b>
     * <pre>
     *   Nuevo turno:  14:00 ───── 15:00
     *   Existente A:  13:00 ── 14:30  ← SOLAPA (horaInicio nuevo < horaFin existente)
     *   Existente B:  14:30 ───── 16:00  ← SOLAPA (horaFin nuevo > horaInicio existente)
     *   Existente C:  13:00 ── 13:59  ← NO solapa (termina justo antes)
     *   Existente D:  15:00 ── 16:00  ← NO solapa (empieza justo después)
     * </pre>
     * Solo verifica turnos del MISMO tipo de cancha (F5 no bloquea F8).
     */
    @Query("""
        SELECT COUNT(t) > 0 FROM Turno t
        WHERE t.canchaTipo = :canchaTipo
          AND t.fecha = :fecha
          AND t.estado = 'reservado'
          AND t.horaInicio < :horaFin
          AND t.horaFin > :horaInicio
        """)
    boolean existeSolapamiento(
            @Param("canchaTipo") Integer canchaTipo,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin
    );

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTAS POR USUARIO (teléfono)
    // ─────────────────────────────────────────────────────────────────────────

    /** Turnos activos de un usuario por número de teléfono. */
    List<Turno> findByUsuarioNumAndEstado(String usuarioNum, String estado);

    /** Todos los turnos de un usuario, ordenados del más reciente al más viejo. */
    List<Turno> findByUsuarioNumOrderByFechaDescHoraInicioDesc(String usuarioNum);

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN
    // ─────────────────────────────────────────────────────────────────────────

    List<Turno> findByFechaOrderByHoraInicioAsc(LocalDate fecha);

    @Query("""
        SELECT t FROM Turno t
        WHERE t.fecha BETWEEN :desde AND :hasta
          AND t.estado = 'reservado'
        ORDER BY t.fecha ASC, t.horaInicio ASC
        """)
    List<Turno> findTurnosEnRango(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );
}
