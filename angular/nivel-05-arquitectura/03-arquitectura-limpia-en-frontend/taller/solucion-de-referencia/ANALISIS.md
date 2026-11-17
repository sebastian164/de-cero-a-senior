# Taller n5-03 — Arquitectura limpia en el frontend

Funcionalidad: **asistente de solicitud de crédito** con pasos condicionales (el paso de codeudor solo existe si se pide o si el monto pasa de 20 millones), una cuota que se calcula en pantalla y una regla del 40 % de los ingresos.

| # | Solución | Prueba |
|---|---|---|
| 1 | Cuatro capas: `dominio/` (`solicitud.ts` y `puertos.ts`), `aplicacion/asistente.ts`, `datos/` (adaptadores y configuración), `ui/asistente.pagina.ts`. Ensamblaje en `rutas.ts` | E2E del flujo completo |
| 2 | Reglas del dominio probadas sin Angular, sin `TestBed` y sin HTTP (`dominio.spec.ts`, 10 pruebas; la cuota se contrasta con un cálculo independiente en Python: 499.241,02) | `ng test` |
| 3 | Segundo adaptador, `SolicitudesEnMemoria`; se elige por configuración (`MODO_DATOS`, desde `?datos=memoria`) | `aplicacion.spec.ts` y E2E (con `?datos=memoria` no sale ninguna petición POST; el id es negativo) |
| 4 | Regla de lint: `dominio` no importa `@angular/*` ni `rxjs` (salvo `puertos.ts`, que nombra `Observable`); `aplicacion` no importa `datos` ni `ui` | `node limite-dominio.mjs`: el código real cumple y cuatro importaciones indebidas fallan |
| 5 | `JUSTIFICACION-CRUD.md` | |

## Decisiones y hallazgos

- **`puertos.ts` y RxJS.** El README dice que el dominio "no importa nada externo", pero su propio ejemplo de puertos usa `Observable`. Se resolvió así: el dominio de reglas (`solicitud.ts`) no importa nada; el archivo de puertos puede importar `rxjs` (los contratos de I/O son asíncronos), nunca Angular. Está en la configuración de ESLint. El README ahora dice en la tabla: "Nada externo (los puertos pueden nombrar `Observable` de RxJS; nunca Angular)".
- **Errores que no son de validación.** El asistente tiene `enviando` y `errorGeneral`: un 500 o la falta de red muestran "No se pudo enviar la solicitud" y el botón sigue disponible para reintentar (antes se descartaban en silencio). Pruebas: `aplicacion.spec.ts` (500 y reintento) y E2E con el servidor en 500.
- **El paso de retorno sale del campo rechazado**, no está fijado en 'ingresos': `dominio/solicitud.ts › pasoDelCampo` (mapa campo → paso). Pruebas: rechazo del monto vuelve a ingresos; rechazo de `codeudorDocumento` vuelve al paso del codeudor.
- **El servidor vuelve a validar.** E2E: con monto de 60 millones el cliente deja pasar la solicitud (cumple su regla del 40 %) y el servidor responde 422 con `campos.monto`; el asistente vuelve al paso de ingresos y muestra el mensaje del servidor. Es la respuesta práctica a "cómo evitas que las reglas se desincronicen": el cliente ayuda, el servidor decide, y el cliente sabe mostrar el desacuerdo.
- El reloj inyectado (`RELOJ`) del ejemplo del README hace trivial probar la regla de las 24 horas (`ejemplo.spec.ts`).
- Prueba del ejemplo del README: el código se montó tal cual (con `aPedido` y `horasEntre`, que el README no define) y pasa.
