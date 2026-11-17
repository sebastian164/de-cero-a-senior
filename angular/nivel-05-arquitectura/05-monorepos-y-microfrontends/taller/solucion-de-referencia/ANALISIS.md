# Taller n5-05 — Monorepos y microfrontends

Dos espacios de trabajo reales: `proyectos/nx-tienda` (Nx, Angular 22) para los puntos 1 a 4 y `proyectos/mfe` (CLI de Angular 22.2 + Native Federation 22.2.5) para el 5. Se ejecutan con `ejecutar-nx.mjs` y `microfrontend.mjs` (ambos salen con código 1 si algo falla).

| # | Solución | Comprobación |
|---|---|---|
| 1 | Bibliotecas por ámbito y tipo con etiquetas (`scope:shop`, `scope:shared`, `scope:api`; `type:feature`, `type:ui`, `type:data`) y la regla `@nx/enforce-module-boundaries`. | `nx run-many -t lint` pasa. Dos dependencias indebidas hacen fallar el lint: `feature-products` → `@org/api/products` ("A project tagged with `scope:shop` can only depend on libs tagged with `scope:shop`, `scope:shared`") y `models` (`type:data`) → `design-system` ("A project tagged with `type:data` can only depend on libs tagged with `type:data`") |
| 2 | `nx graph --file` → 14 aristas en `GRAFO-NX.txt` | ver abajo |
| 3 | Un cambio en `feature-product-detail` afecta a `feature-product-detail`, `shop` y `shop-e2e` (3 de 10); uno en `models` afecta a 9 de 10 | `nx affected -t test --base=HEAD` ejecuta solo `feature-product-detail` y `shop` |
| 4 | `packages/shared/design-system`: `ds-boton`, `ds-etiqueta`, `ds-tarjeta` con `tokens.css`, sus pruebas y `README.md` con ejemplos | `nx test design-system` |
| 5 | Anfitrión (`projects/host`, puerto 49281) y remoto (`projects/remote`, 49282) con Native Federation; el anfitrión descarga `remoteEntry.json` en ejecución y carga `./Resumen` con `loadRemoteModule` | `microfrontend.mjs`: 8 comprobaciones |
| 6 | ADR al final de este archivo | |

## Punto de partida del punto 1 y qué es trabajo del taller

El enunciado pide "convierte una aplicación en un espacio de trabajo". **No se partió de una aplicación del curso**, sino del espacio de ejemplo que genera `create-nx-workspace` (preset Angular con `shop` y `api`, y las bibliotecas `@org/...`), porque lo que el taller evalúa es la estructura de bibliotecas, no el dominio. Lo que viene de la plantilla de Nx: las aplicaciones `shop`, `shop-e2e` y `api`, las bibliotecas iniciales y el `eslint.config.mjs` con `@nx/enforce-module-boundaries`. **Añadido a mano para el taller:** las etiquetas (`scope:*`, `type:*`) en cada `project.json`, las `depConstraints`, las dos dependencias indebidas de prueba (que se aplican y se revierten en `ejecutar-nx.mjs`), el sistema de diseño `packages/shared/design-system` con sus tres componentes, pruebas y README, y el microfrontend.

Las etiquetas del README del curso son ejemplos; aquí se usaron dos ejes (ámbito: `scope:shop`, `scope:shared`, `scope:api`; tipo: `type:feature`, `type:ui`, `type:data`) porque con la plantilla de Nx las bibliotecas son de tipo `feature` (pantallas), `ui`, `data` (modelos y acceso a datos) y se reparten entre la tienda, lo compartido y la API; el eje de ámbito impide que la tienda dependa de la API y el de tipo que los modelos dependan de la interfaz.

## Aislamiento de git en `ejecutar-nx.mjs`

El script necesita un repositorio para que `nx affected --base=HEAD` tenga contra qué comparar. Hace `git add`, `git commit` y `git checkout -- .` **solo** si `git rev-parse --show-toplevel` es la propia carpeta `proyectos/nx-tienda`; si no lo es, ejecuta `git init` allí, y si aun así la raíz no coincide, aborta sin tocar git. Así nunca confirma ni descarta cambios del repositorio del curso, y funciona "desde cero" aunque el `.git` anidado no viaje con una copia.

## 2. El grafo

`shop` → `feature-products`, `feature-product-detail`; ambas → `data`, `shared-ui`, `models`; `data` → `models`; `shared-ui` → `models`; `api` → `products`, `models`; `products` → `models`; `shop-e2e` → `shop`. Observaciones: `models` es el nodo del que depende todo (el 90 % del espacio se reconstruye al tocarlo: debe cambiar poco y por eso es `type:data` sin dependencias); la aplicación `api` y la `shop` solo comparten `models`, que es la costura correcta; no hay ciclos.

