package banco.jpa;

import banco.dominio.Dinero;
import banco.dominio.Movimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "movimiento")
public class MovimientoJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private String cuentaId;

    @Enumerated(EnumType.STRING)                                 // el nombre, no la posición: reordenar el enum no rompe los datos
    @Column(nullable = false)
    private Movimiento.Tipo tipo;

    @Column(nullable = false)
    private long centavos;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false)
    private String referencia;

    protected MovimientoJpa() {
    }

    static MovimientoJpa desde(Movimiento movimiento) {
        MovimientoJpa fila = new MovimientoJpa();
        fila.cuentaId = movimiento.cuentaId();
        fila.tipo = movimiento.tipo();
        fila.centavos = movimiento.valor().centavos();
        fila.fecha = movimiento.fecha();
        fila.referencia = movimiento.referencia();
        return fila;
    }

    Movimiento aDominio() {
        return new Movimiento(cuentaId, tipo, new Dinero(centavos), fecha, referencia);
    }
}
