package banco.aplicacion.puertos.salida;

import banco.aplicacion.EventoDeCuenta;

public interface PublicadorDeEventos {

    void publicar(EventoDeCuenta evento);
}
