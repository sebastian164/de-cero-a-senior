# Taller 0 — Un pedido que se defiende solo (solución de referencia)

Cómo ejecutarla: `javac -d out *.java && java -cp out Main && java -cp out Pruebas`.
La demostración del diagnóstico se compila con el código de inicio (`../inicio/`):
`javac -d out2 diagnostico/DemoViolaciones.java ../inicio/*.java && java -cp out2 DemoViolaciones`.

## Parte 1 — Diagnóstico

### 1. Problemas de diseño

| Problema | Qué puede salir mal en un sistema real |
|---|---|
| Todos los campos de `Pedido` son públicos | Cualquier pantalla, job o integración deja el pedido en cualquier estado: subtotal negativo, cliente nulo, estado `"ENVIADO"` sin pago. |
| El constructor no recibe nada | Existen pedidos a medio construir; un `NullPointerException` aparece lejos de donde se creó el objeto (`pedido.estado.equals(...)` con estado nulo). |
| Tipo de cliente y estado como `String` | `"vip"`, `"Vip"` o `"PAGAD0"` compilan. El error aparece como un descuento que no se aplica o un pedido que no avanza, en producción. |
| Las reglas viven en `Main` (`calcularTotal`, `pagar`, `enviar`) | Otra clase que necesite el total o el envío las copia u olvida. Baja cohesión: datos en un sitio, reglas en otro. |
| Fallos silenciosos | `pagar` sobre un pedido ya pagado no hace nada; `enviar` sin pagar imprime en consola y sigue. Quien llama cree que la operación ocurrió. |
| Descuentos en una cadena de `if` | Cada tipo nuevo obliga a abrir `calcularTotal`; un tipo desconocido cobra precio completo sin avisar. |
| Dinero en `long` sin moneda | Aceptable para el ejercicio; en un sistema real faltaría modelar la moneda y el redondeo. |

### 2. Reglas que el código viola sin fallar

Las cuatro. `diagnostico/DemoViolaciones.java` lo demuestra ejecutando el código de inicio real:

1. Regla 1: `pedido.subtotal = -50_000;` y `pedido.cliente = null;` → total `-50000`.
2. Regla 2: `pedido.estado = "ENVIADO";` en un pedido recién creado.
3. Regla 3: `pedido.tipoCliente = "vip";` → paga 100000 en lugar de 90000.
4. Regla 4: `Main.pagar(pedidoEnviado);` no cambia nada y no avisa.

Salida real:

```
Regla 1 violada: cliente=null total=-50000
Regla 2 violada: nace en ENVIADO
Regla 3 violada: vip paga 100000 (esperado 90000)
Regla 4 violada: pagar un enviado deja el estado en ENVIADO sin error
```

### 3. Cliente mayorista en la versión actual

- `Main.calcularTotal`: agregar la rama `else if ("MAYORISTA")`.
- `Pedido.java`: actualizar el comentario de `tipoCliente` (el único lugar donde están documentados los valores válidos).
- Todo otro código que compare `tipoCliente` con textos (hoy ninguno más, pero nada lo garantiza: hay que buscar por texto en todo el proyecto).

## Parte 2 — Refactorización

| Criterio | Dónde se cumple |
|---|---|
| Imposible crear un pedido inválido | Constructor de `Pedido`: exige cliente no vacío, tipo no nulo y subtotal >= 0; no hay constructor vacío. |
| Imposible llevarlo a un estado inválido desde afuera | `estado` es privado; solo cambia con `pagar()` y `enviar()`, que delegan en `EstadoPedido.siguiente`. |
| Cada regla en un único lugar | Regla 1: constructor. Regla 2: `EstadoPedido.siguiente`. Regla 3: `TipoCliente`. Regla 4: las excepciones de esos tres lugares. |
| Mayorista sin tocar `Pedido` | Se agregó `MAYORISTA(15)` en `TipoCliente`; `Pedido` no cambió. |
| `Main` con casos válidos y tres o más inválidos | `Main` muestra cliente vacío, subtotal negativo, enviar sin pagar y pagar dos veces, todos rechazados. |
| Sin herencia para los descuentos | `TipoCliente` es un `enum` con un dato (porcentaje), no una jerarquía. |

`Pruebas.java` verifica 15 casos (descuentos de los cuatro tipos, transiciones válidas e inválidas, validaciones del constructor).

## Parte 3 — Defensa

1. **Decisiones.** Los descuentos son un `enum` con el porcentaje como dato porque hoy todos los descuentos son "un porcentaje del subtotal": el comportamiento es el mismo y solo cambia el número. La máquina de estados va en `EstadoPedido` para que la tabla de transiciones se lea en un solo método. Se usan excepciones (`IllegalArgumentException` para datos, `IllegalStateException` para transiciones) para cumplir la regla 4.
2. **Alternativa descartada.** Una interfaz `PoliticaDescuento` con una clase por tipo, como `PoliticaComision` en la lección. Se descartó porque los cuatro tipos difieren solo en un número; cuatro clases serían indirección sin beneficio. Si aparece un descuento con otra forma (tope máximo, escalonado), se cambia `TipoCliente` para que reciba una estrategia, y `Pedido` sigue sin tocarse.
3. **Lo discutible.** Agregar el mayorista modifica `TipoCliente` (es un `enum` cerrado). Si los tipos de cliente los configurara el negocio sin desplegar, el `enum` sería un error y haría falta una tabla. También es discutible que `EstadoPedido` tenga lógica: para tres estados un `switch` en `Pedido` sería igual de claro.
4. **Preguntas del paso 9.**
   1. Le preguntaría: ¿puedo dejar un objeto en un estado inválido desde afuera? ¿Dónde vive la regla X, en la clase de los datos o en un servicio? ¿Cuántos `if` por tipo hay? Si las clases son bolsas de getters y setters y las reglas viven en servicios estáticos, es programación procedural con la palabra `class`.
   2. No. Un setter público por campo equivale a un campo público: cualquiera escribe cualquier valor. Encapsular es que el objeto proteja sus invariantes; los setters sin validación las entregan.
   3. Se ganó: agregar comisiones sin tocar `Cuenta`, eliminar los textos mal escritos y probar cada política por separado. Se perdió: más archivos y una indirección para leer el cálculo. Habría dejado el `if` con dos tipos estables que nadie piensa cambiar, en un programa pequeño.
   4. Porque permite que la clase que usa la comisión dependa de un contrato (`PoliticaComision`) y no de las implementaciones. La dirección de la dependencia se invierte: las implementaciones nuevas no obligan a recompilar ni modificar a quien las usa. La reutilización es un efecto secundario.
   5. Ejemplo representativo (no proviene de un proyecto real del autor de esta solución): una entidad JPA `Prestamo` con `@Data` y un `PrestamoService` que calcula la cuota y valida que el monto no supere el cupo. Un job nocturno de reestructuración modificaba `setMonto` directamente sin pasar por el servicio, y quedaban préstamos por encima del cupo. Riesgo: la regla existe, pero solo para los caminos que se acuerdan de llamarla.