## 5. Problemas que aparecieron (y qué se hizo)

| Área | Problema | Comprobado | Tratamiento |
|---|---|---|---|
| Dependencias | Si Angular se cargara dos veces, los servicios (`Router`, inyector) no se compartirían | `performance` muestra **un solo** `_angular_core.*.js` descargado | Las dependencias van como `singleton` + `strictVersion` en `federation.config.mjs` (`shared`); una versión distinta de Angular en el remoto haría fallar la carga en vez de duplicarla |
| Estilos | Los estilos globales se filtran entre aplicaciones | El `h2` verde del remoto **no** afectó a los `h2` del anfitrión: la encapsulación de Angular lo evita mientras los estilos sean de componente | Solo estilos de componente en el remoto; los globales (reset, tokens) viven en el sistema de diseño compartido |
| Sesión | El remoto es otro paquete: no comparte los servicios del anfitrión | El remoto solo vio el usuario "ana" porque ambos leen `sessionStorage`; un `signal` del anfitrión no llega al remoto | Contrato explícito: token de inyección compartido como biblioteca singleton, o eventos del navegador; nunca el estado interno de uno en el otro |
| Configuración regional | `LOCALE_ID` y datos regionales los pone el anfitrión | El remoto formateó `$150,000` (en-US) porque el anfitrión no registra `es-CO` | Registrar la configuración regional en el anfitrión y declararla en el contrato |
| Red | El remoto vive en otro origen | Sin `Access-Control-Allow-Origin` el anfitrión no pudo cargarlo (4 errores de consola, 1 petición fallida) | CORS en el origen del remoto |
| Construcción | `ng add` del adaptador cambió el constructor a `@angular-architects/native-federation:build` y movió `main.ts` a `bootstrap.ts` | | Es el coste de entrada: cada aplicación federada tiene un arranque en dos pasos |

## Hallazgos en el espacio Nx

- El repositorio de ejemplo de Nx para Angular traía **5 componentes con `styles: [ ... ]` en línea que no se pueden probar** con Vitest 5 y el complemento de Analog (`Cannot find module '/@id/__x00__virtual:angular:jit:style:inline…'`); se pasaron a `styleUrl` con su archivo `.css`. Con eso `nx run-many -t lint test build` pasa para los 10 proyectos.
- El prefijo de selector de `design-system` era `lib` y sus componentes `ds-*`: el lint lo marcó como error. Se cambió el prefijo a `ds`.

## 6. ADR — ¿Monorepo, microfrontends o ninguno?

**Contexto.** Una empresa de comercio con 3 equipos de 6 personas (18 en total): tienda web, panel de administración y una aplicación de atención al cliente. Las tres comparten el catálogo de pedidos y el sistema de diseño; se despliegan hoy una vez por semana y el equipo de la tienda necesita desplegar a diario. Un solo producto de cara al cliente, un solo sistema de identidad.

**Decisión.** Un **monorepo Nx con tres aplicaciones y bibliotecas por ámbito y tipo**, sin microfrontends. Un sistema de diseño en una biblioteca (`scope:shared`, `type:ui`) y un `models` compartido; despliegues independientes por aplicación con `nx affected`.

**Por qué.**
- El problema que tienen es **coordinación del código compartido**, no despliegue acoplado de partes de una misma pantalla. El monorepo con reglas de límites lo resuelve con un coste bajo y se verificó que hace fallar las dependencias indebidas y que ejecuta solo lo afectado (3 de 10 proyectos para un cambio en una hoja).
- Los microfrontends añadieron, en el prototipo, 5 clases de problemas (versiones, estilos, sesión, regional, CORS) y un arranque en dos pasos. Esa complejidad solo compensa cuando hay **equipos que no pueden coordinar versiones ni calendarios**, o una pantalla compuesta por partes de equipos distintos.
- Con 18 personas y 3 aplicaciones separadas por ruta (no por región de una misma pantalla), las fronteras de despliegue ya existen: son tres aplicaciones.

**Consecuencias.** Un cambio en `models` reconstruye casi todo (9 de 10 en el prototipo): se mantiene pequeño y estable. Se necesita una disciplina de etiquetas y una revisión del grafo en cada solicitud de cambios. Si la empresa crece a más de ~6 equipos o a una pantalla con regiones de equipos distintos, se vuelve a decidir con Native Federation (`proyectos/mfe` es el punto de partida).

**Alternativas descartadas.** Varios repositorios con paquetes npm (versiones desfasadas del sistema de diseño, el problema que queremos evitar). Microfrontends desde el inicio (coste sin beneficio a este tamaño).
