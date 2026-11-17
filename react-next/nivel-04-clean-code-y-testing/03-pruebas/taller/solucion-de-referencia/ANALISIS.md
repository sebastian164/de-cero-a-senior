# Taller nivel-04/03: pruebas. Cobertura de los seis puntos

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-04-clean-code-y-testing/03-pruebas/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

`taller.test.tsx` (Vitest, jsdom, MSW) más `e2e/n4-03.spec.ts` (Chromium contra `next start`) y `scripts/mutacion.sh`.

| Punto | Dónde | Resultado |
|---|---|---|
| 1. Dominio y reductores | `taller.test.tsx`, "punto 1" | agregar, repetir, cantidad (0 o menos quita), quitar, vaciar, pureza (no muta la entrada), `totalCarrito`, `sePuedeCancelar` dentro y fuera de 24 h |
| 2. Formulario como usuario | "punto 2" | escribir, salir del campo y ver "Escribe un correo válido"; corregir; "Las claves no coinciden" sin llamar al servidor; envío correcto con los cuatro valores; error del servidor junto a su campo |
| 3. Pantalla con MSW | "punto 3" | éxito, vacío (sin elementos ni alerta), error con reintento y respuesta de forma inesperada convertida en error |
| 4. Acción de servidor | "punto 4" | camino feliz (invalida la etiqueta del usuario), sin sesión (no toca nada), entradas inválidas `42`, `undefined`, `'no-es-uuid'`, `{ id }`, pedido ajeno (responde como si no existiera) y fuera de plazo |
| 5. Dos pruebas de extremo a extremo | `e2e/n4-03.spec.ts` | (1) armar el carrito, total $25.000 (18.000 + 2 × 3.500) y que sobreviva a recargar; (2) registro: error al salir del campo, error del servidor "Ese correo ya está registrado" y su corrección |
| 6. Introducir un error | `scripts/mutacion.sh` | se cambia `cantidad: l.cantidad + 1` por `cantidad: l.cantidad` en el reductor; la copia del original se restaura al salir. Resultado: **las pruebas fallan** ("OK: las pruebas detectaron el error introducido") |

## Hallazgo sobre el README (conservado como prueba)

La prueba del README esperaba un error "al salir del campo" sobre `FormularioRegistro`, que usa una acción de servidor y solo valida al enviar. `hallazgo-readme.test.tsx` conserva la evidencia (con ese formulario no hay alerta al salir del campo); el README ahora incluye `FormularioRegistroEnVivo` (con `mode: 'onBlur'`) y la prueba de punto 2 usa ese.
