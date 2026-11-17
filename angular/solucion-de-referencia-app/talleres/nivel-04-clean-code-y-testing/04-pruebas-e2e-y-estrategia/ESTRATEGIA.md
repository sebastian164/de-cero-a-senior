# Estrategia de pruebas de la aplicación (una página)

La aplicación son 28 sesiones de ejemplos y talleres de Angular 22. La lógica vive, sobre todo, en funciones puras (reglas, adaptadores, validadores), almacenes con señales y componentes pequeños.

| Nivel | Qué prueba | Qué NO prueba | Herramienta | Tiempo medido |
|---|---|---|---|---|
| Estático | Tipos con `strict` y `strictTemplates`; contratos entre piezas; que los `.caso.ts` "no compila" fallen con su código NG | Comportamiento | `ngc` | 1,5 s |
| Unitarias | Reglas puras, adaptadores, validadores, almacenes (con dobles de la API), servicios HTTP (`HttpTestingController`), directivas y guardas | El render de la pantalla | Vitest 5.1 + `TestBed` | |
| Componentes | Comportamiento visible como lo haría una persona: roles, texto, `userEvent`, estados de carga, vacío y error | Que la red real responda | Vitest + Testing Library | 6,0 s (unitarias y componentes juntas: son la misma ejecución) |
| E2E | Recorridos críticos con el navegador y el servidor reales: comprar, registrarse, las pantallas de cada sesión abren sin errores de consola | Cada validación, cada combinación | Playwright 1.65 | 3,7 s los 7 de la tienda; el barrido completo de las 28 sesiones, más |
| Transversales | Accesibilidad (axe, WCAG 2 A y AA) en una pantalla por flujo; presupuesto de tamaño del paquete inicial (la construcción falla si pasa del límite) | Contraste de marca, rendimiento real | `@axe-core/playwright`, `budgets` de `angular.json` | segundos |

## Reglas para decidir dónde va una prueba

1. **Lo más abajo posible que todavía dé confianza.** Una regla de documento se prueba una vez, como función pura; el E2E solo comprueba que el formulario la usa.
2. **Cada regla, en un solo nivel.** El E2E de registro no repite todos los casos de documento.
3. **Los E2E son recorridos, no casos.** Pocos, sobre lo que cuesta dinero: comprar y registrarse. Una red simulada (`page.route`) cubre los errores del servidor sin depender de él.
4. **Forma de trofeo con un tope bajo:** muchas pruebas de componentes, porque ahí está la interfaz; muchas unitarias baratas porque la lógica está en funciones puras; pocas E2E.

## Reparto en la integración continua

| Cuándo | Qué corre | Por qué |
|---|---|---|
| Cada *push* | Estático + unitarias + componentes (≈ 8 s en total) | Retroalimentación inmediata |
| Cada *pull request* | Lo anterior + construcción de producción (con presupuesto) + E2E | Que las piezas encajen y el paquete no crezca sin querer |
| Antes de desplegar | Lo anterior + una prueba de humo contra el ambiente real (solo lectura) | El contrato real del backend |
| Siempre que algo falle | Trazas y capturas guardadas (`trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`) | Diagnosticar sin reproducir |

Una prueba inestable se arregla o se retira. Durante el desarrollo apareció una (n4-02: escribir en un campo de señales milisegundos después de crearlo daba NG0950) y se arregló esperando a la primera detección de cambios, no con un reintento: `retries: 0` en `playwright.config.ts`.
