# Taller 03 — Auditoría de code smells: solución

Módulo auditado: la facturación heredada de `../../../01-nombres/taller/solucion-de-referencia/legado/Main.java` (anonimizada, 158 líneas en
`FacturaUtil`). Los refactors se aplicaron sobre la versión ya renombrada del taller 01, en `Main.java`.
La salida de `Main.java` se compara con `../../../01-nombres/taller/solucion-de-referencia/legado/salida-esperada.txt`
(`java Main.java | diff - ../../../01-nombres/taller/solucion-de-referencia/legado/salida-esperada.txt`): es idéntica, así que los tres refactors no cambiaron comportamiento.

## 1. Inventario de olores

Doce olores en cinco categorías. Las ubicaciones usan los nombres del original (`legado/Main.java`).

| # | Olor | Categoría | Ubicación | Por qué lo es | Refactor propuesto |
|---|---|---|---|---|---|
| 1 | Método largo | Infladores | `proc` (≈60 líneas) | Valida, suma líneas, descuenta, calcula IVA, envío, numera, formatea y registra | Extraer método por paso; luego mover cada regla a su dueño |
| 2 | Lista larga de parámetros | Infladores | `proc(nom, mail, p, t, lst, env, reg)` | Siete parámetros, dos de ellos banderas; fácil invertir `env` y `reg` | Introducir objeto parámetro (`SolicitudDeFactura`) |
| 3 | Obsesión por primitivos | Infladores | Cliente como `String`, país como `"CO"`, líneas como `String[]`, registro como `"F-1;Ana;230000;CO"` | Las reglas viven en `split(";")[3]` repartidos por la clase | Objetos de valor: `Pais`, `LineaDeFactura`, `RegistroDeFactura` |
| 4 | Sentencias `switch` sobre un código de tipo | Abusadores de la OO | `switch (t)` con 1, 2, 3 | Cada tipo nuevo obliga a tocar el `switch` y a recordar el número | Reemplazar código de tipo por `enum` con comportamiento |
| 5 | Campo temporal | Abusadores de la OO | `tmp` | Es un campo, pero solo tiene sentido durante `proc`; entre llamadas guarda basura | Convertir en variable local |
| 6 | Código duplicado | Prescindibles | Los porcentajes de IVA en `proc` y en `calcular`; la validación del nombre en `proc` y en `etiqueta`; el recorrido de `l` con `split` en `comision`, `anular` y `totCli` | Cambiar el IVA de Colombia exige tocar dos sitios (cirugía de escopeta) | Una sola fuente para el IVA (taller 05); extraer la búsqueda en el registro |
| 7 | Código muerto | Prescindibles | `calc2` | Nadie lo llama; además calcula un IVA "+1" que confunde a quien lo lee | Eliminarlo |
| 8 | Generalidad especulativa | Prescindibles | `IExportador`, `FabricaExportadores` con un único formato | Tres tipos para un `String.join` | Inlinear (taller 05) |
| 9 | Comentarios que explican código oscuro | Prescindibles | `// t: tipo de cliente 1=normal...`, `// contador` | Compensan nombres malos | Renombrar (hecho en el taller 01) |
| 10 | Cadena de mensajes | Acopladores | `etiqueta`: `cd.getDir().getCiudad().getNombre()` | La facturación conoce la estructura interna de la dirección | Ocultar el delegado: la dirección arma su etiqueta |
| 11 | Envidia de características | Acopladores | `pts(cd, compras)` | Solo usa datos del cliente (`getAnios`, `getNombre`) | Mover método a `Cliente` |
| 12 | Cambio divergente | Impiden el cambio | `FacturaUtil` | Cambia por IVA, por descuentos, por envío, por comisiones, por el formato del reporte y por fidelización: seis motivos | Separar en clases por motivo (después de 1-6) |

## 2. Prioridad por costo/beneficio

| Orden | Olor | Costo | Beneficio | Razón |
|---|---|---|---|---|
| 1 | 7 Código muerto | Mínimo | Medio | Borrar no puede romper nada que se ejecute (el compilador lo confirma) y elimina una pista falsa sobre el IVA |
| 2 | 4 `switch` de tipo de cliente | Bajo | Alto | Los descuentos son lo que más cambia en ventas; el `enum` concentra la regla y el compilador obliga a cubrir cada tipo |
| 3 | 10 Cadena de mensajes | Bajo | Medio | Cambio local, deja de propagar la estructura de `Direccion` |
| 4 | 6 Duplicación del IVA | Bajo | Alto | Riesgo real de cobrar un IVA y reportar otro; va en el taller 05 |
| 5 | 5 Campo temporal | Bajo | Medio | Hoy es inofensivo porque todo es secuencial, pero comparte estado si dos hilos facturan |
| 6 | 2 Lista larga de parámetros | Medio | Medio | Toca a todos los que llaman a `proc` |
| 7 | 1 Método largo | Medio | Alto | Más fácil después de 2, 4, 5 y 6 |
| 8 | 11 Envidia de características | Bajo | Bajo | Un solo método |
| 9 | 12 Cambio divergente | Alto | Alto | Es el resultado de todos los anteriores; se ataca al final, por partes |
| 10 | 3 Obsesión por primitivos en el registro | Alto | Medio | Ver punto 4: se deja |

## 3. Tres refactors aplicados (en `Main.java`)

1. **Eliminar código muerto:** se borró `calc2`.
2. **Reemplazar código de tipo por `enum` con comportamiento:** `int tipoDeCliente` y el `switch` pasaron a
   `enum TipoDeCliente { DETAL, MAYORISTA, EMPLEADO }`, cada uno con su `descuento(subtotal, unidades)`.
   Las constantes de porcentaje se mudaron al tipo que las usa.
3. **Ocultar el delegado (cadena de mensajes):** `etiquetaDeEnvio` ya no recorre `getDireccion().getCiudad().getNombre()`;
   llama a `direccion.paraEtiqueta()`, que a su vez pide `ciudad.paraEtiqueta()`. Se eliminaron los getters que quedaron sin uso.

Comprobación: misma salida que el original, línea por línea (18 líneas, incluidas las facturas, los dos reportes,
la etiqueta y la exportación).

## 4. Un olor que decido no corregir: el registro como texto (`"F-1;Ana Ruiz;230000;CO"`)

Es obsesión por primitivos de manual y produce los `split(";")[2]` repartidos por la clase. Aun así lo dejo:

- **Es un contrato hacia afuera.** El exportador CSV devuelve esas líneas tal cual; cualquier consumidor del
  archivo depende de ese formato exacto. Cambiar la representación interna obliga a mantener una conversión
  que produzca bytes idénticos y a probarla contra los consumidores, que no están en este módulo.
- **El beneficio llega casi gratis con otros cambios.** Al eliminar la duplicación del IVA (taller 05) y extraer
  la búsqueda en el registro, los `split` quedan en dos o tres sitios. Lo que queda es poco y estable.
- **Regla pragmática de la lección:** se refactoriza lo que se va a cambiar. Nadie ha pedido cambiar el
  registro. Queda anotado como deuda con su costo (una tarea, no un `TODO`): "Reemplazar el registro de
  texto por `RegistroDeFactura`; requiere acordar el formato del CSV con contabilidad".
