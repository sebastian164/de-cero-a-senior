# 10 — Mockito y dobles de prueba

Código ilustrativo, no compilado en la preparación del curso. Usa el proyecto Maven de la lección anterior y agrega `org.mockito:mockito-junit-jupiter` (ya incluido en `spring-boot-starter-test`).

## Idea sencilla

En una película, las escenas peligrosas las hace un doble, no el actor. El doble se parece lo suficiente para que la escena funcione y permite filmarla sin riesgo y las veces que haga falta.

En una prueba, los dobles reemplazan a los colaboradores reales (la base de datos, el correo, la pasarela de pagos) para poder ejercitar la clase que interesa de forma rápida, controlada y sin efectos reales.

## Explicación técnica

### Los cinco tipos de doble

| Doble | Qué hace | Ejemplo |
|---|---|---|
| **Dummy** | Rellena un parámetro; no se usa | Un `null` o un objeto vacío que la prueba no toca |
| **Stub** | Devuelve respuestas preparadas | Un repositorio que siempre devuelve el mismo cliente |
| **Spy** | Registra cómo fue usado, y puede delegar en el real | Un notificador que anota los mensajes enviados |
| **Mock** | Tiene expectativas sobre las llamadas que debe recibir | "Se debe llamar a `guardar` una vez con este pedido" |
| **Fake** | Implementación real pero simplificada | Un repositorio en memoria con un `HashMap` |

En el lenguaje cotidiano se llama "mock" a todos. Distinguirlos importa porque llevan a dos estilos de verificación:

- **Verificar estado:** ejecuto y compruebo el resultado o el estado final. Las pruebas sobreviven a los refactors.
- **Verificar interacción:** compruebo que se llamó a cierto método con ciertos argumentos. Las pruebas se atan a la implementación.

Regla práctica: **usa stubs para las consultas y verifica interacciones solo en los comandos cuyo efecto es el propósito de la operación** (enviar un correo, publicar un evento, cobrar).

### Mockito en lo esencial

```java
@ExtendWith(MockitoExtension.class)
class ServicioPedidosTest {

    @Mock RepositorioPedidos repositorio;
    @Mock Notificador notificador;
    @InjectMocks ServicioPedidos servicio;

    @Test
    void confirmarGuardaElPedidoYNotificaAlCliente() {
        // Preparar: stub de la consulta
        Pedido pedido = new Pedido("P-1", "ana@correo.example", 180_000);
        when(repositorio.buscar("P-1")).thenReturn(Optional.of(pedido));

        // Actuar
        servicio.confirmar("P-1");

        // Comprobar: estado y, solo para los comandos, interacción
        assertThat(pedido.estaConfirmado()).isTrue();
        verify(repositorio).guardar(pedido);
        verify(notificador).pedidoConfirmado(pedido);
    }

    @Test
    void confirmarUnPedidoInexistenteFallaYNoNotifica() {
        when(repositorio.buscar("P-9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.confirmar("P-9"))
                .isInstanceOf(PedidoNoEncontradoException.class);

        verifyNoInteractions(notificador);
    }
}
```

| Elemento | Uso |
|---|---|
| `when(…).thenReturn(…)` / `thenThrow(…)` | Programar una respuesta |
| `verify(mock).metodo(…)` | Comprobar que se llamó |
| `verify(mock, never())`, `times(n)` | Cuántas veces |
| `any()`, `eq()`, `argThat(…)` | Coincidencia flexible de argumentos |
| `ArgumentCaptor` | Capturar el argumento para inspeccionarlo |
| `@Spy` | Objeto real con algunos métodos sustituidos |
| `verifyNoInteractions`, `verifyNoMoreInteractions` | Ausencia de llamadas |

## Código con problemas

```java
@Test
void test() {
    Pedido pedido = mock(Pedido.class);                     // simula el objeto de dominio
    when(pedido.getTotal()).thenReturn(100_000L);
    when(pedido.getCliente()).thenReturn(mock(Cliente.class));
    when(repositorio.buscar(any())).thenReturn(Optional.of(pedido));
    when(calculadora.descuento(any(), any())).thenReturn(10_000L);

    servicio.confirmar("P-1");

    InOrder orden = inOrder(repositorio, calculadora, pedido, notificador);
    orden.verify(repositorio).buscar("P-1");
    orden.verify(calculadora).descuento(any(), any());
    orden.verify(pedido).setTotal(90_000L);
    orden.verify(pedido).setEstado("CONFIRMADO");
    orden.verify(repositorio).guardar(pedido);
    orden.verify(notificador).pedidoConfirmado(pedido);
    verifyNoMoreInteractions(repositorio, calculadora, pedido, notificador);
}
```

