# SRP — Principio de responsabilidad única

Requisito: haber completado la [Clase 0](../../nivel-1-fundamentos-oo/00-que-es-la-oo/). Esta lección usa los conceptos de cohesión y acoplamiento que se presentaron allí.

## 1. Explicación sencilla

Imagina un restaurante pequeño donde una sola persona, Marta, cocina, cobra y lleva la contabilidad.

- El chef consultor quiere cambiar las recetas: habla con Marta.
- El dueño quiere cambiar los precios: habla con Marta.
- El contador quiere cambiar cómo se reportan los impuestos: habla con Marta.

Tres personas distintas, con intereses distintos, le piden cambios a la misma persona. Un día el contador le cambia el horario para cuadrar caja y, sin que nadie lo haya querido, la cocina abre una hora tarde. El contador no sabía nada de cocina; solo tocó lo suyo. Pero lo suyo y lo de los demás estaban en el mismo lugar.

El principio de responsabilidad única dice que eso no debería poder pasar: **cada pieza del sistema debería responder ante un solo grupo de personas que piden cambios**. Si el contador pide un cambio, debería tocarse una pieza que solo le importa al contador.

## 2. Definición técnica

La formulación clásica de Robert C. Martin: una clase debe tener una sola razón para cambiar.

Como "razón" resultó ambiguo, el propio Martin la precisó más tarde: un módulo debe ser responsable ante un único **actor**. Un actor es un grupo de personas (usuarios, áreas del negocio, interesados) que pide el mismo tipo de cambios por los mismos motivos.

Tres aclaraciones que evitan los malentendidos más comunes:

**SRP no significa "una clase hace una sola cosa".** Esa regla existe, pero aplica a funciones. Una clase con quince métodos puede cumplir SRP si los quince cambian por el mismo motivo, y una clase con dos métodos puede violarlo.

**SRP no se mide contando líneas ni métodos.** Se mide preguntando: ¿quién va a pedir cambios en este código, y por qué?

**SRP es cohesión vista desde el cambio.** Junta lo que cambia por la misma razón y separa lo que cambia por razones distintas. La primera mitad de esa frase es tan importante como la segunda, y se olvida con frecuencia.

## 3. Código que viola el principio

El código completo está en [`ejemplos/antes`](ejemplos/antes).

```java
public class Factura {

    private final String numero;
    private final String cliente;
    private final String correoCliente;
    private final List<Item> items = new ArrayList<>();

    public long calcularSubtotal() { ... }
    public long calcularIva() { return calcularSubtotal() * 19 / 100; }
    public long calcularTotal() { ... }

    public String generarTexto() { ... }          // arma el texto de la factura

    public void guardar(Path carpeta) { ... }     // la escribe en un archivo

    public void enviarPorCorreo() { ... }         // se la envía al cliente
}
```

Mira que este código no tiene los problemas de la Clase 0: los campos son privados, hay validaciones y los datos están junto a su comportamiento. Está bien encapsulado. El problema es otro.

## 4. Por qué es un problema

Antes de leer, haz el ejercicio: escribe quién podría pedir un cambio en esta clase y qué pediría.

| Actor | Cambio que pediría | Método afectado |
|---|---|---|
| Contabilidad | Cambia la tarifa del IVA, o aparecen productos exentos. | `calcularIva`, `calcularTotal` |
| Imagen ante el cliente | La factura debe mostrar el logo, otro orden, otro idioma, o salir en PDF. | `generarTexto` |
| Infraestructura | Las facturas ya no van a archivos, van a una base de datos o a la nube. | `guardar` |
| Servicio al cliente | Se envía por WhatsApp además del correo, o cambia el asunto. | `enviarPorCorreo` |

Cuatro actores, una clase. Consecuencias concretas:

**Cambios que rompen cosas ajenas.** `guardar` y `enviarPorCorreo` usan `generarTexto`. Si alguien cambia el formato para que el correo se vea mejor, cambia también lo que se guarda en disco. Nadie lo pidió y ninguna de las dos personas lo va a notar hasta que haga daño.

**Conflictos entre personas.** Dos desarrolladores que atienden pedidos de áreas distintas editan el mismo archivo a la vez. Los conflictos de merge son la señal visible; los errores de integración son la consecuencia cara.

**Pruebas difíciles.** Para probar el cálculo del IVA hay que instanciar una clase que también escribe archivos y envía correos. La regla más importante del sistema queda pegada a sus partes más frágiles.

**Reutilización imposible.** Otro módulo necesita calcular impuestos de una cotización. No puede usar la lógica de `Factura` sin arrastrar el formato, el disco y el correo.

