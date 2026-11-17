# Taller TypeScript 02: orientación a objetos. Demostraciones y explicaciones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/typescript/02-orientacion-a-objetos-en-typescript/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.ts` (Vitest, `tsc --strict`). Código: `curso.ts`, `privados.ts`, `notificador.ts`, `empleados.ts`, `respuesta-http.ts`.

## Punto 1. `Curso`: clase frente a estado inmutable

Mismas reglas (cupo máximo, sin inscribir dos veces, no retirar a quien no está), dos formas. Clase con campos privados: lanza "El curso está lleno" y "Ana ya está inscrito", y no expone el arreglo interno (un `push` sobre `curso.inscritos` no cambia el curso). Versión inmutable con funciones: `inscribir(curso, alumno)` devuelve `{ ok, curso }` o `{ ok: false, error }` y el curso original no cambia.

## Punto 2. `private` de TypeScript se salta en ejecución; `#privado` no

| | `private saldo` (TypeScript) | `#saldo` (JavaScript) |
|---|---|---|
| Lectura por nombre en ejecución (`cuenta['saldo']`) | **100** | `undefined` |
| Escritura por nombre (`= 1_000_000`) | cambia el saldo: `consultar()` devuelve **1.000.000** | crea una propiedad pública distinta; `consultar()` sigue en **100** |
| `Object.keys(cuenta)` | incluye `'saldo'` | no incluye el campo privado |

`private` solo existe para el compilador (se borra al compilar); `#` lo impone el motor. Si lo que se protege es una regla de negocio y no solo una convención de equipo, hay que usar `#` (o una clausura/función que no exponga el estado).

## Punto 3. `Notificador` con tres implementaciones

Una interfaz `Notificador` con `NotificadorCorreo`, `NotificadorSms` (ambas con `implements`) y `notificadorConsola` (un objeto que la cumple por su forma, sin `implements`: tipado estructural). `avisarATodos(notificadores, destino, mensaje)` las usa sin conocerlas y devuelve cuántas fallaron: con `ana@correo.example` las tres envían (0 fallos); con un celular, el correo rechaza el destino (1 fallo).

## Punto 4. Composición en lugar de herencia

`Empleado` → `EmpleadoPorHoras`, `EmpleadoAsalariado` frente a `{ nombre, pago: Pago }` con `Pago = porHoras | asalariado | comision`. La nómina da el mismo total que la jerarquía (Ana 160 h × 20.000 + Luis 4.000.000) y, a diferencia de la jerarquía, permite **cambiar el esquema de pago de una persona** (`cambiarEsquema`) sin crear una clase nueva: Ana pasa a comisión (1.000.000 + 5 % de 10.000.000 = 1.500.000).
Con herencia, pasar de por horas a comisión obliga a reconstruir el objeto con otra clase o a crear una combinación (`EmpleadoPorHorasConComision`).

## Punto 5. Por qué una respuesta HTTP tipada como una clase no tiene sus métodos

`obtenerComoClase` hace `JSON.parse(cuerpo) as PedidoClase`. La aserción solo le dice al compilador "créeme"; **no construye nada**. JSON no puede transportar métodos ni prototipos: `JSON.parse` devuelve un objeto plano con `id` y `lineas`.
Resultado observado: `pedido.id` es `'p1'`, `typeof pedido.total` es `'undefined'` y `pedido.total()` lanza `TypeError`, a pesar de que `tsc` no se queja. La solución es construir la instancia tras validar (`obtenerYConstruir(cuerpo).total()` da 3000) o, mejor, usar un tipo plano y una función `total(pedido)`.
