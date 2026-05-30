package org.example.chatbot.service.state;

/**
 * Estados de la conversación para guiar el flujo de reserva paso a paso.
 */
public enum EstadoConversacion {

    /** Esperando que el usuario diga qué quiere hacer. */
    INICIO,

    /** El usuario quiere reservar — preguntar tipo de cancha. */
    ESPERANDO_TIPO_CANCHA,

    /** Ya sabemos el tipo de cancha — preguntar la fecha. */
    ESPERANDO_FECHA,

    /** Ya sabemos la fecha — preguntar la hora. */
    ESPERANDO_HORA,

    /** Ya sabemos fecha y hora — preguntar el número de teléfono. */
    ESPERANDO_TELEFONO,

    /** Tenemos todos los datos — confirmar antes de guardar. */
    CONFIRMANDO_RESERVA,

    /** El usuario quiere cancelar — preguntar teléfono para buscar sus turnos. */
    ESPERANDO_TELEFONO_CANCELAR,

    /** Encontramos los turnos del usuario — preguntar cuál cancelar. */
    ESPERANDO_ID_CANCELAR,

    /** El usuario quiere ver sus turnos (/mis_turnos) — esperando el teléfono. */
    ESPERANDO_TELEFONO_CONSULTA,

    /** Turno reservado o cancelado — volver a inicio. */
    COMPLETADO
}