**Dependencias que se propagan.** `Factura` importa `java.nio.file`. Cualquier clase que use una factura depende, de forma indirecta, del sistema de archivos.

## 5. Refactorización paso a paso

El resultado está en [`ejemplos/despues`](ejemplos/despues).

**Paso 1 — Identificar los actores.** Es la tabla anterior. Sin este paso la refactorización es partir clases al azar.

**Paso 2 — Decidir qué se queda.** `Factura` conserva lo que una factura es: sus datos y el subtotal, que depende únicamente de sus ítems.

```java
public class Factura {
    public void agregarItem(Item item) { ... }
    public long subtotal() { ... }
    // getters
}
```

**Paso 3 — Extraer la regla tributaria.**

```java
public class CalculadoraImpuestos {
    private static final long PORCENTAJE_IVA = 19;

    public long iva(Factura factura) {
        return factura.subtotal() * PORCENTAJE_IVA / 100;
    }

    public long total(Factura factura) {
        return factura.subtotal() + iva(factura);
    }
}
```

**Paso 4 — Extraer la presentación.** `FormateadorFactura` recibe una factura y devuelve texto. No guarda ni envía nada.

**Paso 5 — Extraer la persistencia.** `ArchivoFacturas` sabe escribir en disco. No sabe qué es una factura: recibe un número y un contenido.

**Paso 6 — Extraer la comunicación.** `NotificadorCorreo` sabe enviar un correo. Tampoco sabe qué es una factura.

**Paso 7 — Alguien tiene que coordinar.** Al separar las piezas aparece una responsabilidad que antes estaba escondida: el proceso de emitir una factura.

```java
public class ServicioFacturacion {

    public void emitir(Factura factura) {
        String texto = formateador.formatear(factura);
        archivo.guardar(factura.getNumero(), texto);
        notificador.enviar(factura.getCorreoCliente(), "Su factura " + factura.getNumero(), texto);
    }
}
```

`ServicioFacturacion` también tiene una sola razón para cambiar: que cambien los pasos del proceso.

### Qué se ganó

- Cambiar el IVA toca una clase que solo le importa a contabilidad.
- `CalculadoraImpuestos` se prueba sin disco ni correo.
- `ArchivoFacturas` y `NotificadorCorreo` sirven para otros documentos.
- Guardar y enviar ya no comparten formato por accidente: si deben ser distintos, se le pasan textos distintos.

### Qué se pagó

- De dos clases a siete. Para entender cómo se emite una factura ahora hay que abrir varios archivos.
- `Factura` expone getters que antes no necesitaba, porque otras clases leen sus datos. Se cedió algo de encapsulamiento a cambio de separación.
- `Main` tiene que ensamblar las piezas. Ese ensamblaje es lo que Spring hace por nosotros, como se verá en DIP.

### Dos decisiones discutibles a propósito

**¿Por qué `subtotal()` se quedó en `Factura` y el IVA no?** El subtotal es aritmética sobre los propios ítems y no depende de ninguna norma externa. El IVA depende de una ley que cambia por decisión de terceros. Es una decisión de criterio, no una verdad: si en tu negocio el IVA jamás va a cambiar ni a tener excepciones, dejarlo en `Factura` sería defendible.

**¿Por qué las clases extraídas son concretas y no interfaces?** Porque esta lección es sobre separar responsabilidades. Depender de abstracciones es otro principio (DIP) y tendrá su lección. Mezclar los dos ahora ocultaría cuál beneficio viene de cuál decisión.

## 6. Ejemplo empresarial con Spring Boot

Los fragmentos de esta sección son ilustrativos; no forman parte del código compilable del repo.

### El controlador que hace todo

```java
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JavaMailSender mail;

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        // validación del request
        if (body.get("clienteId") == null) {
            return ResponseEntity.badRequest().body("clienteId es obligatorio");
        }
        // regla de negocio
        BigDecimal total = calcularTotalConDescuento(body);
        // SQL
        jdbc.update("INSERT INTO pedidos (cliente_id, total) VALUES (?, ?)",
                body.get("clienteId"), total);
        // correo
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(buscarCorreo(body.get("clienteId")));
        mensaje.setSubject("Pedido recibido");
        mail.send(mensaje);
        // formato de la respuesta
        return ResponseEntity.ok(Map.of("total", total));
    }
}
```

Actores: quien define el contrato de la API, el negocio (descuentos), quien administra la base de datos y quien define las comunicaciones. Todos cambian el mismo método.

### El reparto habitual

