import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        RepositorioDeMovimientos repositorio = new RepositorioDeMovimientos();
        ServicioDeTasas tasas = new ServicioDeTasas();
        ServicioDeMovimientos servicio = new ServicioDeMovimientos(repositorio, tasas);

        List<Movimiento> devueltos = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            devueltos.add(servicio.registrar("cuenta-" + i, 10_000).block());   // 100 dólares cada uno
        }

        System.out.println("Devueltos por el servicio:     " + devueltos.size() + " movimientos");
        System.out.println("Primero:                       " + devueltos.getFirst());
        System.out.println("Guardados en el repositorio:   " + repositorio.cuantos());
        System.out.println("Llamadas remotas a las tasas:  " + tasas.llamadasRemotas() + " (con la caché llena bastaba 1)");
    }
}
