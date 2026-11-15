# 08 — Kafka en un servicio hexagonal

> Requiere el módulo de [arquitectura hexagonal](../../../hexagonal/). El ejemplo de esta sesión se compila **junto con el núcleo del banco** de ese módulo, sin modificarlo.

## Idea

El enchufe de la pared no sabe si le conectas una lámpara o un cargador, y la lámpara no sabe si la electricidad viene de una represa o de un panel solar. Kafka, para un servicio bien armado, es eso: algo que está al otro lado de un enchufe. Tu servicio tiene un enchufe por donde salen hechos y otro por donde entran. Lo que hay detrás puede cambiar sin que las reglas se enteren.

## Lo esencial

### Dónde va cada pieza de Kafka

Todo lo que aprendiste en las sesiones 01 a 07 tiene un sitio exacto en la arquitectura, y ninguno está en el dominio.

| Pieza | Qué es en la arquitectura | Qué conoce de Kafka |
|---|---|---|
| El evento como hecho del negocio (`EventoDeCuenta`) | Un tipo de la aplicación | Nada |
| `PublicadorDeEventos` | Puerto de salida | Nada |
| La bandeja de salida | Adaptador de salida, sobre tu base de datos | Nada |
| El mensajero que lee la bandeja y publica | Adaptador de salida | **Todo**: tema, clave, serialización, confirmaciones |
| El consumidor | Adaptador de **entrada** | **Todo**: grupo, posiciones, reintentos, tema de fallidos |
| El caso de uso que atiende el evento | Aplicación, detrás de un puerto de entrada | Nada |
| La idempotencia del consumidor | Aplicación, con un puerto de salida que recuerda los eventos vistos | Nada |

Dos consecuencias prácticas:

- **El consumidor es un controlador.** Hace lo mismo que el adaptador HTTP: traduce un mensaje a una llamada a un puerto de entrada y traduce el resultado a "confirmo la posición" o "no la confirmo".
- **La idempotencia no es del adaptador.** Que un evento repetido no cuente dos veces es una regla que debe cumplirse llegue el evento por Kafka, por HTTP o por una prueba. Por eso vive en la aplicación.

### Dos servicios, un contrato

```
   BANCO                                          VIGILANCIA
   caso de uso ──► PublicadorDeEventos            ConsumidorDeTransferencias ──► RegistrarTransferencia
                        │ (bandeja de salida)              ▲ (adaptador de entrada)        │
                        ▼                                  │                               ▼
                   mensajero ──────►  tema: banco.transferencias  ─────────────►     caso de uso
```

Los dos servicios no comparten **ninguna clase**. Lo único que comparten es el formato del mensaje: ese es el contrato de la [sesión 05](../05-eventos-como-contrato/). En el ejemplo, el paquete `vigilancia` no importa nada de `banco`.

### Qué garantiza cada tramo

| Tramo | Garantía | Quién la da |
|---|---|---|
| Caso de uso → bandeja | El evento existe si y solo si la operación se confirmó | La transacción ([hexagonal 04](../../../hexagonal/04-transacciones/)) |
| Bandeja → tema | Al menos una vez | El mensajero: borra de la bandeja solo tras publicar |
| Tema → consumidor | Al menos una vez | El consumidor: confirma la posición solo tras procesar |
| Efecto en el receptor | Exactamente una vez | La idempotencia del caso de uso |
| Orden | Por clave | La clave del mensaje: misma cuenta, misma partición |

"Exactamente una vez" no es una propiedad del transporte. Es el resultado de sumar "al menos una vez" e idempotencia.

## Ejemplo

Java puro. El papel de Kafka lo hace `RegistroDeEventos`, un registro particionado en memoria con posiciones por grupo: suficiente para ver el comportamiento sin levantar un broker. El banco es el real del módulo hexagonal.

```
ejemplo/src/
  registro/RegistroDeEventos.java                            el doble de Kafka
  banco/adaptadores/salida/registro/PublicadorEnRegistro.java   adaptador de salida del banco
  vigilancia/dominio/EnviosDelDia.java                       la regla
  vigilancia/aplicacion/…                                    puertos y caso de uso
  vigilancia/adaptadores/…                                   consumidor y memoria
  Main.java                                                  composición de los dos servicios y demostración
```

