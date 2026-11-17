# Taller ODD 03: diagnosticar con telemetría

Versiones: JDK 21 (OpenJDK de Homebrew), agente Java de OpenTelemetry (se descarga de Maven Central y se verifica con su suma SHA-256; `comprobar.sh` lo guarda en `~/.cache/de-cero-a-senior/opentelemetry`), `grafana/otel-lgtm:0.40.0` (Prometheus, Tempo, Grafana y promtool), nginx con módulo OpenTelemetry, OpenTelemetry Collector con muestreo por cola (`otelcol.yaml`), python3 del sistema.
`comprobar.sh` repite todo (tres copias de la API detrás de nginx, tráfico continuo, los cinco fallos, alertas, muestreo) y comprueba cada criterio. Puertos 48400-48420. Los tiempos son de una ejecución real; se repiten con pocos segundos de diferencia.

## Paso 1: pruebas de la instrumentación
Dos comprobaciones automáticas: guardar una nota emite exactamente un evento `peticion` y uno `nota.guardada`, con todos sus campos y sin el contenido de la nota; y la traza de esa petición (consultada a Tempo) tiene los tramos `POST /api/notas` y `notas.escribir`.

## Paso 2: cinco fallos activables por configuración (variables de entorno, sin tocar el código)
| Fallo | Activación | Del enunciado o propio |
|---|---|---|
| `notas_largas` | `DEFECTO_NOTA_LARGA_MS=2000` | enunciado (ruta lenta para notas largas) |
| `errores_1_de_5` | `FALLOS=errores_1_de_5`; la excepción tiene un mensaje neutro ("fallo al escribir") | enunciado |
| `disco_solo_lectura` | **real, sin código**: después de arrancar, el simulacro quita el permiso de escritura (`chmod a-w`) a `notas.txt` y a la carpeta de datos de la copia 2; el JDK lanza su propia `AccessDeniedException` | enunciado |
| `clave_distinta` | `CLAVE_API` distinta en una copia (rechaza con 401/4xx lo que las otras aceptan) | propio |
| `lectura_lenta` | `FALLOS=lectura_lenta` (leer tarda proporcional al número de notas) | propio (parecido a la fuga) |

## Paso 3 y 4: simulacro (instrumentación inicial, nivel 1)
El script sortea el orden (semilla 7) y activa cada fallo reiniciando las copias con otro entorno (el disco, con `chmod`). Quien diagnostica (`diagnosticar.py`) solo habla con Prometheus y Tempo a través de Grafana; no conoce el fallo y **no compara ningún texto que el inyector ponga**: usa proporciones, el reparto por copia, los atributos de los tramos y el tipo de excepción que emite el JDK.

**Qué es el nivel 1.** Es la instrumentación de la sesión 02 sin dos atributos (`bitacora.nota.longitud` y `bitacora.notas.total`), que la solución de la sesión 02 ya traía. Es una **regresión deliberada** para ejercitar el paso 5 (cerrar huecos), no un hallazgo sobre la sesión 02.
Los tiempos de abajo son los de la consulta, **sin** la espera fija de 40 s que el tráfico necesita para exponer el fallo; el número de consultas es la medida más estable.

| Fallo | Diagnóstico | Segundos de diagnóstico | Consultas | Resultado |
|---|---|---|---|---|
| errores_1_de_5 | errores_1_de_5 | 0 | 8 | acertó |
| notas_largas | sin diagnóstico (indeterminado_post_lento) | - | 6 | no se pudo |
| clave_distinta | clave_distinta | 0 | 5 | acertó |
| lectura_lenta | sin diagnóstico (indeterminado_lectura_lenta) | - | 5 | no se pudo |
| disco_solo_lectura | disco_solo_lectura | 1 | 8 | acertó |

