# Taller n4-03 — Pruebas unitarias y de componentes

Herramientas: Vitest 5.1.0 (ejecutor de `ng test`), `@testing-library/angular` 19.6, `@testing-library/user-event` 14.6, `@testing-library/jest-dom` 7. El dominio probado es el del ejemplo (`proyectos/.../03-pruebas-unitarias-y-de-componentes/pedidos.ts`); las piezas nuevas (buscador, formulario de contacto) viven aquí.

| # | Qué se prueba | Dónde |
|---|---|---|
| 1 | Funciones puras: `total`, `validarConsulta` | `taller.spec.ts` › 1 |
| 2 | Almacén: carga, filtro, cancelación optimista y su reversa (con un `Subject` que falla), cancelación exitosa | `taller.spec.ts` › 2 |
| 3 | Servicio HTTP: traducción del DTO y caso 500 (`flush('boom', { status: 500 })`) | `taller.spec.ts` › 3 |
| 4 | Formulario de contacto como usuario: `userEvent.type`, clic, `getByRole('alert')`, `getByRole('status')` | `taller.spec.ts` › 4 |
| 5 | Buscador con `debounceTime(300)` usando `vi.useFakeTimers()`: a los 299 ms no emite, a los 300 emite una vez | `taller.spec.ts` › 5 |
| 6 | Error introducido a propósito: `mutacion.mjs` aplica tres mutaciones (IVA 21 %, reversa que no restaura, estado invertido) y exige que `ng test` falle con cada una; restaura el archivo siempre | `node mutacion.mjs`: las tres detectadas |

Notas encontradas al hacerlo:
- Con `userEvent` y tiempo simulado hay que pasar `advanceTimers: (ms) => vi.advanceTimersByTime(ms)` a `userEvent.setup`; si no, `type` se queda esperando.
- Con `validateHttp` pendiente (sesión n4-02), `fixture.whenStable()` no termina mientras la petición de `HttpTestingController` no se responda: ahí se espera con un temporizador y se responde a mano.
- Los cuatro ejemplos del README se ejecutaron tal cual (solo faltaba el `import`): pasan.
