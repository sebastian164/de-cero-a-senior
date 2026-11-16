import java.util.List;

public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        // Mismos resultados que el código de inicio.
        igual("costo moto", 7_000L, new Moto().costo(new Envio(3, 8)));
        igual("costo camión", 224_000L, new Camion().costo(new Envio(300, 120)));
        igual("tiempo moto", 1, new Moto().tiempoEstimadoHoras(8));
        igual("moto no lleva 40 kg", false, new Moto().puedeLlevar(new Envio(40, 1)));
        igual("bicicleta costo", 3_500L, new Bicicleta().costo(new Envio(1, 5)));
        igual("tiempo bicicleta", 2, new Bicicleta().tiempoEstimadoHoras(24));
        igual("tiempo camión mínimo", 2, new Camion().tiempoEstimadoHoras(10));
        // Dron.
        igual("dron 2 kg 10 km", true, new Dron().puedeLlevar(new Envio(2, 10)));
        igual("dron 3 kg", false, new Dron().puedeLlevar(new Envio(3, 5)));
        igual("dron 11 km", false, new Dron().puedeLlevar(new Envio(1, 11)));
        // Más barato.
        List<Transporte> flota = List.of(new Moto(), new Camion(), new Bicicleta(), new Dron());
        igual("barato liviano", "BICICLETA", Transportes.masBarato(flota, new Envio(1, 5)).orElseThrow().nombre());
        igual("barato pesado", "CAMION", Transportes.masBarato(flota, new Envio(300, 120)).orElseThrow().nombre());
        igual("nadie puede", true, Transportes.masBarato(flota, new Envio(6_000, 1)).isEmpty());
        igual("fábrica dron", "DRON", Transportes.desdeTexto("DRON").nombre());
        total++;
        try { Transportes.desdeTexto("moto"); fallos++; System.out.println("FALLA texto desconocido"); }
        catch (IllegalArgumentException e) { }
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }
}
