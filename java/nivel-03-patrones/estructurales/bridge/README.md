# Bridge

Familia: estructural. Frecuencia en Java empresarial: media (a menudo sin nombrarlo).

## El problema

Un concepto varía en **dos dimensiones independientes**. Con herencia, cada combinación exige una clase: 3 tipos de aviso × 3 canales = 9 clases, y agregar un canal suma 3 más. Es la explosión de subclases de la lección 06 del Nivel 1, con nombre de patrón.

## Cómo reconocer que lo necesitas

- Los nombres de las clases combinan dos ideas: `AlertaUrgentePorSms`, `RecordatorioPorCorreo`.
- Agregar una variante en una dimensión obliga a crear varias clases.
- Quieres cambiar una dimensión en ejecución sin tocar la otra.

## Antes

```java
class AlertaPorCorreo { ... }
class AlertaPorSms { ... }
class RecordatorioPorCorreo { ... }
class RecordatorioPorSms { ... }
// agregar WhatsApp: dos clases más. Agregar "Promoción": tres más.
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Canal sms = (destino, texto) -> System.out.println("[SMS " + destino + "] " + texto);
        Canal correo = (destino, texto) -> System.out.println("[Correo " + destino + "] " + texto);

        new Alerta(sms).enviar("3001234567", "Intento de acceso sospechoso");
        new Alerta(correo).enviar("ana@correo.example", "Intento de acceso sospechoso");
        new Recordatorio(sms).enviar("3001234567", "Tu cita es mañana a las 9:00");
    }
}

/** Dimensión 2 (la "implementación"): por dónde se envía. */
interface Canal {
    void transmitir(String destino, String texto);
}

/** Dimensión 1 (la "abstracción"): qué tipo de aviso es. Tiene un canal: ese es el puente. */
abstract class Aviso {

    private final Canal canal;

    protected Aviso(Canal canal) {
        this.canal = canal;
    }

    final void enviar(String destino, String mensaje) {
        canal.transmitir(destino, formatear(mensaje));
    }

    protected abstract String formatear(String mensaje);
}

class Alerta extends Aviso {
    Alerta(Canal canal) { super(canal); }
    @Override protected String formatear(String mensaje) { return "URGENTE: " + mensaje.toUpperCase(); }
}

class Recordatorio extends Aviso {
    Recordatorio(Canal canal) { super(canal); }
    @Override protected String formatear(String mensaje) { return "Recuerda: " + mensaje; }
}
```

Con 3 tipos y 3 canales hay 6 piezas en lugar de 9, y cada dimensión crece sin tocar la otra.

## Bridge frente a patrones parecidos

- **Strategy** tiene la misma forma. La diferencia es de intención: Strategy intercambia un algoritmo; Bridge separa dos jerarquías que evolucionan por su cuenta.
- **Adapter** se aplica después, para hacer encajar algo que ya existe. Bridge se diseña antes, para que dos dimensiones no se enreden.

## Alternativas

- **Composición simple con una interfaz** (como en la lección 06 del Nivel 1): suele bastar sin llamarlo Bridge.
- **Herencia**, si una de las dimensiones tiene un solo valor y no se espera otro.

## En Spring y en Java

- **JDBC**: tu código usa `Connection` y `Statement`; cada motor aporta su *driver*. La API y los drivers evolucionan por separado.
- **SLF4J** (API) y Logback o Log4j (implementación).
- La abstracción de caché de Spring (`CacheManager`) sobre Caffeine, Redis o Ehcache; `PlatformTransactionManager` sobre JPA, JDBC o JMS.

## Trade-offs

- A favor: evita la explosión combinatoria; cada dimensión se extiende y se prueba sola; se puede cambiar la implementación en ejecución.
- En contra: más abstracto y difícil de explicar; es sobrediseño si solo hay una dimensión real; hay que acertar cuáles son las dimensiones.

## Errores comunes

- Aplicarlo cuando una dimensión es ficticia.
- Abstracciones que necesitan conocer la implementación concreta (`if (canal instanceof Sms)`): las dimensiones no eran independientes.
- Una interfaz de implementación demasiado específica de un caso.

## Cuándo no usarlo

Cuando solo hay una dimensión de variación; cuando las combinaciones son pocas y fijas.

## Taller

Un sistema de reportes tiene tipos de reporte (ventas, inventario, cartera) y formatos de salida (PDF, Excel, pantalla). Simula los formatos imprimiendo texto.

1. Impleméntalo primero con una clase por combinación y cuenta las clases.
2. Rediseña con Bridge.
3. Agrega un tipo de reporte y un formato. ¿Cuántas clases creaste y cuántas modificaste?
4. El reporte de cartera necesita colores que solo PDF y Excel soportan. ¿Tu diseño lo aguanta? ¿Qué te dice eso sobre la independencia de las dimensiones?

Entrega: rama `taller/n3-bridge`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