### El doble de Kafka

<!-- file: ejemplo/src/registro/RegistroDeEventos.java -->
```java
package registro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lo mínimo de Kafka: temas con particiones, posiciones, y una posición confirmada por grupo y partición. */
public final class RegistroDeEventos {

    public record Mensaje(int particion, long posicion, String clave, String valor) {
    }

    private final int particiones;
    private final Map<String, List<List<Mensaje>>> temas = new HashMap<>();
    private final Map<String, long[]> confirmadas = new HashMap<>();

    public RegistroDeEventos(int particiones) {
        this.particiones = particiones;
    }

    /** La misma clave va siempre a la misma partición: así se conserva el orden por clave. */
    public synchronized Mensaje publicar(String tema, String clave, String valor) {
        int particion = Math.floorMod(clave.hashCode(), particiones);
        List<Mensaje> registro = tema(tema).get(particion);
        Mensaje mensaje = new Mensaje(particion, registro.size(), clave, valor);
        registro.add(mensaje);
        return mensaje;
    }

    /** Lo que el grupo aún no ha confirmado. Si no confirma, la próxima lectura devuelve lo mismo. */
    public synchronized List<Mensaje> leer(String grupo, String tema) {
        long[] desde = confirmadas(grupo, tema);
        List<Mensaje> pendientes = new ArrayList<>();
        for (int particion = 0; particion < particiones; particion++) {
            List<Mensaje> registro = tema(tema).get(particion);
            pendientes.addAll(registro.subList((int) desde[particion], registro.size()));
        }
        return pendientes;
    }

    public synchronized void confirmar(String grupo, String tema, Mensaje procesado) {
        confirmadas(grupo, tema)[procesado.particion()] = procesado.posicion() + 1;
    }

    private List<List<Mensaje>> tema(String nombre) {
        return temas.computeIfAbsent(nombre, nuevo -> {
            List<List<Mensaje>> registros = new ArrayList<>();
            for (int particion = 0; particion < particiones; particion++) {
                registros.add(new ArrayList<>());
            }
            return registros;
        });
    }

    private long[] confirmadas(String grupo, String tema) {
        return confirmadas.computeIfAbsent(grupo + "@" + tema, nuevo -> new long[particiones]);
    }
}
```

### El banco: del puerto al tema

El banco ya tenía el puerto `PublicadorDeEventos` y la bandeja de salida. Solo falta el destino del mensajero. Es **el único archivo del banco que sabe de temas y claves**.

<!-- file: ejemplo/src/banco/adaptadores/salida/registro/PublicadorEnRegistro.java -->
```java
package banco.adaptadores.salida.registro;

import banco.aplicacion.EventoDeCuenta;
import java.util.function.Consumer;
import registro.RegistroDeEventos;

/** El destino del mensajero de la bandeja de salida: traduce un evento del banco a un mensaje del tema. */
public final class PublicadorEnRegistro implements Consumer<EventoDeCuenta> {

    public static final String TEMA = "banco.transferencias";

    private final RegistroDeEventos registro;

    public PublicadorEnRegistro(RegistroDeEventos registro) {
        this.registro = registro;
    }

    @Override
    public void accept(EventoDeCuenta evento) {
        // La clave es la cuenta: todos los eventos de una cuenta quedan en orden, en la misma partición.
        registro.publicar(TEMA, evento.cuentaId(), serializar(evento));
    }

    /** El contrato entre servicios: texto con campos nombrados. El receptor ignora los que no conozca. */
    static String serializar(EventoDeCuenta evento) {
        return "id=" + evento.id() + ";tipo=" + evento.tipo() + ";cuenta=" + evento.cuentaId()
                + ";centavos=" + evento.centavos() + ";ocurridoEn=" + evento.ocurridoEn();
    }
}
```

### Vigilancia: otro servicio, otro hexágono

Regla: si una cuenta envía más de 500.000 pesos en un día, se avisa. Una vez, al cruzar el umbral.

