# DIP — Principio de inversión de dependencias

Requisito: [ISP](../04-isp/).

## 1. Explicación sencilla

Una lámpara no va soldada a la red eléctrica de la casa. Termina en un enchufe, y la pared tiene una toma. La lámpara no sabe si la energía viene de una hidroeléctrica, de paneles solares o de una planta de emergencia; la casa no sabe si conectarás una lámpara o un ventilador. Ambos conocen una sola cosa: la forma del enchufe.

Ese estándar es lo que permite cambiar cualquiera de los dos lados sin tocar el otro. El principio pide que las partes importantes del código se conecten con las partes cambiantes a través de un "enchufe" definido, y no directamente.

## 2. Definición técnica

1. Los módulos de alto nivel no deben depender de módulos de bajo nivel. Ambos deben depender de abstracciones.
2. Las abstracciones no deben depender de los detalles. Los detalles deben depender de las abstracciones.

- **Alto nivel:** las reglas del negocio, lo que le da valor al sistema. Cambia cuando cambia el negocio.
- **Bajo nivel:** los detalles técnicos: base de datos, correo, archivos, APIs externas, frameworks. Cambian por razones técnicas.

### Por qué "inversión"

En un diseño ingenuo, el servicio de negocio llama al repositorio y por tanto **depende** de él: la dependencia del código sigue la dirección de la llamada. Con DIP, el negocio define una interfaz que expresa lo que necesita y la infraestructura la implementa. La llamada sigue yendo de negocio a infraestructura, pero la dependencia del código fuente apunta al revés: la infraestructura depende del negocio.

```
Antes:   ServicioPedidos ──────────► RepositorioMySql

Después: ServicioPedidos ──► RepositorioPedidos ◄── RepositorioMySql
         (negocio)           (interfaz, del negocio)   (infraestructura)
```

**La interfaz pertenece a quien la usa**, no a quien la implementa. Se llama `RepositorioPedidos`, no `ClienteMySql`, y habla el lenguaje del negocio.

### Cuatro términos que se confunden

| Término | Qué es |
|---|---|
| Inversión de dependencias (DIP) | Un principio de diseño: hacia dónde apuntan las dependencias. |
| Inversión de control (IoC) | Un estilo: el framework llama a tu código, y no al revés. |
| Inyección de dependencias (DI) | Una técnica: un objeto recibe sus colaboradores en lugar de crearlos. |
| Contenedor de IoC | Una herramienta que crea objetos y los inyecta. Spring es uno. |

Se puede usar Spring e inyectar todo y aun así violar DIP: basta inyectar clases concretas de infraestructura en el negocio.

## 3. Código que viola el principio

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        ServicioPedidos servicio = new ServicioPedidos();
        servicio.confirmar("P-100", "ana@correo.example", 180_000);
    }
}

class ServicioPedidos {

    private final RepositorioMySql repositorio = new RepositorioMySql("jdbc:mysql://prod-db:3306/tienda");
    private final ClienteSmtp correo = new ClienteSmtp("smtp.empresa.example", 587);

    void confirmar(String numero, String correoCliente, long valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("El valor debe ser positivo");
        }
        repositorio.ejecutarInsert("INSERT INTO pedidos VALUES ('" + numero + "', " + valor + ")");
        correo.enviarMime(correoCliente, "Pedido " + numero + " confirmado", "text/plain");
    }
}

class RepositorioMySql {
    private final String url;
    RepositorioMySql(String url) { this.url = url; }
    void ejecutarInsert(String sql) { System.out.println("[" + url + "] " + sql); }
}

