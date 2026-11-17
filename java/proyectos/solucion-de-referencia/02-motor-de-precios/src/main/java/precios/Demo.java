package precios;

import precios.Modelo.*;
import precios.Promocion.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static precios.Modelo.d;

/** Ejemplo ejecutable: calcula un carrito con un combo, un 2x1, un cupón y envío gratis, y explica el resultado. */
public class Demo {
    public static void main(String[] args) {
        Producto cafe = new Producto("Café", d("10.00"), "alimentos");
        Producto taza = new Producto("Taza", d("20.00"), "hogar");
        Combo desayuno = new Combo("Desayuno", d("25.00"), List.of(cafe, taza));
        Motor motor = new Motor(
                List.of(new DosPorUno("2x1 en café", "Café"), new Cupon("Cupón BIENVENIDA", "BIENVENIDA", d("10"))),
                Map.of("alimentos", d("0.05"), "hogar", d("0.19")), d("5.00"), d("100.00"));
        Resultado r = motor.calcular(new Carrito(List.of(new Linea(desayuno, 2), new Linea(cafe, 2)), Set.of("BIENVENIDA")));
        System.out.println(r.explicacion());
    }
}
