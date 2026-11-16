# 01 — Nombres significativos

## Idea sencilla

En una bodega con cajas rotuladas "cosas", "varios" y "caja 2" hay que abrir cada una para saber qué tiene. Con rótulos como "facturas 2025" o "repuestos de impresora", encuentras lo que buscas sin abrir nada. Un nombre en el código es un rótulo: si es bueno, no hay que leer la implementación.

## Explicación técnica

Un nombre debe responder: qué representa, para qué existe y cómo se usa. Si necesita un comentario para entenderse, el nombre falló.

| Regla | Mal | Bien |
|---|---|---|
| Revela la intención | `int d` | `int diasDeMora` |
| No desinforma | `listaClientes` (es un `Map`) | `clientesPorCedula` |
| Hace distinciones con significado | `cliente1`, `cliente2`, `datosInfo` | `remitente`, `destinatario` |
| Es pronunciable y buscable | `fchCrcn`, `7` | `fechaCreacion`, `DIAS_POR_SEMANA` |
| Clases: sustantivos | `Procesar`, `Manager`, `Data` | `Factura`, `CalculadoraIntereses` |
| Métodos: verbos | `total()` que además guarda | `calcularTotal()`, `guardar()` |
| Booleanos: afirmaciones | `flag`, `estado`, `noActivo` | `estaActivo`, `tieneMora` |
| Una palabra por concepto | `obtener`, `traer`, `buscar`, `get` mezclados | una sola, siempre |
| Vocabulario del negocio | `Registro`, `Item2` | `Poliza`, `Siniestro` |
| Alcance proporcional | `i` en un bucle de 3 líneas: bien | un campo de clase llamado `x`: mal |

Dos ideas de fondo:

- **El nombre correcto suele aparecer cuando se entiende el problema.** No poder nombrar algo es señal de que hace varias cosas o de que el concepto no está claro.
- **Renombrar es el refactor más barato y más rentable.** El IDE lo hace sin riesgo.

## Código con problemas

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<int[]> l = new ArrayList<>();
        l.add(new int[] {1, 45, 0});
        l.add(new int[] {2, 10, 1});
        l.add(new int[] {3, 95, 0});
        System.out.println(proc(l));
    }

    static List<Integer> proc(List<int[]> l) {
        List<Integer> r = new ArrayList<>();
        for (int[] x : l) {
            if (x[1] > 30 && x[2] == 0) {
                r.add(x[0]);
            }
        }
        return r;
    }
}
```

## Análisis

No hay errores y no se entiende nada. ¿Qué es `x[1]`? ¿Qué significa `x[2] == 0`? ¿Por qué 30? Quien mantenga esto tendrá que encontrar al autor o adivinar. Cada lectura futura paga ese costo.

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Factura> facturas = List.of(
                new Factura(1, 45, false),
                new Factura(2, 10, true),
                new Factura(3, 95, false));
        System.out.println(numerosDeFacturasEnCobroJuridico(facturas));
    }

    private static final int DIAS_PARA_COBRO_JURIDICO = 30;

    static List<Integer> numerosDeFacturasEnCobroJuridico(List<Factura> facturas) {
        List<Integer> numeros = new ArrayList<>();
        for (Factura factura : facturas) {
            if (factura.requiereCobroJuridico(DIAS_PARA_COBRO_JURIDICO)) {
                numeros.add(factura.numero());
            }
        }
        return numeros;
    }
}

record Factura(int numero, int diasDeMora, boolean tieneAcuerdoDePago) {

    boolean requiereCobroJuridico(int diasLimite) {
        return diasDeMora > diasLimite && !tieneAcuerdoDePago;
    }
}
```

La lógica es la misma. Ahora el código cuenta la regla: van a cobro jurídico las facturas con más de 30 días de mora que no tienen acuerdo de pago. Nombrar bien obligó además a crear el tipo `Factura`: los buenos nombres empujan hacia un mejor diseño.

## Taller

Toma una clase de más de 150 líneas de un proyecto tuyo (anonimiza lo necesario).

1. Lista diez nombres mejorables y para cada uno: qué regla incumple, el nombre nuevo y qué tuviste que entender para proponerlo.
2. Encuentra un concepto con más de un nombre en el proyecto, o un nombre con más de un significado.
3. Encuentra un número o texto mágico y dale nombre.
4. Construye un glosario de diez términos del negocio y verifica si el código los usa igual que los usuarios.
5. Elimina tres comentarios reemplazándolos por nombres.

Se revisará: que los nombres nuevos usen vocabulario del dominio y que no se haya cambiado comportamiento. Rama `taller/n4-01-nombres`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Aplicación profesional

- **Lenguaje ubicuo** (DDD, Nivel 5): el código usa las mismas palabras que el negocio. Si el usuario dice "póliza", no hay una clase `Contrato`.
- **Convenciones de Spring:** sufijos `Controller`, `Service`, `Repository` indican el rol; no reemplazan al nombre. `PedidoService` con 40 métodos no describe nada: `ConfirmacionDePedidos` sí.
- **APIs REST:** los nombres de recursos y campos son un contrato público; cambiarlos después es costoso.
- **Idioma:** elige uno para el código y mantenlo. Mezclar `getClientePorId` y `findCustomerByName` duplica el vocabulario.

## Preguntas senior

1. ¿Qué te dice sobre el diseño que una clase solo se pueda nombrar con "Manager", "Helper" o "Util"?
2. ¿Cuándo un comentario es mejor que un nombre largo?
3. Un nombre es correcto pero el negocio usa otra palabra. ¿Cuál gana y por qué?
4. ¿Cómo conduces un renombrado masivo en un repositorio con diez equipos?
5. ¿Qué convención de nombres para pruebas hace que un fallo se entienda sin abrir el archivo?

## Cuándo no exagerar

Nombres kilométricos que repiten el contexto (`Factura.numeroDeFacturaDeLaFactura`), prefijos de tipo (`strNombre`), o gastar una hora nombrando una variable de un bucle de tres líneas.

Siguiente: [02 — Métodos y clases](../02-metodos-y-clases/).
