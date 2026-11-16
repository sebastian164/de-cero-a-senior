# Taller 09 — JUnit 5: solución

Proyecto Maven con las dependencias de la lección, tal cual (JUnit Jupiter 5.11.4, AssertJ 3.27.3), más
JaCoCo 0.8.14 (punto 5) y PIT 1.30.2 con `pitest-junit5-plugin` 1.2.3 (complemento del punto 4).
Ejecutado con JDK 21 y Maven 3.10.

- `src/main/java/banco/Cuentas.java`: `Cuentas` y sus excepciones, copiadas de
  `java/nivel-04-clean-code-testing/07-excepciones/ejemplos/despues/Main.java` (solo se agregó `package`).
- `src/main/java/academia/Curso.java`: `Curso` copiado de
  `java/nivel-01-fundamentos-oo/02-encapsulamiento/ejemplos/despues/Main.java`.
- Ambas copias son idénticas a los archivos del curso, salvo la línea `package`.

## 1. Pruebas: una regla por prueba

`CuentasTest` (22 ejecuciones) y `CursoTest` (18 ejecuciones): 40 pruebas en total (es la cifra que imprime
Surefire), todas en verde.
Cada prueba crea sus objetos en `@BeforeEach` o en el propio método: no hay estado `static` y JUnit
puede ejecutarlas en cualquier orden. Los casos de error comprueban tipo **y** mensaje, porque el
mensaje con contexto es parte del contrato que enseña la lección 07.

Reglas cubiertas en `Cuentas`: saldos iniciales, la transferencia mueve el monto, fondos insuficientes
(con mensaje), rechazo sin efectos parciales, cuenta de origen inexistente, cuenta de destino inexistente
(y el origen no pierde el dinero), monto no positivo, saldos como foto inmodificable y transferencia a la
misma cuenta.

Reglas cubiertas en `Curso`: nombre obligatorio, cupo positivo, cupos libres iniciales, inscribir,
duplicado, curso lleno, la lista es de solo lectura, la lista es una vista viva, y qué motivo gana cuando
un inscrito repite en un curso lleno.

### Error encontrado al escribir las pruebas

La primera versión de la prueba de la misma cuenta falló contra la versión del curso: `transferir("A", "A", 40_000)`
dejaba a A con 140.000. El método lee los dos saldos antes de escribir (para "validar todo antes de modificar
nada") y la segunda escritura pisa la primera. Se corrigió en el curso (lección 07, `ejemplos/despues` y su
bloque en el README) con una validación de origen igual a destino. La copia de este taller es la versión
corregida. Como el comportamiento ya está definido, quedó en dos pruebas sin `try/catch`:
`transferirALaMismaCuentaSeRechaza` (tipo `IllegalArgumentException` y mensaje exacto) y
`unaTransferenciaALaMismaCuentaNoModificaElSaldo` (A = 100.000 y B = 0 tras el rechazo).

## 2. Valores límite

| Regla | Frontera | Casos parametrizados |
|---|---|---|
| Monto positivo (`monto <= 0`) | 0 / 1 | 0, -1, `Long.MIN_VALUE` se rechazan; 1 pasa |
| Saldo suficiente (`saldoOrigen < monto`) | saldo / saldo + 1 | 1, 99.999 y 100.000 pasan; 100.001 y `Long.MAX_VALUE` se rechazan |
| Nombre del curso | vacío / con texto | `null`, `""`, `" "`, `"\t\n"` se rechazan |
| Cupo positivo (`cupoMaximo <= 0`) | 0 / 1 | 0, -1, `Integer.MIN_VALUE` se rechazan; 1, 2 y 40 se aceptan |
| Curso lleno | cupo / cupo + 1 | En `Lleno`: la inscripción número 2 entra y la 3 se rechaza |

## 3. `@Nested` con estado previo

- `CuentasTest.DespuesDeUnaTransferencia`: A transfirió 40.000 a B.
- `CursoTest.ConCupoDos.Lleno`: anidado dos niveles, el curso de cupo 2 ya tiene a Ana y Luis.

## 4. Errores introducidos a propósito (resultado real)

Se copia el proyecto, se cambia un operador y se ejecuta `mvn test`:

| Cambio | Resultado | Prueba que lo detecta |
|---|---|---|
| `saldoOrigen < monto` → `<=` | **falla** | `unMontoHastaElSaldoCompletoSeTransfiere [100000]`, `bPuedeDevolverTodoLoQueRecibio` |
| `monto <= 0` → `< 0` | **falla** | `unMontoQueNoEsPositivoSeRechaza [0]` |
| `cupoMaximo <= 0` → `< 0` | **falla** | `conCupoQueNoEsPositivoSeRechaza [0]` |
| `cuposLibres() == 0` → `<= 0` | **pasa** | ninguna, y no puede haberla |

El último no es un hueco de las pruebas: es un **mutante equivalente**. `inscribir` nunca deja entrar a
alguien sin cupo, así que `cuposLibres()` no puede ser negativo y las dos condiciones se comportan igual.
Ninguna prueba puede distinguirlos. Antes de concluir "falta una prueba" hay que preguntarse si el cambio
altera algún comportamiento observable.

PIT confirma el resto de forma sistemática: genera 20 mutantes (límites de condiciones, condiciones negadas,
aritmética y valores de retorno) y las pruebas matan los 20.

## 5. Cobertura: qué significa el número

Resultado real de JaCoCo (`mvn verify`, `target/site/jacoco/jacoco.csv`):

| Batería | Instrucciones | Ramas | Mutantes muertos (PIT) |
|---|---|---|---|
| Las pruebas del taller | 179/179 (100 %) | 18/18 (100 %) | 20/20 (100 %) |
| `sin-aserciones/`: dos pruebas que ejecutan todo y no comprueban nada | 179/179 (100 %) | 18/18 (100 %) | 11/20 (55 %) |

**Qué significa el 100 %:** cada instrucción y cada rama de `Cuentas` y `Curso` se ejecutó durante las
pruebas. No hay código que ninguna prueba haya recorrido.

**Qué no significa:**

- Que el código esté bien. La batería sin aserciones tiene exactamente la misma cobertura y no comprueba
  nada. Sus 11 "muertes" son mutantes que lanzan una excepción no capturada (por ejemplo, una condición
  negada que hace fallar `saldos.get`), no verificaciones.
- Que estén todos los casos. El 100 % ya se alcanzaba antes de escribir `transferirALaMismaCuentaNoCreaDinero`,
  y aun así el error de la misma cuenta estaba ahí: la cobertura mide líneas recorridas, no combinaciones de
  datos.
- Que los valores límite estén cubiertos. Con un solo caso por rama (por ejemplo, 500.000 para fondos
  insuficientes) la cobertura de ramas ya es completa, y el cambio de `<` por `<=` sobreviviría.

Conclusión: la cobertura sirve para encontrar código sin probar, y el puntaje de mutación dice si las
pruebas se enteran cuando el código cambia. Ninguno de los dos es una meta en sí mismo.
