package reservas.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    // Desajuste 2: el mismo dato (la sede) aparece dos veces; JPA no sabe que deben coincidir.
    // La coherencia la garantiza la clave foránea compuesta en la base, no el mapeo.
    @Column(name = "sede_id", nullable = false)
    private Long sedeId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "tarifa_id", nullable = false)
    private Long tarifaId;

    // Desajuste 3: la franja es un rango (tstzrange) en la restricción de exclusión, pero JPA solo ve dos instantes.
    // La regla "no se solapan" no existe en el modelo de objetos: solo se descubre al confirmar.
    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Column(name = "precio_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE_PAGO;

    @Column(name = "creada_en", nullable = false, insertable = false, updatable = false)
    private Instant creadaEn;

    @Version
    private long version;

    protected Reserva() {
    }

    public Reserva(Cancha cancha, Long clienteId, Long tarifaId, Instant inicio, Instant fin, BigDecimal precioTotal) {
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La franja debe terminar después de empezar");
        }
        this.cancha = cancha;
        this.sedeId = cancha.getSedeId();
        this.clienteId = clienteId;
        this.tarifaId = tarifaId;
        this.inicio = inicio;
        this.fin = fin;
        this.precioTotal = precioTotal;
    }

    public void cancelar() { this.estado = EstadoReserva.CANCELADA; }
    public Long getId() { return id; }
    public EstadoReserva getEstado() { return estado; }
    public BigDecimal getPrecioTotal() { return precioTotal; }
}
