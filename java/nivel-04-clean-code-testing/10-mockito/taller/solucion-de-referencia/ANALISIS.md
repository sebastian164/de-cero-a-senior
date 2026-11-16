# Taller 10 — Mockito y dobles de prueba: solución

Proyecto Maven (`pom.xml`): JUnit Jupiter 5.11.4, AssertJ 3.27.3 y `mockito-junit-jupiter` 5.14.2, JDK 21.

El primer paso escribe la solución del taller de DIP del Nivel 2 a
partir de su `taller/inicio` (`../../../../nivel-02-solid/05-dip/taller/inicio/`): puertos `Cobros` y `RegistroRenovaciones` en el idioma del negocio (`Cobro`,
`Comprobante`, "suscriptor", "valor en pesos"), adaptadores `CobrosConAcme` y `RegistroEnArchivoLog`, el
`Clock` inyectado (la dependencia oculta que no es una clase propia) y la raíz de composición en `Main`.
`./ejecutar-pasos.sh` comprueba que `Main` imprime lo mismo que ese `taller/inicio`.

Cada paso está en `pasos/` con el código completo, el mensaje del commit y el resultado esperado de
`mvn test`. `./ejecutar-pasos.sh` los aplica sobre un repositorio git nuevo, con un commit por paso:

| Paso | Qué | `mvn test` |
|---|---|---|
| 01 | DIP + las dos baterías (Mockito y fakes) | pasa: 13 pruebas |
| 02 | Refactor 1: extraer la regla de precio a `Tarifa` | pasa, sin tocar ninguna prueba |
| 03 | Refactor 2 con las pruebas sin tocar (no entra al historial) | **no compila** |
| 04 | Refactor 2: objeto parámetro `Renovacion` en el puerto `RegistroRenovaciones`, pruebas adaptadas | pasa |

## 1. Dos baterías para los mismos seis comportamientos

| Comportamiento | `ServicioSuscripcionesMockitoTest` | `ServicioSuscripcionesFakesTest` |
|---|---|---|
| 3 meses cobra 90.000 | captor sobre `cobrar` | `pasarela.cobrosRecibidos()` |
| 12 meses cobra con 20 % (288.000) | captor | estado de la pasarela falsa |
| 11 meses no tiene descuento (frontera) | captor | estado |
| Vencimiento (31-ene + 1 mes = 28-feb) | valor devuelto | valor devuelto |
| Registra la renovación con el comprobante | `verify(registro).registrar(...)` | `registro.lineas()` |
| Pasarela rechaza: no se registra | `verifyNoInteractions(registro)` | `registro.lineas()` vacío |
| (Mockito) ejemplo sobre-especificado | `InOrder` + `times(1)` + `verifyNoMoreInteractions` | — |

En ninguna de las dos se simulan `Cobro`, `Comprobante`, `Renovacion` ni el reloj: son valores y se usan
reales (`Clock.fixed`). Solo se reemplazan los dos puertos, que son lo lento y lo que tiene efectos.

**Legibilidad.** La de fakes se lee como una especificación: preparar, actuar, mirar el resultado. La de
Mockito obliga a leer `when(cobros.cobrar(any())).thenReturn(...)` en cada prueba y a entender captores. A
cambio, los fakes son dos clases más (`PasarelaFalsa` 30 líneas, `RegistroEnMemoria` 24) que hay que mantener.

**Fragilidad.** Ver el punto 4.

## 2. `ArgumentCaptor`

`renovarTresMesesCobraElValorCompleto` captura el `Cobro` enviado y lo compara completo:
`new Cobro("ana", 90_000, "Renovación 3 meses")`. Como `Cobro` es un `record`, la igualdad es por valor y
la aserción falla si cambia cualquier campo.

## 3. La pasarela lanza una excepción

`siLaPasarelaRechazaElCobroNoSeRegistraLaRenovacion`: con Mockito, `thenThrow(new PagoRechazadoException(...))`
y `verifyNoInteractions(registro)`; con fakes, `pasarela.rechazarConMotivo(...)` y el registro en memoria
vacío. Las dos comprueban además que la excepción sale con su mensaje: el servicio no la traga.

## 4. Refactors internos: qué se rompió (resultado real)

| Refactor | Pruebas Mockito editadas | Pruebas con fakes editadas | Otros archivos de prueba |
|---|---|---|---|
| 1. Extraer `Tarifa` (sin cambiar puertos) | 0 de 7 | 0 de 6 | — |
| 2. Objeto parámetro `Renovacion` en `RegistroRenovaciones` | **2 de 7** (`registraLaRenovacion...` y `sobreEspecificada...`) | **0 de 6** | `RegistroEnMemoria`: 2 líneas |

Errores de compilación del paso 03, tal como los dio `mvn test`:

```
RegistroEnMemoria.java:[8,1] ... does not override abstract method registrar(suscripciones.Renovacion)
ServicioSuscripcionesMockitoTest.java:[95,25] method registrar ... cannot be applied to given types;
ServicioSuscripcionesMockitoTest.java:[119,41] method registrar ... cannot be applied to given types;
```

El refactor 1 no rompe nada porque las dos baterías verifican a través de los puertos, no de los métodos
internos. El refactor 2 cambia la firma de un puerto: en la batería de Mockito hay que tocar cada prueba que
lo menciona; en la de fakes el cambio se absorbe en un solo lugar, el fake, y las pruebas no se enteran.
Ese es el costo y la ventaja de un fake: es código que se mantiene, pero se mantiene una vez.

## 5. `verify` necesario y sobre-especificación

Están marcados en el código con comentarios `NECESARIO` y `SOBRE-ESPECIFICACIÓN`:

- **Necesarios:** `verify(cobros).cobrar(captor)` (cobrar es el propósito de la operación),
  `verify(registro).registrar(...)` (dejar constancia es el otro efecto prometido) y
  `verifyNoInteractions(registro)` tras el rechazo (la ausencia del efecto es lo que se prueba).
- **Sobre-especificación** (`sobreEspecificada_...`): el `InOrder` (el orden ya lo impone el dato: sin
  comprobante no hay qué registrar), el `times(1)` explícito y `verifyNoMoreInteractions`, que prohíbe
  cualquier llamada nueva aunque no cambie el resultado. No hay `when` de consultas verificadas con
  `verify`: los stubs de `cobrar` se usan para devolver el comprobante, y lo que se verifica es el efecto.
