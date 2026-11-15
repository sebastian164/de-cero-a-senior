package banco;

import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.ReglaDeNegocioViolada;
import banco.dominio.Transferencia;
import java.time.LocalDate;

/** Las reglas, probadas sin nada alrededor. */
public final class PruebasDelDominio {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private PruebasDelDominio() {
    }

    public static void ejecutar() {
        System.out.println("Dominio:");

        Comprobar.lanza("un valor no puede ser negativo", ReglaDeNegocioViolada.class, () -> new Dinero(-1));

        Cuenta ana = Cuenta.abrir("c-1", "Ana", Dinero.pesos(1_000_000), HOY);
        Cuenta luis = Cuenta.abrir("c-2", "Luis", Dinero.pesos(200_000), HOY);

        Transferencia.ejecutar(ana, luis, Dinero.pesos(300_000), HOY);
        Comprobar.igual("transferir resta del origen", Dinero.pesos(700_000), ana.saldo());
        Comprobar.igual("transferir suma al destino", Dinero.pesos(500_000), luis.saldo());
        Comprobar.igual("el dinero total no cambia", Dinero.pesos(1_200_000), ana.saldo().mas(luis.saldo()));

        Comprobar.lanza("no se transfiere más de lo que hay", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(luis, ana, Dinero.pesos(500_001), HOY));
        Comprobar.lanza("no se transfiere a la misma cuenta", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(ana, ana, Dinero.pesos(1_000), HOY));
        Comprobar.lanza("no se transfiere cero", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(ana, luis, Dinero.CERO, HOY));

        Transferencia.ejecutar(ana, luis, Dinero.pesos(500_000), HOY);
        Comprobar.igual("se puede llegar exactamente al tope diario", Cuenta.TOPE_DIARIO, ana.enviadoHoy());
        Comprobar.lanza("un centavo por encima del tope se rechaza", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(ana, luis, new Dinero(1), HOY));
        Comprobar.igual("una transferencia rechazada no mueve nada", Dinero.pesos(200_000), ana.saldo());

        Transferencia.ejecutar(ana, luis, Dinero.pesos(100_000), MANANA);
        Comprobar.igual("al día siguiente el tope empieza de cero", Dinero.pesos(100_000), ana.enviadoHoy());

        Comprobar.igual("transferir todo el saldo deja la cuenta en cero", Dinero.CERO, transferirTodo());
    }

    private static Dinero transferirTodo() {
        Cuenta origen = Cuenta.abrir("c-3", "Marta", Dinero.pesos(50_000), HOY);
        Cuenta destino = Cuenta.abrir("c-4", "Pedro", Dinero.CERO, HOY);
        Transferencia.ejecutar(origen, destino, Dinero.pesos(50_000), HOY);
        return origen.saldo();
    }
}
