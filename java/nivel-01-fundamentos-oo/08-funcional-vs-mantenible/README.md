# 08 — Código funcional frente a código mantenible

Aquí "funcional" significa "que funciona", no "programación funcional".

## Paso 1 — Explicación para principiantes

Dos casas tienen luz en todas las habitaciones. En una, los cables van dentro de las paredes, con un tablero donde cada interruptor está rotulado. En la otra, los cables cruzan los pasillos, hay empalmes con cinta y nadie sabe qué interruptor corta qué.

Las dos funcionan. La diferencia aparece el día que hay que instalar un aire acondicionado: en la primera es una tarde de trabajo; en la segunda es un riesgo de incendio.

El código funcional resuelve el problema de hoy. El código mantenible además permite resolver el de mañana.

## Paso 2 — Explicación técnica

Que el código funcione es el requisito mínimo, no el objetivo. La mayor parte del costo de un sistema ocurre después de la primera entrega: corregir, extender, adaptar. El código se lee muchas más veces de las que se escribe.

### Qué hace mantenible a un código

| Cualidad | Pregunta que la comprueba |
|---|---|
| Legible | ¿Alguien que no lo escribió entiende qué hace sin ejecutarlo? |
| Modificable | ¿Un cambio de requisito toca un lugar o veinte? |
| Verificable | ¿Se puede probar una parte sin montar todo? |
| Predecible | ¿Hace lo que su nombre dice y nada más? |

### Deuda técnica

Cada atajo de diseño es un préstamo: entregas antes, y pagas intereses cada vez que tocas ese código. No toda deuda es mala: un préstamo consciente para llegar a una fecha puede ser buena decisión. Lo que arruina proyectos es la deuda que nadie sabe que tiene.

### Un orden de trabajo sensato

1. Haz que funcione.
2. Haz que sea correcto: nombres, estructura, responsabilidades.
3. Haz que sea rápido, solo si se demostró que hace falta.

El error habitual es detenerse en el paso 1.

### Lo que este nivel te deja como lista de revisión

- ¿Cada concepto del negocio tiene una clase? (01)
- ¿Los objetos protegen sus reglas? (02)
- ¿Cada herencia modela un "es un" real? (03)
- ¿Hay cadenas de `if` por tipo? (04)
- ¿El código cliente depende de contratos? (05)
- ¿Se puede cambiar una pieza sin crear otra clase? (06)
- ¿Lo que cambia junto está junto y lo demás separado? (07)