<!-- file: ejemplo/src/vigilancia/dominio/EnviosDelDia.java -->
```java
package vigilancia.dominio;

import java.time.LocalDate;

/** Lo que una cuenta ha enviado en un día. Decide cuándo hay que avisar. */
public final class EnviosDelDia {

    public static final long UMBRAL_CENTAVOS = 50_000_000;       // 500.000 pesos

    private final String cuentaId;
    private final LocalDate dia;
    private long totalCentavos;

    public EnviosDelDia(String cuentaId, LocalDate dia, long totalCentavos) {
        this.cuentaId = cuentaId;
        this.dia = dia;
        this.totalCentavos = totalCentavos;
    }

    /** Suma un envío. Devuelve verdadero solo la vez que el total cruza el umbral. */
    public boolean registrar(long centavos) {
        boolean estabaDebajo = totalCentavos <= UMBRAL_CENTAVOS;
        totalCentavos += centavos;
        return estabaDebajo && totalCentavos > UMBRAL_CENTAVOS;
    }

    public String cuentaId() {
        return cuentaId;
    }

    public LocalDate dia() {
        return dia;
    }

    public long totalCentavos() {
        return totalCentavos;
    }
}
```

<!-- file: ejemplo/src/vigilancia/aplicacion/RegistrarTransferencia.java -->
```java
package vigilancia.aplicacion;

import java.time.Instant;

/** Puerto de entrada. No dice de dónde llega la transferencia: hoy de un tema, mañana de donde sea. */
public interface RegistrarTransferencia {

    record Transferencia(String idDelEvento, String cuentaId, long centavos, Instant ocurridaEn) {
    }

    enum Resultado {
        REGISTRADA,
        REPETIDA
    }

    Resultado registrar(Transferencia transferencia);
}
```

<!-- file: ejemplo/src/vigilancia/aplicacion/PuertosDeSalida.java -->
```java
package vigilancia.aplicacion;

import java.time.LocalDate;
import java.util.Optional;
import vigilancia.dominio.EnviosDelDia;

/** Lo que el caso de uso necesita del exterior. Agrupados en un archivo por brevedad. */
public final class PuertosDeSalida {

    private PuertosDeSalida() {
    }

    public interface EnviosPorCuenta {

        Optional<EnviosDelDia> buscar(String cuentaId, LocalDate dia);

        void guardar(EnviosDelDia envios);
    }

    public interface EventosVistos {

        /** Devuelve falso si ese evento ya se había atendido. */
        boolean registrarSiEsNuevo(String idDelEvento);
    }

    public interface Notificador {

        void avisar(String cuentaId, long totalCentavos);
    }
}
```

<!-- file: ejemplo/src/vigilancia/aplicacion/ServicioDeVigilancia.java -->
```java
package vigilancia.aplicacion;

import java.time.LocalDate;
import java.time.ZoneOffset;
import vigilancia.aplicacion.PuertosDeSalida.EnviosPorCuenta;
import vigilancia.aplicacion.PuertosDeSalida.EventosVistos;
import vigilancia.aplicacion.PuertosDeSalida.Notificador;
import vigilancia.dominio.EnviosDelDia;

/**
 * El caso de uso. En un servicio con base de datos, lo que hay dentro de registrar va en una unidad de
 * trabajo, como en el banco: marcar el evento como visto y sumar el envío se confirman juntos o no ocurren.
 */
public final class ServicioDeVigilancia implements RegistrarTransferencia {

    private final EnviosPorCuenta envios;
    private final EventosVistos vistos;
    private final Notificador notificador;

    public ServicioDeVigilancia(EnviosPorCuenta envios, EventosVistos vistos, Notificador notificador) {
        this.envios = envios;
        this.vistos = vistos;
        this.notificador = notificador;
    }

    @Override
    public Resultado registrar(Transferencia transferencia) {
        if (!vistos.registrarSiEsNuevo(transferencia.idDelEvento())) {
            return Resultado.REPETIDA;                           // el transporte entrega al menos una vez: aquí se descarta
        }
        LocalDate dia = LocalDate.ofInstant(transferencia.ocurridaEn(), ZoneOffset.UTC);
        EnviosDelDia delDia = envios.buscar(transferencia.cuentaId(), dia)
                .orElseGet(() -> new EnviosDelDia(transferencia.cuentaId(), dia, 0));

        boolean cruzoElUmbral = delDia.registrar(transferencia.centavos());   // la regla, en el dominio

        envios.guardar(delDia);
        if (cruzoElUmbral) {
            notificador.avisar(delDia.cuentaId(), delDia.totalCentavos());
        }
        return Resultado.REGISTRADA;
    }
}
```

