# Taller n5-01 — Estructura por funcionalidades y carga diferida

Solución: una tienda en línea (`tienda/`) con las funcionalidades `catalogo`, `pedidos` y `admin`, más `compartido/` y `nucleo/`, como en el árbol del README (`dominio`, `datos`, `estado`, `ui`, `paginas`, `<f>.rutas.ts`, `index.ts`). Se sirve en `/n5-01/taller`.

| # | Solución | Cómo se ejecuta |
|---|---|---|
| 1 | Estructura por funcionalidades (`tienda/`). `pedidos` usa la API pública de `catalogo` (`catalogo/index.ts`) | `ngc`, `ng build`, E2E |
| 2 | `node grafo.mjs` genera `GRAFO.md` (Mermaid) leyendo los imports: `catalogo -> compartido`, `pedidos -> catalogo`; sin ciclos. El detector se autocomprueba con un ciclo sintético | `node grafo.mjs` |
| 3 | `eslint.config.mjs` (raíz de esta aplicación): reglas `no-restricted-imports` por funcionalidad y capa. `node limites.mjs` comprueba que el código real pasa y que cuatro importaciones indebidas (profunda a otra funcionalidad, `dominio` que importa Angular, `ui` que importa `datos`, `compartido` que importa `nucleo`) fallan con su mensaje | `node limites.mjs` |
| 4 | `medir-paquete.mjs` construye dos variantes (`n5-01-eager`: todo importado, incluida la gráfica; `n5-01-lazy`: `loadChildren` + `@defer`): paquete inicial 301,97 kB → 258,66 kB (-14 %); transferido 88,22 kB → 72,83 kB | `node medir-paquete.mjs` |
| 5 | `admin.pagina.ts` difiere la gráfica con `@defer (on viewport; on interaction(cargar))`; la serie de 6000 puntos vive en el trozo diferido | E2E: al entrar en admin se descargan el código de admin y, aparte, el de la gráfica |
| 6 | Criterios de entrada a `compartido`, abajo | |

## Lo que se aprendió

- **Lint: una regla posterior reemplaza a la anterior.** En la configuración plana de ESLint, dos bloques que coinciden con el mismo archivo y configuran la misma regla no se suman: gana el último. La primera versión (un bloque por regla) dejaba a `pedidos/ui` sin la regla de importación profunda; se corrigió generando un bloque por funcionalidad y capa con la lista completa de patrones.
- **Un error que conviene evitar:** la gráfica (`ui`) importaba directamente los datos (`datos`). Se movió la importación a un contenedor (`paginas/grafica-ventas-conectada.ts`), que es lo que se difiere; la `ui` recibe la serie por entrada.
- **El medidor honesto:** con todo cargado por separado pero con `@defer`, la diferencia es 1 %; la mejora real viene de que el "antes" arrastra todo (páginas, almacén y la gráfica con su serie). Las dos cifras están medidas, no estimadas.
- **`providers` en una ruta:** el servicio es de la funcionalidad y lo comparten sus páginas (spec y E2E), pero sobrevive a salir de la ruta; ver la nota añadida al README.
- `canMatch` evita incluso la descarga del código de `admin` (la E2E cuenta los `.js` pedidos).

## Criterios de entrada a `compartido` (para el equipo)

1. Lo usan al menos dos funcionalidades hoy (no "podrían usarlo").
2. No contiene lógica de negocio: un botón, un formateador de fechas, sí; "calcular el descuento", no.
3. No importa ninguna funcionalidad ni `nucleo` (verificado por lint: `compartido/ui/mala-compartido.ts` falla).
4. Tiene dueño y prueba. Si solo la usa una funcionalidad, vuelve a ella.
