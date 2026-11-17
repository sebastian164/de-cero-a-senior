# Taller nivel-06/01: rendimiento, medido

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-06-avanzado/01-rendimiento/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Next.js 16.4.6 y React 19.3.1, sobre `next build` + `next start` (nunca en desarrollo), en Chromium sin cabeza de Playwright 1.65.1. Cifras exactas en `mediciones/n6-01.json`
(las escribe `e2e/n6-01.spec.ts`; el compilador, `scripts/compilador.sh`). Son mediciones de un equipo concreto: sirven para comparar variantes entre sí, no como valores absolutos.

## 1. Reducir el JavaScript inicial (con medición)
Herramienta: `next analyze --output` (analizador de paquetes de Turbopack; `scripts` lo ejecuta y comprueba que genera `index.html`), y la suma de bytes (gzip) de los scripts descargados por Chromium.

| Variante | JavaScript al cargar (gzip) |
|---|---|
| Antes: página de cliente que importa `marked` y la gráfica | 163 558 B |
| Después: Markdown convertido en el servidor + `Grafica` con `dynamic(..., { ssr: false })` (bloque 3 del README) | 150 570 B (−12 988 B, 8 %); 454 B más llegan después, cuando la gráfica se pide |

Dos cambios, una medición cada uno: (1) mover `marked` al servidor quita la biblioteca del paquete; (2) la gráfica deja el paquete inicial y se descarga al hidratar.
El grueso del paquete (~150 KB) es el entorno de ejecución de React y Next.js: ningún cambio de la aplicación lo reduce.

## 2. Perfilar una pantalla lenta
`taller.test.tsx` usa `<Profiler>`: con el ejemplo "Antes" del README (estado del campo en la página, tabla pesada al lado), escribir 8 letras suma muchas veces más tiempo de renderizado
que la versión "Después"; la causa es que la tabla se vuelve a renderizar en cada tecla porque su padre cambia de estado.

## 3. Bajar el estado y `children`, sin memorización
`estado-y-children.tsx` con contadores de renderizado: con el estado en el padre, el hijo pesado se renderiza 1 + 4 veces al escribir 4 letras; con el estado en el componente del campo, solo 1.
Con `children`, el contenedor con estado cambia dos veces y el contenido recibido se renderiza solo 1 vez. Ningún `memo`, `useMemo` ni `useCallback`.

## 4. 10.000 filas
| | Tiempo hasta el siguiente cuadro tras mostrar | Nodos `<li>` |
|---|---|---|
| Sin virtualizar | 91 ms | 10 000 |
| Con `@tanstack/react-virtual` | 39 ms | 23 |

## 5. `useDeferredValue` en un filtro costoso (CPU 4× más lenta, 6 teclas)
Latencia hasta el siguiente cuadro tras cada tecla: sin diferir, mediana **61 ms** (máximo 86); con `useDeferredValue` (ejemplo del README), mediana **14 ms** (máximo 30).

## 6. Compilador de React (`reactCompiler`, `babel-plugin-react-compiler` 1.0.0)
Mismo hijo, 5 pulsaciones del padre. Sin compilador: con `memo`/`useMemo`/`useCallback` manuales el hijo se renderiza **1** vez; sin memorización, **6**.
Con el compilador activado y **sin** memorización manual: **1** vez. El compilador logra solo lo que antes se escribía a mano.

## 7. Métricas web en campo y Lighthouse
`web-vitals` (`onLCP`, `onCLS`, `onINP`, `onFCP`, `onTTFB`) envía a `/api/vitales`. En esa visita (equipo local, sin limitación): LCP 28 ms, FCP 28 ms, CLS 0, INP 16 ms, TTFB 8,8 ms.
Lighthouse 13.6.0 (laboratorio, con su limitación por defecto) sobre la misma pantalla: LCP 757 ms, CLS 0, TBT 2 ms, puntaje 1. La diferencia de LCP (28 ms frente a 757 ms) es la limitación simulada de red y CPU:
el laboratorio es un escenario fijo y pesimista; el campo refleja a cada usuario real, aquí uno solo y sin limitación.
