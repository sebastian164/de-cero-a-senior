# Taller nivel-06/02: seguridad, qué se hizo y cómo se comprobó

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Todo lo siguiente corre contra `next build` + `next start` (`e2e/n6-02.spec.ts`, Chromium) y Vitest (`taller.test.ts`, 30 pruebas).

1. **Invocar la acción directamente.** El navegador hace una acción legítima (borrar un pedido propio); la prueba captura esa petición (`next-action` + cuerpo) y la repite con el id de un pedido de Luis.
   - Con la acción "Antes" del README (`eliminarPedido(id)` sin comprobar nada): la petición responde 200 y el pedido de Luis desaparece. Se confirmó el fallo.
   - Con la acción "Después" (sesión, esquema `uuid`, comprobación de propiedad): responde "Pedido no encontrado" y el pedido de Luis sigue en su lista.
2. **Capa de acceso a datos** (`acceso.ts`, bloque 1 del README, más `acceso-lista.ts`): las páginas (`/privado`, `/privado/pedidos/[id]`) y las acciones pasan por ella; devuelve `null` igual para "ajeno" que para "inexistente" y proyecta solo `id`, `estado`, `total`, `lineas`.
3. **Qué llega al navegador.** El HTML de `/privado` no contiene `costoInterno`, `notaDeRiesgo`, `clienteId` ni el id del pedido de Luis.
4. **Secretos en el paquete.** La prueba recorre todo `.next/static` y no encuentra el secreto de sesión, las claves de los usuarios, `costoInterno` ni `contracargo previo`.
5. **`server-only`** en `bd.ts`, `sesion.ts` y `usuarios.ts` (módulos sensibles).
6. **Cabeceras y CSP.** Estáticas desde `next.config.ts`: HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, sin `X-Powered-By`. CSP con nonce por petición desde `src/proxy.ts` para `/n6/02-seguridad/*`
   (script-src con `'nonce-…'` y `'strict-dynamic'`, `frame-ancestors 'none'`, `object-src 'none'`): los scripts de Next.js llevan el nonce, cada petición recibe uno distinto y la página funciona sin violaciones en la consola.
   Costo: esas rutas dejan de poder ser estáticas (`ƒ` en la construcción). `upgrade-insecure-requests` no se activó porque se probó sobre `http://127.0.0.1`.
7. **Límite de tasa.** 5 intentos por minuto por IP y por correo (`limite.ts`, ventana deslizante en memoria: con varias réplicas iría en un almacén compartido). El sexto intento muestra "Demasiados intentos" y el mensaje de credenciales incorrectas es el mismo exista o no el correo.
   Un archivo `'use server'` solo debe exportar acciones: el reinicio del límite para pruebas vive en `limite.ts`, no en la acción.
8. **Pruebas de aislamiento.** Un usuario no lee (`obtenerPedido`, página, lista) ni modifica (`eliminarPedido`) el recurso de otro; sin sesión tampoco; el administrador sí lee.
Además: sesión en cookie `HttpOnly`, `Secure`, `SameSite=Lax`, firmada con HMAC-SHA256; JavaScript no la lee; tras "Salir" la cookie copiada deja de servir; el parámetro `retorno` solo acepta rutas propias (`//evil.example` vuelve a `/privado`).

## Simplificaciones de demostración (qué NO copiar a producción)

Este taller es una demostración; cuatro cosas están simplificadas a propósito y no deben copiarse tal cual:

| Simplificación | Riesgo | Qué se haría en producción |
|---|---|---|
| El límite de tasa toma la IP de `x-forwarded-for` (`acciones-ingreso.ts`) | esa cabecera la puede escribir quien llama: un atacante cambia el valor en cada intento y evita el límite por IP | leer la IP solo de la cabecera que añade **el proxy de confianza** (o del campo de conexión que el proxy rellena), descartando lo que traiga el cliente; fijar cuántos saltos de proxy se aceptan |
| El límite por correo (5 por minuto) | permite **bloquear a una víctima**: quien conoce su correo la deja sin poder ingresar | combinar IP y correo en la clave, bloquear con retardo creciente y no con cierre total, avisar a la persona y ofrecer un desafío (CAPTCHA o código por correo) |
| `scrypt` con `N: 1024` (`usuarios.ts`) para que las pruebas sean rápidas | muy por debajo de lo recomendable: el costo de adivinar claves robadas es bajo | `N` ≥ 2^17 (131072) con `r: 8`, `p: 1` (o Argon2id), calibrado a ~100 ms por hash en el servidor real; guardar los parámetros con el hash para poder subirlos |
| El "webhook" `/api/n5-02/revalidar` invalidaba cualquier etiqueta sin autenticación | cualquiera podía vaciar la caché | **corregido**: exige la cabecera `x-secreto-webhook` igual a `SECRETO_WEBHOOK` (comparación en tiempo constante); sin ella responde 401 (`e2e/n5-02.spec.ts`). En producción: firma HMAC del cuerpo con marca de tiempo (anti repetición), no un secreto estático en una cabecera |
