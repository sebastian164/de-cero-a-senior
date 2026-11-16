# 05 — DRY, KISS y YAGNI

## Idea sencilla

- **DRY** (*Don't Repeat Yourself*): si el número de teléfono de la empresa está escrito en veinte volantes, el día que cambie habrá que corregir veinte, y alguno se quedará mal. Que esté en un solo lugar.
- **KISS** (*Keep It Simple*): para colgar un cuadro basta un clavo. No hace falta un sistema de rieles motorizado.
- **YAGNI** (*You Aren't Gonna Need It*): no construyas el segundo piso de la casa "por si algún día" cuando hoy vives solo y no sabes si lo necesitarás.

## Explicación técnica

### DRY

La formulación original (Hunt y Thomas) habla de **conocimiento**, no de texto: cada pieza de conocimiento debe tener una representación única y autorizada en el sistema.

Eso cambia qué se considera duplicación:

| Caso | ¿Es duplicación en el sentido de DRY? |
|---|---|
| La tarifa del IVA escrita en cinco clases | Sí: una sola regla, cinco lugares. |
| Dos métodos con el mismo código, que cambian por motivos distintos | No: coincidencia. Unirlos los acopla. |
| La misma validación en el frontend y en el backend | Es la misma regla, pero suele ser inevitable; se mitiga, no se elimina. |
| Estructura de una tabla repetida en la entidad, el DTO y el script SQL | Sí, y por eso existen los generadores y las migraciones. |

**Regla de tres:** la primera vez, escríbelo. La segunda, tolera la duplicación. La tercera, extrae. Con dos casos no se conoce todavía la forma correcta de la abstracción.

**"Prefiere duplicación a la abstracción equivocada"** (Sandi Metz): una abstracción prematura acumula parámetros y condicionales para atender casos que no encajan, y deshacerla cuesta más que la duplicación que evitó.

### KISS

La solución más simple que resuelve el problema completo. Simple no es lo mismo que fácil ni que corto:

- **Simple:** pocas partes, pocas relaciones entre ellas, se entiende leyendo.
- **Fácil:** lo que ya sabes hacer.
- **Ingenioso:** lo opuesto a simple. Una expresión de una línea que nadie entiende no es simple.

### YAGNI

No implementes nada hasta que se necesite de verdad. El costo de lo especulativo es triple: construirlo, mantenerlo y cargarlo en la cabeza de todos los que leen el código, y además suele adivinarse mal.

YAGNI aplica a **funcionalidad**. No es excusa para omitir lo que hace que el código sea barato de cambiar después: pruebas, nombres claros, responsabilidades separadas. Eso es lo que permite aplicar YAGNI sin miedo.

### Las tres en tensión

| Tensión | Cómo se resuelve |
|---|---|
| DRY contra KISS | Eliminar duplicación puede crear abstracciones complejas. Si la abstracción es más difícil de entender que la repetición, gana KISS. |
| DRY contra desacoplamiento | Compartir código entre módulos o servicios los ata. Entre servicios, cierta duplicación es el precio de la independencia. |
| YAGNI contra OCP | OCP pide puntos de extensión; YAGNI pide no anticipar. Se concilian esperando evidencia del eje de variación. |

## Código con problemas

```java
// DRY mal entendido: dos conceptos distintos unidos porque hoy se parecen
long calcular(long base, boolean esImpuesto, boolean esComision, boolean redondear, String pais) {
    long porcentaje = esImpuesto ? (pais.equals("CO") ? 19 : 16) : (esComision ? 3 : 0);
    long valor = base * porcentaje / 100;
    return redondear ? valor / 100 * 100 : valor;
}

// YAGNI ignorado: una interfaz, una fábrica y un registro de plugins para un solo exportador
interface ExportadorPlugin { ... }
class RegistroDeExportadores { ... }
class FabricaAbstractaDeExportadores { ... }
class ExportadorCsv implements ExportadorPlugin { ... }   // el único que existe

// KISS ignorado: ingenioso, corto e ilegible
return lista.stream().collect(groupingBy(x -> x.t() % 3 == 0 ? x.c() : x.c() >> 1,
        collectingAndThen(reducing((a, b) -> a.v() > b.v() ? a : b), Optional::get)));
```

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        System.out.println(Impuestos.iva(100_000, Pais.COLOMBIA));
        System.out.println(Comisiones.deVenta(100_000));
        System.out.println(ExportadorCsv.exportar(java.util.List.of("Teclado", "Mouse")));
    }
}

