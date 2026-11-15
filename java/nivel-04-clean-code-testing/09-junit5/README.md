# 09 — JUnit 5

Desde esta lección el código usa bibliotecas externas. Los ejemplos son ilustrativos: no se compilaron en la preparación del curso. Para ejecutarlos, crea un proyecto Maven con el `pom.xml` de abajo. Verifica las versiones actuales antes de usarlas. Si tu proyecto ya usa una versión mayor más reciente de JUnit, la API de Jupiter que se muestra aquí es la misma.

## Idea sencilla

Antes de despegar, un piloto recorre una lista de verificación: cada punto es una comprobación concreta con respuesta de sí o no. No confía en "ayer funcionó". Una batería de pruebas es esa lista para el código, ejecutada por la máquina en segundos cada vez que algo cambia.

## Explicación técnica

Una prueba unitaria verifica una unidad de comportamiento de forma aislada, rápida y repetible. JUnit 5 es el marco estándar en Java.

### Proyecto mínimo

```xml
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.27.3</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

El código va en `src/main/java` y las pruebas en `src/test/java`, en el mismo paquete. Se ejecutan con `mvn test`. En un proyecto Spring Boot, `spring-boot-starter-test` ya trae JUnit 5, AssertJ y Mockito.

### Anatomía: preparar, actuar, comprobar (AAA)

```java
class CuentaTest {

    @Test
    void retirarDescuentaElMontoDelSaldo() {
        // Preparar
        Cuenta cuenta = new Cuenta(100_000);

        // Actuar
        cuenta.retirar(30_000);

        // Comprobar
        assertThat(cuenta.saldo()).isEqualTo(70_000);
    }

    @Test
    void retirarMasQueElSaldoLanzaExcepcion() {
        Cuenta cuenta = new Cuenta(100_000);

        assertThatThrownBy(() -> cuenta.retirar(200_000))
                .isInstanceOf(FondosInsuficientesException.class)
                .hasMessageContaining("100000");
    }
}
```

### Herramientas esenciales

| Elemento | Para qué |
|---|---|
| `@Test` | Marca una prueba |
| `@BeforeEach` / `@AfterEach` | Preparación y limpieza por prueba |
| `@BeforeAll` / `@AfterAll` | Una vez por clase (estáticos) |
| `@DisplayName` | Nombre legible en el informe |
| `@Nested` | Agrupar pruebas por escenario |
| `@ParameterizedTest` | La misma prueba con varios datos |
| `@Disabled` | Desactivar (con motivo; es deuda) |
| `assertThrows` / `assertThatThrownBy` | Verificar excepciones |
| `assertAll` | Varias comprobaciones que se reportan juntas |
| `@TempDir` | Directorio temporal |

### Pruebas parametrizadas

```java
@ParameterizedTest(name = "{0} días de mora -> {1}")
@CsvSource({
        "0,   AL_DIA",
        "30,  AL_DIA",
        "31,  EN_MORA",
        "90,  EN_MORA",
        "91,  COBRO_JURIDICO"
})
void clasificaSegunLosDiasDeMora(int dias, EstadoCartera esperado) {
    assertThat(Cartera.clasificar(dias)).isEqualTo(esperado);
}
```

Los casos 30/31 y 90/91 son **valores límite**: los errores viven en las fronteras.

### Qué hace buena a una prueba (FIRST)

- **Rápida** (*Fast*): milisegundos. Si son lentas, nadie las ejecuta.
- **Independiente** (*Isolated*): no depende de otra prueba ni del orden.
- **Repetible** (*Repeatable*): mismo resultado en cualquier máquina y a cualquier hora.
- **Autoverificable** (*Self-validating*): pasa o falla, sin inspección manual.
- **Oportuna** (*Timely*): escrita junto con el código.

### Nombres

El nombre describe el comportamiento, no el método: `retirarMasQueElSaldoLanzaExcepcion`, no `testRetirar2`. Cuando falla, el nombre debe decir qué regla se rompió.

## Código con problemas

```java
class PedidoTest {

    static Pedido pedido = new Pedido();      // estado compartido entre pruebas

    @Test
    void test1() {
        pedido.agregar("Teclado", 2, 120_000);
        pedido.agregar("Mouse", 1, 60_000);
        assertEquals(300_000, pedido.total());
        pedido.aplicarCupon("VERANO10");
        assertEquals(270_000, pedido.total());
        pedido.confirmar();
        assertTrue(pedido.estaConfirmado());
        System.out.println(pedido);           // "verificación" visual
    }

