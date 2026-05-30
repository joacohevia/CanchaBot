package org.example.chatbot.tools;

/**
 * Resultado de ejecutar una tool.
 * Se serializa a JSON y se devuelve al modelo como tool_result,
 * para que pueda formular la respuesta final en lenguaje natural.
 */
public class ToolResult {

    private final boolean exito;
    private final String mensaje;   // descripción legible para el modelo
    private final Object datos;     // datos estructurados opcionales (lista de slots, etc.)

    private ToolResult(boolean exito, String mensaje, Object datos) {
        this.exito = exito;
        this.mensaje = mensaje;
        this.datos = datos;
    }

    public static ToolResult ok(String mensaje) {
        return new ToolResult(true, mensaje, null);
    }

    public static ToolResult ok(String mensaje, Object datos) {
        return new ToolResult(true, mensaje, datos);
    }

    public static ToolResult error(String mensaje) {
        return new ToolResult(false, mensaje, null);
    }

    public boolean isExito() { return exito; }
    public String getMensaje() { return mensaje; }
    public Object getDatos() { return datos; }

    @Override
    public String toString() {
        // El modelo recibe esto como string en el tool_result
        if (datos != null) {
            return "{\"exito\":" + exito + ",\"mensaje\":\"" + mensaje + "\",\"datos\":" + datos + "}";
        }
        return "{\"exito\":" + exito + ",\"mensaje\":\"" + mensaje + "\"}";
    }
}
