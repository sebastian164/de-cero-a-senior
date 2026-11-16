# Prototype — análisis

1. **Copia superficial**: `Pedido.copiaSuperficial()` copia las referencias. Al cambiar la ciudad o la cantidad en el pedido "repetido", el original cambia también (`copiaSuperficialCorrompeElOriginal`). En producción: el cliente corrige la dirección del pedido nuevo y se despacha a esa dirección el pedido viejo.

2. **Copia correcta**: `Pedido.repetir(numeroNuevo, hoy)` copia en profundidad la dirección, la lista y cada ítem. **No** se copian:
   - el número: es la identidad; dos pedidos con el mismo número rompen la contabilidad;
   - la fecha: un pedido repetido se crea hoy;
   - el estado: el nuevo no está pagado ni enviado, empieza en `CREADO`.

3. **Valores inmutables** (`DireccionInmutable`, `ItemInmutable`, `PedidoConValores`): desaparecieron los dos constructores de copia y el bucle que copia ítems. `repetir` se reduce a una línea que comparte la dirección y la lista (la prueba usa `assertSame`): es seguro porque nadie puede modificarlas; los cambios devuelven un valor nuevo (`conCiudad`, `conCantidad`).

4. **`clone()`**: `Object.clone()` hace copia superficial y no pasa por ningún constructor. `ConClone.demostrar()` lo muestra: después de `b = a.clone()`, `b.etiquetas.add("frágil")` aparece en `a`, porque ambas comparten la misma lista. Además obliga a capturar `CloneNotSupportedException` (comprobada) y a implementar la interfaz marcadora `Cloneable`, que no declara `clone()`. Para corregirlo hay que reasignar campos `final`, lo que es imposible: hay que quitar el `final`.