    @Test
    void test2() {
        assertEquals(3, pedido.cantidadDeItems());   // depende de que test1 corra antes
    }

    @Test
    void test3() {
        try {
            pedido.agregar("Monitor", 0, 800_000);
        } catch (Exception e) {
            // si lanza, bien
        }
    }
}
```

## Análisis

- **`test1` verifica tres comportamientos.** Si falla la primera aserción, no se sabe nada de las otras dos.
- **Estado `static` compartido:** `test2` pasa o falla según el orden de ejecución, que JUnit no garantiza.
- **`test3` nunca falla:** si no se lanza la excepción, la prueba pasa igual. Es una prueba que no prueba.
- **Nombres sin significado.** `System.out.println` como verificación.

## Código mejorado

```java
class PedidoTest {

    private Pedido pedido;

    @BeforeEach
    void crearPedido() {
        pedido = new Pedido();   // uno nuevo para cada prueba
    }

    @Test
    void elTotalEsLaSumaDeLosItems() {
        pedido.agregar("Teclado", 2, 120_000);
        pedido.agregar("Mouse", 1, 60_000);

        assertThat(pedido.total()).isEqualTo(300_000);
    }

    @Test
    void unCuponDelDiezPorCientoReduceElTotal() {
        pedido.agregar("Teclado", 1, 100_000);

        pedido.aplicarCupon("VERANO10");

        assertThat(pedido.total()).isEqualTo(90_000);
    }

    @Test
    void noSePuedeAgregarUnItemConCantidadCero() {
        assertThatThrownBy(() -> pedido.agregar("Monitor", 0, 800_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    class UnPedidoConfirmado {

        @BeforeEach
        void confirmar() {
            pedido.agregar("Teclado", 1, 100_000);
            pedido.confirmar();
        }

        @Test
        void noAdmiteMasItems() {
            assertThatThrownBy(() -> pedido.agregar("Mouse", 1, 60_000))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void noAdmiteCupones() {
            assertThatThrownBy(() -> pedido.aplicarCupon("VERANO10"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
```

## Taller

Crea un proyecto Maven y toma la clase `Cuentas` de la lección 07 (versión mejorada) y la clase `Curso` de la lección 02 del Nivel 1.

1. Escribe pruebas para todos los comportamientos, incluidos los casos de error. Una regla por prueba.
2. Identifica los valores límite de cada regla y cúbrelos con pruebas parametrizadas.
3. Usa `@Nested` para al menos un escenario con estado previo.
4. Introduce a propósito un error en el código de producción (cambia un `<` por `<=`). ¿Alguna prueba falla? Si no, falta una prueba.
5. Mide la cobertura (JaCoCo) y explica qué significa y qué **no** significa el porcentaje obtenido.

Se revisará: nombres, independencia entre pruebas, casos límite y que cada prueba pueda fallar. Rama `taller/n4-09-junit`.

## Aplicación profesional

- **La mayoría de las pruebas no necesitan Spring.** Las de dominio y de servicios con dependencias inyectadas son JUnit puro, y corren en milisegundos.
- **Cobertura:** indica qué código no se ejecutó en las pruebas. No indica que lo ejecutado esté bien verificado. Un 90 % sin aserciones no vale nada. Úsala para encontrar huecos, no como meta.
- **Pruebas de mutación** (PIT): modifican tu código y comprueban si alguna prueba se entera. Miden la calidad de las pruebas, no su cantidad.
- **Integración continua:** las pruebas corren en cada *push*; una prueba roja bloquea la integración.
- **Pruebas inestables (*flaky*):** una prueba que a veces falla destruye la confianza en toda la batería. Se arregla o se elimina de inmediato.

## Preguntas senior

1. ¿Qué es una "unidad"? ¿Una clase, un método, un comportamiento? Defiende una definición.
2. ¿Por qué una cobertura del 100 % no garantiza ausencia de errores?
3. ¿Cuántas aserciones por prueba? Da un criterio en lugar de un número.
4. ¿Qué haces con una prueba que falla una de cada veinte ejecuciones?
5. ¿Pruebas sobre métodos privados? ¿Por qué sí o por qué no?
6. ¿Qué convierte una batería de pruebas en un freno para refactorizar, y cómo se evita?

## Cuándo no escribir una prueba unitaria

Getters y setters triviales, código generado, configuración declarativa. Y cuando la lógica vive en la interacción con una base de datos o un servicio externo: ahí la prueba que aporta es de integración.

Siguiente: [10 — Mockito](../10-mockito/).
