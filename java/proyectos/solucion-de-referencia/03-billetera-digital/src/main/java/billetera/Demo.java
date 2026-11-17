package billetera;

import billetera.Billetera.Cuenta;

import java.math.BigDecimal;
import java.time.Clock;

/** Ejemplo ejecutable: deposita, retira y transfiere, e imprime el historial. */
public class Demo {
    public static void main(String[] args) {
        Clock reloj = Clock.systemUTC();
        Cuenta ana = new Cuenta("ana", "1234", reloj);
        Cuenta beto = new Cuenta("beto", "0000", reloj);
        ana.depositar(new BigDecimal("500.00"));
        ana.retirar("1234", new BigDecimal("100.00"));
        ana.transferir("1234", beto, new BigDecimal("200.00"));
        System.out.println("Saldo de ana: " + ana.saldo() + ", saldo de beto: " + beto.saldo());
        ana.historial().forEach(m -> System.out.println(m.tipo() + " " + m.monto()));
        try {
            ana.retirar("9999", BigDecimal.ONE);
        } catch (Billetera.ErrorDeDominio e) {
            System.out.println("Error de dominio: " + e.getMessage());
        }
    }
}
