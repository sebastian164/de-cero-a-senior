package vigilancia.adaptadores.entrada;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import registro.RegistroDeEventos;
import registro.RegistroDeEventos.Mensaje;
import vigilancia.aplicacion.RegistrarTransferencia;
import vigilancia.aplicacion.RegistrarTransferencia.Resultado;
import vigilancia.aplicacion.RegistrarTransferencia.Transferencia;

/** Lee del tema, traduce cada mensaje a una llamada al puerto de entrada y confirma la posición. */
public final class ConsumidorDeTransferencias {

    private static final String TEMA = "banco.transferencias";   // el nombre es parte del contrato, no una clase compartida

    public record Lote(int registradas, int repetidas, int fallidas) {
    }

    private final RegistroDeEventos registro;
    private final String grupo;
    private final RegistrarTransferencia registrarTransferencia;
    private final List<Mensaje> fallidos = new ArrayList<>();

    public ConsumidorDeTransferencias(RegistroDeEventos registro, String grupo, RegistrarTransferencia registrarTransferencia) {
        this.registro = registro;
        this.grupo = grupo;
        this.registrarTransferencia = registrarTransferencia;
    }

    /**
     * Procesa lo pendiente. Con seCaeAntesDeConfirmar se simula que el proceso muere después de
     * procesar y antes de confirmar las posiciones: lo que ocurre en un despliegue o en una caída.
     */
    public Lote procesarLote(boolean seCaeAntesDeConfirmar) {
        int registradas = 0;
        int repetidas = 0;
        int fallidas = 0;
        for (Mensaje mensaje : registro.leer(grupo, TEMA)) {
            try {
                Resultado resultado = registrarTransferencia.registrar(traducir(mensaje.valor()));
                if (resultado == Resultado.REGISTRADA) {
                    registradas++;
                } else {
                    repetidas++;
                }
            } catch (IllegalArgumentException ilegible) {
                fallidos.add(mensaje);                           // reintentar no lo arregla: se aparta y se sigue
                fallidas++;
            }
            if (!seCaeAntesDeConfirmar) {
                registro.confirmar(grupo, TEMA, mensaje);        // solo después de procesar
            }
        }
        return new Lote(registradas, repetidas, fallidas);
    }

    public List<Mensaje> fallidos() {
        return List.copyOf(fallidos);
    }

    /** Lee los campos que necesita e ignora el resto: así el emisor puede añadir campos sin romper a nadie. */
    private static Transferencia traducir(String valor) {
        Map<String, String> campos = new HashMap<>();
        for (String par : valor.split(";")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2) {
                campos.put(partes[0], partes[1]);
            }
        }
        try {
            return new Transferencia(exigir(campos, "id"), exigir(campos, "cuenta"),
                    Long.parseLong(exigir(campos, "centavos")), Instant.parse(exigir(campos, "ocurridoEn")));
        } catch (RuntimeException malFormado) {
            throw new IllegalArgumentException("mensaje ilegible: " + valor, malFormado);
        }
    }

    private static String exigir(Map<String, String> campos, String nombre) {
        String valor = campos.get(nombre);
        if (valor == null) {
            throw new IllegalArgumentException("falta el campo " + nombre);
        }
        return valor;
    }
}
