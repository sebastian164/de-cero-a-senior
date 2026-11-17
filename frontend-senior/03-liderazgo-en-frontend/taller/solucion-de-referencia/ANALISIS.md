# Taller frontend-senior/03: liderazgo en frontend. Qué entrega y cómo se comprobó

El entregable es un documento, [`LIDERAZGO.md`](../../../../react-next/solucion-de-referencia-app/talleres/frontend-senior/03-liderazgo-en-frontend/LIDERAZGO.md), con las siete partes del taller final, y un contrato con el backend en código (`contrato.ts`) con sus simulacros.

## Qué comprueba la prueba (`taller.test.ts`)

| Parte | Cómo se comprueba |
|---|---|
| 1. Radar tecnológico | Diez elementos, cada uno con anillo válido y una justificación de al menos ocho palabras. |
| 2. Registros de decisión (ADR) | Tres, con contexto, decisión, alternativas y consecuencias. |
| 3. Definición de terminado | Cada punto dice si se verifica de forma automática y con qué. |
| 4. Contrato y simulacros | Los simulacros de éxito y de error cumplen su esquema; el simulacro filtra por estado y responde 400 ante un estado desconocido; una respuesta que rompe el contrato se detecta; el documento incluye el contrato y los casos. |
| 5. Negociación con diseño | Tiene costo, riesgo y dos alternativas. |
| 6. Defensa de diez minutos | El guion cabe en diez minutos a 130 palabras por minuto e incluye problema, decisiones, alternativas, costos y evolución. |
| 7. Balance | Cubre todos los niveles de la ruta y un plan a seis meses. |

## Qué conviene aprender de la comparación

Casi todo el taller es escritura, y la prueba solo exige que esté completa y sea coherente. Lo que aporta valor es la parte 4: un contrato escrito como esquema, con simulacros que lo cumplen, permite que frontend y backend avancen a la vez y que un cambio que rompe el acuerdo se detecte en una prueba y no en producción.
