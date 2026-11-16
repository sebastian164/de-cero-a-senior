# Taller 11 — TDD e integración: solución

La solución vive en dos proyectos de esta carpeta: `parte-a-parqueadero/`
(Parte A) y `parte-b-spring-integracion/` (Parte B). Versiones: JDK 21, Maven 3.10.1, JUnit 5.11.4, AssertJ 3.27.3,
Spring Boot 4.1.1, Testcontainers 2.0.5, `postgres:16` (16.16).

## Parte A — Kata: precio de parqueadero con TDD

Reglas del taller 08 del Nivel 1: carro 5.000 la primera hora y 3.000 cada adicional; moto 2.000 y 1.000;
bicicleta gratis; la fracción de hora cuenta como hora completa; más de 12 horas se cobra tarifa de día
(30.000 carro, 12.000 moto); el residente paga la mitad. El cupo y las entradas y salidas de placas no son
parte de una calculadora de precio y quedan fuera.

`parte-a-parqueadero/ciclos/` tiene un paso por carpeta (`ESPERADO` y `MENSAJE`). `ejecutar-ciclos.sh` los
aplica en orden sobre un repositorio git nuevo, ejecuta `mvn test` en cada uno y comprueba el resultado
esperado antes de hacer el commit. Historial real (20 commits):

| # | Commit | `mvn test` |
|---|---|---|
| 01 | rojo: una bicicleta no paga | no compila (`Tarifa` no existe) |
| 02 | verde: lo mínimo, precio 0 | pasa |
| 03 | rojo: un carro paga 5.000 la primera hora | falla |
| 04 | verde: 5.000 salvo bicicleta | pasa |
| 05 | rojo: cada hora adicional de un carro cuesta 3.000 | falla |
| 06 | verde: primera hora más adicionales | pasa |
| 07 | rojo: la fracción de hora cuenta como hora completa | falla |
| 08 | verde: redondear hacia arriba | pasa |
| 09 | rojo: tarifa de moto | falla |
| 10 | verde: tarifa según el tipo | pasa |
| 11 | rojo: más de 12 horas se cobra tarifa de día | falla |
| 12 | verde: tarifa de día | pasa |
| 13 | rojo: el residente paga la mitad | no compila (`precioParaResidente` no existe) |
| 14 | verde: `precioParaResidente`, sin parámetro bandera | pasa |
| 15 | rojo: una permanencia negativa se rechaza | falla |
| 16 | verde: rechazar la permanencia negativa | pasa |
| 17 | rojo: cero minutos se cobran como la primera hora | falla |
| 18 | verde: como mínimo se cobra una hora | pasa |
| 19 | pruebas de frontera: 1, 60 y 61 minutos | pasa (no es un rojo, ver abajo) |
| 20 | refactor: tarifas en `TipoVehiculo`, sin caso especial de bicicleta, constantes con nombre | pasa |
| (21) | mutación `>` por `>=` en el límite de 12 horas (no entra al historial) | **falla**, como debe |
| (22) | mutación: el redondeo suma 60 en vez de 59 (no entra al historial) | **falla**, como debe |
| (23) | mutación: se quita el mínimo de una hora (no entra al historial) | **falla**, como debe |

Cada rojo falló antes de pasar. Hay dos pruebas que no son rojos: `doceHorasExactasSeCobranPorHora` (dentro del
ciclo 11) y las de frontera del ciclo 19 (1, 60 y 61 minutos). Ya pasaban con el código anterior, porque la
fórmula por hora las cumple sin tarifa de día ni mínimo. Son pruebas de valor límite que protegen el verde: se
comprobó que pueden fallar con las mutaciones 21 y 22.

**Decisión sobre la permanencia en los extremos (ciclos 15 a 18).** Una permanencia negativa es un error de quien
llama (reloj mal leído): se rechaza con `IllegalArgumentException` y el valor en el mensaje. Una permanencia de
cero minutos (se entró y se salió en el mismo minuto) se cobra como la primera hora: el precio mínimo es una hora,
también para la moto (2.000) y es 0 para la bicicleta. Antes de escribir estas pruebas, `Tarifa.precio` devolvía
2.000 para el carro y 1.000 para la moto con cero minutos, porque `(0 + 59) / 60` da 0 horas y la fórmula restaba
una hora adicional. El ciclo 17 lo mostró en rojo.

El residente se modela con una segunda consulta con nombre, `precioParaResidente(tipo, permanencia)`, no con un
parámetro `boolean` (lección 02 del mismo nivel: los parámetros bandera ocultan qué hace la llamada). En el
refactor (ciclo 20) se quitó el `if (tipo == BICICLETA)` porque las tarifas 0 del `enum` ya dan 0, y la constante se
llama `HORAS_MAXIMAS_COBRADAS_POR_HORA` porque se usa con `>`: a las 12 horas exactas todavía se cobra por hora.

