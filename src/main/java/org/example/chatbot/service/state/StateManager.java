package org.example.chatbot.service.state;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Máquina de estados por sesión.
 *
 * Guía el flujo de reserva paso a paso:
 *   INICIO → ESPERANDO_TIPO_CANCHA → ESPERANDO_FECHA → ESPERANDO_HORA
 *          → ESPERANDO_DNI → CONFIRMANDO_RESERVA → COMPLETADO → INICIO
 */
@Service
public class StateManager {

    private final Map<String, EstadoConversacion> estados = new ConcurrentHashMap<>();

    /**
     * Obtiene el estado actual de una sesión (INICIO por defecto).
     */
    public EstadoConversacion obtenerEstado(String sessionId) {
        return estados.getOrDefault(sessionId, EstadoConversacion.INICIO);
    }

    /**
     * Cambia el estado de una sesión.
     */
    public void cambiarEstado(String sessionId, EstadoConversacion nuevoEstado) {
        estados.put(sessionId, nuevoEstado);
    }

    /**
     * Reinicia una sesión al estado INICIO.
     */
    public void reiniciar(String sessionId) {
        estados.put(sessionId, EstadoConversacion.INICIO);
    }

    /**
     * Datos recolectados durante el flujo de reserva.
     * Se limpian al completar o cancelar la reserva.
     */
    private final Map<String, DatosReserva> datosReserva = new ConcurrentHashMap<>();

    /**
     * Obtiene los datos de reserva en curso para una sesión.
     */
    public DatosReserva obtenerDatosReserva(String sessionId) {
        return datosReserva.computeIfAbsent(sessionId, k -> new DatosReserva());
    }

    /**
     * Limpia los datos de reserva al completar o cancelar.
     */
    public void limpiarDatosReserva(String sessionId) {
        datosReserva.remove(sessionId);
        estados.put(sessionId, EstadoConversacion.INICIO);
    }

    /**
     * Datos recolectados paso a paso durante el flujo de reserva.
     */
    public static class DatosReserva {
        public String tipoCancha;   // "5", "6", u "8"
        public String fecha;        // YYYY-MM-DD
        public String hora;         // HH:MM
        public String usuarioNum;   // número de teléfono del cliente

        public boolean estanTodosLosDatos() {
            return tipoCancha != null && fecha != null && hora != null
                    && usuarioNum != null;
        }

        public void limpiar() {
            tipoCancha = null;
            fecha = null;
            hora = null;
            usuarioNum = null;
        }
    }
}
