# CanchaBot — Asistente Virtual para Reserva de Canchas de Fútbol

Chatbot con IA para gestionar reservas de canchas de fútbol (F5, F6, F8) vía Telegram y API REST.
Utiliza **DeepSeek** como motor de IA con **function calling** nativo y **PostgreSQL** como base de datos.

## Arquitectura

```
Telegram / REST API
        │
        ▼
  ChatController          ← POST /api/chat/prompt
        │
        ▼
  ChatService             ← Orquestador principal
   ├── IntentDetector     ← Detección local de intenciones (sin LLM)
   ├── MemoryService      ← Historial de conversación por sesión
   ├── StateManager       ← Máquina de estados del flujo de reserva
   └── LLMOrchestrator    ← Agentic loop (LLM → tools → repetir)
            │
            ▼
       ToolExecutor        ← Ejecuta tools pedidas por el LLM
            │
            ▼
       ToolDispatcher       ← Router: elige qué handler ejecutar
            │
            ▼
  ┌─────────┴─────────┐
  │                   │
  ▼                   ▼
CanchaBusiness      ReservaBusiness
Service             Service
  │                   │
  └─────────┬─────────┘
            ▼
     TurnoRepository     ← JPA + PostgreSQL
```
### Capas

| Capa | Responsabilidad |
|---|---|
| **Controller** | Recibe requests HTTP y de Telegram |
| **ChatService** | Orquestador: coordina intención, memoria, estado y LLM |
| **IntentDetector** | Detecta intenciones simples por keywords (saludo, precios, horarios) sin gastar tokens del LLM |
| **MemoryService** | Historial de conversación por `chatId`, truncado a 12 mensajes |
| **StateManager** | Máquina de estados: `INICIO → ESPERANDO_TIPO_CANCHA → ESPERANDO_FECHA → ESPERANDO_HORA → ESPERANDO_TELEFONO → CONFIRMANDO_RESERVA` |
| **LLMOrchestrator** | Agentic loop: envía system prompt + tools al LLM, ejecuta tools, repite hasta respuesta final |
| **ToolExecutor** | Ejecuta herramientas pedidas por el LLM y formatea resultados |
| **ToolDispatcher** | Router por nombre de tool, delega en Business Services |
| **Business Services** | Reglas de negocio y acceso a datos (validaciones, transaccionalidad) |
| **Repository** | JPA sobre PostgreSQL en Supabase |

---

## Motor de IA

| Proveedor | Modelo | Endpoint |
|---|---|---|
| **DeepSeek** | `deepseek-v4-flash` | `https://api.deepseek.com/v1/chat/completions` |

El LLM recibe:
- **System prompt** con contexto: fecha actual, reglas de la cancha, precios, horarios
- **Estado actual** de la conversación (`ESTADO_ACTUAL`, `DATOS_YA_RECOLECTADOS`)
- **Tools disponibles** con definiciones tipadas (function calling)

### Tools que el LLM puede invocar

| Tool | Descripción |
|---|---|
| `consultar_disponibilidad` | Devuelve slots libres para una fecha y tipo de cancha |
| `reservar_turno` | Crea una reserva (requiere todos los datos) |
| `cancelar_turno` | Cancela por ID |
| `consultar_turnos_por_numero` | Busca turnos activos por teléfono |

---
## Flujo de un request

### 1. Usuario manda un mensaje por Telegram
```
Usuario: "quiero reservar una cancha"
     │
     ▼
TelegramBotConfig.onUpdateReceived()
     │
     ▼
ChatService.consultarConIA(texto, chatId)
```

### 2. El orquestador procesa el mensaje