## Parte B — Integración (`parte-b-spring-integracion/`)

Entidad `Pedido`, repositorio, `ServicioPedidos`, `PedidoController`, `ManejadorDeErrores`.

1. `PedidoRepositoryTest`: `@DataJpaTest` + Testcontainers (`postgres:16`) con una consulta que filtra por
   cliente y estado y ordena por fecha (`findPendientesPorCliente`). Contiene la prueba copiada de la lección
   (solo cuenta pendientes de un cliente) y la propia del taller,
   `devuelveSoloLosPendientesDelClienteOrdenadosPorFecha`: guarda dos pendientes de ana en orden de fecha inverso,
   uno entregado de ana y uno pendiente de luis, y afirma `containsExactly(antigua, reciente)`. La de la lección
   sola no basta: seguía pasando sin el filtro por cliente y sin el `order by`. Si quitas
   cada uno sobre una copia, `PedidoRepositoryTest` falla.
2. `PedidoControllerTest`: `@WebMvcTest`, validación de la petición (sin cliente, 400).
   `ManejadorDeErroresTest`: `@WebMvcTest`, el error de negocio (422) y el recurso inexistente (404), ambos
   con Problem Details.
3. `FlujoCompletoTest`: `@SpringBootTest` con `@AutoConfigureMockMvc` y PostgreSQL real: crea, consulta,
   entrega, intenta entregar de nuevo (422) y consulta uno inexistente (404). El `id` se lee de la respuesta del
   POST: no se supone que el pedido sea el primero que guarda el contenedor.

### 4. Tiempos medidos (una máquina de desarrollo, JDK 21, contenedor `postgres:16` ya descargado)

| Tipo | Clase | Tiempo |
|---|---|---|
| JUnit puro (Parte A, 14 pruebas) | `TarifaTest` | 0,03 s |
| `@WebMvcTest` | `PedidoControllerTest`, `ManejadorDeErroresTest` | 0,02 s y 0,46 s (el primero que arranca el contexto web paga el costo) |
| `@DataJpaTest` + Testcontainers | `PedidoRepositoryTest` | 3,3 s (arranque del contenedor incluido) |
| `@SpringBootTest` + Testcontainers | `FlujoCompletoTest` | 1,0 s (contenedor propio incluido; ver nota) |

Nota: cada clase con `@Container static` arranca su propio PostgreSQL. La primera en ejecutarse paga además la
inicialización de Testcontainers y de Hibernate en frío (3,3 s); las siguientes bajan a 1 s. Un contenedor nuevo
cuesta del orden de un segundo con la imagen en caché. El contexto de Spring es lo caro: se cachea por combinación
de configuración, así que cada `@MockitoBean` o propiedad distinta crea otro. Son cifras de una sola ejecución en
una máquina compartida con otros procesos: sirven para el orden de magnitud, no para comparar décimas.

### Distribución propuesta para el proyecto

- **Muchas** pruebas de JUnit puro sobre las reglas (precio, validaciones, máquina de estados del pedido): décimas
  de segundo para toda la batería.
- **Pocas por controlador** con `@WebMvcTest`: validación y traducción de errores a HTTP; no repiten reglas de negocio.
- **Una por consulta no trivial** con `@DataJpaTest` y PostgreSQL real (no H2): solo lo que depende del SQL.
- **Una o dos** `@SpringBootTest`: el camino feliz y un error de negocio de punta a punta.

Regla práctica: si una regla se puede probar en un nivel inferior, no se prueba también en el superior.

## Parte C — Reflexión

- **Incomodidad.** En el ciclo 04 ("verde": devolver 5.000 fijo para que pase la primera prueba de carro). Escribir
  una constante a sabiendas de que está mal parece perder el tiempo; sirve para ver el rojo siguiente, que obliga a
  generalizar con un dato real. También fue incómodo el ciclo 01: la prueba ni compila, porque aún no existe `Tarifa`.
- **Decisión de diseño por escribir la prueba primero.** La firma `Tarifa.precio(TipoVehiculo, Duration)` (y la consulta aparte `precioParaResidente`, que reemplazó a un
  `boolean` que se veía incómodo en cada llamada):
  al escribir la prueba se vio que recibir una `Duration` y no dos fechas deja el reloj fuera de la calculadora, y que
  el tipo debe ser un `enum` (no un texto) para que `BICICLETA` no dependa de ortografía. El `enum` terminó cargando
  las tres tarifas (ciclo 20) porque las pruebas de moto repetían el patrón del carro.