### El consumidor: un adaptador de entrada

<!-- file: ejemplo/src/vigilancia/adaptadores/entrada/ConsumidorDeTransferencias.java -->
```java
package vigilancia.adaptadores.entrada;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import registro.RegistroDeEventos;
import registro.RegistroDeEventos.Mensaje;
import vigilancia.aplicacion.RegistrarTransferencia;
import vigilancia.aplicacion.RegistrarTransferencia.Resultado;
import vigilancia.aplicacion.RegistrarTransferencia.Transferencia;

/** Lee del tema, traduce cada mensaje a una llamada al puerto de entrada y confirma la posición. */
public final class ConsumidorDeTransferencias {

    private static final String TEMA = "banco.transferencias";   // el nombre es parte del contrato, no una clase compartida

    public record Lote(int registradas, int repetidas, int fallidas) {
    }

    private final RegistroDeEventos registro;
    private final String grupo;
    private final RegistrarTransferencia registrarTransferencia;
    private final List<Mensaje> fallidos = new ArrayList<>();

    public ConsumidorDeTransferencias(RegistroDeEventos registro, String grupo, RegistrarTransferencia registrarTransferencia) {
        this.registro = registro;
        this.grupo = grupo;
        this.registrarTransferencia = registrarTransferencia;
    }

    /**
     * Procesa lo pendiente. Con seCaeAntesDeConfirmar se simula que el proceso muere después de
     * procesar y antes de confirmar las posiciones: lo que ocurre en un despliegue o en una caída.
     */
    public Lote procesarLote(boolean seCaeAntesDeConfirmar) {
        int registradas = 0;
        int repetidas = 0;
        int fallidas = 0;
        for (Mensaje mensaje : registro.leer(grupo, TEMA)) {
            try {
                Resultado resultado = registrarTransferencia.registrar(traducir(mensaje.valor()));
                if (resultado == Resultado.REGISTRADA) {
                    registradas++;
                } else {
                    repetidas++;
                }
            } catch (IllegalArgumentException ilegible) {
                fallidos.add(mensaje);                           // reintentar no lo arregla: se aparta y se sigue
                fallidas++;
            }
            if (!seCaeAntesDeConfirmar) {
                registro.confirmar(grupo, TEMA, mensaje);        // solo después de procesar
            }
        }
        return new Lote(registradas, repetidas, fallidas);
    }

    public List<Mensaje> fallidos() {
        return List.copyOf(fallidos);
    }

    /** Lee los campos que necesita e ignora el resto: así el emisor puede añadir campos sin romper a nadie. */
    private static Transferencia traducir(String valor) {
        Map<String, String> campos = new HashMap<>();
        for (String par : valor.split(";")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2) {
                campos.put(partes[0], partes[1]);
            }
        }
        try {
            return new Transferencia(exigir(campos, "id"), exigir(campos, "cuenta"),
                    Long.parseLong(exigir(campos, "centavos")), Instant.parse(exigir(campos, "ocurridoEn")));
        } catch (RuntimeException malFormado) {
            throw new IllegalArgumentException("mensaje ilegible: " + valor, malFormado);
        }
    }

    private static String exigir(Map<String, String> campos, String nombre) {
        String valor = campos.get(nombre);
        if (valor == null) {
            throw new IllegalArgumentException("falta el campo " + nombre);
        }
        return valor;
    }
}
```

<!-- file: ejemplo/src/vigilancia/adaptadores/salida/VigilanciaEnMemoria.java -->
```java
package vigilancia.adaptadores.salida;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import vigilancia.aplicacion.PuertosDeSalida.EnviosPorCuenta;
import vigilancia.aplicacion.PuertosDeSalida.EventosVistos;
import vigilancia.dominio.EnviosDelDia;

public final class VigilanciaEnMemoria implements EnviosPorCuenta, EventosVistos {

    private final Map<String, EnviosDelDia> envios = new HashMap<>();
    private final Set<String> vistos = new HashSet<>();

    @Override
    public Optional<EnviosDelDia> buscar(String cuentaId, LocalDate dia) {
        return Optional.ofNullable(envios.get(cuentaId + "@" + dia))
                .map(guardado -> new EnviosDelDia(guardado.cuentaId(), guardado.dia(), guardado.totalCentavos()));
    }

    @Override
    public void guardar(EnviosDelDia delDia) {
        envios.put(delDia.cuentaId() + "@" + delDia.dia(), delDia);
    }

    @Override
    public boolean registrarSiEsNuevo(String idDelEvento) {
        return vistos.add(idDelEvento);
    }
}
```

