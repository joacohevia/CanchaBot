package org.example.chatbot.service.intent;

/**
 * Intenciones que el sistema puede detectar localmente sin llamar al LLM.
 */
public enum Intencion {

    /** Saludo inicial (hola, buenas, etc.). */
    SALUDO,

    /** El usuario quiere ver qué turnos hay disponibles. */
    CONSULTAR_DISPONIBILIDAD,

    /** El usuario quiere reservar una cancha. */
    RESERVAR_TURNO,

    /** El usuario quiere cancelar un turno existente. */
    CANCELAR_TURNO,

    /** El usuario quiere ver sus turnos activos. */
    VER_MIS_TURNOS,

    /** Pregunta por precios de canchas. */
    PRECIOS,

    /** Pregunta por horarios de atención. */
    HORARIOS,

    /** Pregunta por la ubicación. */
    UBICACION,

    /** Pide ayuda o instrucciones. */
    AYUDA,

    /** Ninguna intención clara — necesita procesamiento del LLM. */
    GENERAL
}
