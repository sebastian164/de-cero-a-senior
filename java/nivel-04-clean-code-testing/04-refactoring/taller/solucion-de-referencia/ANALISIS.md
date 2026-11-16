# Taller 04 — Refactoring: solución

Se usó el `liquidar` del taller 02 (`taller/inicio/Main.java` de `../../../02-metodos-y-clases/`), movido a `nomina.Nomina`
dentro de un proyecto Maven con JUnit 5.11.4 y AssertJ 3.27.3 (`pom.xml`).

Cada paso del refactor es una carpeta en `pasos/` con el código completo, el mensaje del commit
(`MENSAJE`) y el resultado esperado de `mvn test` (`ESPERADO`). `./ejecutar-pasos.sh` los aplica en orden
sobre un repositorio git nuevo, ejecuta las pruebas en cada uno y hace un commit por paso. Así el
historial de commits, que es la entrega, se reconstruye desde cero y cada commit queda comprobado.

## 1. Red de seguridad: pruebas de caracterización

`NominaCaracterizacionTest` tiene 8 casos (el taller pide al menos 6). Los valores esperados se obtuvieron
ejecutando el código original, no razonando sobre lo que "debería" dar: eso es lo que distingue una prueba
de caracterización de una prueba unitaria normal. Cubren:

- con y sin detalle (la bandera `incluirDetalle`),
- horas extra nocturnas y diurnas, y cero horas extra,
- los tres caminos de país: CO (8 %), MX (5 %) y uno sin descuento (PE),
- salarios divisibles y no divisibles por 30 y por 240 (donde la aritmética entera redondea),
- salario cero.

La red existe antes del primer cambio: es el commit 1, y el código de ese commit es el original sin tocar.

## 2 y 3. Historial (resultado real)

| # | Commit | Refactor aplicado | `mvn test` |
|---|---|---|---|
| 1 | Agregar pruebas de caracterización de liquidar (8 casos) sin tocar el código | — | pasa |
| 2 | Consolidar el condicional duplicado de horas extra en una variable explicativa (recargo) | Consolidar condicional / introducir variable explicativa | pasa |
| 3 | Extraer método porcentajeDeDescuento(pais) | Extraer método | pasa |
| 4 | Extraer método valorHorasExtra | Extraer método | pasa |
| 5 | Reemplazar números mágicos por constantes con nombre | Reemplazar número mágico por constante | pasa |
| 6 | Separar el cálculo del formato: introducir el record Liquidacion | Introducir objeto de valor / dividir fase | pasa |
| 7 | Mover el formato a Liquidacion (resumen y conDetalle) | Mover método | pasa |
| — | (sin commit) la prueba del error falla con el código del paso 07 | — | **falla**, como debe |
| 8 | Corregir error: la base dividía antes de multiplicar y un mes completo no pagaba el salario completo | Cambio de comportamiento | pasa |

**Ningún commit de refactor cambió comportamiento:** en los commits 2 a 7 el archivo de pruebas es
byte a byte el mismo del commit 1 (se comprueba con `cmp`) y pasa en todos. Cada paso
toca un solo método o una sola idea; el más grande (commit 6) agrega 19 líneas y quita 6.

## Error encontrado

Al caracterizar con 1.300.000 y 30 días, la base salió 1.299.990: `salario / 30 * dias` divide primero y
pierde hasta 29 pesos. Un mes completo no paga el salario completo. Se anotó y **no** se corrigió durante el
refactor (las pruebas de caracterización fijan el comportamiento actual, error incluido).

Se corrigió al final, en un commit aparte (el 8):

1. Primero se escribió la prueba `unMesCompletoPagaElSalarioCompleto` y se comprobó que falla contra el
   código del commit 7 (paso `08-reproducir-el-error`, que `ejecutar-pasos.sh` ejecuta pero no guarda en el
   historial para que cada commit del historial quede en verde y `git bisect` funcione).
2. El commit 8 cambia una línea (`salario * diasTrabajados / DIAS_DEL_MES`) y actualiza, a la vista, los tres
   casos de caracterización que cambian (5, 6 y 7). Quien revisa ve exactamente qué resultados cambian y por qué.

## Qué quedó pendiente

El historial es una secuencia de pasos pequeños, no un destino. Al terminar, `liquidar` sigue con 6 parámetros y 2
banderas, y los porcentajes de descuento (8 y 5) siguen sin nombre aunque el commit 5 sustituyó otros números
mágicos. Los siguientes pasos serían nombrar esos porcentajes, reemplazar las banderas por tipos y agrupar los
parámetros; esa parte es el taller 02 (`../../../02-metodos-y-clases/`).

## 4. Evidencia

`ejecutar-pasos.sh` imprime el historial reconstruido. La rama pedida (`taller/n4-04-refactoring`) sería ese
mismo historial en tu repositorio.
