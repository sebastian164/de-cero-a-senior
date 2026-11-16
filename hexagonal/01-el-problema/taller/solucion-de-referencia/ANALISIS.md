# Taller 01 — Análisis de `Pedidos.java`

Solución de referencia. Se ejecutó con JDK 21.0.13 (`java Pedidos.java` y `Reproducir.java`, que se compila junto al
código de inicio sin modificarlo). `Reproducir` termina con código 1 si alguna afirmación de este análisis deja de ser cierta.

## Paso 1. La salida, línea a línea

```
  [correo a cliente-1] recibimos tu pedido pedido-1
201 {"id":"pedido-1","totalCentavos":36000000}
422 {"error":"sin existencias"}
422 {"error":"falta el cliente"}
```

| Línea | Qué pasó |
|---|---|
| `[correo a cliente-1] …` | La primera petición (2 teclados y 1 ratón) pasó todas las validaciones; se escribió en `pedidos.txt` y se "envió" el correo, que es un `println` dentro del manejador. Sale antes que el 201 porque el correo se imprime en el hilo del servidor antes de responder |
| `201 {"id":"pedido-1","totalCentavos":36000000}` | 2 × 15.000.000 + 1 × 6.000.000 = 36.000.000 centavos (360.000 pesos) |
| `422 sin existencias` | `cliente-2` pide 5 ratones; quedan 2 (3 iniciales menos el del pedido 1). Se rechaza antes de descontar |
| `422 falta el cliente` | El cliente va vacío. **Pero el ratón ya se descontó**: la línea se procesa (y descuenta) antes de mirar el cliente. Quedan 1 ratón, no 2 |

## Paso 2. Reglas de negocio (líneas de `taller/inicio/Pedidos.java`)

| Regla | Línea |
|---|---|
| Solo se venden productos del catálogo | 52–56 |
| La cantidad de una línea es al menos 1 | 57–61 |
| No se vende más de lo que hay en existencias | 62–65 |
| Vender descuenta existencias | 66 |
| El total es la suma de precio × cantidad | 67 |
| Un pedido necesita cliente | 72–75 |
| Un pedido tiene al menos una línea | 76–79 |
| El total no supera 5.000.000 de pesos (500.000.000 centavos) | 80–83 |
| Un pedido nuevo nace `PENDIENTE` | 86 |
| Al crear el pedido se avisa al cliente | 88 |
| Precios del catálogo (teclado, ratón, monitor) | 21 |
| Existencias iniciales | 22 |

## Paso 3. Lo que no es regla de negocio

| Qué | Dónde |
|---|---|
| Llega por HTTP (`HttpServer`, ruta `/pedidos`, método no comprobado) | 26–28, 39–40 |
| Formato de la petición: formulario `clave=valor&…`, líneas `producto:cantidad` | 45–51, 57 |
| Formato de la respuesta: JSON escrito a mano y códigos 201/422 | 54, 59, 63, 73, 77, 81, 89, 92–97 |
| De dónde salen los precios: un `Map` constante en memoria | 21 |
| De dónde salen las existencias: un `HashMap` estático mutable, compartido por todos los hilos | 22 |
| Generación del identificador: contador estático `secuencia`, sin sincronizar, se pierde al reiniciar | 23, 84 |
| Dónde se guarda: un archivo de texto en el directorio temporal, separado por `|` | 85–87 |
| De dónde sale la fecha: `LocalDateTime.now()` (hora local del servidor, sin zona) | 86 |
| Cómo se avisa: `System.out.println` simulando un correo | 88 |
| Cliente HTTP de prueba y el "programa" de ejemplo | 25–37, 99–104 |

## Paso 4. Probar "un pedido por encima del tope se rechaza" sin tocar el archivo

Obstáculos encontrados al intentarlo (`Reproducir.pasoCuatro`):

