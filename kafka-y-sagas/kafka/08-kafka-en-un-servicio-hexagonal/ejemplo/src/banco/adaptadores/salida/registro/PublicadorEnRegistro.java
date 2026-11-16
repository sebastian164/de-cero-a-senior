package banco.adaptadores.salida.registro;

import banco.aplicacion.EventoDeCuenta;
import java.util.function.Consumer;
import registro.RegistroDeEventos;

/** El destino del mensajero de la bandeja de salida: traduce un evento del banco a un mensaje del tema. */
public final class PublicadorEnRegistro implements Consumer<EventoDeCuenta> {

    public static final String TEMA = "banco.transferencias";

    private final RegistroDeEventos registro;

    public PublicadorEnRegistro(RegistroDeEventos registro) {
        this.registro = registro;
    }

    @Override
    public void accept(EventoDeCuenta evento) {
        // La clave es la cuenta: todos los eventos de una cuenta quedan en orden, en la misma partición.
        registro.publicar(TEMA, evento.cuentaId(), serializar(evento));
    }

    /** El contrato entre servicios: texto con campos nombrados. El receptor ignora los que no conozca. */
    static String serializar(EventoDeCuenta evento) {
        return "id=" + evento.id() + ";tipo=" + evento.tipo() + ";cuenta=" + evento.cuentaId()
                + ";centavos=" + evento.centavos() + ";ocurridoEn=" + evento.ocurridoEn();
    }
}