## Paso 3 — Código que funciona, pero no es mantenible

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        System.out.println(calc(4_000_000, 1, 3, true));
        System.out.println(calc(1_500_000, 2, 0, false));
    }

    // t: 1 = fijo, 2 = por horas (s es el valor de 160 horas)
    static long calc(long s, int t, int a, boolean x) {
        long r = s;
        if (t == 1) {
            if (a > 2) {
                r = r + s * 5 / 100;
            }
            if (a > 5) {
                r = r + s * 5 / 100;
            }
        }
        if (x) {
            r = r + 200_000;
        }
        r = r - s * 4 / 100;
        r = r - s * 4 / 100;
        if (s <= 2_847_000) {
            r = r + 200_000;
        }
        return r;
    }
}
```

## Paso 4 — Análisis

El código calcula el pago neto de un empleado y da el resultado esperado. Intenta responder sin ejecutarlo:

- ¿Qué son `a` y `x`? ¿Por qué hay dos restas idénticas del 4 %?
- ¿Qué es 2.847.000? ¿Qué pasa cuando ese valor cambie el próximo año? ¿En cuántos otros lugares del sistema estará escrito?
- Hay dos sumas de 200.000 que significan cosas distintas. Si una cambia, ¿cuál es cuál?
- ¿Un empleado por horas con cuatro años de antigüedad recibe bonificación? El código dice que no. ¿Es una regla o un olvido?

Problemas concretos:

- **Nombres que no dicen nada.** El conocimiento del negocio está en la cabeza del autor.
- **Números mágicos.** Valores sin nombre y repetidos.
- **Tipos primitivos para conceptos.** Un `int` para el tipo de contrato, un `boolean` cuyo significado hay que adivinar en la llamada: `calc(4_000_000, 1, 3, true)`.
- **Varias reglas mezcladas en un bloque.** No se puede probar ni cambiar una sin leer todas.
- **Un comentario que intenta compensar el código.**

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Empleado ana = new Empleado(4_000_000, TipoContrato.FIJO, 3, true);
        Empleado luis = new Empleado(1_500_000, TipoContrato.POR_HORAS, 0, false);

        Nomina nomina = new Nomina();
        System.out.println(nomina.pagoNeto(ana));
        System.out.println(nomina.pagoNeto(luis));
    }
}

enum TipoContrato { FIJO, POR_HORAS }

class Empleado {

    private final long salarioBase;
    private final TipoContrato contrato;
    private final int aniosAntiguedad;
    private final boolean trabajaRemoto;

    Empleado(long salarioBase, TipoContrato contrato, int aniosAntiguedad, boolean trabajaRemoto) {
        this.salarioBase = salarioBase;
        this.contrato = contrato;
        this.aniosAntiguedad = aniosAntiguedad;
        this.trabajaRemoto = trabajaRemoto;
    }

    long salarioBase() { return salarioBase; }
    TipoContrato contrato() { return contrato; }
    int aniosAntiguedad() { return aniosAntiguedad; }
    boolean trabajaRemoto() { return trabajaRemoto; }
}

class Nomina {

    private static final long TOPE_AUXILIO_TRANSPORTE = 2_847_000;
    private static final long AUXILIO_TRANSPORTE = 200_000;
    private static final long AUXILIO_CONECTIVIDAD = 200_000;
    private static final int PORCENTAJE_SALUD = 4;
    private static final int PORCENTAJE_PENSION = 4;
    private static final int PORCENTAJE_BONO_ANTIGUEDAD = 5;

    long pagoNeto(Empleado empleado) {
        return empleado.salarioBase()
                + bonoAntiguedad(empleado)
                + auxilios(empleado)
                - deducciones(empleado);
    }

    private long bonoAntiguedad(Empleado empleado) {
        if (empleado.contrato() != TipoContrato.FIJO) {
            return 0;
        }
        int tramos = 0;
        if (empleado.aniosAntiguedad() > 2) {
            tramos++;
        }
        if (empleado.aniosAntiguedad() > 5) {
            tramos++;
        }
        return porcentaje(empleado.salarioBase(), PORCENTAJE_BONO_ANTIGUEDAD) * tramos;
    }

    private long auxilios(Empleado empleado) {
        long total = 0;
        if (empleado.trabajaRemoto()) {
            total += AUXILIO_CONECTIVIDAD;
        }
        if (empleado.salarioBase() <= TOPE_AUXILIO_TRANSPORTE) {
            total += AUXILIO_TRANSPORTE;
        }
        return total;
    }

    private long deducciones(Empleado empleado) {
        return porcentaje(empleado.salarioBase(), PORCENTAJE_SALUD)
                + porcentaje(empleado.salarioBase(), PORCENTAJE_PENSION);
    }

    private long porcentaje(long valor, int porcentaje) {
        return valor * porcentaje / 100;
    }
}
```

El resultado numérico es idéntico. Lo que cambió:

1. **`pagoNeto` se lee como la regla de negocio:** salario más bono más auxilios menos deducciones.
2. **Cada número tiene nombre.** Las dos sumas de 200.000 ya son distinguibles, y pueden cambiar por separado.
3. **Las dos restas idénticas resultaron ser salud y pensión.** El código ahora lo dice.
4. **`TipoContrato` reemplaza al `int`.** `new Empleado(…, 7, …)` ya no compila.
5. **La regla dudosa quedó visible:** el bono de antigüedad solo aplica a contrato fijo. Ahora es posible preguntarle al negocio si es intencional.

Este refactor no cambió comportamiento: solo estructura. Esa es la definición de refactorizar, y se estudia con pruebas de respaldo en el Nivel 4.

