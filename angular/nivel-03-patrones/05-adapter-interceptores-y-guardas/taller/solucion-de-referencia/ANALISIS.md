# Taller n3-05 — Adapter, interceptores y guardas

| # | Solución | Prueba |
|---|---|---|
| 1 | `adaptadores.ts`: DTO de `/users` y `/posts` con la forma de JSONPlaceholder, modelos `Autor` y `Publicacion`, funciones `aAutor` y `aPublicacion` que además validan | `taller.spec.ts` › 1; `medir-renombre.mjs` |
| 2 | `interceptores.ts`: correlación, errores, renovación y autenticación, en ese orden (`INTERCEPTORES`) | `taller.spec.ts` › 2 (incluida la prueba de que el orden importa) |
| 3 | `RenovacionDeToken` en `sesion.ts`: guarda el observable de la renovación en curso con `shareReplay` y lo olvida en `finalize` | `taller.spec.ts` › 3 y E2E (tres peticiones, una renovación contada por el servidor) |
| 4 | `guardas.ts`: `requiereSesion`, `requiereRol`, `sinCambiosPerdidos` (`CanDeactivateFn`) | `taller.spec.ts` › 4 |
| 5 | `PaginaAdmin` pide `/api/admin/datos`, que el servidor entrega sin comprobar el token | E2E: sin sesión la guarda redirige, pero `fetch` desde la consola obtiene los sueldos |

## 1. Medición del cambio de nombre

La API pública elegida es JSONPlaceholder (`/users`, `/posts`). Para no depender de internet, el servidor
simulado (`servidor/apis/n3-05.mjs`) sirve exactamente esa forma.

`node medir-renombre.mjs` copia el taller, cambia `userId` por `user_id` en `PublicacionDto` y compila la copia:

```
Archivos que se rompen al renombrar userId → user_id: 2
  adaptadores.ts
  taller.spec.ts
```

Se toca el adaptador y su prueba (que construye un DTO de ejemplo). Ninguna pantalla se entera: solo conocen `Publicacion.autorId`.

## 2. Orden de los interceptores

`[correlación, errores, renovación, autenticación]`. El primero de la lista ve la petición antes que nadie y la respuesta después que todos:

- **Correlación primero**: el reintento tras renovar conserva el mismo `X-Correlation-Id` (lo comprueba la prueba 3).
- **Autenticación al final**: lee el token en cada intento, así el reintento sale con el token nuevo sin código extra.
- **Errores por fuera de la renovación**: solo ve un 401 cuando renovar no bastó. La prueba "el orden importa" invierte
  errores y renovación y muestra el fallo: la petición se recupera, pero el usuario ya fue enviado a `/ingreso`.

## 3. Una sola renovación

Las tres peticiones reciben 401 casi a la vez. La primera crea la renovación; las otras dos encuentran `enCurso` y se
suscriben al mismo observable (`shareReplay({ bufferSize: 1, refCount: false })`). Al completar, `finalize` lo borra para
que un vencimiento posterior vuelva a renovar. La prueba E2E lo comprueba contra el servidor: `renovaciones: 1`.

## 5. Una guarda no protege los datos

Con las herramientas del navegador, sin haber ingresado:

1. Abrir `/n3-05/taller/admin`: la guarda redirige a `/n3-05/taller/ingreso?volverA=…`. La pantalla no se ve.
2. En la consola: `await (await fetch('/api/n3-05/admin/datos')).json()` → `{ sueldos: [4200000, 5100000] }`.
   El dato salió del servidor igual. (También se ve la petición en la pestaña Red si alguien ingresa como admin, y el
   código de la guarda está en el JavaScript descargado, donde se puede leer y saltar.)
3. `fetch('/api/n3-05/admin/datos-protegidos')` sin token → 401. Esa es la protección real: la comprueba el servidor.

La prueba E2E ejecuta exactamente estos pasos con `page.evaluate`.