## Análisis

- **La prueba es una copia de la implementación.** Verifica cada llamada y su orden. Cualquier refactor que no cambie el comportamiento (reordenar dos líneas, extraer un método) la rompe.
- **Simula objetos de dominio.** `Pedido` y `Cliente` deberían ser objetos reales: son baratos de crear y contienen la lógica que se quiere ejercitar.
- **No prueba ninguna lógica real.** La calculadora está simulada, el pedido está simulado: la prueba comprueba que Mockito funciona.
- **`any()` en todas partes** oculta si se pasaron los argumentos correctos.
- **Da una confianza falsa:** pasa aunque el descuento real esté mal calculado.

## Código mejorado

```java
class ServicioPedidosTest {

    // Fakes y objetos reales en lugar de mocks donde es posible
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final NotificadorEspia notificador = new NotificadorEspia();
    private final ServicioPedidos servicio =
            new ServicioPedidos(repositorio, new CalculadoraDescuentos(), notificador);

    @Test
    void confirmarAplicaElDescuentoDeClienteFrecuente() {
        repositorio.guardar(new Pedido("P-1", clienteFrecuente(), 100_000));

        servicio.confirmar("P-1");

        Pedido confirmado = repositorio.buscar("P-1").orElseThrow();
        assertThat(confirmado.total()).isEqualTo(90_000);
        assertThat(confirmado.estaConfirmado()).isTrue();
        assertThat(notificador.avisos()).containsExactly("P-1");
    }
}
```

Esta prueba describe **qué** debe pasar, no **cómo**. Sobrevive a cualquier reorganización interna y falla si el descuento está mal.

## Taller

Sobre el `ServicioSuscripciones` que refactorizaste en el taller de DIP (Nivel 2):

1. Escribe las pruebas dos veces: una con Mockito y otra con fakes escritos a mano. Compara legibilidad y fragilidad.
2. Usa un `ArgumentCaptor` para verificar el contenido exacto de lo que se envía a la pasarela.
3. Simula que la pasarela lanza una excepción y verifica que no se registra la renovación.
4. Haz un refactor interno del servicio que no cambie su comportamiento. ¿Cuántas pruebas de cada estilo se rompieron?
5. Señala en tus pruebas qué `verify` es necesario y cuál es sobre-especificación.

Se revisará: que no se simulen objetos de valor ni entidades, y que las pruebas no dupliquen la implementación. Rama `taller/n4-10-mockito`.

## Aplicación profesional

- **`@MockitoBean`** (antes `@MockBean`) reemplaza un bean en el contexto de Spring durante una prueba. Cada combinación distinta crea un contexto nuevo y alarga la ejecución; úsalo con moderación.
- **No simules lo que no es tuyo.** Simular `RestTemplate`, `EntityManager` o el SDK de un proveedor fija tu suposición de cómo se comportan, no su comportamiento real. Envuelve lo ajeno tras una interfaz propia, simula esa interfaz, y prueba el adaptador con una prueba de integración.
- **WireMock** para simular APIs HTTP; **Testcontainers** para bases de datos reales. Son mejores que un mock cuando lo que se quiere probar es la integración.
- **Fakes reutilizables:** un repositorio en memoria bien hecho sirve a decenas de pruebas y documenta el contrato.
- **Señal de diseño:** si una prueba necesita ocho mocks, la clase tiene demasiadas dependencias.

## Preguntas senior

1. Diferencia entre mock y stub, y entre verificación de estado y de interacción. ¿Cuál prefieres por defecto?
2. ¿Qué es la sobre-especificación y cómo frena los refactors?
3. "No simules lo que no es tuyo." Explica el riesgo y la alternativa.
4. ¿Cuándo un fake es mejor que un mock, y qué costo tiene mantenerlo?
5. Escuela clásica (Detroit) frente a escuela mockista (Londres) de TDD: ¿qué prioriza cada una?
6. ¿Cómo te aseguras de que tu fake se comporta como la implementación real?

## Cuándo no usar un doble

Con objetos de valor, entidades y colaboradores puros y rápidos: usa los reales. Los dobles son para lo lento, lo no determinista y lo que tiene efectos fuera del proceso.

Siguiente: [11 — TDD y pruebas de integración](../11-tdd-e-integracion/).
