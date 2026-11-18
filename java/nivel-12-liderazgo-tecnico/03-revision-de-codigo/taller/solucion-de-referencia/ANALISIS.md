# Taller 12-03: revisión de código (solución)

## 1. Tres revisiones de pull requests reales de código abierto

Los tres PR son **reales y públicos**, de [google/gson](https://github.com/google/gson) (Java, el mismo ecosistema del curso). Se leyeron sus diffs (guardados sin cambios en `prs/gson-<n>.diff`, con el SHA de fusión fijado) y las observaciones **no se publicaron** en ningún sitio. Alcance honesto: es una revisión de lectura del diff y de la descripción; no se ejecutó el proyecto ni sus pruebas. Cada observación sigue el orden de prioridades (corrección, diseño, pruebas, seguridad, legibilidad, operación) y los prefijos de la guía del punto 5. Los tres PR fueron aceptados por sus mantenedores; la revisión que sigue es la que haría yo como revisor externo, no una crítica de lo que se decidió.

### PR 1: [google/gson#3144](https://github.com/google/gson/pull/3144) "Fix URI and URL adapters deserializing the string "null" as null" (fusión `6c06cbc679fc`; 18 líneas añadidas, 6 borradas)

1. **pregunta (corrección):** quitar el caso especial hace que un JSON que contiene la cadena `"null"` en un campo `URL` pase de dar `null` a lanzar `JsonSyntaxException`. Para `URI` el cambio es una corrección clara (`new URI("null")` es válido y hoy no sobrevive a ida y vuelta). Para `URL`, ¿sabemos por qué estaba el caso especial? ¿Se mira el historial (`git blame`) para ver si protegía a alguien (por ejemplo, serializaciones antiguas que escribían `"null"`)?
2. **sugerencia (operación):** es un cambio de comportamiento visible para quien usa la biblioteca; merece una línea en las notas de la versión o en el registro de cambios, y que el título del PR diga "behavior change" para que no pase inadvertido en una versión de parche.
3. **sugerencia (diseño):** el mismo bloque estaba duplicado en dos adaptadores; el PR borra los dos y queda bien, pero conviene comprobar si otros adaptadores del mismo archivo (`UUID`, `Locale`, `InetAddress`) conservan un caso parecido, para no dejar el tratamiento inconsistente.
4. **sugerencia (pruebas):** `testUrlDeserializationOfNullString` compara el mensaje de error completo (`"Failed parsing 'null' as URL; at path $"`); acopla la prueba a una redacción que puede cambiar. Bastaría el tipo de excepción y la causa `MalformedURLException`, que ya se comprueban.
5. **pregunta (pruebas):** ¿y como clave de mapa (`Map<URI, X>` con la clave `"null"`)? Es otro camino de lectura que no pasa por `read` de la misma forma.
6. **elogio:** las dos pruebas explican *por qué* en un comentario y cubren los dos lados del problema (ida y vuelta válida para `URI`, rechazo para `URL`); el cuerpo del PR muestra el código exacto que se quita.

Veredicto: aprobar con comentarios menores; condicionado a la respuesta de (1).

### PR 2: [google/gson#3157](https://github.com/google/gson/pull/3157) "Avoid quadratic clearing of JsonArray sublists" (fusión `7047a257a22c`; 46 líneas añadidas, 0 borradas)

1. **pregunta (corrección):** `removeRange` delega en `delegate.subList(from, to).clear()`. `AbstractList` ya valida los índices antes de llamarlo, de acuerdo; pero la lista envoltorio no incrementa su propio `modCount`. ¿Es un problema para los iteradores o sublistas de *este* envoltorio si alguien modifica la lista de respaldo por otro camino? Las pruebas nuevas reutilizan la sublista después de vaciarla (`subList.add(...)`), lo que cubre el caso de tamaño, pero no el de modificación concurrente.
2. **pregunta (diseño):** si `clear()` sobre una sublista era cuadrático por el `removeRange` por defecto, ¿hay otros métodos heredados con el mismo patrón (`addAll(index, c)`, `removeAll` sobre sublistas, `replaceAll`)? Conviene dejar anotado cuáles se revisaron.
3. **sugerencia (pruebas):** no hay prueba que falle si alguien quita el `removeRange` (las dos pruebas pasarían igual con la implementación cuadrática, solo más lenta). Una prueba sin tiempos: envolver la lista de respaldo en una lista que cuente operaciones (`remove(int)` frente a una sola llamada de `removeRange` en el `ArrayList`).
4. **sugerencia (descripción):** la afirmación "cuadrático" pide un dato: unos números antes y después para 100.000 elementos harían el PR autosuficiente y ayudarían a decidir si merece la pena.
5. **detalle (legibilidad):** un comentario de una línea sobre *por qué* se sobrescribe un método que parece redundante evita que el siguiente mantenedor lo borre como código muerto.
6. **elogio:** la prueba anidada (`subList.subList(1, 3).clear()`) y el vaciado de rango vacío (`subList(1, 1)`) son los casos límite correctos.

Veredicto: aprobar; (3) se puede hacer en un seguimiento.

### PR 3: [google/gson#3137](https://github.com/google/gson/pull/3137) "Correct logic for BOM detection." (fusión `845664ba1c30`; 9 líneas añadidas, 1 borrada)

1. **pregunta (corrección):** la condición pasa de `lineNumber == 0 && lineStart == 0` a `bufferStart == 0 && pos == 0`. ¿Existe algún camino en que `fillBuffer` se llame por segunda vez con `pos == 0`, `bufferStart == 0` y `buffer[0] == '\ufeff'`? Por ejemplo, un primer llenado que no consuma nada y un segundo llenado que encuentre un BOM *real* en el primer carácter. Si no existe, vale la pena decirlo en el PR (un comentario con el invariante).
2. **sugerencia (pruebas):** la prueba nueva usa lectores que entregan 2 caracteres por llamada. Añadir el caso en que el primer bloque trae **solo** el BOM (límite de 1 carácter) y el caso de un documento vacío con solo BOM, que son los bordes de `limit > 0`.
3. **sugerencia (pruebas):** el nombre `testRepeatedBom` no dice qué se espera; algo como `testOnlyFirstBomIsConsumedWhenReadInChunks` documenta la regla.
4. **detalle (legibilidad):** el comentario "if this is the first read" ya no describe la condición (ahora es "al inicio del flujo y del búfer"); actualizarlo.
5. **pregunta (seguridad / robustez):** un analizador sintáctico es superficie de ataque; ¿hay corpus de pruebas de fuzzing que se ejecuten en la integración continua y valga la pena correr con este cambio?
6. **elogio:** el cambio es mínimo, la descripción explica la motivación (la nueva variable `bufferStart` de otro PR) y la prueba reproduce el caso que fallaba.

Veredicto: aprobar con comentarios; (1) es la única duda de fondo.

### Anexo: un cambio propio del curso (10-03)

"Añadir `preStop` de 10 s y apagado ordenado" sobre el servicio `pedidos` (no es un PR publicado; es un cambio realmente ejecutado en el taller 10-03). Revisión:

1. **bloqueante (corrección):** `terminationGracePeriodSeconds` debe ser mayor que `preStop` + tiempo de drenaje; con 10 + 20 s, 45 s es correcto, pero si alguien sube `timeout-per-shutdown-phase` a 40 s el pod se mata a mitad. Propón una prueba (k6) que lo detecte.
2. **pregunta (diseño):** ¿por qué el `preStop` usa `sleep` del propio kubelet y no un `exec` con `sleep`? (La imagen JRE no trae `sleep` en todas las variantes.)
3. **sugerencia (operación):** documentar que los despliegues tardan 10 s más por pod.
4. **detalle (estilo):** YAML con indentación mixta; lo debería arreglar un *linter*.

## 2. Cinco comentarios reescritos

| Antes | Después |
|---|---|
| "Esto está mal." | "**bloqueante:** si `total` llega negativo se guarda sin error; propongo validarlo en el constructor y añadir una prueba." |
| "Usa un Map." | "**sugerencia:** el bucle anidado es O(n×m); con 50.000 pedidos tardaría. ¿Un `Map<Long, Cliente>` previo?" |
| "No me gusta el nombre." | "**detalle:** `procesar` no dice qué hace; ¿`cobrarPedido`?" |
| "Falta test." | "**bloqueante:** no hay prueba del caso de pedido duplicado; sin ella, un cambio futuro podría romperlo sin aviso." |
| "LGTM" (en 1.200 líneas) | "Es demasiado grande para revisarlo bien. ¿Lo partimos en refactor, migración y endpoint?" |

## 3. Dividir un cambio grande

Cambio original: "Estado de pedidos con Kafka y migración" (1.100 líneas).

1. **Migración compatible:** añadir columna `estado` opcional (30 líneas). Se despliega sola; no cambia comportamiento.
2. **Refactor sin cambio de comportamiento:** extraer `ServicioPedidos` (150 líneas), con las pruebas existentes en verde.
3. **Lógica de estado con pruebas:** transiciones válidas, escribe `estado` (200 líneas).
4. **Evento Kafka y endpoint:** publicar `pedidos-estado` y exponer la consulta (250 líneas), tras una bandera de función.

**Orden:** la base de datos primero (hacia atrás compatible), después el refactor (se puede revertir sin consecuencias), después la lógica, y al final lo visible. Cada paso se puede desplegar y revertir sin los demás.

## 4. Plantilla de descripción de PR

```markdown
## Qué y por qué
(Una o dos frases. El problema, no solo la solución. Enlace a la incidencia.)

## Cómo probarlo
(Comandos o pasos. Resultado esperado.)

## Dónde mirar con cuidado
(Lo riesgoso, lo que dudo.)

## Operación
- [ ] Migración compatible hacia atrás
- [ ] Configuración o secretos nuevos
- [ ] Plan de reversa

## Tamaño
(Menos de 400 líneas; si no, por qué no se pudo partir.)
```

## 5. Guía de revisión (una página)

- **Qué se revisa, en este orden:** corrección, diseño, pruebas, seguridad, legibilidad, operación.
- **Qué se automatiza:** formato, imports, nombres, análisis estático y vulnerabilidades de dependencias. Un comentario sobre estilo es un fallo de la herramienta.
- **Tamaño:** hasta 400 líneas; mayores se devuelven para partirlos.
- **Tiempos:** primera respuesta en un día hábil; si pasa de ese plazo, quien pidió la revisión avisa por el canal del equipo.
- **Prefijos:** `bloqueante:`, `sugerencia:`, `pregunta:`, `detalle:`, `elogio:`. Solo lo bloqueante impide fusionar.
- **Desacuerdos:** dos rondas de comentarios como máximo; luego una llamada de 10 minutos; si no hay acuerdo, decide quien es responsable del módulo y se registra en un ADR si la decisión es irreversible.
- **Aprobar con comentarios menores** sin exigir otra vuelta.

## 6. Aplicación a las entregas del curso

Cada rama `taller/n12-*` se abre con la plantilla del punto 4 y se divide como en el punto 3.
