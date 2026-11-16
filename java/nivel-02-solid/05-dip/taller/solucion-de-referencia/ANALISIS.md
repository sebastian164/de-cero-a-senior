# Taller DIP — Suscripciones (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (7 pruebas, 0 fallos). `Main` hace la renovación del inicio (misma salida) y después las dos verificaciones del punto 3, que imprimen `OK` y terminan con código 1 si fallan.

## 1. Dependencias ocultas de `ServicioSuscripciones` (inicio)

1. `PasarelaPagosAcme`, creada con `new` dentro del servicio.
2. La clave `"clave-secreta-prod"`, escrita en el código: las pruebas cobrarían con la clave de producción y el secreto queda en el repositorio.
3. `ArchivoLog` y la ruta `/var/log/suscripciones.log`, que no existe ni es escribible en una máquina de desarrollo o de CI.
4. El formato de la línea del log (`fecha;usuario;valor;código`).
5. Detalles de la API de ACME en el negocio: moneda `"COP"` y 3 reintentos.
6. **La que no es una clase tuya: el reloj del sistema**, a través de `LocalDate.now()` (y con él, la zona horaria por defecto de la JVM). Hace que la fecha de vencimiento sea imposible de verificar: cambia cada día.

## 2. DIP aplicado

| Abstracción (del negocio) | Implementación (detalle) |
|---|---|
| `Cobros.cobrar(usuario, valor)` | `CobrosConAcme` → `PasarelaPagosAcme` (agrega `"COP"` y 3 reintentos) |
| `HistorialRenovaciones.registrar(fecha, usuario, valor, código)` | `HistorialEnArchivo` → `ArchivoLog` (arma la línea con `;`) |
| `java.time.Clock` | `Clock.systemDefaultZone()` en producción, `Clock.fixed(...)` en las verificaciones |

Las interfaces hablan de "cobrar una renovación" y "dejar constancia", no de "tarjeta guardada", "moneda" ni "escribir línea". `ServicioSuscripciones` ya no importa nada de ACME ni de archivos; la regla del descuento quedó en `valorPor`. Para `Clock` no se creó una interfaz propia: el JDK ya trae la abstracción correcta.

## 3. Verificaciones sin infraestructura

En `Main`, con dobles en lambdas:

- 12 meses: el doble de `Cobros` captura el valor cobrado y se comprueba que es 288.000 (360.000 con 20 % de descuento). Salida real: `OK    12 meses aplican 20 % de descuento`.
- Fecha de vencimiento: con `Clock.fixed(2026-01-31T12:00Z)` en la zona `America/Bogota`, una renovación de un mes vence el `2026-02-28` (fin de mes). Salida real: `OK    31 de enero + 1 mes vence el 28 de febrero`.

`Pruebas` agrega los bordes 11/12/24 meses, el contenido del historial y un caso de zona horaria: `2026-03-01T02:00Z` en Bogotá todavía es 28 de febrero, así que la zona también es parte de la dependencia del reloj.

## 4. Cambio de pasarela

Se tocan: un archivo nuevo `CobrosConNuevaPasarela.java` (el adaptador, que traduce `cobrar(usuario, valor)` a la API nueva), el cliente del proveedor nuevo, y `Main.java` (una línea en la raíz de composición). No se tocan `ServicioSuscripciones`, `Cobros`, `HistorialRenovaciones` ni sus implementaciones. `CobrosConAcme` y `PasarelaPagosAcme` se pueden borrar después.

## 5. La raíz de composición

`Main.main`: es el único lugar que hace `new` de adaptadores, conoce la clave (que ahora se lee de la variable de entorno `ACME_CLAVE`, con el valor original como respaldo para no cambiar la salida), la ruta del log y el reloj real. Si dos clases distintas empezaran a ensamblar objetos, habría dos fuentes de verdad sobre qué implementación se usa: por ejemplo, una parte del sistema cobraría con ACME y otra con la pasarela nueva después de una migración a medias, o cada una crearía su propio `ArchivoLog` y habría dos historiales; cambiar de implementación obligaría a buscar todos los `new`. Es exactamente lo que un contenedor como Spring centraliza.
