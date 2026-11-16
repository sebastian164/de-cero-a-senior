# Taller OCP — Calculadora de precio (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (9 pruebas, 0 fallos). `Main` imprime `123000` y `120000`, igual que el código de inicio, y `105000` para ENVIOGRATIS.

## 1. Ejes de variación, del más al menos probable

1. **Cupones.** Marketing crea campañas cada semana o cada mes; tienen fecha de vencimiento y formas distintas (porcentaje, valor fijo, envío gratis, 2x1).
2. **Recargos de envío.** Cambian con cada contrato con la transportadora y con cada ciudad nueva; varias veces al año.
3. **Recargos adicionales (empaque, seguro).** Aparecen de vez en cuando con productos nuevos.
4. **Impuestos por categoría.** Los fija la ley: cambian con una reforma tributaria, pocas veces y para todos a la vez.

## 2. Refactorización

- `Cupon` (interfaz) con `CuponPorcentaje`, `CuponValorFijo` y `CuponEnvioGratis`. Un cupón nuevo de forma conocida es una instancia nueva; de forma nueva, una clase nueva.
- `Recargo` (interfaz) con `RecargoEnvio` y `RecargoEmpaqueFragil`. Para probar el punto se agregó `RecargoSeguro` (1 % sobre pedidos de más de 500.000) sin tocar `CalculadoraPrecio`; la prueba "recargo seguro" lo verifica.
- `CalculadoraPrecio` recibe la lista de recargos y de cupones por constructor. `Main` es la raíz de composición: el único punto que se modifica para registrar una extensión.
- Cambio de comportamiento deliberado: un cupón desconocido lanza `IllegalArgumentException`; el original lo ignoraba en silencio y cobraba precio completo.

## 3. Eje que se dejó sin abstraer

El impuesto por categoría (`Categoria`, un `enum` con el porcentaje). Es el eje menos probable, sus cambios son por ley y afectan a todos los productos a la vez, y un `enum` hace visible en un solo archivo toda la tabla tributaria. Abstraerlo en una clase por tarifa sería especular. Si el impuesto empezara a depender de más cosas (región, exenciones por cliente), se abstraería entonces.

## 4. ENVIOGRATIS

Mi primer diseño tenía `Cupon.descuento(long base)`: todos los cupones del enunciado calculaban sobre la base. ENVIOGRATIS no se pudo expresar así, porque necesita saber cuánto fue el recargo de envío. El supuesto que se rompió: "un cupón solo depende del valor base". Tuve que cambiar el contrato a `descuento(Desglose)`, donde `Desglose` expone base, impuesto y los recargos por nombre, y la calculadora pasó a construir ese desglose antes de aplicar el cupón. Modificó `Cupon`, los dos cupones existentes (cambia el parámetro) y `CalculadoraPrecio`. Es la lección del principio: el diseño quedó cerrado respecto a los cambios que se anticiparon, no respecto a todos. Un cupón futuro que dependa del cliente (primer pedido) volvería a romper el contrato, salvo que `Desglose` exponga también el pedido, cosa que ya hace.

## 5. El orden de aplicación

En un solo lugar: `CalculadoraPrecio.precioFinal`, documentado en su comentario: (1) impuesto sobre la base, (2) recargos, (3) cupón calculado sobre el desglose ya armado. El cupón porcentual se calcula sobre la base antes de impuestos (así lo hacía el original: `base * 10 / 100`). Que el orden esté fijo en la calculadora es una decisión: si los cupones tuvieran que encadenarse o elegir su propio orden, haría falta una lista ordenada de "ajustes" y esa lista sería el nuevo punto de configuración.
