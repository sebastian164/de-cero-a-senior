# Taller n2-02 — OCP

| # | Solución | Prueba |
|---|---|---|
| 1 | `tabla.ts`: `TablaComponent<T>` recibe `filas` y descubre las columnas con `contentChildren(ColumnaDirective)`; cada columna es un `<ng-template appColumna="Encabezado" let-fila>` | `taller.spec.ts` 1; la página la usa con empleados y con productos |
| 2 | `notificaciones.ts` (cerrado) + `vistas-base.ts` (pedido, pago, alerta) + `vista-envio.ts` (cuarto tipo) | 2: se pinta el tipo `envio` y la prueba lee la fuente de `notificaciones.ts` para comprobar que no menciona ningún tipo |
| 3 | `tarjeta-producto.ts`: `TarjetaProductoConfigurableComponent` (siete entradas booleanas) frente a `TarjetaProductoComponent` (cuatro huecos con `ng-content`) | página: dos tarjetas distintas sin una sola bandera |

## 3. Qué cambió al pasar de banderas a proyección

La versión configurable tiene siete booleanos (`mostrarImagen`, `mostrarPrecio`, `mostrarDescripcion`, `mostrarBoton`, `conBorde`, `modoCompacto`, `destacado`): 128 combinaciones, y algunas se contradicen (`modoCompacto` oculta la imagen aunque `mostrarImagen` sea verdadero). Cada variante nueva (un sello de "agotado", dos botones) obliga a otra entrada y a otro `@if`. La versión por composición no sabe qué lleva: el que la usa proyecta la imagen, el nombre, el cuerpo y las acciones. "Compacta" es simplemente no proyectar la imagen.

## 4. Dónde un `@switch` habría sido mejor

En las **notificaciones**, si los tipos fueran tres estables y los mantuviera un solo equipo. El registro con `multi: true` y `NgComponentOutlet` agrega indirección (un token, un componente por tipo, la búsqueda en el registro) y el compilador ya no comprueba que `datos` tenga los campos que la vista lee (`datos()['numero']`). Con un `@switch (n.tipo)` y una unión discriminada, TypeScript estrecharía el tipo en cada rama. El registro se justifica cuando los tipos llegan de módulos cargados de forma diferida o los aportan equipos distintos, que es el caso que el taller simula con `vista-envio.ts`.

En la **tabla** y la **tarjeta** no: ahí la variación es de contenido, no de tipo, y la proyección es más simple que cualquier `switch`.
