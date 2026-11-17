# Ruta Angular

De los fundamentos de TypeScript al diseño y la defensa de aplicaciones Angular completas.

## Niveles

| Nivel | Tema | Sesiones |
|---|---|---|
| 1 | [Fundamentos: TypeScript y Angular](nivel-01-fundamentos/) | 7 |
| 2 | [SOLID en Angular](nivel-02-solid/) | 5 |
| 3 | [Patrones en Angular](nivel-03-patrones/) | 6 |
| 4 | [Clean code, formularios y pruebas](nivel-04-clean-code-y-testing/) | 4 |
| 5 | [Arquitectura frontend](nivel-05-arquitectura/) | 5 |
| 6 | [Angular avanzado](nivel-06-angular-avanzado/) | 4 |
| 7 | [Nivel senior de frontend](../frontend-senior/) (común con React/Next) | 3 |

Al final de cada bloque: [proyectos de aplicación](proyectos/).

## Relación con la ruta Java

Los conceptos de diseño (orientación a objetos, SOLID, patrones, arquitectura, liderazgo) no dependen del lenguaje. La explicación larga de cada uno, con analogías, casos y excepciones, está en la [ruta Java](../java/). Esta ruta enlaza a esas lecciones y se concentra en cómo se aplican con TypeScript y Angular. Puedes seguirla sin saber Java: los ejemplos de aquí son autosuficientes.

## Versión de Angular

Angular publica versiones mayores con frecuencia y ha cambiado varias recomendaciones en los últimos años. Esta ruta está escrita contra Angular 22 (junio de 2026) y usa su estilo: componentes independientes, señales, control de flujo integrado (`@if`, `@for`, `@defer`, `@let`), `inject()`, `input()`, `output()` y `model()`, consultas con señales, formularios con señales, recursos (`resource`, `httpResource`), `OnPush` por defecto y detección de cambios sin zona.

| Novedad | Estado en Angular 22 | Dónde se ve |
|---|---|---|
| Entradas, salidas, modelos y consultas con señales | Estable | [Nivel 1, sesión 03](nivel-01-fundamentos/03-componentes-con-senales/) |
| `resource`, `rxResource`, `httpResource` | Estable desde la 22 | [Nivel 1, sesión 04](nivel-01-fundamentos/04-datos-asincronos-con-resource/) |
| Formularios con señales | Estable desde la 22 | [Nivel 4, sesión 02](nivel-04-clean-code-y-testing/02-formularios-y-validacion/) |
| Interceptores, guardas y *resolvers* funcionales | Estable | [Nivel 3, sesión 05](nivel-03-patrones/05-adapter-interceptores-y-guardas/) |
| Almacén con señales | Biblioteca (NgRx SignalStore) | [Nivel 5, sesión 02](nivel-05-arquitectura/02-gestion-de-estado/) |
| `OnPush` por defecto y aplicaciones sin zona | Por defecto | [Nivel 6, sesión 01](nivel-06-angular-avanzado/01-deteccion-de-cambios-y-rendimiento/) |
| Vitest como ejecutor de pruebas | Por defecto desde la 21 | [Nivel 4, sesión 03](nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/) |

Angular publica una versión mayor cada seis meses. Si usas una posterior, o una anterior en un proyecto existente, habrá diferencias: manda la documentación oficial de tu versión, angular.dev.

## Sobre el código

Los ejemplos "después" y las soluciones de los talleres de esta ruta se compilaron y se ejecutaron en una aplicación real con Angular 22.2, Vitest 5 y Playwright 1.65 (Node 26): pruebas unitarias, pruebas en el navegador y comprobación de que ninguna pantalla escribe errores en la consola. Los fragmentos de la sección "Antes" están mal a propósito. Donde la ejecución mostró un error del texto, el README de la sesión lo dice.

## Requisitos

- Node.js en una versión LTS vigente y la CLI de Angular.
- Conocer HTML y CSS básicos.
