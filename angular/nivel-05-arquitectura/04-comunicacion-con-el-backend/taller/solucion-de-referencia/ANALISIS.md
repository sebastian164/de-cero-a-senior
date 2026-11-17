# Taller n5-04 — Comunicación con el backend

Servidor simulado: `servidor/apis/n5-04.mjs` (404, 500, 503 intermitente, lista vacía, 422, tres recursos de 300 ms para la cascada).

| # | Solución | Prueba |
|---|---|---|
| 1 | Cuatro estados en `ListaPedidos` (`paginas.ts`), con `httpResource`: cargando (`aria-busy`), éxito, vacío y error con "Reintentar" (`reload()`); botones para simular vacío y error | E2E "los cuatro estados" |
| 2 | `errores.ts`: `aErrorDeDominio` (0 → `SinConexion`, 404 → `PedidoNoEncontrado`, 422 → `ErrorDeValidacion` con campos, `TimeoutError` → `TiempoAgotado`, resto → `ErrorDeServidor`) y `mensajeParaElUsuario`. Se usa en las tres pantallas (lista, nuevo, detalle) | `comunicacion.spec.ts` (14 pruebas) y E2E |
| 3 | Sin conexión y red lenta con `page.route` (`abort('internetdisconnected')` y respuesta demorada) | E2E: lo escrito se conserva, el mensaje distingue "sin conexión" de "tardó demasiado", y se puede reintentar. El tiempo límite es configurable (`TIEMPO_LIMITE_MS`, `?limite=` en la URL) para no esperar 8 s |
| 4 | Cascada → paralelo (`PedidosApi.cascada` / `paralelo`): usuario → pedidos → recomendaciones en serie (3 × 300 ms) frente a recomendaciones en paralelo con usuario→pedidos (2 × 300 ms). Los pedidos sí dependen del id del usuario: esa dependencia es real y se conserva | E2E: cascada ≥ 850 ms, paralelo < cascada − 200 ms |
| 5 | Tipos desde OpenAPI: `openapi/pedidos.openapi.yaml` → `openapi-typescript` 7.13 → `api.generado.ts`; `cliente.ts` los usa. `generar-y-romper.mjs` renombra un campo en la especificación y comprueba que `tsc` falla (`Property 'cliente_nombre' does not exist`) | `node openapi/generar-y-romper.mjs` |
| 6 | `TOKEN.md` | |

## Hallazgos

- **Error del README corregido:** `retry` sin filtrar reintentaba también los 404 y 422 (que no se arreglan solos). Se comprobó (tres peticiones para un 404) y se cambió por un reintento solo para fallos de red y 5xx. Además, `timeout` va antes que `retry`, así que el tiempo límite es por intento: el peor caso son 3 intentos de 8 s.
- **Fetch por defecto (Angular 22):** el E2E comprueba que todas las peticiones a la API son de tipo `fetch`, no `xhr`.
- **POST no se reintenta** (no es idempotente): `comunicacion.spec.ts` lo comprueba con un 503.
- `openapi-typescript` 7.13 declara `typescript@^5` como peer y el proyecto usa TypeScript 6: se instaló en su propia carpeta (`proyectos/openapi/`) con TypeScript 5.9 para no forzar el árbol de dependencias de la aplicación.
