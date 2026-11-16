# Taller integrador de patrones — análisis

Código: `src/main/java/taller/integrador/`. `Main` recorre el escenario completo (crear curso, inscribir, cotizar, pagar, consumir lecciones, guardar y retomar el examen, completar y certificar) y luego los cinco cambios de la Parte 3. `PlataformaTest` (15 pruebas) lo verifica.

## Parte 1 — Análisis por requisito

| # | Requisito | Problema de diseño (tabla del síntoma) | Elección | Descartado y por qué |
|---|---|---|---|---|
| 1 | Cursos > módulos > lecciones (video, lectura, examen) | "Estructura en árbol" + tipos de nodo con operaciones | **Sin patrón**: `record` con listas para la jerarquía (profundidad fija de 3 niveles) y `sealed interface Leccion` + `switch` para los tipos | **Composite**: solo aporta cuando la profundidad es arbitraria (combos dentro de combos); aquí un módulo nunca contiene un curso. **Visitor**: `sealed` + `switch` da lo mismo sin `aceptar`. |
| 2 | Precio con promociones combinables | "`if`/`switch` que elige un algoritmo" + reglas de combinación | **Strategy** (`Promocion`) + un único `Cotizador` con la regla: las acumulables se suman; de las exclusivas, la mejor y sola; gana lo que más convenga al cliente | **Decorator** apilando descuentos: el orden de las capas cambiaría el resultado y la regla "exclusiva" no se puede expresar sin que cada capa conozca a las otras. **Interpreter**: las reglas no las escriben usuarios. |
| 3 | Estados de la inscripción | "`if`/`switch` por estado en muchos métodos" | **Sin patrón**: `enum` con tabla de transiciones | **State**: los estados no tienen comportamiento propio, solo transiciones permitidas; seis clases para una tabla de seis filas. La tabla además responde "¿desde dónde se llega a ACTIVA?". `Plataforma` expone `suspender`, `reactivar` y `reembolsar`, que delegan en la tabla (`suspenderReactivarYReembolsar`). |
| 4 | Pasarelas distintas por país, cada una con su API | "API ajena que no encaja" | **Adapter** por SDK hacia el puerto `PasarelaPago`; selección por país en un **registro** (mapa) | **Facade**: no se trata de simplificar sino de encajar en un contrato. `switch` por país: obliga a tocarlo con cada pasarela. |
| 5 | Al completar: certificado, aviso, estadísticas, recomendaciones | "Cuando pase X, avisar a varios" | El **certificado es parte de la operación** (sin él el curso no está completado); el resto, **Observer** (`Publicador` + oyentes), con política de fallos aislados | Llamadas directas: cada consecuencia nueva toca el caso de uso. Certificado como oyente: si fallara, habría un curso completado sin certificado. **Pasar la `Inscripcion` en el evento**: es el error común de la lección (un oyente podría llamar `aplicar` o `marcarCompletada`); `CursoCompletado` es un hecho inmutable con solo los datos que leen los oyentes (ids, nombre y correo del estudiante, autorización, curso y certificado). La prueba `elEventoNoExponeLaEntidadMutable` comprueba que ningún componente del record es una entidad. |
| 6 | Verificar sesión, inscripción, desbloqueo y dispositivos antes de entregar | "Etapas que pueden cortar" | **Chain of Responsibility** (tubería): un eslabón por verificación; el orden se ve en un solo lugar | Un método con cuatro `if`: válido hoy, pero la Parte 3 agrega un quinto control y cada uno merece prueba propia. Los eslabones sin efectos van primero; los dos con efectos (registrar dispositivo, consumir cupo) solo los aplican si el resto de la cadena entregó, para que un rechazo posterior no deje nada registrado (`unRechazoPosteriorNoDejaDispositivoNiCupoRegistrado`). |
| 7 | Guardar y retomar el progreso de un examen | "Acción que se guarda o se deshace" | **Sin patrón**: `record Progreso` inmutable que se guarda y se recupera | **Memento**: protege el interior de un originador mutable para deshacer; aquí no hay deshacer ni interior que ocultar: el progreso **es** el valor. |

