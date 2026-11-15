package banco.jpa;

import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/** La fila de una cuenta. No tiene reglas: solo datos y la traducción desde y hacia el dominio. */
@Entity
@Table(name = "cuenta")
public class CuentaJpa {

    @Id
    private String id;

    @Column(nullable = false)
    private String titular;

    @Column(name = "saldo_centavos", nullable = false)
    private long saldoCentavos;

    @Column(name = "enviado_hoy_centavos", nullable = false)
    private long enviadoHoyCentavos;

    @Column(name = "dia_del_tope", nullable = false)
    private LocalDate diaDelTope;

    @Version                                                     // Hibernate la comprueba y la incrementa en cada UPDATE
    private long version;

    protected CuentaJpa() {                                      // lo exige JPA
    }

    static CuentaJpa nueva(Cuenta cuenta) {
        CuentaJpa fila = new CuentaJpa();
        fila.id = cuenta.id();
        fila.copiarDesde(cuenta);
        return fila;
    }

    void copiarDesde(Cuenta cuenta) {
        this.titular = cuenta.titular();
        this.saldoCentavos = cuenta.saldo().centavos();
        this.enviadoHoyCentavos = cuenta.enviadoHoy().centavos();
        this.diaDelTope = cuenta.diaDelTope();
    }

    Cuenta aDominio() {
        return Cuenta.existente(id, titular, new Dinero(saldoCentavos), new Dinero(enviadoHoyCentavos), diaDelTope, version);
    }

    long version() {
        return version;
    }
}
