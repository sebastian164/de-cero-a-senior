# 03 — Code smells

## Idea sencilla

Un olor a gas en la cocina no es una explosión, pero indica que hay que revisar. Un *code smell* es lo mismo: una señal visible en la superficie del código que sugiere un problema de diseño más profundo. No es un error y no siempre requiere arreglo; es un motivo para mirar.

## Explicación técnica

El término lo popularizaron Kent Beck y Martin Fowler. Lo útil de los olores es que tienen nombre: permiten decir en una revisión "esto es envidia de características" en lugar de "no me gusta".

### Catálogo esencial

**Hinchazón: cosas que crecieron demasiado**

| Olor | Señal | Refactor habitual |
|---|---|---|
| Método largo | No cabe en pantalla; comentarios que separan secciones | Extraer método |
| Clase grande | Muchos atributos; grupos de métodos sin relación | Extraer clase |
| Lista larga de parámetros | Más de tres o cuatro | Introducir objeto parámetro |
| Obsesión por primitivos | `String cedula`, `long valor`, `int tipo` para conceptos | Objeto de valor, `enum` |
| Grupos de datos | Los mismos tres parámetros viajan siempre juntos | Extraer clase |

**Abuso de orientación a objetos**

| Olor | Señal | Refactor habitual |
|---|---|---|
| `switch` por tipo | Misma cadena condicional en varios métodos | Polimorfismo, Strategy, State |
| Herencia rechazada | Subclase que anula o no usa lo heredado | Composición |
| Campo temporal | Atributo que solo tiene valor en ciertos momentos | Extraer clase, pasar por parámetro |

**Obstáculos al cambio**

| Olor | Señal | Refactor habitual |
|---|---|---|
| Cambio divergente | Una clase cambia por motivos sin relación | Extraer clase (SRP) |
| Cirugía de escopeta | Un cambio toca muchas clases | Mover método, unificar |
| Jerarquías paralelas | Crear una subclase obliga a crear otra en otra jerarquía | Unificar, composición |

**Prescindibles**

| Olor | Señal | Refactor habitual |
|---|---|---|
| Código duplicado | Bloques iguales o casi iguales | Extraer método o clase |
| Código muerto | Nadie lo llama | Eliminar; Git lo recuerda |
| Generalidad especulativa | Abstracciones "por si acaso" | Eliminar (YAGNI) |
| Comentarios que explican qué hace el código | El código no se entiende solo | Renombrar, extraer |
| Clase de datos | Solo getters y setters | Mover comportamiento hacia ella |

**Acoplamiento**

| Olor | Señal | Refactor habitual |
|---|---|---|
| Envidia de características | Un método usa más datos de otra clase que de la suya | Mover método |
| Intimidad inapropiada | Dos clases conocen los detalles internos de la otra | Mover, extraer, ocultar |
| Cadena de mensajes | `a.getB().getC().getD()` | Ocultar delegado |
| Intermediario | Una clase que solo reenvía llamadas | Eliminar intermediario |

## Código con problemas

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente();
        cliente.nombre = "Ana";
        cliente.tipo = 2;
        cliente.calle = "Cra 10 # 20-30";
        cliente.ciudad = "Tunja";
        cliente.codigoPostal = "150001";
        Facturador facturador = new Facturador();
        System.out.println(facturador.etiquetaDeEnvio(cliente));
        System.out.println(facturador.descuento(cliente, 200_000));
    }
}

class Cliente {
    String nombre;
    int tipo;            // 1 = regular, 2 = frecuente, 3 = corporativo
    String calle;
    String ciudad;
    String codigoPostal;
}

class Facturador {

    String etiquetaDeEnvio(Cliente c) {
        return c.nombre + "\n" + c.calle + "\n" + c.ciudad + " " + c.codigoPostal;
    }

    long descuento(Cliente c, long valor) {
        if (c.tipo == 2) {
            return valor * 5 / 100;
        } else if (c.tipo == 3) {
            return valor * 12 / 100;
        }
        return 0;
    }
}
```

## Análisis

Cinco olores en treinta líneas:

- **Clase de datos:** `Cliente` no hace nada.
- **Obsesión por primitivos:** `int tipo` con un comentario que explica los valores.
- **Grupo de datos:** calle, ciudad y código postal viajan juntos; son una `Direccion` sin nombre.
- **Envidia de características:** los dos métodos de `Facturador` solo usan datos de `Cliente`.
- **`switch` por tipo:** en `descuento`, y aparecerá en cada regla que dependa del tipo.

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente("Ana", TipoCliente.FRECUENTE,
                new Direccion("Cra 10 # 20-30", "Tunja", "150001"));
        System.out.println(cliente.etiquetaDeEnvio());
        System.out.println(cliente.descuentoSobre(200_000));
    }
}

enum TipoCliente {
    REGULAR(0), FRECUENTE(5), CORPORATIVO(12);

    private final int porcentajeDescuento;

    TipoCliente(int porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }

    long descuentoSobre(long valor) {
        return valor * porcentajeDescuento / 100;
    }
}

record Direccion(String calle, String ciudad, String codigoPostal) {
    String enDosLineas() {
        return calle + "\n" + ciudad + " " + codigoPostal;
    }
}

record Cliente(String nombre, TipoCliente tipo, Direccion direccion) {

    String etiquetaDeEnvio() {
        return nombre + "\n" + direccion.enDosLineas();
    }

    long descuentoSobre(long valor) {
        return tipo.descuentoSobre(valor);
    }
}
```

`Facturador` desapareció: no tenía comportamiento propio.

## Taller

Auditoría de olores sobre un módulo real (anonimizado) o sobre un proyecto de código abierto.

1. Encuentra al menos ocho olores de al menos cuatro categorías. Para cada uno: nombre, ubicación, por qué lo es y refactor propuesto.
2. Ordénalos por relación costo/beneficio: cuál arreglarías primero y cuál dejarías.
3. Elige tres y aplícales el refactor.
4. Señala un olor que decidas **no** corregir y defiende la decisión.

Se revisará: uso correcto del vocabulario y criterio de priorización. Rama `taller/n4-03-smells`.

## Aplicación profesional

- **Revisión de código con vocabulario compartido:** nombrar el olor despersonaliza la crítica.
- **SonarQube y los inspectores del IDE** detectan duplicación, complejidad ciclomática, métodos largos. No detectan envidia de características ni nombres engañosos.
- **Olores típicos de Spring:** servicio dios, entidad anémica con `@Data`, controlador con lógica, `@Autowired` en diez campos, `Map<String, Object>` como tipo de retorno universal, `Utils` estáticas.
- **Deuda visible:** registra los olores importantes como tareas con su costo estimado en lugar de comentarios `TODO` que nadie lee.

## Preguntas senior

1. Diferencia entre cambio divergente y cirugía de escopeta. ¿Qué principio viola cada uno?
2. ¿Cuándo una clase de datos no es un olor?
3. Hay 400 avisos en SonarQube. ¿Cómo decides qué atender?
4. ¿Por qué eliminar código muerto es seguro y por qué la gente no lo hace?
5. ¿Qué olor consideras más caro a largo plazo y por qué?

## Cuándo no actuar

Un olor en código estable que nadie toca no necesita arreglo. La regla es pragmática: se refactoriza lo que se va a cambiar, cuando se va a cambiar.

Siguiente: [04 — Refactoring](../04-refactoring/).
