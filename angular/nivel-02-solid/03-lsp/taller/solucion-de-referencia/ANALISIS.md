# Taller n2-03 — LSP

| # | Solución | Prueba |
|---|---|---|
| 1 | `notificador.ts`: `Notificador` con tres implementaciones (pantalla, consola, remoto) | `notificador.spec.ts`: la función `contrato()` corre la misma batería (entrega, mensaje vacío, fallo del destino) contra las tres: 9 pruebas |
| 2 | `reportes.ts`: `ExportadorLigeroAntes` lanzaba "No implementado" en `aPdf` y `aExcel`; rediseño con un contrato por formato | `componentes.spec.ts` › 2 |
| 3 | `boton.component.ts`: `button[appBotonPrimario]`, componente con selector de atributo sobre el `<button>` nativo | `componentes.spec.ts` › 3: `disabled` dinámico y estático, `type`, ARIA, Tab, Enter y Espacio |
| 4 | `estrellas.component.ts`: `ControlValueAccessor` con los cuatro métodos | `componentes.spec.ts` › 4: `disable()` del `FormControl` deshabilita y bloquea clics; tocado al salir |

## 1. Lo que la batería común descubrió

La primera versión de `NotificadorEnPantalla` no envolvía los fallos del destino: si la vista ya no podía recibir el aviso, lanzaba el error original en lugar de `NotificacionFallida`. Con un contrato escrito una sola vez, la prueba "si el destino falla" lo detectó para esa implementación y no para las otras dos. Ese es el punto del taller: la forma (los tres tienen `notificar(): Promise<void>`) la verifica TypeScript; el comportamiento solo lo verifica una batería compartida.

## 3. Por qué un selector de atributo

Un componente `<app-boton-primario>` que dibuja un `<button>` adentro tiene que reenviar a mano `disabled`, `type`, `form`, `aria-*`, el foco y los eventos; cada uno que olvida es una violación de LSP que el usuario sufre. Con `button[appBotonPrimario]` el elemento **es** el `<button>`: todo eso viene del navegador. Lo único que hubo que cuidar: una entrada con alias `disabled` recibe `''` cuando se escribe sin valor (`<button appBotonPrimario disabled>`). Sin `booleanAttribute`, el compilador con `strictTemplates` lo rechaza (`TS2322: Type 'string' is not assignable to type 'boolean'`, comprobado quitando la transformación); con ella, `''` significa verdadero, igual que en el nativo, y la prueba del atributo estático lo confirma.

## 5. Un simulacro frente al servicio real

En la sesión 01 del nivel 1, la prueba original del servicio de pedidos usaba un doble `{ listar: () => of([...]) }`. Diferencias con el real:

| Aspecto | Real (`HttpClient`) | Doble |
|---|---|---|
| Asincronía | Responde después, en otra vuelta del ciclo de eventos | `of()` responde en el acto: oculta estados de carga |
| Errores | 4xx, 5xx, red caída, tiempo vencido | Nunca falla: el manejo de errores nunca se ejecuta |
| Cancelación | Cancelable al desuscribirse | No hay nada que cancelar |
| Forma de los datos | DTO del servidor | El modelo ya traducido |

Corrección aplicada en este curso: las pruebas de pantallas usan `HttpTestingController`, que sí permite responder tarde, con error o no responder, y los dobles de contrato (como `AlmacenamientoEnMemoria` del ejemplo) pasan la misma batería que el real.
