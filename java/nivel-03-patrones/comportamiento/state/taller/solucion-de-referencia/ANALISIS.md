# State — análisis

Las dos versiones implementan la misma interfaz `Solicitud` y pasan **las mismas** pruebas parametrizadas.

1. **`enum` + tabla** (`EstadoVacaciones`, `SolicitudConEnum`): toda la máquina está en `TRANSICIONES`; las reglas con condición de fecha son guardas en la clase.
2. **Patrón State** (`SolicitudConState`): una clase por estado; la base prohíbe todo y cada estado sobrescribe lo que permite.

3. **Comparación** (líneas contadas en el código de este taller, sin comentarios):
   | | enum + tabla | State |
   |---|---|---|
   | Líneas de código (sin comentarios ni vacías) | 46 | 60 |
   | Ver todo el flujo de un vistazo | sí, la tabla | no, repartido en 6 clases |
   | Ver qué permite un estado | sí, su fila | sí, su clase |
   | "¿Desde dónde se llega a APROBADA?" | se consulta: `origenesDe(APROBADA)` = `{ENVIADA}` | hay que leer todas las clases y buscar `new Aprobada()` |
   | Reglas con condición (fecha) | quedan fuera de la tabla, como guardas | dentro del estado que las aplica |

4. **Estado "en disfrute"** (no se puede cancelar): en la tabla, una fila vacía y una entrada `INICIAR_DISFRUTE` en `APROBADA`, más una guarda de fecha en la clase. En State, una clase nueva y un método en `Aprobada`; la prohibición de cancelar sale gratis (se hereda) y se sobrescribió solo para dar un mensaje propio. Ambas lo absorbieron con poco cambio; **la tabla** lo absorbió mejor porque el cambio estructural es una línea y la pregunta "¿desde dónde se llega?" sigue respondiéndose sola. State gana cuando cada estado tiene comportamiento propio rico (no solo transiciones).

5. **Persistencia**:
   - enum: una columna `VARCHAR` con `name()` (`@Enumerated(EnumType.STRING)` en JPA; nunca `ORDINAL`, que se rompe al reordenar).
   - State: se guarda igualmente un nombre/código (`estado.nombre()`) y al cargar se reconstruye el objeto con una fábrica (`switch` sobre el texto, o un `enum` cuyo valor crea el estado). Las clases de estado no se persisten; en la práctica las dos versiones terminan guardando el mismo texto.
