# ADR 0001: el dominio y los casos de uso no conocen Spring ni la base

**Estado:** aceptada.

**Contexto.** Las reglas de reservas (franjas, solapes, plazo de cancelación, lista de espera) son lo valioso. Spring, JDBC y HTTP son detalles que cambian más rápido. Si las reglas importan anotaciones de Spring, solo se pueden probar levantando el contenedor.

**Decisión.** Tres paquetes con dependencias en una sola dirección: `infraestructura` -> `aplicacion` -> `dominio`.

- `dominio`: `Franja` y `Reserva`, Java puro.
- `aplicacion`: `ServicioReservas` (los casos de uso) y `Puertos` (las interfaces que necesita: `RepositorioReservas`, `Transaccion`). Solo conoce el dominio.
- `infraestructura`: el controlador REST, el repositorio JDBC, la seguridad y la configuración que une todo (`Configuracion` crea el servicio a mano, sin `@Service`).

Una prueba de ArchUnit (`ArquitecturaTest`) falla si el dominio importa Spring, `jakarta`, `java.sql` o capas superiores, o si la aplicación importa Spring o la infraestructura.

**Alternativas.** Anotar el servicio con `@Service` y usar `@Transactional`: menos código, pero el caso de uso queda atado a Spring y su prueba exige el contenedor. Se descartó. Un solo paquete con convenciones: funciona hasta el primer atajo, y nadie lo detecta.

**Consecuencias.** Los casos de uso se prueban con un repositorio en memoria y un reloj fijo, en milisegundos (`ServicioReservasSinInfraestructuraTest`). El coste es un puerto `Transaccion` y una clase de configuración que parecen ceremonia hasta que se prueba algo sin base.