Requisitos resueltos **sin** patrón: 1, 3 y 7 (mínimo exigido: 2). El servicio de aplicación `Plataforma` coordina los casos de uso; es una fachada en el sentido del curso ("un servicio de aplicación es una fachada sobre el modelo"), pero no se introdujo como patrón: es la capa de casos de uso.

## Parte 2 — Implementación

Resumen de la salida real de `java -cp target/classes taller.integrador.Main` (OpenJDK 21.0.13): curso de 4 lecciones y 121 minutos; cotización $200.000 − $60.000 (lanzamiento 20 % + cupón 10 %) = $140.000; pago aprobado por la pasarela de Colombia (el SDK recibe 14.000.000 centavos); la lección 3 se rechaza antes de completar la 2; se completan L1–L3, se guarda el examen en la pregunta 1 y se retoma; al completar L4 se emite `CERT-1`, la inscripción pasa a `FINALIZADA` y los cuatro oyentes reaccionan.

## Parte 3 — Los cambios

| # | Cambio | Qué se tocó | Clases nuevas | Clases modificadas |
|---|---|---|---|---|
| 1 | Nueva pasarela (EE. UU.) | `SdkPagosUs` (simulado) + `AdaptadorUs`; una línea `registrar("US", ...)` en el ensamblaje | 1 adaptador | 0 (solo el ensamblaje) |
| 2 | Taller en vivo con cupo | `record TallerEnVivo` agregado al `permits`; el compilador marcó los dos `switch` de `Catalogo` (`minutos`, `describir`); eslabón nuevo `CupoTallerEnVivo` y una línea en la cadena | 2 | `Catalogo` (2 `switch` + `permits`) |
| 3 | 2×1 en la misma categoría | `record DosPorUno implements Promocion` (exclusiva) y una línea en el registro de promociones | 1 | 0 |
| 4 | Periodo de prueba de 7 días | Fila `EN_PRUEBA` y entrada `INICIAR_PRUEBA` en la tabla; `Inscripcion.iniciarPrueba` y la regla de vigencia en `daAcceso` | 0 | `EstadoInscripcion`, `Inscripcion` |
| 5 | Publicar el logro si el estudiante lo autorizó | Campo `autorizaPerfilPublico` en `Estudiante`; oyente `PerfilPublico` y su suscripción | 1 | `Estudiante` (un campo) |

## Parte 4 — Autocrítica

1. **El patrón que menos se justifica**: la cadena de verificaciones de entrega. Son cinco controles síncronos, todos de corte, ninguno envuelve al resto (no hay "antes y después" como en métricas). Un método con una lista de `Predicate` y mensajes haría lo mismo con menos indirección. Lo mantendría solo porque la Parte 3 agregó un control y porque el orden importa y se lee en un solo lugar; si no se esperan más controles, lo reemplazaría por una lista de validaciones.

2. **El cambio más costoso**: el taller en vivo (cambio 2). Tocó el catálogo (tipo nuevo y dos `switch`), la entrega (eslabón nuevo) y obliga a pensar el cupo como estado compartido entre estudiantes, algo que el diseño no tenía: el cupo terminó en el eslabón, que ahora guarda estado mutable (`Map` de inscritos) y no es seguro entre hilos. Revela que el diseño modeló bien las variaciones de **comportamiento** (promociones, pasarelas, reacciones) pero no tenía un lugar para **recursos con capacidad**; con persistencia real el cupo debería ser un agregado propio (`SesionEnVivo` con reservas) y no un detalle de la entrega. El periodo de prueba (cambio 4) también tocó dos clases: la tabla no basta para reglas con tiempo, que quedaron en `daAcceso`.

3. **Lo más difícil de seguir sin conocer patrones**: la entrega con la tubería de `Cadena`/`desde(i)` (lambdas que se llaman recursivamente) y la regla de combinación de promociones en el `Cotizador` (acumulables frente a la mejor exclusiva). La primera es indirección pura; la segunda es una regla de negocio que merece un comentario y pruebas con nombre, que es lo que tiene (`exclusivaGanaSoloSiEsMejor`).
