# 11 — TDD y pruebas de integración

Código compilado y ejecutado con JDK 21, Spring Boot 4.1.1, Testcontainers 2.0.5 y PostgreSQL 16 (Docker), salvo el bloque de "Código con problemas": es un esquema (`servicio.crear(...)` y `servicio.descuento(...)` no existen) y no compila tal cual.

## Idea sencilla

**TDD.** Un sastre no cose el traje completo y luego mira si te queda. Toma medidas primero: las medidas definen qué es "quedar bien" antes de cortar la tela. En TDD la prueba es la medida: se escribe antes del código y define qué significa que esté terminado.

**Integración.** Probar cada pieza de un motor en el banco no garantiza que el motor encienda. En algún momento hay que armarlo y girar la llave.

## Explicación técnica

### TDD: el ciclo

1. **Rojo:** escribe una prueba pequeña para un comportamiento que aún no existe. Ejecútala y mírala fallar (así sabes que puede fallar).
2. **Verde:** escribe el código mínimo para que pase. Mínimo de verdad.
3. **Refactor:** con la prueba en verde, mejora la estructura sin cambiar el comportamiento.

Repite en ciclos de pocos minutos.

### Lo que TDD da y lo que no

| Da | No da |
|---|---|
| Código testeable por construcción | Buen diseño automático |
| Solo el código que un requisito pide (YAGNI) | Pruebas de integración o de rendimiento |
| Una red de seguridad desde el primer minuto | Los requisitos correctos |
| Retroalimentación sobre la API antes de implementarla | Velocidad el primer mes |

TDD es una técnica de diseño tanto como de pruebas: escribir primero el uso obliga a pensar en la interfaz antes que en la implementación.

### La pirámide de pruebas

| Nivel | Qué verifica | Velocidad | Cantidad |
|---|---|---|---|
| Unitarias | Lógica de una unidad aislada | Milisegundos | Muchas |
| Integración | Que las piezas encajan: SQL real, serialización, configuración | Segundos | Algunas |
| Extremo a extremo | El sistema completo desde afuera | Minutos | Pocas |

Cuanto más arriba, más confianza por prueba y también más lentitud, fragilidad y dificultad para localizar el fallo. El error habitual es la pirámide invertida: pocas unitarias y muchísimas pruebas lentas por la interfaz.

### Qué se prueba en integración

Lo que una prueba unitaria no puede ver: que la consulta SQL es válida, que el mapeo JPA funciona, que el JSON tiene la forma esperada, que la seguridad bloquea lo que debe, que las transacciones revierten, que la configuración arranca.

### Pruebas de integración en Spring Boot

| Anotación | Qué carga | Para qué |
|---|---|---|
| `@SpringBootTest` | El contexto completo | Flujos de punta a punta dentro de la aplicación |
| `@WebMvcTest(Controlador.class)` | Solo la capa web | Rutas, validación, serialización, códigos HTTP |
| `@DataJpaTest` | Solo JPA y repositorios | Consultas y mapeos |
| `@JsonTest` | Solo serialización | Formato del JSON |
| `@RestClientTest` | Clientes HTTP | Llamadas salientes |

Las pruebas de "rebanada" (*slice*) cargan una parte del contexto: son más rápidas y más enfocadas que `@SpringBootTest`.

```java
@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ServicioPedidos servicio;

    @Test
    void crearUnPedidoSinClienteDevuelve400() throws Exception {
        mvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": []}"))
                .andExpect(status().isBadRequest());
    }
}
```

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PedidoRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired PedidoRepository repositorio;

    @Test
    void encuentraLosPedidosPendientesDeUnCliente() {
        repositorio.save(pedidoPendienteDe("ana"));
        repositorio.save(pedidoEntregadoDe("ana"));

        assertThat(repositorio.findPendientesPorCliente("ana")).hasSize(1);
    }
}
```

**Testcontainers** levanta una base de datos real en Docker para la prueba. Evita las sorpresas de probar con H2 y desplegar en PostgreSQL: los dialectos SQL no son iguales.

## Ejemplo de TDD: un ciclo completo

Requisito: una contraseña es válida si tiene al menos 8 caracteres y al menos un número.

```java
// Ciclo 1 — Rojo
@Test
void unaContrasenaCortaNoEsValida() {
    assertThat(Contrasena.esValida("abc1")).isFalse();
}
// No compila: Contrasena no existe. Ese también es un "rojo".

// Ciclo 1 — Verde: lo mínimo
static boolean esValida(String texto) {
    return false;
}

// Ciclo 2 — Rojo: una prueba que obligue a generalizar
@Test
void unaContrasenaLargaConNumeroEsValida() {
    assertThat(Contrasena.esValida("clave1234")).isTrue();
}

// Ciclo 2 — Verde
static boolean esValida(String texto) {
    return texto.length() >= 8;
}

