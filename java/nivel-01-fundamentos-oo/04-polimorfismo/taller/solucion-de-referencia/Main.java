import java.util.List;

public class Main {
    public static void main(String[] args) {
        Transporte moto = Transportes.desdeTexto("MOTO");
        Transporte camion = Transportes.desdeTexto("CAMION");
        System.out.println(moto.costo(new Envio(3, 8)));
        System.out.println(camion.costo(new Envio(300, 120)));
        System.out.println(moto.tiempoEstimadoHoras(8));
        System.out.println(moto.puedeLlevar(new Envio(40, 1)));

        List<Transporte> flota = List.of(moto, camion, new Bicicleta(), new Dron());
        Envio paquete = new Envio(1, 5);
        System.out.println("Más barato para 1 kg a 5 km: "
                + Transportes.masBarato(flota, paquete).map(Transporte::nombre).orElse("ninguno"));
        System.out.println("Más barato para 300 kg a 120 km: "
                + Transportes.masBarato(flota, new Envio(300, 120)).map(Transporte::nombre).orElse("ninguno"));
    }
}