class ClienteSmtp {
    private final String servidor;
    private final int puerto;
    ClienteSmtp(String servidor, int puerto) { this.servidor = servidor; this.puerto = puerto; }
    void enviarMime(String destino, String asunto, String tipo) {
        System.out.println("[" + servidor + ":" + puerto + "] a " + destino + ": " + asunto);
    }
}
```

## 4. Por qué es un problema

- **La regla de negocio no se puede probar sin una base de datos y un servidor de correo.** `confirmar` tiene una sola regla (el valor debe ser positivo) y para verificarla hay que conectarse a producción.
- **El negocio conoce detalles técnicos:** SQL, la URL de la base, el puerto SMTP, el tipo MIME. Cambiar MySQL por PostgreSQL obliga a editar la clase que decide cómo se confirma un pedido.
- **La configuración está incrustada.** No hay forma de usar otra base en desarrollo o en pruebas.
- **La dependencia apunta de lo estable a lo volátil.** Las reglas de un pedido cambian poco; los proveedores de infraestructura cambian más. Lo que debería ser la parte más protegida es la más expuesta.
- **Los `new` dentro de la clase ocultan sus dependencias.** Nada en la firma de `ServicioPedidos` avisa que necesita una base de datos.

## 5. Refactorización paso a paso

**Paso 1 — Preguntar qué necesita el negocio, en su propio idioma.** No "ejecutar un insert", sino "guardar un pedido". No "enviar MIME por SMTP", sino "avisar al cliente".

**Paso 2 — Declarar esas necesidades como interfaces del negocio.**

**Paso 3 — El servicio recibe sus colaboradores por el constructor.**

**Paso 4 — La infraestructura implementa las interfaces.**

**Paso 5 — Un único punto del programa conoce las clases concretas y ensambla.**

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Raíz de composición: el único lugar que conoce las clases concretas.
        ServicioPedidos produccion = new ServicioPedidos(
                new RepositorioPedidosMySql("jdbc:mysql://prod-db:3306/tienda"),
                new NotificadorCorreo("smtp.empresa.example", 587));
        produccion.confirmar(new Pedido("P-100", "ana@correo.example", 180_000));

        // La misma regla de negocio, probada sin base de datos ni correo.
        RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
        NotificadorEspia notificador = new NotificadorEspia();
        ServicioPedidos enPrueba = new ServicioPedidos(repositorio, notificador);
        enPrueba.confirmar(new Pedido("P-200", "luis@correo.example", 90_000));
        System.out.println("Guardados: " + repositorio.guardados.size()
                + ", avisos: " + notificador.avisos);
    }
}

// ---------- Negocio (alto nivel) ----------

class Pedido {
    final String numero;
    final String correoCliente;
    final long valor;

    Pedido(String numero, String correoCliente, long valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("El valor debe ser positivo");
        }
        this.numero = numero;
        this.correoCliente = correoCliente;
        this.valor = valor;
    }
}

/** Lo que el negocio necesita, dicho en su idioma. La interfaz es del negocio. */
interface RepositorioPedidos {
    void guardar(Pedido pedido);
}

interface Notificador {
    void pedidoConfirmado(Pedido pedido);
}

class ServicioPedidos {

    private final RepositorioPedidos repositorio;
    private final Notificador notificador;

    ServicioPedidos(RepositorioPedidos repositorio, Notificador notificador) {
        this.repositorio = repositorio;
        this.notificador = notificador;
    }

    void confirmar(Pedido pedido) {
        repositorio.guardar(pedido);
        notificador.pedidoConfirmado(pedido);
    }
}

// ---------- Infraestructura (bajo nivel) ----------

class RepositorioPedidosMySql implements RepositorioPedidos {
    private final String url;
    RepositorioPedidosMySql(String url) { this.url = url; }

    @Override
    public void guardar(Pedido pedido) {
        System.out.println("[" + url + "] INSERT pedido " + pedido.numero);
    }
}

class NotificadorCorreo implements Notificador {
    private final String servidor;
    private final int puerto;
    NotificadorCorreo(String servidor, int puerto) { this.servidor = servidor; this.puerto = puerto; }

    @Override
    public void pedidoConfirmado(Pedido pedido) {
        System.out.println("[" + servidor + ":" + puerto + "] a " + pedido.correoCliente
                + ": Pedido " + pedido.numero + " confirmado");
    }
}

// ---------- Dobles para pruebas ----------

class RepositorioEnMemoria implements RepositorioPedidos {
    final List<Pedido> guardados = new ArrayList<>();
    @Override public void guardar(Pedido pedido) { guardados.add(pedido); }
}

class NotificadorEspia implements Notificador {
    final List<String> avisos = new ArrayList<>();
    @Override public void pedidoConfirmado(Pedido pedido) { avisos.add(pedido.numero); }
}
```

