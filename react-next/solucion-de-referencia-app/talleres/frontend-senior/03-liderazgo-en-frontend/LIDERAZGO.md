# Taller final de la ruta React y Next.js: liderazgo en frontend

Ejemplo resuelto sobre la tienda de la ruta. Lo ejecutable (contrato y simulacros) está en `contrato.ts`; `taller.test.ts` comprueba la forma de cada entregable.

## 1. Radar tecnológico

| Elemento | Anillo | Justificación |
|---|---|---|
| Next.js 16 con enrutador de aplicación | Adoptar | Ya sirve toda la tienda, da renderizado en servidor y la caché declarativa que el equipo domina |
| TypeScript en modo estricto | Adoptar | Los fallos de tipos se detectan en la construcción y no en producción, con costo casi nulo |
| Vitest y Testing Library | Adoptar | Pruebas rápidas del comportamiento visible, sin acoplarse a la implementación de los componentes |
| Playwright | Adoptar | Los recorridos críticos y la accesibilidad con axe se verifican en un navegador real en cada cambio |
| Zod en los bordes | Adoptar | Valida entradas del servidor y formularios con un solo esquema y entrega los tipos gratis |
| TanStack Query | Probar | Útil para datos de cliente con caché; falta comprobar si se justifica donde ya hay componentes de servidor |
| Compilador de React | Probar | Elimina memorización manual, pero hay que medir en la tienda real si mejora algo antes de activarlo |
| Zustand | Evaluar | Solo hace falta si aparece estado compartido de cliente que el contexto no resuelve con claridad |
| Redux Toolkit | Retener | No se añade a lo nuevo: el estado de la tienda cabe en URL, servidor y contexto, sin otra capa |
| Microfrontends | Retener | Con un equipo y un despliegue, el costo de coordinación supera cualquier beneficio previsible |

## 2. Registros de decisión (ADR)

### ADR-001: Estado de filtros y paginación en la URL
- **Estado**: aceptada, 2026-01-12.
- **Contexto**: el listado de pedidos pierde sus filtros al recargar, no se pueden compartir enlaces con soporte y el botón Atrás no vuelve al filtro anterior.
- **Decisión**: los filtros, el orden y el cursor viven en los parámetros de búsqueda; la página es de servidor y lee `searchParams`.
- **Alternativas**: estado local en el cliente (rápido de escribir, no compartible); almacén global (otra capa y sincronización con la URL).
- **Consecuencias**: enlaces compartibles y Atrás correcto; cada cambio es una navegación, así que se usa `useTransition` para no bloquear la entrada; las URLs son parte del contrato público y hay que mantenerlas.

### ADR-002: Autorización en cada acción de servidor
- **Estado**: aceptada, 2026-01-20.
- **Contexto**: una acción de servidor es un punto de entrada público; ocultar el botón no impide invocarla con el identificador de otro usuario.
- **Decisión**: toda acción verifica la sesión y la propiedad del recurso en la capa de datos, y devuelve datos ya proyectados; se prueba con una invocación directa.
- **Alternativas**: confiar en el middleware de rutas (no cubre las acciones); validar solo en el backend remoto (duplica reglas y alarga la respuesta).
- **Consecuencias**: algo de código repetido en cada acción, mitigado con un envoltorio común; la prueba de invocación directa entra en la definición de terminado.

### ADR-003: Presupuesto de rendimiento en integración continua
- **Estado**: propuesta, 2026-02-02.
- **Contexto**: el paquete creció sin que nadie lo notara y el LCP en móvil empeoró dos versiones seguidas.
- **Decisión**: la construcción falla si el JavaScript comprimido supera 400 kB o si Lighthouse da menos de 90 de rendimiento en tres pantallas clave.
- **Alternativas**: revisión manual antes de cada versión (se olvida); solo monitoreo en producción (avisa tarde).
- **Consecuencias**: falsos positivos por ruido de laboratorio (se repite la medición dos veces) y un presupuesto que debe revisarse cada trimestre.

## 3. Definición de terminado de una pantalla

| Punto | Cómo se verifica | Herramienta |
|---|---|---|
| Tipos estrictos y sin avisos de lint | Automático | `tsc --noEmit`, ESLint |
| Pruebas de comportamiento de la pantalla | Automático | Vitest y Testing Library |
| Recorrido crítico en navegador real | Automático | Playwright |
| Sin violaciones de accesibilidad automáticas (WCAG 2.1 AA) | Automático | axe en Playwright |
| Presupuesto de tamaño y rendimiento | Automático | Script de presupuesto y Lighthouse en CI |
| Sin errores en la consola durante el recorrido | Automático | Playwright |
| Navegable solo con teclado y leído con lector de pantalla | Manual | Revisión con NVDA o VoiceOver |
| Estados de carga, vacío, error y sin red existen | Manual | Revisión de código y de la vista previa |
| Textos internacionalizados y revisados por diseño | Manual | Revisión de la vista previa |
| La vista previa por cambio fue mostrada a producto | Manual | Demostración |

## 4. Contrato con el backend: listado de pedidos

Escrito antes de programar y firmado por ambos equipos. Esquema ejecutable en `contrato.ts`.