// Ciclo 3 — Rojo
@Test
void unaContrasenaLargaSinNumeroNoEsValida() {
    assertThat(Contrasena.esValida("clavesegura")).isFalse();
}

// Ciclo 3 — Verde
static boolean esValida(String texto) {
    return texto.length() >= 8 && texto.chars().anyMatch(Character::isDigit);
}

// Refactor: nombrar las reglas
private static final int LONGITUD_MINIMA = 8;

static boolean esValida(String texto) {
    return tieneLongitudMinima(texto) && contieneNumero(texto);
}
```

Cada prueba nueva obliga a que el código sea un poco más general. Ninguna línea de producción existe sin una prueba que la exija.

## Código con problemas

```java
@SpringBootTest
class TodoTest {

    @Autowired ServicioPedidos servicio;

    @Test
    void calculaElDescuento() {                       // lógica pura probada con todo Spring
        assertThat(servicio.descuento(100_000, "VIP")).isEqualTo(10_000);
    }

    @Test
    void flujoCompleto() throws Exception {
        servicio.crear(...);
        Thread.sleep(3_000);                          // "esperar" a un proceso asíncrono
        assertThat(servicio.listar()).hasSize(1);     // falla si otra prueba dejó datos
    }
}
```

## Análisis

- Arranca todo el contexto (segundos) para probar una multiplicación.
- `Thread.sleep` hace la prueba lenta y además inestable: a veces 3 segundos no alcanzan.
- Depende de los datos que dejaron otras pruebas.
- Si esta es la única forma de probar la lógica, el diseño no es testeable.

## Código mejorado

- El cálculo del descuento se prueba con JUnit puro, sin Spring.
- El flujo asíncrono usa **Awaitility** en lugar de `sleep`: `await().atMost(5, SECONDS).untilAsserted(() -> …)`.
- Cada prueba de integración crea sus datos y limpia después (o corre en una transacción que se revierte).
- Los contextos de Spring se reutilizan manteniendo la misma configuración entre clases de prueba.

## Taller

**Parte A — Kata con TDD.** Implementa una calculadora de precio de parqueadero (usa las reglas del taller 08 del Nivel 1) siguiendo el ciclo estricto. Haz un commit por cada paso rojo, verde y refactor. El historial es la entrega.

**Parte B — Integración.** En un proyecto Spring Boot pequeño (una entidad, un repositorio, un servicio, un controlador):

1. Una prueba `@DataJpaTest` con Testcontainers para una consulta no trivial.
2. Una prueba `@WebMvcTest` para la validación de una petición y otra para el manejo de un error de negocio.
3. Una prueba `@SpringBootTest` de un flujo completo.
4. Mide cuánto tarda cada tipo y propón una distribución razonable para el proyecto.

**Parte C — Reflexión.** ¿En qué momento del kata TDD te resultó incómodo y por qué? ¿Qué decisión de diseño tomaste gracias a escribir la prueba primero?

Se revisará: tamaño de los ciclos, que cada prueba haya fallado antes de pasar y que cada nivel de prueba verifique lo que le corresponde. Rama `taller/n4-11-tdd`.

Para practicar TDD a fondo, con katas, dobles de prueba y código heredado, están los [cuatro talleres de TDD](../../../practicas/tdd/).

## Aplicación profesional

- **TDD donde hay lógica:** reglas de negocio, cálculos, validaciones, máquinas de estado. Es menos útil en código de pegamento y en exploración de una tecnología nueva.
- **TDD para corregir errores:** primero una prueba que reproduzca el error (rojo), luego la corrección (verde). El error no vuelve.
- **Pruebas de contrato** (Pact, Spring Cloud Contract): verifican que dos servicios coinciden en la forma de sus mensajes sin desplegarlos juntos.
- **Pruebas de arquitectura** (ArchUnit): verifican reglas como "el dominio no importa clases de infraestructura". Convierten decisiones de diseño en pruebas.
- **Tiempo de la batería:** si tarda más de unos minutos, la gente deja de ejecutarla. La velocidad es un requisito.

## Preguntas senior

1. ¿TDD mejora el diseño o solo lo hace testeable? Argumenta.
2. ¿En qué situaciones no usarías TDD?
3. Pirámide, trofeo y panal son formas distintas de repartir las pruebas. ¿De qué depende cuál conviene?
4. ¿H2 o Testcontainers? ¿Qué riesgo concreto asume cada opción?
5. ¿Cómo mantienes las pruebas de integración rápidas e independientes?
6. ¿Qué pruebas escribirías primero en un sistema heredado sin ninguna?
7. ¿Cómo sabes que tu batería de pruebas es buena? Nombra tres indicadores que no sean la cobertura.

## Cuándo no aplicar TDD estricto

Prototipos desechables, exploración de una API desconocida (primero un experimento, luego se reescribe con pruebas), código de interfaz puramente visual, configuración.

Con esto termina el Nivel 4. Siguiente: [Nivel 5 — Arquitectura de software](../../nivel-05-arquitectura/).
