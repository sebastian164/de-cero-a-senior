package banco.transferencias.aplicacion.puerto.salida;

import banco.transferencias.dominio.Dinero;
import banco.transferencias.dominio.NumeroCuenta;
import banco.transferencias.dominio.Transferencia;

import java.time.LocalDate;

public interface RegistroDeTransferencias {
    void registrar(Transferencia transferencia);
    /** Suma de lo transferido (aceptado) desde la cuenta en ese día. */
    Dinero transferidoDesde(NumeroCuenta origen, LocalDate dia);
}