```
┌─ IntentDetector.detectar("quiero reservar una cancha")
│  → RESERVAR_TURNO
│
├─ ¿Es intención simple? (SALUDO, PRECIOS, HORARIOS, AYUDA)
│  → No, RESERVAR_TURNO va al LLM
│
├─ StateManager.cambiarEstado(sessionId, ESPERANDO_TIPO_CANCHA)
│
├─ construirSystemPromptConContexto()
│  → "CONTEXTO: hoy es viernes 2026-05-28. ESTADO_ACTUAL: ESPERANDO_TIPO_CANCHA"
│
└─ construirMessages(historial, texto)
   → [{role: "system", content: "..."}, {role: "user", content: "Contexto..."}, {...historial...}, {role: "user", content: "quiero reservar"}]
```
### 3. Agentic loop (LLMOrchestrator)
```
Loop 1:
  ┌─ IAclient.preguntarConTools(systemPrompt, messages, tools)
  │
  ├─ Respuesta LLM: finish_reason = "stop", content = "¿Qué tipo de cancha querés? F5, F6 o F8?"
  │
  └─ No hay tool_calls → devuelve respuesta al usuario

  Usuario responde: "futbol 5"

Loop 2:
  ┌─ IAclient.preguntarConTools(...)
  │
  ├─ Respuesta LLM: finish_reason = "stop", content = "¿Para qué día y hora?"
  └─ (sucesivamente hasta tener todos los datos)

  Usuario: "mañana a las 15" → "¿Tu número de teléfono?"
  Usuario: "3511234567"

Loop 5:
  ┌─ IAclient.preguntarConTools(...)
  │
  ├─ Respuesta LLM: finish_reason = "tool_calls"
  │   tool_call: { name: "reservar_turno", arguments: {...} }
  │
  ├─ ToolExecutor.ejecutar() → ToolDispatcher.dispatch()
  │   → switch("reservar_turno")
  │   → ReservaBusinessService.reservar()
  │       ├─ Validar no pasado
  │       ├─ Forzar hora_fin = hora_inicio + 1h
  │       ├─ Validar solapamiento por cancha_tipo
  │       └─ turnoRepository.save()
  │
  └─ ToolResult vuelve al LLM como mensaje "tool"
      → LLM responde: "¡Listo! Turno reservado. ID: 42 | F5 | 29/05 15:00-16:00"
```

---

## Base de datos

| Tabla | Campos |
|---|---|
| `turnos` | id, usuario_num (teléfono), cancha_tipo (5/6/8), fecha, hora_inicio, hora_fin, estado (reservado/cancelado), creado_en, notas |

---
### Telegram Bot

| Comando / Mensaje | Acción |
|---|---|
| `/start` o `hola` | Menú de bienvenida con opciones A/B/C/D |
| `A` o `ver turnos disponibles` | Muestra slots libres (va al LLM con tools) |
| `B` o `precios` | Precios sin LLM |
| `C` o `horarios` | Horarios + ubicación sin LLM |
| `D` o `reservar` | Inicia flujo de reserva paso a paso |
| `mis turnos` | Pide teléfono y busca turnos activos sin LLM |
| `cancelar turno` | Flujo de cancelación guiado por LLM |

### REST API

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/chat/health` | Health check |
| `POST` | `/api/chat/prompt` | Chat principal |

**Body del POST:**
```json
{
  "mensaje": "quiero reservar una cancha",
  "sessionId": "8875180335"
}
```

---

## Validaciones de negocio

| # | Validación | Dónde |
|---|---|---|
| 1 | Turnos de **1 hora exacta** (se fuerza en backend) | `ReservaBusinessService.reservar()` |
| 2 | No se permite reservar en el pasado | `ReservaBusinessService.reservar()` |
| 3 | Disponibilidad devuelve **slots libres**, no ocupados | `CanchaBusinessService.calcularDisponibles()` |
| 4 | Teléfono: solo dígitos, 8 a 12 caracteres | `ToolDispatcher.reservarTurno()` |
| 5 | Solapamiento **por tipo de cancha** (F5 no bloquea F8) | `TurnoRepository.existeSolapamiento()` |

---
## Stack técnico

| Componente | Tecnología |
|---|---|
| Backend | Java 17, Spring Boot 3.2.5 |
| IA | DeepSeek API (OpenAI-compatible function calling) |
| DB | PostgreSQL |
| ORM | Hibernate / Spring Data JPA |
| Mensajería | Telegram Bots API |
| Build | Maven |

## URL prueba
https://canchabot.onrender.com/api/chat/health