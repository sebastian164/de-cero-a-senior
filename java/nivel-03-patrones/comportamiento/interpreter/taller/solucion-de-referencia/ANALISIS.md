# Interpreter — análisis

1. **Expresiones nuevas**: `MenorQue`, `Entre` (inclusivo; rechaza rangos vacíos al construirse) y `EnLista`.

2. **Analizador** sin paréntesis, por descenso recursivo. Precedencia: `NO` > `Y` > `O`, como en la lógica habitual. `vip Y total > 200000 O categoria = LIBROS Y NO nuevo` produce exactamente el árbol del ejemplo (la prueba compara los `record` con `equals`). Errores de sintaxis con mensaje: número inválido, regla incompleta, tokens sobrantes con su posición. Limitación conocida: los valores no pueden contener espacios ni las palabras `Y`/`O`.

3. **Errores de tipos**: el ejemplo hace `(Long) contexto.get(campo)` y un dato mal tipado produce un `ClassCastException` sin contexto. `Campos.numero/booleano/valor` validan y lanzan `ErrorDeRegla` con la regla y el campo: `En la regla 'total > 100' el campo 'total' debe ser numérico, pero es String ("mucho")`. Además, el campo ausente ya no se confunde con `false`.

4. **Con `Predicate<Map<String,Object>>`** (`Predicados.reglaDelEjemplo`): evalúa igual en los tres casos, en menos líneas. Se pierde:
   - `describir`: una lambda compuesta no sabe mostrarse (`toString` da algo como `java.util.function.Predicate$$Lambda/0x...`), así que no se puede explicar al usuario por qué aplica o no una promoción, ni auditar qué regla estaba vigente;
   - la estructura: no se puede recorrer, serializar, guardar en base de datos ni comparar dos reglas (`equals`);
   - el contexto de los errores (vuelve el `ClassCastException`).

5. **SpEL escrito por el usuario**: SpEL con `StandardEvaluationContext` permite invocar métodos y tipos arbitrarios (`T(java.lang.Runtime).getRuntime().exec('...')`), es decir, ejecución remota de código; también lectura de propiedades del contexto y expresiones costosas (denegación de servicio). Mitigaciones: usar `SimpleEvaluationContext.forReadOnlyDataBinding()` (sin referencias a tipos, constructores ni beans), evaluar contra un objeto raíz con solo los datos permitidos, limitar el largo de la expresión (`new SpelParserConfiguration(...)` con `maxExpressionLength`), validar contra una lista blanca de campos y operadores, y preferir una gramática propia y pequeña como la de este taller cuando las reglas las escriben usuarios de negocio.

Esto se comprobó con un proyecto Spring de pruebas (`SpelRiesgoTest`, Spring Framework 7.0.x): con `StandardEvaluationContext` la expresión `T(java.lang.System).getProperty('user.home')` lee una propiedad del sistema (podría ser `Runtime.exec`); con `SimpleEvaluationContext.forReadOnlyDataBinding()` la misma expresión lanza `SpelEvaluationException`, mientras `total > 200000 and vip` sigue funcionando; y un parser con `maxExpressionLength` pequeño rechaza expresiones largas.
