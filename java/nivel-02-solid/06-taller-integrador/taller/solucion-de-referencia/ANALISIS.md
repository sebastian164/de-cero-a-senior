# Taller integrador de SOLID — Clínica veterinaria (solución de referencia)

| Carpeta | Contenido | Ejecución |
|---|---|---|
| `1-sin-diseno/` | Parte 1, una clase (78 líneas) | `java 1-sin-diseno/ClinicaVeterinaria.java` |
| `2-rediseno/` | Parte 3 (16 archivos, 263 líneas) | `javac -d out *.java && java -cp out Main` (5 verificaciones con dobles y luego el sistema con archivo) |
| `3-con-cambios/` | Parte 4: el rediseño con los cinco cambios aplicados | ídem, más `java -cp out Pruebas` (32 pruebas, 0 fallos) |

Las dos versiones imprimen el mismo reporte para el mismo escenario (`Total facturado: 505000`, 3 líneas en el archivo). Puedes comprobar además que el conjunto de archivos que da `diff -rq 2-rediseno 3-con-cambios` es igual al conjunto de archivos que nombra la tabla de la Parte 4.

## Parte 2 — Diagnóstico (líneas de `1-sin-diseno/ClinicaVeterinaria.java`)

**SRP — se viola.** `registrarCita` (20-48) responde a cuatro actores: la tarifa (22-25, 36: administración), la regla clínica de la cirugía (26-35: veterinarios), la persistencia (40-46: infraestructura) y la comunicación (47: atención al cliente). `imprimirReporte` (50-60) mezcla el cálculo del total con la salida por consola (52, 55, 59).

**OCP — se viola.** Un tipo nuevo obliga a abrir la cadena `if/else` (22-39); un canal nuevo de aviso, la línea 47; otro almacenamiento, las líneas 42-46. Nada se extiende sin modificar.

**LSP — no se viola,** porque no hay herencia ni implementaciones de un contrato: no existe ningún subtipo que pueda incumplirlo. (El `String[]` posicional de la línea 40 es otro problema: un contrato implícito entre las líneas 29, 40 y 55 que el compilador no verifica.)

**ISP — no se viola en sentido estricto,** porque no hay interfaces. Hay una versión latente: la clase pública ofrece registrar e imprimir reportes a todo cliente, y quien solo quiere el reporte depende de `FileWriter` y del correo.

**DIP — se viola.** La regla de negocio depende directamente de detalles: `FileWriter` y la ruta (42), `System.out` como canal de correo (47) y como salida del reporte (52-59), y un `String` como tipo (22-26). No hay ninguna abstracción, así que es imposible verificar la regla de la cirugía sin escribir un archivo.

## Parte 3 — Rediseño

```
                         Main (raíz de composición)
                          │ crea y conecta
        ┌─────────────────┼───────────────────┐
        ▼                 ▼                   ▼
   AgendaCitas      ReporteDiario       adaptadores (abajo)
    │   │   │             │
    │   │   └──────┐      │
    │   │          ▼      ▼
    │   │       RepositorioCitas ◄──implementa── CitasEnArchivo
    │   │                        ◄──implementa── CitasEnMemoria (doble)
    │   ▼
    │  Notificador ◄──implementa── NotificadorCorreo
    │              ◄──implementa── NotificacionesEnMemoria (doble)
    ▼
  TipoCita ──► Requisito ◄──implementa── RequisitoConsultaPrevia
    ▲
  TiposDeCita (catálogo: precio y requisitos por tipo)
  Cita, Dueno, CitaRechazada (valores del dominio)
```

Todas las flechas de los adaptadores (`CitasEnArchivo`, `NotificadorCorreo`) apuntan hacia las interfaces del negocio (`RepositorioCitas`, `Notificador`); el negocio no importa `java.nio` ni la consola (los dobles `CitasEnMemoria` y `NotificacionesEnMemoria` también implementan esas interfaces).

- Ninguna regla depende de archivos ni de correo: `AgendaCitas`, `Requisito`, `TiposDeCita` y `ReporteDiario` no importan nada de E/S. `ReporteDiario` devuelve texto.
- Las reglas se verifican desde `Main` con `CitasEnMemoria` y `NotificacionesEnMemoria` (salida real: cinco líneas `OK`).

## Parte 4 — La prueba del cambio

Salida real de `diff -rq 2-rediseno 3-con-cambios`, repartida por cambio:

| Cambio | Archivos nuevos | Archivos existentes modificados |
|---|---|---|
| 1. Peluquería 35.000 | — | `TiposDeCita` |
| 2. WhatsApp a elección | `CanalAviso`, `NotificadorWhatsApp`, `NotificadorSegunPreferencia` | `Dueno` (teléfono y canal, con un constructor de compatibilidad para no tocar a quienes ya creaban dueños), `CitasEnArchivo` (guarda y relee teléfono y canal), `Main` |
| 3. Base de datos simulada | `CitasEnBaseDeDatos` (ya nace guardando teléfono y canal) | `Main` |
| 4. Vacunas cada más de 30 días | `RequisitoIntervaloVacunas` | `TiposDeCita` |
| 5. 10 % con más de cinco citas en el año | — | `RepositorioCitas`, `CitasEnArchivo`, `CitasEnMemoria`, `AgendaCitas` (y `CitasEnBaseDeDatos`, que ya nació con el método) |
| Pruebas de los cinco cambios | `Pruebas` | — |

Los cambios 1, 3 y 4 tocaron como mucho dos clases existentes. **El 2 tocó tres y el 5 tocó cuatro.**

Supuesto que falló en el cambio 2: "el dueño es un dato de la cita". En el rediseño, `Dueno` no es una entidad con su propio repositorio: viaja embebido en cada `Cita` y cada adaptador lo aplana en la fila (nombre y correo). Por eso agregarle teléfono y canal obligó a abrir `CitasEnArchivo`; si no se hace, el adaptador relee al dueño con `new Dueno(nombre, correo)` y un dueño que eligió WhatsApp vuelve como CORREO (pérdida de datos silenciosa, que la primera versión de esta solución tenía y que ahora fija la prueba de ida y vuelta de `Pruebas` para cada adaptador). La alternativa habría sido un `RepositorioDuenos` y guardar en la cita solo el correo como identificador; con un dueño de dos campos era especular, y con el tercer campo empieza a pagarse. El formato del archivo quedó compatible: las líneas de 6 campos escritas antes del cambio se siguen leyendo, con canal CORREO.

Supuestos que fallaron en el cambio 5:

1. "El precio de una cita depende solo de su tipo". `TipoCita.precio()` se usaba directamente en `AgendaCitas`; no había una abstracción de precio porque no había ninguna razón para ella. El descuento obligó a calcular el precio con datos del dueño.
2. "El repositorio se consulta por mascota y por día". El descuento necesita una consulta nueva (citas de un dueño en un año), y agregar un método a una interfaz obliga a modificar todas sus implementaciones. Es el costo de un puerto de repositorio con consultas específicas.

¿Debió anticiparse? No con lo que se sabía: crear una `PoliticaDePrecio` y un repositorio con consultas genéricas el primer día habría sido especular. Ahora que hay dos reglas de precio (tarifa y fidelidad), el siguiente cambio de precio sí justificaría extraer `PoliticaDePrecio` de `AgendaCitas`.

Supuestos de interpretación: "más de cinco citas en el año" cuenta las citas ya registradas del dueño en el año calendario de la nueva cita, así que el descuento empieza en la séptima; "más de 30 días" significa que a los 30 días exactos se rechaza y a los 31 se acepta, y la distancia se mide contra cualquier vacuna del historial de la mascota, también contra una ya agendada en el futuro (una vacuna el 5 de junio se rechaza si ya hay otra el 10 de junio). `RequisitoIntervaloVacunas` recibe por constructor el nombre del tipo que cuenta como vacuna, en lugar de compararlo con un literal.

## Parte 5 — Contra el sobrediseño

1. **Abstracción con una sola implementación de producción:** `Notificador` en `2-rediseno` (solo `NotificadorCorreo`). Se defiende porque el requisito dice "Debe ser posible verificar las reglas con dobles en memoria": tiene una segunda implementación real, el doble, y además el cambio 2 la usó sin tocar `AgendaCitas`. `Requisito` en `2-rediseno` también tenía una sola (`RequisitoConsultaPrevia`); fue la apuesta más especulativa y la que el cambio 4 justificó.
2. **Dónde no se aplicó un principio:** `TiposDeCita` es un catálogo cerrado (constantes), no algo que se extienda sin modificarlo: agregar peluquería modifica ese archivo. OCP se aplicó a los requisitos (clases nuevas), no al catálogo, porque los tipos de cita los decide la clínica pocas veces al año y un solo archivo con todos los precios es más fácil de revisar. Tampoco se segregó `RepositorioCitas` por cliente (ISP): `ReporteDiario` solo usa `delDia`, pero partirla habría duplicado los adaptadores para una ganancia nula.
3. **Una clínica, seis meses y a la basura:** entregaría una versión intermedia: la de `1-sin-diseno` con dos arreglos baratos (un `enum` para el tipo en lugar del texto y separar el cálculo del reporte de la impresión) y unas pocas pruebas. El rediseño cuesta más del triple de líneas (263 frente a 78) y su beneficio (cambios locales, verificación con dobles) se cobra con el tiempo y con muchos cambios; en seis meses y sin evolución prevista no se recupera. La decisión cambia si los "seis meses" no son una promesa firme: muchos sistemas desechables siguen en producción años después.
