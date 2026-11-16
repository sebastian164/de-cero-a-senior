package transacciones.auditoria;

import org.springframework.stereotype.Service;

@Service
public class Cuentas {
    @Auditable("bloquear cuenta")
    public void bloquear(String cuenta) { if (cuenta.isBlank()) throw new IllegalArgumentException("cuenta vacía"); }

    public void consultar(String cuenta) { }      // sin anotación: no se audita
}
