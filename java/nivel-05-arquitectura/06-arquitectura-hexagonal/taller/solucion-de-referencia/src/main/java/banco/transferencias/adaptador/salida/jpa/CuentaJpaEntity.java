package banco.transferencias.adaptador.salida.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cuentas")
class CuentaJpaEntity {
    @Id String numero;
    long saldo;
    long topeDiario;

    protected CuentaJpaEntity() { }
    CuentaJpaEntity(String numero, long saldo, long topeDiario) { this.numero = numero; this.saldo = saldo; this.topeDiario = topeDiario; }
}
