# Taller n1-04 — resource y httpResource

| # | Solución | Prueba |
|---|---|---|
| 1 | `lista-pedidos.component.ts` con `httpResource` | `taller.spec.ts` 6 |
| 2 | `detalle.component.ts`, ruta `pedidos/:id`; el servidor simulado tarda 1500 ms con el pedido 1 y 100 ms con el 2 | unitaria (la petición vieja queda `cancelled`) y E2E (navega a 1, cambia a 2 y espera 2 s: sigue viéndose el 2) |
| 3 | `urlDeBusqueda` devuelve `undefined` con menos de dos letras | 3 |
| 4 | `buscador.component.ts`: `debounceTime` (RxJS) y `debounced()` de `@angular/core` | 4, con temporizadores simulados: tres teclas, una petición por versión |
| 5 | `PedidosEscritura.eliminar` con `HttpClient`, y `pedidos.reload()` al terminar | 6 › con datos |
| 6 | Cuatro ramas en la plantilla | 6, una prueba por estado |
| 7 | `ListaDePedidos` (servicio con el recurso) usado por dos componentes | 7: una petición; con un recurso por componente, dos |

## 1. Comparación con la sesión 01

| | Sesión 01 (tres señales + suscripción) | Con `httpResource` |
|---|---|---|
| Líneas de la clase | 14 (tres señales, constructor con `subscribe`, `next` y `error`) | 1 (`httpResource(...)`) más `eliminar` |
| Estados posibles | 2 × 2 × lista = combinaciones imposibles representables (`cargando` y `error` a la vez, lista vieja mientras carga) | Uno solo a la vez: `status()` es `loading`, `reloading`, `resolved`, `error`, `idle` o `local` |
| Reintentar | Repetir el `subscribe` | `reload()` |
| Cancelación al destruir | `takeUntilDestroyed()` explícito | Incluida |

## 4. Esperar a que el usuario deje de escribir, de dos maneras

- **Con RxJS:** `toSignal(toObservable(texto).pipe(debounceTime(300), distinctUntilChanged()))`. Ida y vuelta entre señal y observable; explícito y con todo el vocabulario de RxJS disponible (`distinctUntilChanged`, `filter`).
- **Sin RxJS:** `debounced(texto, 300)` de `@angular/core`. Devuelve un **recurso** (no una señal), así que hay que preguntar `hasValue()` antes de leer. Es más corto, pero en Angular 22.2 está marcado `@experimental 22.0`: su API puede cambiar.

Las dos producen una petición por búsqueda; la prueba 4 lo comprueba con temporizadores simulados.

## 5. Por qué no se usa un recurso para eliminar

Un recurso se ejecuta solo cuando cambian sus dependencias y puede volver a ejecutarse (al recargar, al cambiar un parámetro, al montar otro componente). Eso está bien para leer y es peligroso para escribir: un `DELETE` que se dispara solo, se repite o se cancela a mitad porque cambió una señal. Una escritura es una orden del usuario: se ejecuta una vez, cuando él la pide, con un método del servicio, y al terminar se le pide a la lectura que se recargue.

## 7. ¿Cuántas peticiones salen ahora?

Una. El recurso es una sola instancia dentro de un servicio `providedIn: 'root'`; los dos componentes leen la misma señal. Con un recurso declarado en cada componente salen dos (la prueba de comparación lo demuestra). Esto no lo convierte en caché: si un tercer componente crea otro recurso, o si se llama `reload()`, vuelve a pedir; no hay expiración, deduplicación por clave ni revalidación.

## Lo que comprobó la prueba E2E de error

La primera versión de `ContadorPedidosComponent` leía `lista.recurso.value()` sin preguntar `hasValue()`, confiando en `defaultValue: []`. Con el servidor respondiendo 503, Angular 22.2.6 lanzó `Http failure response … 503` desde la plantilla: con `defaultValue`, `value()` sigue lanzando en estado de error. Es exactamente la regla 3 del README ("lee `value()` solo cuando `hasValue()` es verdadero"); la prueba `n1-04 taller: estado de error forzado` lo detectó por el error en la consola y ahora los dos componentes comprueban `hasValue()`.
