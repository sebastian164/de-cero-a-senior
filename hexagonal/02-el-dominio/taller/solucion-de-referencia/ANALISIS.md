# Taller 02 — El dominio de los pedidos

Proyecto en esta carpeta (organización de `hexagonal/proyecto/`: `src` y `pruebas`). Compilado con
`javac -Xlint:all -Werror` y ejecutado con JDK 21.0.13: **38 comprobaciones, 0 fallos** (se repite con los comandos de abajo).

```bash
# desde esta carpeta
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida tienda.TodasLasPruebas
```

## Paso 1. Conceptos

| Concepto | Tipo | Por qué |
|---|---|---|
| `Dinero` | Objeto de valor | Dos valores de 100 pesos son intercambiables; inmutable y sin identidad |
| `LineaDePedido` | Objeto de valor | Una línea no tiene vida propia: es "2 teclados a 150.000" dentro de un pedido. Lleva el precio con el que se vendió, no el del catálogo de mañana |
| `Pedido` | Entidad | Tiene identidad (`id`) y estado que cambia en el tiempo |
| `EstadoDePedido` | Enumeración | PENDIENTE, CONFIRMADO, CANCELADO |
| `Existencias` | Entidad | Su identidad es el producto; sus unidades cambian con cada venta |
| `Reserva` | Servicio de dominio | "No se vende más de lo que hay" para un pedido entero cruza el pedido y las existencias de varios productos: no es de ninguno |

## Paso 2. Qué necesita el dinero

Solo sumar (`mas`), multiplicar por una cantidad (`por`) y comparar con el tope (`esMayorQue`). No hay restas ni divisiones:
un pedido nunca las usa. Las operaciones usan `Math.*Exact`: un desbordamiento es un error, no un número negativo.

## Pasos 3 y 4. Reglas dentro de los objetos y transiciones

| Regla | Dónde |
|---|---|
| Cantidad mínima 1, producto y precio obligatorios | Constructor de `LineaDePedido` |
| Cliente obligatorio, al menos una línea, total ≤ tope (5.000.000 de pesos) | `Pedido.crear` |
| Solo se confirma un pedido pendiente | `Pedido.confirmar` |
| Se cancela pendiente o confirmado; uno cancelado no se cancela otra vez | `Pedido.cancelar` |
| Las existencias nunca son negativas; no se reserva más de lo que hay | `Existencias.de`, `Existencias.reservar` |
| Un pedido se reserva entero o no se reserva | `Reserva.reservar` |

Transiciones: `PENDIENTE → CONFIRMADO`, `PENDIENTE → CANCELADO`, `CONFIRMADO → CANCELADO`. No hay forma de volver a
`PENDIENTE` ni de salir de `CANCELADO`. No existe un `setEstado`: solo `confirmar` y `cancelar`.

## Paso 5. Las existencias

No pertenecen al pedido: un pedido dura minutos y las existencias de un producto son compartidas por todos los pedidos.
Son su propia entidad (`Existencias`), con la regla "no se vende más de lo que hay" en `reservar`. Para un pedido de varias
líneas, `Reserva.reservar` comprueba **todas** antes de descontar ninguna.

## Paso 6. Imports

`PruebasDeArquitectura` (incluida en la ejecución) verifica que `tienda/dominio` solo importa `java.lang`, `java.time`,
`java.util` y el propio dominio. Resultado: lista vacía. La fecha de creación (`Instant creadoEn`) llega como parámetro.

## Paso 7. Límites probados

Pedido justo en el tope (aceptado) y un centavo por encima (rechazado); la última unidad (se vende, quedan 0) y una de más
(rechazada, no descuenta); cantidad 0 y -1; cantidad mínima 1.

## Paso 8. El defecto de la sesión 01

Prueba "si una línea no alcanza, se rechaza el pedido" + "y las existencias de la primera línea siguen intactas": pedido de
2 teclados y 5 monitores con 10 y 2 en existencias. Con este dominio **no puede ocurrir** porque `Reserva` valida todas las
líneas antes de descontar. También se prueba que dos líneas del mismo producto cuentan juntas (1 + 2 monitores con 2 en
existencias se rechaza).

Lo que el dominio **no** puede garantizar solo: que el descuento de existencias y el guardado del pedido queden juntos.
Eso es la unidad de trabajo de la sesión 04.

## Paso 9. Métodos `set` y campos modificables

| Candidato | ¿Deja un objeto inválido? |
|---|---|
| `Pedido.lineas()` | No: es `List.copyOf`, inmutable. Probado: `add` lanza `UnsupportedOperationException`, y cambiar la lista con la que se creó no afecta al pedido |
| `Pedido.estado` (campo mutable) | Solo cambia por `confirmar` y `cancelar`, que validan la transición |
| `Existencias.unidades` (campo mutable) | Solo cambia por `reservar` (no baja de 0) y `devolver` |
| `Pedido.existente(...)` | Reconstruye sin validar: es la puerta para los adaptadores que leen datos ya guardados. Es un riesgo aceptado y nombrado, igual que `Cuenta.existente` en el banco |

Riesgo aceptado: `Pedido.crear` no valida `id` nulo/en blanco ni `creadoEn` nulo (igual que `Cuenta.abrir` en el banco), y
`Reserva.liberar` lanza `NullPointerException` si falta el producto en el mapa. Las reglas del negocio sí están cubiertas.

## Criterios de aceptación

| Criterio | Resultado |
|---|---|
| El dominio solo importa `java.*`, sin entrada y salida | Prueba de arquitectura: ok |
| Las reglas están dentro de los objetos | No hay ninguna clase servicio con `if` de negocio fuera del dominio (aún no hay aplicación) |
| Los límites exactos están probados | Tope, tope + 1 centavo, última unidad, una de más |
| No puede construirse un pedido inválido | Una prueba por intento: sin cliente, cliente nulo, sin líneas, líneas nulas, sobre el tope, línea con cantidad 0, -1, sin producto, sin precio, dinero negativo, existencias negativas |