Cómo se llega a cada diagnóstico, y qué consulta dio la pista:
- **errores_1_de_5**: el 5xx es el 19 % de las escrituras y se reparte entre las tres copias (0,29 / 0,35 / 0,36), cada copia falla el 18 a 21 % de las suyas; los tramos `notas.escribir` con error llevan `bitacora.escritura.secuencia` 5, 5, 5, 10, 10, 15: **siempre múltiplos de 5**. La pista es la secuencia, no el mensaje de la excepción (`IOException`, sin más).
- **disco_solo_lectura**: todo el 5xx sale de la copia 2 y esa copia falla el 100 % de sus escrituras mientras las otras fallan 0 %; la excepción de los tramos es `java.nio.file.AccessDeniedException`, del JDK, no escrita por nadie del simulacro.
- **clave_distinta**: los 4xx (no 5xx) de guardar salen de una sola copia (copia-3 = 100 %).
- **notas_largas** y **lectura_lenta**: p95 por ruta separa escritura de lectura y Tempo dice que el tiempo está en `notas.escribir` / `notas.leer`, pero sin los atributos no se ve qué tienen en común las lentas. Es el hueco.

Lo que faltó (huecos): `bitacora.nota.longitud` en el tramo de escribir y `bitacora.notas.total` en el de leer.

## Paso 5: cerrar los huecos y repetir
| Fallo | Antes | Después |
|---|---|---|
| notas_largas | sin diagnóstico (6 consultas, sin atributo) | **diagnosticado** en 6 consultas: las escrituras lentas son todas de 450 caracteres o más y ninguna rápida pasa de 19 |
| lectura_lenta | sin diagnóstico (5 consultas, sin atributo) | **diagnosticado** en 5 consultas: la duración de `notas.leer` sigue al número de notas (correlación de rangos de Spearman entre 0,9 y 1,00, unos 20 ms por nota; la de Pearson dio 0,89 en una ejecución con muestreo porque la duración se aplana en el tope de 1,5 s, por eso se usan rangos) |

Para que `lectura_lenta` tenga **contraste**, las tres copias arrancan con 5, 50 y 150 notas (la lectura tarda 100 ms, 1 s y el tope de 1,5 s): sin copias con cantidades distintas no hay con qué comparar y `diagnosticar.py` dice "sin contraste" en lugar de inventar una causa.
"Cuánto tardas ahora": la medida es el número de consultas y los segundos de la consulta (0 s en una máquina local); los 40 s de espera de tráfico son los mismos antes y después y no miden nada del diagnóstico.

Los cinco se diagnostican solo con la interfaz.

Límite: `diagnosticar.py` es un clasificador con una rama por cada una de estas cinco causas conocidas; no descubre causas nuevas. Ante un fallo distinto respondería "sin causa" (o "indeterminado"), no inventaría una. Para `lectura_lenta` informa la mediana de la duración de las copias con menos y con más notas, y la correlación de rangos.

## Paso 6: tres alertas (`alertas.yaml`)
Validadas con `promtool check rules` (3 reglas, SUCCESS).
| Alerta | Condición | Por qué exige acción |
|---|---|---|
| GuardarNotaGastaElPresupuesto | proporción de guardados que fallan o pasan de 500 ms > 14,4 x 1 % durante 2 min | el usuario no puede guardar y el presupuesto semanal se agota en menos de 12 horas |
| UnaCopiaRechazaOFalla | una copia rechaza o falla más de la mitad de sus escrituras durante 3 min | el usuario ve fallos "al azar" |
| LeerNotasLento | p95 de leer > 500 ms durante 5 min | leer es lo que más se hace y la lentitud crece con los datos |

Cada una lleva su guía de cinco líneas en la anotación `guia`. Disparada de verdad: la regla GuardarNotaGastaElPresupuesto se cargó en Grafana, con el fallo `errores_1_de_5` activo, y Grafana la evaluó sola cada 10 s hasta ponerla en `firing`.