### Los dos servicios, conectados

<!-- file: ejemplo/src/Main.java -->
```java
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.adaptadores.salida.registro.PublicadorEnRegistro;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import registro.RegistroDeEventos;
import registro.RegistroDeEventos.Mensaje;
import vigilancia.adaptadores.entrada.ConsumidorDeTransferencias;
import vigilancia.adaptadores.entrada.ConsumidorDeTransferencias.Lote;
import vigilancia.adaptadores.salida.VigilanciaEnMemoria;
import vigilancia.aplicacion.ServicioDeVigilancia;

/** Compone los dos servicios alrededor del registro y recorre lo que puede salir mal. */
public class Main {

    private static int fallos = 0;

    public static void main(String[] args) {
        RegistroDeEventos registro = new RegistroDeEventos(3);

        // ---- Servicio 1: el banco del módulo hexagonal, sin tocarle una línea ----
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, almacen,
                () -> Instant.parse("2026-11-15T10:00:00Z"), almacen);
        PublicadorEnRegistro haciaElTema = new PublicadorEnRegistro(registro);

        // ---- Servicio 2: vigilancia ----
        List<String> avisos = new ArrayList<>();
        VigilanciaEnMemoria memoria = new VigilanciaEnMemoria();
        ServicioDeVigilancia vigilancia = new ServicioDeVigilancia(memoria, memoria,
                (cuenta, total) -> avisos.add(cuenta + " ha enviado " + total / 100 + " pesos hoy"));
        ConsumidorDeTransferencias consumidor = new ConsumidorDeTransferencias(registro, "vigilancia", vigilancia);

        String ana = banco.abrir("Ana", 100_000_000).id();
        String luis = banco.abrir("Luis", 20_000_000).id();

        System.out.println("1. Tres transferencias; el mensajero vacía la bandeja de salida en el tema");
        banco.transferir(new Orden("orden-1", ana, luis, 30_000_000));
        banco.transferir(new Orden("orden-2", ana, luis, 15_000_000));
        banco.transferir(new Orden("orden-3", luis, ana, 5_000_000));
        comprobar("los tres eventos salen de la bandeja", almacen.entregarPendientes(haciaElTema) == 3);
        List<Mensaje> enElTema = registro.leer("un-lector-cualquiera", PublicadorEnRegistro.TEMA);
        enElTema.forEach(mensaje -> System.out.println("     partición " + mensaje.particion() + " posición "
                + mensaje.posicion() + " clave " + mensaje.clave()));
        comprobar("los dos eventos de Ana están en la misma partición, en orden",
                enElTema.stream().filter(mensaje -> mensaje.clave().equals(ana)).map(Mensaje::particion).distinct().count() == 1);

        System.out.println("2. El consumidor procesa y se cae antes de confirmar las posiciones");
        Lote primero = consumidor.procesarLote(true);
        comprobar("registró las tres", primero.registradas() == 3);
        comprobar("Ana va por 450.000 pesos: todavía no hay aviso", avisos.isEmpty());

        System.out.println("3. Arranca de nuevo: el registro le entrega otra vez lo mismo");
        Lote segundo = consumidor.procesarLote(false);
        comprobar("las tres llegan repetidas y se descartan", segundo.repetidas() == 3 && segundo.registradas() == 0);
        comprobar("sigue sin aviso: la repetición no sumó", avisos.isEmpty());

        System.out.println("4. La misma caída, en otro grupo cuyo caso de uso no recuerda lo que ya vio");
        List<String> avisosFalsos = new ArrayList<>();
        VigilanciaEnMemoria otraMemoria = new VigilanciaEnMemoria();
        ServicioDeVigilancia sinRecuerdo = new ServicioDeVigilancia(otraMemoria, idDelEvento -> true,
                (cuenta, total) -> avisosFalsos.add(cuenta + " ha enviado " + total / 100 + " pesos hoy"));
        ConsumidorDeTransferencias descuidado = new ConsumidorDeTransferencias(registro, "vigilancia-sin-recuerdo", sinRecuerdo);
        descuidado.procesarLote(true);
        descuidado.procesarLote(false);
        avisosFalsos.forEach(aviso -> System.out.println("     aviso falso: " + aviso));
        comprobar("avisa, aunque Ana solo ha enviado 450.000 pesos", avisosFalsos.size() == 1);

        System.out.println("5. Una cuarta transferencia cruza el umbral de verdad");
        banco.transferir(new Orden("orden-4", ana, luis, 10_000_000));
        almacen.entregarPendientes(haciaElTema);
        consumidor.procesarLote(false);
        comprobar("un solo aviso, con el total correcto", avisos.equals(List.of(ana + " ha enviado 550000 pesos hoy")));

        System.out.println("6. Llega un mensaje ilegible al tema");
        registro.publicar(PublicadorEnRegistro.TEMA, ana, "esto no es un evento");
        banco.transferir(new Orden("orden-5", luis, ana, 1_000_000));
        almacen.entregarPendientes(haciaElTema);
        Lote conBasura = consumidor.procesarLote(false);
        comprobar("el ilegible se aparta", conBasura.fallidas() == 1 && consumidor.fallidos().size() == 1);
        comprobar("y el siguiente se procesa", conBasura.registradas() == 1);
        comprobar("no queda nada pendiente para el grupo", consumidor.procesarLote(false).equals(new Lote(0, 0, 0)));

        System.out.println(fallos == 0 ? "Todo correcto" : fallos + " comprobaciones fallaron");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void comprobar(String que, boolean cumple) {
        System.out.println((cumple ? "   ok    " : "   FALLA ") + que);
        if (!cumple) {
            fallos++;
        }
    }
}
```