### Qué se ganó

- `ServicioPedidos` no menciona SQL, SMTP, URLs ni puertos. Se puede leer entero sin saber qué base de datos usa la empresa.
- La regla de negocio se ejecuta en milisegundos con dobles en memoria.
- Cambiar de proveedor de correo es escribir otra implementación de `Notificador` y cambiar una línea en el ensamblaje.
- Las dependencias de la clase están a la vista en su constructor.

### Qué se pagó

- Dos interfaces y un punto de ensamblaje que antes no existían.
- Para seguir qué ocurre al guardar hay que saltar de la interfaz a la implementación.
- Alguien tiene que ensamblar. En aplicaciones grandes ese trabajo se delega a un contenedor.

## 6. Ejemplo empresarial con Spring Boot

Fragmentos ilustrativos: no forman parte del código del repo. Se compilaron y probaron con Spring Boot 4.1.1 y Java 21; donde aparece `{ ... }` o `// ...` se omitió código que no aporta a la idea.

**Inyección por constructor.** Con un único constructor, Spring inyecta sin necesidad de `@Autowired`:

```java
@Service
public class ServicioPedidos {

    private final RepositorioPedidos repositorio;
    private final Notificador notificador;

    public ServicioPedidos(RepositorioPedidos repositorio, Notificador notificador) {
        this.repositorio = repositorio;
        this.notificador = notificador;
    }
}
```

Evita la inyección por campo (`@Autowired private ...`): oculta las dependencias, impide declararlas `final` y obliga a levantar Spring o usar reflexión para probar la clase.

**Elegir la implementación por ambiente.**

```java
@Component
@Profile("prod")
class NotificadorCorreo implements Notificador { ... }

@Component
@Profile({"dev", "test"})
class NotificadorConsola implements Notificador { ... }
```

Ojo: sin un perfil activo no existe ningún `Notificador` y la aplicación no arranca, porque Spring no encuentra con qué construir `ServicioPedidos`. Activa un perfil (`spring.profiles.active=dev`) o declara una implementación por defecto.

**Usar Spring no garantiza cumplir DIP.** Este servicio usa inyección y lo viola: el negocio depende de detalles.

```java
@Service
public class ServicioPedidos {
    private final JdbcTemplate jdbc;          // detalle de persistencia
    private final JavaMailSender mailSender;  // detalle de correo
    private final RestTemplate restTemplate;  // detalle de HTTP

    public ServicioPedidos(JdbcTemplate jdbc, JavaMailSender mailSender, RestTemplate restTemplate) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }
}
```

**Dónde vive la interfaz importa.** Si `RepositorioPedidos` está en el paquete de infraestructura junto a su implementación JPA, el negocio sigue importando infraestructura. En arquitectura hexagonal (Nivel 5) estas interfaces se llaman **puertos**, viven en el dominio, y las implementaciones (**adaptadores**) viven afuera.

**Un matiz sobre Spring Data.** `interface PedidoRepository extends JpaRepository<Pedido, Long>` es una abstracción, pero expone conceptos de Spring Data y de JPA (`Pageable`, `Example`, `flush`). Para la mayoría de aplicaciones es un acoplamiento aceptable y pragmático. Cuando el dominio es complejo y debe sobrevivir a cambios de tecnología, se define un puerto propio y un adaptador que usa Spring Data por dentro.

## 7. Taller práctico

