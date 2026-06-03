package org.example.chatbot.service.intent;

import org.springframework.stereotype.Service;

/**
 * Detecta la intención del usuario a partir de palabras clave.
 * Solo para intenciones claras que no requieren IA.
 * Si no hay coincidencia clara, devuelve GENERAL para que el LLM lo procese.
 */
@Service
public class IntentDetector {

    /**
     * Analiza el mensaje del usuario y devuelve la intención detectada.
     */
    public Intencion detectar(String mensaje) {
        String m = mensaje.toLowerCase().trim();

        // ── Saludo ──
        if (m.matches("^(hola|buenas|buenos dias|buenas tardes|buenas noches|hey|iniciar|start).*")) {
            return Intencion.SALUDO;
        }

        // ── Disponibilidad ──
        if (contieneAlguna(m, "disponible", "disponibilidad", "hay lugar", "hay cancha",
                "turnos libres", "turno libre", "que dia", "que horario", "a que hora")) {
            return Intencion.CONSULTAR_DISPONIBILIDAD;
        }

        // ── Reservar ──
        if (contieneAlguna(m, "reservar", "agendar", "sacar turno", "sacar un turno",
                "quiero un turno", "quiero reservar", "necesito un turno", "pedir turno",
                "pedir un turno", "alquilar", "alquilar cancha")) {
            return Intencion.RESERVAR_TURNO;
        }

        // ── Cancelar ──
        if (contieneAlguna(m, "cancelar", "anular", "dar de baja", "no voy a ir",
                "no puedo ir", "cancelar turno", "cancelar reserva")) {
            return Intencion.CANCELAR_TURNO;
        }

        // ── Ver mis turnos ──
        if (contieneAlguna(m, "mis turnos", "ver turnos", "consultar turnos",
                "que turnos tengo", "turnos activos", "reservas")) {
            return Intencion.VER_MIS_TURNOS;
        }

        // ── Precios ──
        if (contieneAlguna(m, "precio", "cuesta", "valor", "tarifa", "cuanto sale",
                "cuanto cuesta", "precios")) {
            return Intencion.PRECIOS;
        }

        // ── Horarios ──
        if (contieneAlguna(m, "horario", "atienden", "abren", "cierran", "hora",
                "horarios")) {
            return Intencion.HORARIOS;
        }

        // ── Ubicación ──
        if (contieneAlguna(m, "ubicacion", "direccion", "donde queda", "donde estan",
                "como llegar", "mapa","cual es la direccion","donde esta ubicado","cual es la ubicacion")) {
            return Intencion.UBICACION;
        }

        // ── Ayuda ──
        if (contieneAlguna(m, "ayuda", "help", "que podes hacer", "que haces",
                "como funciona", "opciones", "comandos", "menu", "volver")) {
            return Intencion.AYUDA;
        }

        // ── Sin intención clara ──
        return Intencion.GENERAL;
    }

    private boolean contieneAlguna(String texto, String... palabras) {
        for (String p : palabras) {
            if (texto.contains(p)) return true;
        }
        return false;
    }
}