Desde `ejemplo/`:

```bash
javac -Xlint:all -Werror -d salida -sourcepath ../../../../hexagonal/proyecto/src $(find src -name '*.java')
java -cp salida Main
```

`-sourcepath` le dice al compilador dónde está el núcleo del banco. No se copia ni se modifica.

### Análisis

| Paso | Qué demuestra |
|---|---|
| 1 | El banco publica sin saber que existe un tema. La clave del mensaje la elige el adaptador |
| 2 y 3 | Confirmar después de procesar implica que una caída produce repeticiones, nunca pérdidas |
| 4 | Sin idempotencia en el caso de uso, la repetición se convierte en un aviso falso |
| 5 | Con ella, el efecto ocurre una sola vez |
| 6 | Un mensaje ilegible no detiene la partición: se aparta y se sigue |

Y lo que no aparece en ningún paso: `Cuenta`, `Transferencia` y `ServicioDeCuentas` no cambiaron. Añadir Kafka al banco fue añadir un archivo.

### Con Kafka de verdad

Los dos adaptadores, con el cliente real y con Spring. **Ilustrativo: no se compiló.** La estructura es la misma; cambia lo que hay dentro del adaptador.

```java
/** Destino del mensajero. Espera la confirmación del broker antes de dar el evento por entregado. */
public final class PublicadorKafka implements Consumer<EventoDeCuenta> {

    private final KafkaProducer<String, String> productor;   // con acks=all y enable.idempotence=true

    @Override
    public void accept(EventoDeCuenta evento) {
        try {
            productor.send(new ProducerRecord<>("banco.transferencias", evento.cuentaId(), serializar(evento))).get();
        } catch (InterruptedException | ExecutionException fallo) {
            throw new IllegalStateException("no se pudo publicar " + evento.id(), fallo);   // se queda en la bandeja
        }
    }
}
```

```java
/** Adaptador de entrada. La política de reintentos y el tema de fallidos se configuran en el contenedor. */
@Component
class ConsumidorDeTransferencias {

    private final RegistrarTransferencia registrarTransferencia;

    @KafkaListener(topics = "banco.transferencias", groupId = "vigilancia")
    void recibir(ConsumerRecord<String, String> mensaje) {
        registrarTransferencia.registrar(traducir(mensaje.value()));
        // Si esto lanza una excepción, la posición no se confirma y el manejador de errores decide.
    }
}
```

