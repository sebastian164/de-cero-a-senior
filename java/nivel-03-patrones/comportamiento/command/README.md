# Command

Familia: comportamiento. Frecuencia en Java empresarial: media-alta (como comandos de caso de uso y tareas en cola).

## El problema

Quieres tratar una **acción** como un dato: guardarla, encolarla, ejecutarla más tarde, reintentarla, registrarla en una bitácora o deshacerla. Una llamada directa a un método ocurre y desaparece; no se puede almacenar ni revertir.

## Cómo reconocer que lo necesitas

- Hay que deshacer y rehacer.
- Las operaciones se encolan, se programan o se envían a otro proceso.
- Se necesita auditoría de qué se pidió, por quién y cuándo.
- Quien dispara la acción (un botón, un mensaje, un planificador) no debe conocer a quien la ejecuta.

## Antes

```java
void alPulsarBoton(String boton) {
    if (boton.equals("NEGRITA")) documento.aplicarNegrita();
    else if (boton.equals("BORRAR")) documento.borrarSeleccion();
    // ¿y cómo se deshace lo último?
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.ArrayDeque;
import java.util.Deque;

public class Main {

    public static void main(String[] args) {
        Documento documento = new Documento();
        Historial historial = new Historial();

        historial.ejecutar(new Escribir(documento, "Hola"));
        historial.ejecutar(new Escribir(documento, " mundo"));
        System.out.println(documento.texto());

        historial.deshacer();
        System.out.println(documento.texto());
    }
}

/** La acción convertida en objeto. */
interface Comando {
    void ejecutar();
    void deshacer();
}

/** El receptor: quien realmente sabe hacer el trabajo. */
class Documento {
    private final StringBuilder contenido = new StringBuilder();

    void agregar(String texto) { contenido.append(texto); }
    void quitarUltimos(int cantidad) { contenido.setLength(contenido.length() - cantidad); }
    String texto() { return contenido.toString(); }
}

class Escribir implements Comando {

    private final Documento documento;
    private final String texto;

    Escribir(Documento documento, String texto) {
        this.documento = documento;
        this.texto = texto;
    }

    @Override public void ejecutar() { documento.agregar(texto); }
    @Override public void deshacer() { documento.quitarUltimos(texto.length()); }
}

/** El invocador: ejecuta comandos sin saber qué hacen y recuerda el orden. */
class Historial {

    private final Deque<Comando> ejecutados = new ArrayDeque<>();

    void ejecutar(Comando comando) {
        comando.ejecutar();
        ejecutados.push(comando);
    }

    void deshacer() {
        if (!ejecutados.isEmpty()) {
            ejecutados.pop().deshacer();
        }
    }
}
```

## Alternativas

- **Una lambda** (`Runnable`, `Supplier`), si solo hay que ejecutar más tarde y no deshacer ni serializar.
- **Llamada directa**, si la acción es inmediata y no se guarda.
- **Memento**, si deshacer es más fácil restaurando un estado anterior que invirtiendo la operación.

## En Spring y en Java

- `Runnable` y `Callable` enviados a un `ExecutorService` son comandos.
- **Comandos de caso de uso**: un objeto `CrearPedidoCommand` con los datos de la intención y un manejador que lo procesa. Es la base de CQRS (Nivel 5).
- Mensajes en una cola (RabbitMQ, SQS) que representan trabajo por hacer; trabajos de Spring Batch y tareas `@Scheduled`.
- `TransactionCallback` en `TransactionTemplate`.

## Trade-offs

- A favor: desacopla quien pide de quien ejecuta; permite colas, reintentos, auditoría y deshacer; los comandos se combinan en macros.
- En contra: una clase por acción; deshacer bien es difícil cuando hay efectos externos (un correo enviado no se "desenvía"); los comandos persistidos deben versionarse.

## Errores comunes

- Comandos que contienen la lógica de negocio completa en lugar de delegar en el receptor.
- `deshacer` que asume un estado que ya cambió.
- Comandos serializados con referencias a objetos vivos en memoria.
- Confundir comando (una orden, puede rechazarse) con evento (un hecho, ya ocurrió).

## Cuándo no usarlo

Cuando las acciones se ejecutan de inmediato y nadie las guarda; cuando una lambda es suficiente.

## Taller

Una calculadora de saldos de cuentas soporta `depositar`, `retirar` y `transferir`.

1. Implementa cada operación como comando con `deshacer`.
2. Agrega rehacer. ¿Qué pasa con la pila de rehacer cuando se ejecuta un comando nuevo?
3. Crea un comando macro que agrupe varios y se deshaga completo, en orden inverso.
4. Si `retirar` falla por fondos insuficientes en mitad de un macro, ¿qué debe ocurrir con los comandos anteriores?
5. Diferencia, con un ejemplo, `ConfirmarPedido` (comando) de `PedidoConfirmado` (evento).

Entrega: rama `taller/n3-command`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