<!-- file: taller/inicio/Main.java -->
```java
import java.time.LocalDate;

public class Main {

    public static void main(String[] args) {
        ServicioSuscripciones servicio = new ServicioSuscripciones();
        System.out.println(servicio.renovar("ana", 3));
    }
}

class ServicioSuscripciones {

    private final PasarelaPagosAcme pasarela = new PasarelaPagosAcme("clave-secreta-prod");
    private final ArchivoLog log = new ArchivoLog("/var/log/suscripciones.log");

    String renovar(String usuario, int meses) {
        long valor = meses * 30_000L;
        if (meses >= 12) {
            valor = valor * 80 / 100;
        }
        String codigo = pasarela.cobrarConTarjetaGuardada(usuario, valor, "COP", 3);
        LocalDate vence = LocalDate.now().plusMonths(meses);
        log.escribirLinea(LocalDate.now() + ";" + usuario + ";" + valor + ";" + codigo);
        return "Renovado hasta " + vence + " (" + codigo + ")";
    }
}

class PasarelaPagosAcme {
    private final String clave;
    PasarelaPagosAcme(String clave) { this.clave = clave; }
    String cobrarConTarjetaGuardada(String usuario, long valor, String moneda, int reintentos) {
        System.out.println("[ACME " + clave.length() + "] cobro a " + usuario + " por " + valor + " " + moneda);
        return "ACME-" + Math.abs(usuario.hashCode());
    }
}

class ArchivoLog {
    private final String ruta;
    ArchivoLog(String ruta) { this.ruta = ruta; }
    void escribirLinea(String linea) { System.out.println("[" + ruta + "] " + linea); }
}
```

1. Lista todas las dependencias ocultas de `ServicioSuscripciones`. Hay una que no es una clase tuya: encuéntrala.
2. Aplica DIP. Las interfaces deben hablar el idioma del negocio, no el de la pasarela.
3. Escribe en `Main` dos verificaciones sin infraestructura real: que 12 meses aplican el 20 % de descuento y que la fecha de vencimiento es la correcta. La segunda te obligará a resolver la dependencia del punto 1.
4. La empresa cambia a otra pasarela con una API distinta. Indica exactamente qué archivos se tocan.
5. ¿Dónde quedó tu raíz de composición? ¿Qué pasaría si dos clases distintas empezaran a ensamblar objetos?

Entrega: rama `taller/05-dip`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## 8. Preguntas de entrevista senior

1. Diferencia entre DIP, IoC y DI. Da un ejemplo de código que use DI y viole DIP.
2. ¿A quién pertenece una interfaz: a quien la implementa o a quien la usa? ¿Qué consecuencias tiene para la estructura de paquetes o módulos?
3. ¿Por qué se prefiere la inyección por constructor sobre la de campo y la de setter?
4. ¿Cómo se relaciona DIP con la arquitectura hexagonal y con Clean Architecture?
5. ¿Qué es una raíz de composición y por qué debe ser única?
6. ¿Abstraerías `LocalDate.now()`? ¿Y `String`? ¿Y `List`? Da el criterio general.
7. Tu servicio depende de la interfaz de un SDK de terceros. ¿Cumple DIP? ¿Qué riesgo queda?
8. ¿Qué es un *service locator* y por qué se considera inferior a la inyección?

## 9. Cuándo aplicar DIP produce complejidad innecesaria

**Abstraer lo que es estable.** `String`, las colecciones, `java.time`, las utilidades del lenguaje. DIP protege de lo volátil; depender de lo estable no es un problema.

**Una interfaz por cada clase.** El par `X` / `XImpl` generalizado duplica archivos sin invertir nada. La interfaz se justifica en las fronteras: donde el negocio toca infraestructura, entre módulos, o donde se necesita sustituir.

**Abstraer objetos de valor y entidades.** `Pedido`, `Dinero`, `Direccion` se crean con `new` y no se inyectan. No son servicios.

**Abstracciones que son un calco del detalle.** Una interfaz `ClienteMySql` con `ejecutarSql(String)` no invierte nada: el negocio sigue hablando SQL.

**Aplicaciones pequeñas con infraestructura que no va a cambiar.** En un CRUD sobre una sola base de datos, inyectar directamente un repositorio de Spring Data es razonable. El costo de un puerto y un adaptador se justifica cuando hay reglas de negocio que proteger.

**Abstracciones prematuras de mínimo común denominador.** "Por si cambiamos de base de datos" lleva a interfaces tan genéricas que impiden aprovechar la base que sí se usa.

Criterio: abstrae en la frontera con lo que es volátil, lento o externo. Lo demás, úsalo directamente.

## Comprobación antes de cerrar el nivel

- Explicar qué se invierte exactamente y dibujarlo.
- Distinguir DIP, IoC, DI y contenedor.
- Explicar por qué la interfaz pertenece al módulo de alto nivel.
- Identificar qué merece una abstracción y qué no.

Siguiente: [Taller integrador de SOLID](../06-taller-integrador/).
