# Taller 05 — DRY, KISS y YAGNI: solución

Proyecto: la facturación heredada (`../../../01-nombres/taller/solucion-de-referencia/legado/Main.java`). Los cambios se aplicaron sobre el
resultado del taller 03 y están en `Main.java`. La salida sigue idéntica a
`../../../01-nombres/taller/solucion-de-referencia/legado/salida-esperada.txt`.

## 1. Duplicación real frente a duplicación accidental

**Real (mismo conocimiento): las tarifas de IVA.** `proc` calculaba el IVA con un `if` sobre `"CO"`/`"MX"`
y `calcular` volvía a escribir las mismas tarifas (19 y 16) en un ternario. Es la misma regla de negocio:
"el IVA de Colombia es 19 %". Si cambia, cambia en los dos sitios a la vez, y si alguien actualiza solo uno, la
factura cobra un IVA y el reporte informa otro. Se unificó en `Iva.sobre(base, pais)`.
*Costo declarado:* `facturar` y el reporte quedan acoplados a `Iva`; es el acoplamiento correcto, porque
deben cambiar juntos.

**Accidental (mismo texto, distinto motivo): los dos "10" del mayorista.** `UNIDADES_PARA_DESCUENTO_POR_VOLUMEN = 10`
y `PORCENTAJE_POR_VOLUMEN = 10` tienen el mismo valor, pero uno es un umbral de unidades que decide logística y
el otro un porcentaje que decide comercial. Unirlos en una constante `DIEZ` (o reutilizar una para la otra)
haría que subir el umbral a 12 unidades subiera el descuento al 12 %. Se dejan separados, con nombres distintos.
*Costo declarado:* ninguno; solo hay que resistir la tentación.

## 2. Una abstracción con un solo uso: `IExportador` + `ExportadorCsv` + `FabricaExportadores`

Tres tipos y un `if` para envolver `String.join("\n", ...)`, con un único formato y una única llamada. Se
eliminaron y quedó `facturacion.exportarCsv()`.

**¿Qué pasó al eliminarla?** Nada observable: la salida es idéntica. Se borraron unas 20 líneas y desapareció un
`IllegalArgumentException("Tipo no soportado")` que no podía ocurrir. Si mañana llega un segundo formato,
extraer la interfaz en ese momento cuesta unos minutos y se diseña con los dos casos reales a la vista.
*Costo declarado:* ese trabajo futuro, si llega.

## 3. Una función genérica con banderas: `calcular(base, esIva, esComision, pais)`

Atendía dos conceptos sin relación: el IVA (depende del país) y la comisión del vendedor (3 % fijo, el país
no importa). La llamada `calcular(s, false, true, p)` no se entiende sin abrir el método. Se separó en
`Iva.sobre(base, pais)` y el cálculo de la comisión dentro de `comisionDelVendedor`, que es su único uso.
*Costo declarado:* ninguno medible; hay un método menos.

## 4. El fragmento más ingenioso: el reporte por país

```java
Map<String, Long> m = l.stream().collect(Collectors.groupingBy(x -> x.split(";")[3],
        TreeMap::new, Collectors.summingLong(x -> { ... calcular(total, true, false, f[3]); })));
```

Un colector de tres niveles con una lambda de bloque y una llamada con dos banderas. Se reescribió como un
bucle que nombra cada dato (`pais`, `baseGravableDeLaFactura`, `ventaConIva`) y acumula con `merge`. Un
comentario explica la única decisión no evidente: `TreeMap` para que los países salgan en orden alfabético
(la salida lo exige: `CO`, `MX`, `PE`).
*Costo declarado:* dos líneas más.

## 5. Regla del equipo para extraer código a una biblioteca común

> Solo se extrae a una biblioteca común el código técnico y estable que ya usan al menos tres servicios y que
> cambiaría por el mismo motivo en todos ellos. Las reglas de negocio no se comparten por biblioteca: se
> duplican conscientemente o se exponen como un servicio con dueño.
