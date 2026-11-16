import java.time.Duration;
import java.time.LocalDateTime;

public class Pruebas {
    private static int fallos = 0, total = 0;
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 8, 0);

    public static void main(String[] args) {
        CalculadoraCobro c = new CalculadoraCobro();
        Vehiculo carro = new Vehiculo("C1", TipoVehiculo.CARRO, false);
        Vehiculo moto = new Vehiculo("M1", TipoVehiculo.MOTO, false);
        Vehiculo bici = new Vehiculo("B1", TipoVehiculo.BICICLETA, false);
        igual("carro 1 hora", 5_000L, c.cobrar(carro, Duration.ofMinutes(60)));
        igual("carro fracción = hora", 8_000L, c.cobrar(carro, Duration.ofMinutes(61)));
        igual("carro 0 minutos = 1 hora", 5_000L, c.cobrar(carro, Duration.ZERO));
        igual("carro 12 horas", 5_000L + 11 * 3_000L, c.cobrar(carro, Duration.ofHours(12)));
        igual("carro 12 h 1 min = día", 30_000L, c.cobrar(carro, Duration.ofMinutes(12 * 60 + 1)));
        igual("carro 25 horas = 2 días", 60_000L, c.cobrar(carro, Duration.ofHours(25)));
        igual("moto 3 horas", 4_000L, c.cobrar(moto, Duration.ofHours(3)));
        igual("moto día", 12_000L, c.cobrar(moto, Duration.ofHours(13)));
        igual("bici gratis", 0L, c.cobrar(bici, Duration.ofHours(30)));
        igual("residente 50 %", 2_500L, c.cobrar(new Vehiculo("C2", TipoVehiculo.CARRO, true), Duration.ofHours(1)));
        igual("residente moto día", 6_000L, c.cobrar(new Vehiculo("M2", TipoVehiculo.MOTO, true), Duration.ofHours(20)));

        Parqueadero p = new Parqueadero(c);
        for (int i = 0; i < 20; i++) p.registrarEntrada(new Vehiculo("CAR" + i, TipoVehiculo.CARRO, false), T0);
        igual("cupo carros lleno", 0, p.cuposLibres(TipoVehiculo.CARRO));
        lanza("carro 21 sin cupo", () -> p.registrarEntrada(new Vehiculo("CAR99", TipoVehiculo.CARRO, false), T0));
        p.registrarEntrada(new Vehiculo("M1", TipoVehiculo.MOTO, false), T0);
        igual("cupo de motos independiente", 29, p.cuposLibres(TipoVehiculo.MOTO));
        lanza("placa repetida", () -> p.registrarEntrada(new Vehiculo("m1", TipoVehiculo.MOTO, false), T0));
        lanza("salida sin entrada", () -> p.registrarSalida("NADA", T0));
        igual("salida cobra", 5_000L, p.registrarSalida("CAR0", T0.plusMinutes(30)));
        igual("salida libera cupo", 1, p.cuposLibres(TipoVehiculo.CARRO));
        lanza("salir dos veces", () -> p.registrarSalida("CAR0", T0.plusHours(1)));
        lanza("salida antes de entrada", () -> p.registrarSalida("CAR1", T0.minusHours(1)));
        igual("salida fallida no libera cupo", 1, p.cuposLibres(TipoVehiculo.CARRO));
        for (int i = 0; i < 15; i++) p.registrarEntrada(new Vehiculo("BI" + i, TipoVehiculo.BICICLETA, false), T0);
        lanza("bici 16 sin cupo", () -> p.registrarEntrada(new Vehiculo("BI99", TipoVehiculo.BICICLETA, false), T0));
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }

    static void lanza(String n, Runnable a) {
        total++;
        try { a.run(); fallos++; System.out.println("FALLA " + n + ": no lanzó"); }
        catch (IllegalStateException | IllegalArgumentException x) { }
    }
}
