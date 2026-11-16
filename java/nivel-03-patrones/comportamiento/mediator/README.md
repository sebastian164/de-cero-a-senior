# Mediator

Familia: comportamiento. Frecuencia en Java empresarial: media.

## El problema

Varios objetos necesitan coordinarse y cada uno conoce a los demás. Con cinco participantes puede haber veinte relaciones directas. Cambiar uno afecta a todos, ninguno se puede reutilizar solo y la regla de coordinación no vive en ningún lugar: está repartida.

## Cómo reconocer que lo necesitas

- Objetos que se llaman entre sí en red, con referencias cruzadas.
- No se puede probar uno sin instanciar los otros.
- Las reglas de interacción ("cuando A cambia, B se desactiva y C se recalcula") están dispersas.
- Dependencias circulares entre componentes.

## Antes

```java
class CampoPais {
    CampoCiudad ciudad; CampoIndicativo indicativo; BotonEnviar boton;
    void cambiar(String pais) {
        ciudad.cargarCiudadesDe(pais);
        indicativo.fijar(pais.equals("CO") ? "+57" : "+52");
        boton.habilitar(false);
    }
}
// y CampoCiudad conoce a BotonEnviar, que conoce a todos...
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        FormularioRegistro formulario = new FormularioRegistro();
        formulario.pais.cambiar("CO");
        formulario.ciudad.cambiar("Tunja");
    }
}

/** El mediador: único lugar donde vive la regla de coordinación. */
interface Mediador {
    void notificar(Campo origen);
}

/** Los participantes solo conocen al mediador. */
class Campo {

    final String nombre;
    private final Mediador mediador;
    private String valor = "";

    Campo(String nombre, Mediador mediador) {
        this.nombre = nombre;
        this.mediador = mediador;
    }

    void cambiar(String nuevoValor) {
        this.valor = nuevoValor;
        mediador.notificar(this);
    }

    void fijarSinAvisar(String nuevoValor) {
        this.valor = nuevoValor;
    }

    String valor() {
        return valor;
    }
}

class FormularioRegistro implements Mediador {

    final Campo pais = new Campo("pais", this);
    final Campo ciudad = new Campo("ciudad", this);
    final Campo indicativo = new Campo("indicativo", this);
    private boolean envioHabilitado = false;

    @Override
    public void notificar(Campo origen) {
        if (origen == pais) {
            ciudad.fijarSinAvisar("");
            indicativo.fijarSinAvisar(pais.valor().equals("CO") ? "+57" : "+52");
        }
        envioHabilitado = !pais.valor().isEmpty() && !ciudad.valor().isEmpty();
        System.out.println("pais=" + pais.valor() + " ciudad=" + ciudad.valor()
                + " indicativo=" + indicativo.valor() + " envío=" + envioHabilitado);
    }
}
```

`Campo` es reutilizable en cualquier formulario. La regla "al cambiar el país se limpia la ciudad" está en un único lugar.

## Mediator frente a patrones parecidos

- **Observer**: el emisor no sabe quién escucha y no hay coordinación central. En Mediator hay un coordinador que conoce a todos.
- **Facade**: simplifica el acceso desde afuera, en una dirección. Mediator coordina a los de adentro, en ambas direcciones.

## Alternativas

- **Llamadas directas**, si hay dos o tres participantes.
- **Eventos (Observer)**, si las reacciones son independientes entre sí.
- **Un servicio de aplicación** que orqueste, que es un mediador con otro nombre.

## En Spring

- `DispatcherServlet` media entre controladores, resolutores de vistas, adaptadores e interceptores: ninguno conoce a los otros.
- Un caso de uso que orquesta varios servicios de dominio.
- Bibliotecas de bus de comandos y consultas (el estilo de MediatR en .NET tiene equivalentes en Java): los controladores envían un mensaje y el bus encuentra el manejador.
- En frontend, un contenedor de estado (Redux, NgRx) cumple el mismo papel.

## Trade-offs

- A favor: los participantes no se conocen; la coordinación está centralizada y es visible; los participantes son reutilizables y probables por separado.
- En contra: el mediador tiende a crecer hasta ser un objeto dios; la lógica se concentra y el mediador se vuelve el punto de cambio constante.

## Errores comunes

- Un único mediador para toda la aplicación.
- Reglas de negocio de los participantes migrando al mediador.
- Notificaciones que disparan otras notificaciones en bucle (por eso el ejemplo tiene `fijarSinAvisar`).

## Cuándo no usarlo

Con pocas relaciones y simples; cuando la coordinación es de una sola vía (Facade); cuando eventos independientes son suficientes.

## Taller

Una torre de control coordina aviones que quieren aterrizar o despegar en una sola pista.

1. Implementa primero aviones que se consultan entre sí para saber si la pista está libre. Observa el acoplamiento.
2. Introduce la torre como mediador: los aviones solo hablan con ella.
3. Agrega prioridad para emergencias y una cola de espera. ¿Dónde vive esa regla?
4. Tu torre, ¿está creciendo demasiado? Propón cómo dividirla sin volver al diseño inicial.

Entrega: rama `taller/n3-mediator`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
