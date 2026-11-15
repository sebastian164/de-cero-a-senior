public class Main {

    public static void main(String[] args) {
        CalculadoraPrecio calculadora = new CalculadoraPrecio();
        System.out.println(calculadora.precioFinal(100_000, "TECNOLOGIA", "BOGOTA", true, "VERANO10"));
        System.out.println(calculadora.precioFinal(100_000, "ALIMENTOS", "TUNJA", false, null));
    }
}

class CalculadoraPrecio {

    long precioFinal(long base, String categoria, String ciudad, boolean esFragil, String cupon) {
        long precio = base;

        // impuesto según categoría
        if (categoria.equals("TECNOLOGIA")) {
            precio += base * 19 / 100;
        } else if (categoria.equals("ALIMENTOS")) {
            precio += base * 5 / 100;
        }

        // recargo de envío
        if (ciudad.equals("BOGOTA")) {
            precio += 8_000;
        } else {
            precio += 15_000;
        }

        // empaque especial
        if (esFragil) {
            precio += 6_000;
        }

        // cupones
        if ("VERANO10".equals(cupon)) {
            precio -= base * 10 / 100;
        } else if ("BIENVENIDA".equals(cupon)) {
            precio -= 20_000;
        }

        return precio;
    }
}