`GET /pedidos?estado=<pendiente|pagado|enviado|cancelado>&cursor=<opaco>`

| Caso | Respuesta |
|---|---|
| Éxito | 200 `{ items: Pedido[], siguiente: string \| null }`; `siguiente` es `null` en la última página |
| Sin sesión | 401 `{ codigo: "no_autenticado", mensaje }` |
| Estado desconocido | 400 `{ codigo: "parametro_invalido", mensaje }` |
| Fallo del servidor | 500 `{ codigo: "error_interno", mensaje }` |

`Pedido = { id, estado, total >= 0, creadoEn (ISO 8601) }`. Los simulacros (`ok`, `vacio`, `sinSesion`, `parametroInvalido`, `falla`) se usan en el desarrollo y en las pruebas del frontend; la misma batería se ejecuta contra el backend real para detectar rupturas del contrato antes de integrar.

## 5. Negociación con diseño

Diseño pide una animación de paralaje a pantalla completa en la portada de la tienda.
- **Costo**: unos 60 kB de JavaScript más, y trabajo continuo del hilo principal al desplazar; en móvil de gama media se estima una caída de 15 a 20 puntos de rendimiento.
- **Riesgo**: empeora LCP e interactividad justo donde llega el tráfico de pago, y quienes piden movimiento reducido no la verían bien sin una versión alterna.
- **Alternativa A**: la misma idea con transformaciones de CSS y `prefers-reduced-motion`, sin JavaScript; conserva 90 % del efecto visual y costo cercano a cero.
- **Alternativa B**: animar solo la imagen principal al cargar, una vez; es más barata y se puede medir antes de ampliar.
- **Propuesta**: construir la alternativa A, medir con el presupuesto de rendimiento y revisar con diseño con datos en la mano.

## 6. Defensa de diez minutos

**Problema.** Una tienda con catálogo, carrito y cuenta del usuario, usada sobre todo en móvil, con páginas que deben indexarse y datos por usuario que no pueden filtrarse. Un equipo, un despliegue.

**Decisiones.** Next.js con componentes de servidor por defecto y componentes de cliente solo en las hojas interactivas. Caché por etiquetas con invalidación al mutar y claves que incluyen al usuario en lo privado. Estado en la URL, el servidor y el contexto, sin almacén global. Formularios con un esquema compartido entre cliente y servidor. Autorización en la capa de datos y en cada acción.

**Alternativas.** Aplicación de una sola página en cliente: más simple de desplegar, pero sin indexación y con más JavaScript. Almacén global de estado: coherencia fácil al principio y acoplamiento después. Microfrontends: autonomía de equipos que aquí no se necesita.

**Costos.** El modelo de caché exige entender cuándo se regenera cada ruta y probarlo; hay una curva de aprendizaje con la frontera entre servidor y cliente; los componentes de servidor requieren un entorno Node en el despliegue. Se pagan con pruebas de recorrido, un presupuesto de rendimiento en integración continua y una guía corta de decisiones.

**Calidad.** Vitest para comportamiento, Playwright con axe para recorridos y accesibilidad, presupuesto de tamaño y de Lighthouse, cabeceras de seguridad con CSP con nonce, y métricas reales de los usuarios con web-vitals enviadas a un colector.

**Evolución.** Si aparecen varios equipos, se separan funcionalidades en módulos con fronteras verificadas por reglas de importación antes de pensar en microfrontends. Si el estado de cliente se complica, se evalúa Zustand. Si el tráfico crece, la caché pasa a un almacén compartido entre réplicas, porque hoy cada réplica guarda la suya y la invalidación solo alcanza a la que la recibe. Cada decisión queda en un registro con su fecha de revisión.

**Cierre.** El diseño no es el más moderno sino el más fácil de cambiar con seguridad: cada decisión importante tiene una prueba que avisa cuando deja de cumplirse.

## 7. Balance de los niveles

| Nivel | Estado | Qué hago |
|---|---|---|
| Nivel 1: fundamentos de React y Next | Domino | Mantener con la práctica diaria |
| Nivel 2: SOLID en React | Domino | Aplicar en revisiones de código |
| Nivel 3: patrones | Domino | Repasar contexto y reductores con un caso nuevo |
| Nivel 4: código limpio y pruebas | Necesita repaso | Practicar pruebas de mutación y formularios en vivo |
| Nivel 5: arquitectura, caché y despliegue | Necesita repaso | Repetir el taller de réplicas y revisar la invalidación de caché |
| Nivel 6: rendimiento, seguridad y accesibilidad | Necesita repaso | Practicar lector de pantalla y medir en campo |

Plan de seis meses:
- Mes 1: repetir los recorridos de Playwright de la tienda con lector de pantalla real.
- Mes 2: leer a fondo el modelo de caché de la versión vigente y escribir una guía de una página.
- Mes 3: llevar el presupuesto de rendimiento a integración continua en un proyecto propio.
- Mes 4: practicar autorización: auditar las acciones de servidor de un proyecto con invocación directa.
- Mes 5: dar una charla interna de veinte minutos sobre la frontera servidor y cliente.
- Mes 6: revisar el radar y los ADR, y repetir este balance.