## Paso 7: una alerta mala (CPU > 0,1)
- Suena sin que el usuario sufra: ocho peticiones simultáneas a `/api/carga` gastan CPU en una copia; el CPU máximo (0,29 en la última ejecución) supera 0,1, no hay 5xx y el p95 de guardar se queda en unos 5 ms.
- El usuario sufre y no suena: con el disco de la copia 2 en solo lectura, el 100 % de sus escrituras falla (proporción de 5xx = 1) y su CPU está en reposo (alrededor de 0,002 a 0,003). El disco es de verdad de solo lectura (`chmod`), no una excepción fabricada.
Conclusión: el CPU es una causa posible, no un síntoma; la alerta debe mirar lo que el usuario nota (errores y latencia).
Nota sobre el umbral: se midió 0,185, 0,247, 0,287, 0,29 y 0,30 a 0,32 en ejecuciones distintas. El CPU de una copia con 8 hilos ocupados depende de cuántos núcleos libres haya en la máquina: la medida de 0,185 se tomó con otros procesos pesados corriendo a la vez y dejó por debajo el umbral de 0,2 que se usó antes, así que el umbral pasó a 0,1, que sigue siendo una alerta de CPU baja y deja margen sobre el ruido. La copia con el disco roto, en reposo, queda muy por debajo (centésimas).

## Paso 8: costo y muestreo
Mismo tráfico (450 peticiones) a una copia sin muestreo y a otra con el recolector (`otelcol.yaml`, muestreo por cola: conserva todas las trazas con error, todas las de más de 500 ms y una parte del resto).
| | Sin muestreo | Con muestreo |
|---|---|---|
| Trazas en Tempo | 466 | 82 a 100 según la ejecución (80 a 82 % menos) |
| Trazas con error | 33 | 33 |
| Trazas de más de 500 ms | 15 | 15 |
| Tramos recibidos / enviados por el recolector | 781 | 166 a 181 (77 a 81 % menos) |

Una hora de tráfico real no se midió: se midió una ráfaga de 450 peticiones y se extrapola proporcionalmente (a 8 peticiones por segundo, el simulacro generaba unas 29.000 por hora; sin muestreo, unos 50.000 tramos por hora a razón de unos 1,7 tramos por petición, y unos 79 % menos con el muestreo). Con muestreo, los cinco fallos se siguen diagnosticando (0 a 1 s de consulta cada uno, 5 a 8 consultas; `lectura_lenta` con la correlación 1,00).

## Paso 9: informe posterior (simulacro `clave_distinta`, como si fuera real)
Cronología (de la tabla anterior): T+0 se despliega una copia con una clave de API distinta; T+10 s empiezan los 4xx al guardar (el 31 % de las escrituras por nginx); T+40 s el diagnóstico los atribuye a una sola copia. Impacto medido con el indicador: 31 % de los guardados rechazados; leer no se afecta; sin 5xx. Causa: la copia 3 rechaza una clave que las otras aceptan (configuración distinta). Detección: el reparto del 4xx por copia (copia-3 = 100 %). Qué se cambia: la clave se lee de la misma fuente para todas las copias, se añade la alerta UnaCopiaRechazaOFalla y una comprobación previa al despliegue que compara la configuración efectiva entre copias. Sin culpables: el proceso permitía desplegar configuración distinta sin avisar.

## Paso 10: el proceso, en una página
- Al planear: ¿qué preguntas tendremos que contestar sobre esto en producción? ¿Qué indicador mide si el usuario está bien? ¿Qué atributos necesita cada tramo para decir por qué?
- En el pull request: ¿hay evento por petición con correlación? ¿El tramo nuevo lleva los atributos que explican una lentitud? ¿Hay prueba de la instrumentación? ¿No se registra contenido sensible?
- Al desplegar: el indicador y el reparto por copia antes y después; que cada copia reporta (`service.instance.id`); la alerta del indicador sigue verde.
- Después de un incidente: qué faltó para diagnosticar más rápido, añadir ese atributo o alerta, repetir el fallo y medir el tiempo; actualizar la guía de la alerta.

## Criterios de aceptación
| Criterio | Comprobado |
|---|---|
| Los cinco fallos se diagnostican solo con la interfaz | tabla del paso 4 y paso 5 (los cinco, con y sin muestreo) |
| Cada hueco encontrado se cerró y se volvió a probar | paso 5 |
| La instrumentación tiene pruebas | paso 1 |
| Cada alerta tiene guía | paso 6 |
| El muestreo no impide diagnosticar | paso 8 |
