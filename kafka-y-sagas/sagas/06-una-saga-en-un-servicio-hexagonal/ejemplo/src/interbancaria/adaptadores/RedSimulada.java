package interbancaria.adaptadores;

import interbancaria.aplicacion.PuertosDeSalida.RedInterbancaria;
import interbancaria.aplicacion.PuertosDeSalida.RedSinRespuesta;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** El otro banco y la red que lleva hasta él, con fallos programables. */
public final class RedSimulada implements RedInterbancaria {

    private final Set<String> cuentasQueExisten = new HashSet<>();
    private final Map<String, Respuesta> yaAtendidos = new LinkedHashMap<>();   // el otro banco también deduplica
    private long recibidoCentavos = 0;
    private int llamadas = 0;
    private int respuestasPorPerder = 0;
    private int caidasPorOcurrir = 0;

    public void existe(String cuentaDestino) {
        cuentasQueExisten.add(cuentaDestino);
    }

    /** Las próximas llamadas llegan al otro banco, pero la respuesta se pierde por el camino. */
    public void perderRespuestas(int cuantas) {
        respuestasPorPerder = cuantas;
    }

    /** En las próximas llamadas, nuestro proceso muere antes de poder llamar. */
    public void matarElProceso(int cuantas) {
        caidasPorOcurrir = cuantas;
    }

    @Override
    public Respuesta enviar(String idDelEnvio, String bancoDestino, String cuentaDestino, long centavos) {
        if (caidasPorOcurrir > 0) {
            caidasPorOcurrir--;
            throw new IllegalStateException("el proceso murió antes de llamar al otro banco");
        }
        llamadas++;
        Respuesta respuesta = yaAtendidos.get(idDelEnvio);
        if (respuesta == null) {                                 // primera vez que el otro banco ve este envío
            respuesta = cuentasQueExisten.contains(cuentaDestino) ? Respuesta.ACEPTADO : Respuesta.RECHAZADO;
            yaAtendidos.put(idDelEnvio, respuesta);
            if (respuesta == Respuesta.ACEPTADO) {
                recibidoCentavos += centavos;
            }
        }
        if (respuestasPorPerder > 0) {
            respuestasPorPerder--;
            throw new RedSinRespuesta("tiempo de espera agotado");
        }
        return respuesta;
    }

    public long recibidoCentavos() {
        return recibidoCentavos;
    }

    public int llamadas() {
        return llamadas;
    }
}