## Paso 6 — Taller integrador del Nivel 1

Un parqueadero cobra así:

- Carros: 5.000 la primera hora, 3.000 cada hora adicional. Motos: 2.000 y 1.000. Bicicletas: gratis.
- Fracción de hora cuenta como hora completa.
- Más de 12 horas se cobra como tarifa de día: 30.000 carro, 12.000 moto.
- Los residentes del conjunto tienen 50 % de descuento.
- El parqueadero tiene cupo: 20 carros, 30 motos, 15 bicicletas. No se admite un vehículo si no hay cupo de su tipo.
- No se puede registrar la salida de un vehículo que no ha entrado, ni la entrada de una placa que ya está adentro.

**Parte A — Que funcione.** Escribe la versión más rápida que se te ocurra, en un solo archivo, sin pensar en diseño. Guárdala en `taller/solucion/a-funciona/`.

**Parte B — Que sea mantenible.** Reescríbela aplicando todo el Nivel 1. Guárdala en `taller/solucion/b-mantenible/`.

**Parte C — La prueba del cambio.** Llegan tres requisitos nuevos. Para cada uno, anota qué habría que tocar en la versión A y en la B:

1. Vehículos eléctricos: misma tarifa del carro con 20 % de descuento, acumulable con el de residente.
2. La tarifa de día pasa a aplicarse desde las 10 horas.
3. Los domingos las motos no pagan.

**Parte D — Defensa.** En `ANALISIS.md`: qué concepto de cada lección del nivel aplicaste y dónde, qué decidiste no aplicar y por qué, y cuánto más larga es la versión B que la A. ¿Valió la pena?

Entrega: rama `taller/n1-08-integrador`.

## Paso 7 — Revisión técnica

- ¿La versión B resiste los tres cambios tocando pocas clases?
- ¿Los nombres permiten entender las reglas sin comentarios?
- ¿Quedan números mágicos o primitivos representando conceptos?
- ¿Hay sobrediseño: abstracciones que ningún requisito justifica?

## Paso 8 — Aplicación profesional

- **La revisión de código es el filtro.** "Funciona y pasó las pruebas" no basta para aprobar un pull request. La pregunta es si la próxima persona podrá cambiarlo.
- **Regla del explorador:** deja el código un poco mejor de como lo encontraste. Mejoras pequeñas y continuas pagan la deuda sin detener al equipo.
- **Herramientas de análisis estático** (SonarQube, Checkstyle, los avisos del IDE) detectan parte de estos problemas. No detectan nombres que mienten ni reglas en el lugar equivocado.
- **Hay código que no necesita ser mantenible:** un prototipo que se va a desechar, un script que corre una vez, una prueba de concepto. El criterio senior no es aplicar siempre todo, sino saber cuándo.
- **Explicar el costo al negocio:** "refactorizar" no convence a nadie. "Hoy un cambio de tarifa nos toma tres días y un riesgo alto; después de esto tomará dos horas" sí.

## Paso 9 — Evaluación senior

1. ¿Cómo distingues una deuda técnica aceptable de una que hay que pagar ya?
2. Tienes dos semanas y un módulo de 5.000 líneas que funciona pero nadie se atreve a tocar. ¿Qué haces primero y por qué?
3. Un desarrollador entrega rápido y su código funciona, pero cada cambio posterior sobre su trabajo tarda el triple. ¿Cómo lo conviertes en una conversación basada en datos?
4. ¿Cuándo el código mantenible es sobrediseño? Da una señal objetiva.
5. ¿Qué se pierde y qué se gana al reemplazar un comentario por un nombre?

## Comprobación antes de pasar al Nivel 2

Debes poder, ante un código cualquiera:

- Nombrar los problemas de diseño con el vocabulario del nivel.
- Proponer una mejora y decir cuánto cuesta.
- Decir cuándo no vale la pena mejorarlo.

Siguiente: [Nivel 2 — Principios SOLID](../../nivel-02-solid/).