| Pieza | Responsable ante | Cambia cuando |
|---|---|---|
| `PedidoController` | Los consumidores de la API | Cambia el contrato HTTP |
| `PedidoService` | El negocio | Cambian las reglas o el flujo |
| `PedidoRepository` | Quien administra los datos | Cambia el almacenamiento |
| `NotificacionService` | Comunicaciones | Cambia el canal o el mensaje |
| `PedidoMapper` | API y dominio | Cambia la traducción entre ambos |

```java
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService servicio;

    public PedidoController(PedidoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request) {
        return ResponseEntity.ok(servicio.crear(request));
    }
}
```

La arquitectura en capas de Spring es, en buena parte, SRP aplicado a escala de aplicación.

### La violación más frecuente en proyectos reales

No es el controlador gordo, que casi todo el mundo aprendió a evitar. Es el **servicio dios**: un `PedidoService` de dos mil líneas con cuarenta métodos, donde vive todo lo que contenga la palabra "pedido". Tiene nombre de capa correcta y por eso pasa las revisiones.

Señales de alerta:

- El constructor recibe ocho o más dependencias.
- Hay grupos de métodos que usan subconjuntos distintos de esas dependencias.
- Para describir la clase necesitas la palabra "y" varias veces.
- El archivo aparece en casi todos los pull requests del equipo.
- Los tests necesitan una docena de mocks para probar un método que usa dos.

## 7. Taller práctico

El enunciado está en [`taller/`](taller/). No hay solución publicada: primero se intenta.

## 8. Preguntas de entrevista senior

Respóndelas por escrito en tu entrega. Ninguna se contesta con la definición.

1. ¿Qué diferencia hay entre "una clase debe hacer una sola cosa" y SRP? Da un ejemplo de una clase que hace muchas cosas y cumple SRP.
2. `ServicioFacturacion.emitir` formatea, guarda y notifica. ¿Viola SRP? Defiende tu respuesta.
3. Tienes un servicio de 1.800 líneas en producción, sin tests, del que dependen quince clases. ¿Cómo lo divides sin detener al equipo ni romper producción? Describe los primeros tres pasos.
4. Dos métodos tienen exactamente el mismo código, pero uno responde a contabilidad y el otro a logística. ¿Los unificas? ¿Qué principio entra en tensión con SRP aquí?
5. ¿Cómo se relaciona SRP con los límites entre microservicios? ¿Qué pasa cuando un microservicio responde ante varios equipos?
6. Un compañero dividió una clase de 120 líneas en nueve clases de un método cada una "por SRP". ¿Qué le dirías en la revisión de código?
7. ¿Cómo identificas los actores de un módulo en un sistema que no conoces y cuya documentación no existe?

## 9. Cuándo aplicar SRP produce complejidad innecesaria

SRP se cobra en indirección. Hay casos en los que el precio no se justifica.

**Cuando no hay evidencia de que los ejes de cambio sean distintos.** Separar por actores imaginarios es especular. Si en dos años nadie ha pedido cambiar el formato sin cambiar el cálculo, quizá son una sola responsabilidad en tu contexto. Una buena regla práctica: separar cuando el segundo motivo de cambio aparece de verdad, no cuando lo imaginas.

**En código pequeño o desechable.** Un script de migración que corre una vez, un prototipo para validar una idea o una utilidad de cincuenta líneas no necesitan siete clases.

**En un CRUD sin reglas.** Si un caso de uso es recibir datos y guardarlos, crear validador, mapeador, servicio, caso de uso y repositorio por separado es ceremonia.

**Cuando la división destruye la cohesión.** Es el error opuesto y es igual de real: decenas de clases diminutas con un solo método, donde para seguir una regla de negocio hay que saltar entre doce archivos. Recuerda la mitad olvidada del principio: junta lo que cambia por la misma razón.

**Cuando se confunde responsabilidad con capa técnica.** Separar "todo lo que valida" de "todo lo que calcula" a veces separa cosas que pertenecen al mismo actor y cambian juntas.

Una prueba rápida antes de dividir: ¿puedo nombrar al actor de cada parte y describir un cambio realista que afectaría a una sin afectar a la otra? Si no puedes, todavía no dividas.

## Comprobación antes de avanzar

No pases a OCP hasta poder hacer esto sin mirar:

- Explicar qué es un actor y por qué "razón para cambiar" se refiere a personas y no a código.
- Tomar una clase cualquiera de un proyecto tuyo y listar sus actores.
- Explicar por qué una clase bien encapsulada puede violar SRP.
- Dar un caso en el que dividir una clase empeora el diseño.
- Explicar qué responsabilidad nueva aparece cuando se separan las demás.
