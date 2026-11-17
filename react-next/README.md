# Ruta React y Next.js

De los fundamentos de TypeScript y de React al diseño y la defensa de aplicaciones completas con Next.js.

## Niveles

| Nivel | Tema | Sesiones |
|---|---|---|
| 1 | [Fundamentos: TypeScript, React y Next.js](nivel-01-fundamentos/) | 3 de TypeScript + 3 |
| 2 | [SOLID en React](nivel-02-solid/) | 5 |
| 3 | [Patrones en React](nivel-03-patrones/) | 5 |
| 4 | [Clean code, formularios y pruebas](nivel-04-clean-code-y-testing/) | 3 |
| 5 | [Arquitectura con Next.js](nivel-05-arquitectura/) | 4 |
| 6 | [React y Next.js avanzado](nivel-06-avanzado/) | 3 |
| 7 | [Nivel senior de frontend](../frontend-senior/) (común con Angular) | 3 |

Al final de cada bloque: [proyectos de aplicación](proyectos/).

## Relación con las otras rutas

- Los conceptos de diseño (orientación a objetos, SOLID, patrones, arquitectura, liderazgo) se explican a fondo en la [ruta Java](../java/). Esta ruta enlaza a esas lecciones y se concentra en su aplicación con React y Next.js. Se puede seguir sin saber Java.
- Algunos temas de frontend que no dependen del framework (estrategia de pruebas de extremo a extremo, fundamentos de seguridad en el navegador, accesibilidad, organización por funcionalidades) están desarrollados una sola vez, en la [ruta Angular](../angular/), y se enlazan desde aquí. Las sesiones de esta ruta agregan lo específico de React y Next.js.

## Versiones

React y, sobre todo, Next.js cambian rápido. Esta ruta usa componentes de función con hooks y el enrutador de aplicación de Next.js, con componentes de servidor y acciones de servidor. El modelo de caché y algunos nombres de archivos especiales han variado entre versiones mayores: las sesiones explican los conceptos e indican dónde debes confirmar el comportamiento. Ante cualquier diferencia, manda la documentación oficial de tu versión: react.dev y nextjs.org/docs.

## Sobre el código

El código de esta ruta se verificó en una aplicación Next.js 16.4.6 real (React 19.3.1, TypeScript 5.9.3, Node 26, PostgreSQL 17): los ejemplos "después" y las soluciones de los talleres se compilaron con `next build`, se ejecutaron con `next start` y se recorrieron en un navegador (Playwright 1.65.1, Chromium) sin errores de consola, con las pruebas de cada taller en Vitest 4.1.12. Aun así, tu versión puede diferir: ante cualquier diferencia manda la documentación oficial. Hacerlo funcionar en tu proyecto sigue siendo parte de cada taller.

## Requisitos

- Node.js en una versión LTS vigente.
- Conocer HTML y CSS básicos.
