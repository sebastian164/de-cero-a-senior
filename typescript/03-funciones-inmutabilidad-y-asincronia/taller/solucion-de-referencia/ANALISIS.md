# Taller TypeScript 03: funciones, inmutabilidad y asincronía. Mediciones y explicaciones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/typescript/03-funciones-inmutabilidad-y-asincronia/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.ts`. Código: `puras.ts`, `asincronia.ts`. Mediciones: `mediciones/vitest.jsonl`.

## Punto 1. Cinco funciones impuras reescritas como puras

`marcarHecha`, `ordenar`, `quitar`, `etiquetar` y `mudar` (más `marcarHechaImpura` como contraste). Para cada una, la prueba hace `structuredClone` del argumento antes de llamar y exige `toEqual` con la copia después: el original **no cambió**. Una prueba muestra que la versión impura sí lo modifica (por eso se reescribe).

## Punto 2. La copia con `...` es superficial

`copiaSuperficial(perfil)` = `{ ...perfil }`: al cambiar `copia.direccion.ciudad` a "Medellín", **el original también cambia** (`copia.direccion === original.direccion`: el mismo objeto). `copiaDosNiveles` copia también `direccion` y el original conserva "Bogotá".

## Punto 3. Tres `await` en serie frente a `Promise.all`

Tres consultas simuladas con retardos de 300, 500 y 400 ms:

| Forma | Tiempo medido | Esperado |
|---|---|---|
| tres `await` seguidos | **1203 ms** | 300 + 500 + 400 = 1200 |
| `Promise.all` | **502 ms** | el mayor de los tres = 500 |

Los resultados son idénticos en ambas. Se pasa de la suma al máximo. Solo es válido cuando las tres operaciones son independientes (la segunda no necesita el resultado de la primera).

## Punto 4. Condición de carrera en un buscador

Se teclea "c", "ca", "caf", "cafe" y la respuesta a "c" tarda más que las demás. Ingenuo: la pantalla termina mostrando **`resultados de "c"`** (la respuesta vieja llegó al final y pisó a la nueva). Con `AbortController`: muestra **`resultados de "cafe"`** y se cancelaron **3** peticiones (las de "c", "ca" y "caf").

## Punto 5. Orden de `console.log`, `setTimeout(…, 0)` y una promesa ya resuelta

El taller imprime en este orden (observado ejecutándolo): `1 síncrono`, `2 síncrono`, `3 promesa (microtarea)`, `4 setTimeout (tarea)`, aunque el `setTimeout` está escrito antes que la promesa.
Por qué: el código síncrono se ejecuta completo primero (pila de llamadas). Al terminar, el motor vacía la **cola de microtareas** (los `.then` de promesas ya resueltas) y solo después toma la siguiente **tarea** de la cola de tareas, donde está el temporizador (`0` ms significa "lo antes posible, pero no antes de acabar lo actual y las microtareas"). Por eso una promesa resuelta siempre se adelanta a un `setTimeout(…, 0)` escrito antes.
