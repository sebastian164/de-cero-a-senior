/** Cliente del punto 4: solo conoce MetricasDeUsuarios. */
public class TableroMetricas {
    private final MetricasDeUsuarios metricas;

    public TableroMetricas(MetricasDeUsuarios metricas) {
        this.metricas = metricas;
    }

    public String resumen() {
        int activos = metricas.contarActivosUltimoMes();
        return activos == 0 ? "Sin usuarios activos este mes" : "Usuarios activos este mes: " + activos;
    }
}
