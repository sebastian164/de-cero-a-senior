# Decisiones de diseño: motor de precios

**Composite para los combos (`Item` sellado con `Producto` y `Combo`).** Un combo contiene items, y un combo es un item: la recursión resuelve los anidados sin casos especiales. Es el problema real que justifica el patrón: sin él habría un `if` por nivel de anidamiento.

**El precio de un combo se reparte entre sus productos en proporción a su precio de lista.** Las promociones por producto (2x1, volumen) y los impuestos por categoría necesitan saber cuánto vale cada producto *dentro* del carrito. Con el precio de lista, un café de un combo con oferta pagaría más impuesto del que corresponde. Tres pruebas con totales calculados a mano fijan este comportamiento.

**Una promoción es una clase que implementa `Promocion` (Strategy).** Agregar una promoción es agregar una clase; el motor no cambia. El problema que lo justifica: las promociones cambian cada mes y no deben tocar el cálculo.

**El orden y las exclusiones viven en un solo lugar: la lista del `Motor` y el método `grupo()`.** Las promociones no saben de las demás. Cambiar el orden es reordenar una lista. Alternativa descartada: prioridades numéricas dentro de cada promoción, que esparcen el orden por todas las clases.

**Grupos excluyentes: gana la que más descuenta.** Es la regla más defendible para el cliente y no depende del orden. Un cupón y una promoción de porcentaje, por ejemplo, no se acumulan si comparten grupo.

**Cada promoción ve el estado ya rebajado (`Contexto.base`).** Los porcentajes se aplican sobre lo que queda, no sobre el subtotal original; por eso el orden importa y está probado.

**`BigDecimal` con redondeo `HALF_UP` a dos decimales, solo al final de cada descuento.** Los repartos intermedios conservan diez decimales para no acumular error.

**Todo es inmutable.** `Motor`, `Contexto` y los registros no cambian tras construirse: el mismo motor sirve a varios hilos.
