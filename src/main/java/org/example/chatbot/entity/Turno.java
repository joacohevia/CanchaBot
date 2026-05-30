package org.example.chatbot.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entidad Turno — representa una reserva de cancha.
 *
 * Campos:
 *   - id           → autogenerado
 *   - usuario_num  → número de teléfono del cliente (identificador único)
 *   - fecha        → día del turno
 *   - hora_inicio  → inicio del slot
 *   - hora_fin     → fin del slot
 *   - estado       → "reservado" | "cancelado" | "completado"
 *   - cancha_tipo  → tipo de cancha (5, 6 u 8 jugadores)
 *   - creado_en    → timestamp de cuándo se hizo la reserva
 *   - notas        → comentario opcional
 */
@Entity
@Table(name = "turnos")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de teléfono del cliente. Es el identificador principal para buscar turnos. */
    @Column(name = "usuario_num", nullable = false)
    private String usuarioNum;

    // ── Cancha ────────────────────────────────────────────────────────────────

    /** Tipo de cancha: 5 (fútbol 5), 6 (fútbol 6), 8 (fútbol 8). */
    @Column(name = "cancha_tipo", nullable = false)
    private Integer canchaTipo;

    // ── Horario ──────────────────────────────────────────────────────────────

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // ── Estado ───────────────────────────────────────────────────────────────

    @Column(name = "estado", nullable = false)
    private String estado = "reservado";

    // ── Auditoría y extras ───────────────────────────────────────────────────

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "notas")
    private String notas;

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    @PrePersist
    protected void onPersist() {
        this.creadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = "reservado";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GETTERS & SETTERS
    // ─────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsuarioNum() { return usuarioNum; }
    public void setUsuarioNum(String usuarioNum) { this.usuarioNum = usuarioNum; }

    public Integer getCanchaTipo() { return canchaTipo; }
    public void setCanchaTipo(Integer canchaTipo) { this.canchaTipo = canchaTipo; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    @Override
    public String toString() {
        return String.format("Turno{id=%d, tipo=F%d, tel=%s, fecha=%s, %s-%s, estado=%s}",
                id, canchaTipo, usuarioNum, fecha, horaInicio, horaFin, estado);
    }
}
