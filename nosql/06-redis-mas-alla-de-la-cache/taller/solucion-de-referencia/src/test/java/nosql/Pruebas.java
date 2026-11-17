package nosql;

import comun.Entorno;
import java.time.Clock;
import java.util.UUID;
import nosql.aplicacion.PoliticaDeReintento;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;

final class Pruebas {
    private Pruebas() {}

    static Composicion.Sistema nuevos(String adaptador, PoliticaDeReintento politica) {
        return Composicion.elegir(adaptador, Entorno.mongoUrl(), "t04_" + UUID.randomUUID().toString().substring(0, 8), politica, Clock.systemUTC());
    }

    static Solicitud solicitud(String clave, String cliente, String... lineas) {
        return new Solicitud(clave, cliente, java.util.Arrays.stream(lineas).map(t -> new Linea(t.split(":")[0], Integer.parseInt(t.split(":")[1]))).toList());
    }
}