## Errores comunes

- `KafkaTemplate` o `KafkaProducer` inyectado en el servicio de aplicación.
- Publicar en el tema dentro de la transacción en lugar de escribir en la bandeja.
- Lógica de negocio dentro del método anotado con `@KafkaListener`.
- La deduplicación hecha en el adaptador con una caché en memoria: se pierde al reiniciar y no protege a otras entradas.
- Marcar el evento como visto en una transacción y aplicar su efecto en otra.
- Compartir entre servicios una biblioteca con las clases de los eventos: un cambio obliga a desplegar a todos a la vez.
- Elegir la clave del mensaje sin pensar en qué debe ir en orden.
- Confirmar la posición antes de procesar.

## Taller

**Objetivo:** conectar tu servicio de pedidos con un servicio de Inventario a través de Kafka real, sin modificar el dominio de ninguno de los dos.

Sobre tu servicio de pedidos del [módulo hexagonal](../../../hexagonal/) y el [entorno](../../entorno/) de esta ruta.

1. Tu servicio ya escribe eventos en una bandeja de salida. Escribe el destino del mensajero con el cliente de Kafka: tema, clave y serialización. Justifica la clave.
2. Demuestra que el dominio y la aplicación de pedidos no cambiaron: `git diff` de esos paquetes debe estar vacío.
3. Crea el servicio de Inventario como otro hexágono: dominio con la regla de reserva, un puerto de entrada para atender "pedido creado" y los puertos de salida que necesite. No debe compartir ninguna clase con pedidos.
4. Escribe el consumidor como adaptador de entrada. Decide qué excepciones se reintentan y cuáles van al tema de fallidos.
5. Prueba el caso de uso de Inventario sin Kafka: llamando al puerto de entrada desde una prueba.
6. Hazlo idempotente en la aplicación, con la marca de "evento visto" y la reserva en la misma transacción. Demuéstralo publicando el mismo evento tres veces.
7. Mata el consumidor después de procesar y antes de confirmar. Reinícialo y comprueba, con datos, que no se reservó dos veces.
8. Detén Kafka, crea tres pedidos y comprueba que la API sigue respondiendo y que los eventos esperan en la bandeja. Levanta Kafka y comprueba que llegan, en orden por pedido.
9. Añade un campo nuevo al evento sin tocar Inventario. Debe seguir funcionando.
10. Añade un segundo adaptador de entrada a Inventario para la misma operación, por ejemplo HTTP. ¿Cuánto código de negocio repetiste?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ninguna clase de Kafka fuera de los adaptadores | Búsqueda de `import org.apache.kafka` y `org.springframework.kafka` |
| Los dos servicios no comparten clases | Revisión de dependencias |
| Un evento repetido no reserva dos veces | Pasos 6 y 7 |
| Con Kafka caído no se pierde ningún evento | Paso 8 |
| El caso de uso de Inventario se prueba sin broker | Paso 5 |

**Entrega:** rama `taller/kafka-08`, con los dos servicios en `taller/solucion/` y `ANALISIS.md` con la evidencia de los pasos 7 y 8.

## En el trabajo real

Esta es la forma en que se ve un servicio con mensajería en un equipo que cuida la arquitectura: el paquete de mensajería es pequeño, está en el borde y es lo único que cambia si un día el broker es otro. Cuando revisas un servicio ajeno, buscar dónde se importa el cliente de Kafka te dice en un minuto cuánto de su lógica depende del transporte.

## Preguntas senior

1. ¿Por qué un consumidor de Kafka es un adaptador de entrada?
2. ¿En qué capa va la idempotencia del consumidor y por qué?
3. ¿Qué comparten dos servicios que se comunican por eventos y qué no deben compartir?
4. ¿De dónde sale el "exactamente una vez" si el transporte entrega al menos una vez?
5. ¿Quién elige la clave del mensaje y con qué criterio?
6. ¿Qué cambiarías en el servicio si mañana Kafka se sustituye por otro broker?