1. La regla solo se alcanza a través de HTTP: hay que levantar un servidor, armar un formulario y leer un JSON.
2. **Es inalcanzable con los datos del código.** El pedido más caro posible con las existencias iniciales es
   10 teclados + 3 ratones + 2 monitores = 348.000.000 centavos, por debajo del tope (500.000.000). La prueba ejecutó ese pedido
   y respondió `201`. Para probar el tope habría que cambiar `EXISTENCIAS` (estático, compartido) desde la prueba. `Reproducir.java` lo hace sin tocar `Pedidos.java`: pone 6 monitores, envía `monitor:6` y recibe `422 {"error":"supera el tope"}`, y las existencias quedan descontadas (el mismo defecto del paso 5). Con los datos de inicio la regla del tope es código muerto.
3. Cada petición escribe en `pedidos.txt` del directorio temporal y en la salida estándar: la prueba deja basura y ruido.
4. El estado es estático: las existencias y la secuencia sobreviven entre pruebas; el orden de ejecución cambia el resultado.
5. La fecha es `LocalDateTime.now()`: no se puede fijar.
6. Una cantidad que no es número (`raton:dos`) lanza `NumberFormatException` dentro del manejador: la conexión se cierra sin
   respuesta y el cliente recibe una `IOException`, no un 400.

## Paso 5. Las existencias que desaparecen

Salida de `Reproducir.pasoCinco`:

```
antes:   {raton=3, monitor=2, teclado=10}
teclado:2 + monitor:5 -> 422 {"error":"sin existencias"}
después: {raton=3, monitor=2, teclado=8}
sin cliente, raton:1 -> 422 {"error":"falta el cliente"}  existencias de raton: 2
raton:1 + lampara:1 -> 422 {"error":"producto desconocido"}  existencias de raton: 1
```

El defecto: el bucle **valida y descuenta línea a línea**. Cuando una línea posterior (o el cliente, o el tope) rechaza el
pedido, las líneas anteriores ya restaron sus unidades y nadie las devuelve. El pedido no existe, pero el inventario cree
que se vendieron dos teclados. No hay unidad de trabajo: cada `merge` es definitivo en el momento en que ocurre.

## Paso 6. Dos peticiones simultáneas por el último monitor

Recorrido a mano con `EXISTENCIAS.monitor = 1`:

| Paso | Petición A | Petición B | monitor |
|---|---|---|---|
| 1 | `EXISTENCIAS.get("monitor") < 1`? 1 < 1 es falso: sigue | | 1 |
| 2 | | `EXISTENCIAS.get("monitor") < 1`? 1 < 1 es falso: sigue | 1 |
| 3 | `merge(-1)` | | 0 |
| 4 | | `merge(-1)` | **-1** |

Las dos responden 201: se vendió un monitor que no existe. Además `HashMap` no es seguro entre hilos (un `merge` puede
perderse y dejar 0 con dos ventas) y `++secuencia` puede repetir un id.

Comprobado: el código de inicio usa `HttpServer` **sin ejecutor**, que atiende una petición tras otra en un solo hilo, así que
tal cual no muestra la carrera. `Reproducir.pasoSeis` registra el mismo manejador en un servidor con 8 hilos (como cualquier
servidor real) y lanza 1.000 pares de peticiones por el último monitor, con un único `HttpClient` (`Pedidos.enviar` crea uno
por petición y no lo cierra: con miles de carreras se agotan los hilos del sistema). En las ejecuciones de esta solución el
monitor se vendió dos veces en 121 y en 83 de las 1.000, con existencias finales de `-1` y también de `0` (la actualización perdida
del `HashMap`). El número cambia en cada ejecución; por eso `Reproducir` lo informa y no lo exige.

## Paso 7. Qué líneas hay que tocar para cada cambio

| Cambio | Líneas |
|---|---|
| Crear pedidos también desde un mensaje | Todo `crearPedido` (39–90): lectura del formulario, reglas, guardado, aviso y respuesta están mezclados; habría que copiarlo o partirlo |
| Guardar en una base de datos | 84–87, y también 22 y 62–66 si las existencias van a la base; en el mismo método que las reglas |
| Avisar por otro canal | 88 |
| Cambiar el tope | 80 (número mágico, sin nombre) |

## Paso 8. El dibujo

Está en [`DIBUJO.md`](DIBUJO.md).