enum Pais {
    COLOMBIA(19), MEXICO(16);

    final int porcentajeIva;

    Pais(int porcentajeIva) {
        this.porcentajeIva = porcentajeIva;
    }
}

/** Una regla, un lugar. Cambia cuando cambia la ley. */
final class Impuestos {
    private Impuestos() { }

    static long iva(long base, Pais pais) {
        return base * pais.porcentajeIva / 100;
    }
}

/** Otra regla, otro lugar. Cambia cuando cambia la política comercial. */
final class Comisiones {
    private static final int PORCENTAJE_COMISION = 3;

    private Comisiones() { }

    static long deVenta(long base) {
        return base * PORCENTAJE_COMISION / 100;
    }
}

/** Sin interfaz ni fábrica: hay un solo formato. El día que haya dos, se extrae. */
final class ExportadorCsv {
    private ExportadorCsv() { }

    static String exportar(java.util.List<String> filas) {
        return String.join(";", filas);
    }
}
```

`iva` y `deVenta` contienen la misma operación aritmética. No se unifican: son dos conocimientos distintos que hoy coinciden en la fórmula.

## Taller

1. Busca en un proyecto tuyo un caso de duplicación real (mismo conocimiento) y uno de duplicación accidental (mismo texto, distinto motivo). Justifica la diferencia.
2. Busca una abstracción que solo tenga un uso o una implementación. ¿Qué pasaría si la eliminaras?
3. Busca una "función genérica" con parámetros bandera que atienda casos distintos. Sepárala.
4. Busca el fragmento más ingenioso del proyecto y reescríbelo para que lo entienda alguien nuevo.
5. Escribe una regla de tu equipo, en dos frases, para decidir cuándo extraer código compartido a una biblioteca común.

Se revisará: que distingas conocimiento de texto y que cada decisión tenga un costo declarado. Rama `taller/n4-05-principios`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Aplicación profesional

- **Bibliotecas compartidas entre microservicios:** una biblioteca "común" con lógica de negocio obliga a desplegar todos los servicios juntos cuando cambia. Comparte lo técnico y estable; duplica lo de negocio.
- **DTOs "repetidos":** el DTO de la API y la entidad se parecen, pero cambian por motivos distintos. Reutilizar la entidad como DTO ata la API al esquema de la base.
- **Configuración:** una propiedad definida en un solo lugar (`@ConfigurationProperties`) y no repetida en `@Value` por toda la aplicación.
- **YAGNI en arquitectura:** no empezar con microservicios, CQRS y mensajería para una aplicación con cien usuarios.

## Preguntas senior

1. Da un ejemplo donde aplicar DRY aumentó el acoplamiento y fue un error.
2. ¿Cómo distingues una abstracción prematura de una previsión razonable?
3. ¿YAGNI contradice el diseño para la extensibilidad? Concilia ambos.
4. ¿Qué duplicarías conscientemente entre dos microservicios y qué no?
5. ¿Cómo deshaces una abstracción equivocada que ya usan veinte clases?

## Cuándo no aplicarlos al pie de la letra

DRY, KISS y YAGNI son consejos, no métricas. Decisiones difíciles de revertir (el modelo de datos, los contratos públicos, la seguridad) merecen pensar más allá de lo estrictamente necesario hoy.

Siguiente: [06 — Inmutabilidad](../06-inmutabilidad/).
