# Taller integrador del Nivel 1 — Parqueadero (solución de referencia)

- `a-funciona/Main.java`: versión rápida, un archivo. `java a-funciona/Main.java` imprime `11000`, `6000`, `0`, `no ha entrado`, `-1` .
- `b-mantenible/`: versión de diseño. `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (22 pruebas, 0 fallos).

Supuestos (el enunciado no los fija): una permanencia de 0 minutos cobra una hora; por encima de 12 horas, cada bloque de 24 horas empezado cobra una tarifa de día (25 horas de carro = 60.000); el descuento de residente se aplica también sobre la tarifa de día; las placas se comparan sin distinguir mayúsculas.

## Parte C — La prueba del cambio

| Requisito | Versión A | Versión B |
|---|---|---|
| 1. Eléctricos: tarifa de carro con 20 % de descuento, acumulable con residente | Nuevo valor de `tipo` `"ELECTRICO"` en `entrar` (cupo: ¿comparte el de carros?, hay que tocar el contador `carros` y su `if`) y en `salir` (rama de tarifa duplicada de `CARRO` + descuento). Tres o cuatro `if` en dos métodos, y el cupo compartido se resuelve a mano. | `Vehiculo` gana `boolean electrico` (o una lista de condiciones) y `CalculadoraCobro.cobrar` aplica el 20 % después de la tarifa. El tipo sigue siendo `CARRO`, así que cupo y tarifa no se tocan. Dos clases. Si los descuentos siguen creciendo, `CalculadoraCobro` pasa a recibir una lista de descuentos. |
| 2. Tarifa de día desde las 10 horas | Dos literales `12` en `salir` (uno por tipo); hay que encontrarlos todos. | La constante `Tarifa.HORAS_PARA_TARIFA_DE_DIA`. Una línea. Ojo: el enunciado dice "más de 12"; "desde las 10" cambia también el operador (`>` a `>=`), y la prueba de borde "12 h 1 min" lo detectaría. |
| 3. Domingos las motos no pagan | `salir` no conoce la fecha (A trabaja con minutos desde un origen); hay que cambiar la firma de `entrar`/`salir` y el `Map` de entradas para guardar fechas. Cambio transversal. | `CalculadoraCobro.cobrar` necesita la fecha de salida (o de entrada: hay que decidir qué pasa con una moto que entra el sábado y sale el domingo). `Parqueadero` ya tiene la hora y se la pasaría. Dos clases y una decisión de negocio. |

## Parte D — Defensa

### Qué concepto de cada lección se aplicó y dónde

| Lección | Dónde |
|---|---|
| 00 Qué es la OO | Datos y reglas juntos: `Tarifa.valorPor`, `Parqueadero.registrarEntrada`. |
| 01 Clases y objetos | Varios `Parqueadero` independientes son posibles: no hay `static` mutable (A tiene seis). |
| 02 Encapsulamiento | `adentro` y `ocupados` son privados; la invariante "no hay más vehículos que cupo" solo se cambia por `registrarEntrada`/`registrarSalida`. `Vehiculo` y `Tarifa` son inmutables y validan al construirse. |
| 03 Herencia | Decidí no usarla (ver abajo). |
| 04 Polimorfismo | Polimorfismo por datos: cada `TipoVehiculo` trae su cupo y su `Tarifa`; no hay `if` por tipo. |
| 05 Abstracción | `Tarifa` es la abstracción "cuánto cuesta N horas"; `GRATIS` modela la bicicleta sin caso especial. |
| 06 Composición | `TipoVehiculo` tiene una `Tarifa`; `Parqueadero` tiene una `CalculadoraCobro`. |
| 07 Acoplamiento y cohesión | `CalculadoraCobro` (reglas de dinero) separada de `Parqueadero` (ocupación). La hora llega como parámetro: nada lee `LocalDateTime.now()` en mitad de la lógica, y las pruebas fijan el tiempo. |

### Qué se decidió no aplicar

- **Herencia por tipo de vehículo** (`Carro extends Vehiculo`): los tipos difieren solo en datos (cupo y tres precios). Una jerarquía sería indirección sin comportamiento distinto.
- **Interfaz para descuentos**: hoy hay uno (residente). Se crea cuando aparezca el segundo; la Parte C muestra que ese cambio es local.
- **Interfaz para la calculadora o un reloj inyectado**: el tiempo ya entra como parámetro, y eso basta para probar.

### Longitud

A: 41 líneas. B: 124 líneas sin contar pruebas (6 archivos). B es unas tres veces más larga.

### ¿Valió la pena?

Para este enunciado, sí, por dos razones medibles: B tiene 22 pruebas que fijan los bordes (fracción de hora, 12 horas exactas, cupo, placa repetida), mientras A no se puede probar sin reescribirla (estado `static` y salida por consola), y los tres requisitos nuevos tocan en B una o dos clases con un lugar evidente, mientras que en A el requisito 3 obliga a cambiar el modelo del tiempo. A además tiene defectos que nadie pidió: la entrada de un vehículo sin cupo o repetido solo imprime un mensaje y `salir` devuelve `-1` como valor de cobro. Si el parqueadero fuera un script de una tarde para un evento, A bastaría.
