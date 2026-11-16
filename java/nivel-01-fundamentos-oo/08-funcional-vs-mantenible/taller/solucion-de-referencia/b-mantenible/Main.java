import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        Parqueadero parqueadero = new Parqueadero(new CalculadoraCobro());
        LocalDateTime ocho = LocalDateTime.of(2026, 3, 2, 8, 0);
        parqueadero.registrarEntrada(new Vehiculo("ABC123", TipoVehiculo.CARRO, false), ocho);
        System.out.println(parqueadero.registrarSalida("ABC123", ocho.plusMinutes(150)));
        parqueadero.registrarEntrada(new Vehiculo("XYZ12A", TipoVehiculo.MOTO, true), ocho);
        System.out.println(parqueadero.registrarSalida("XYZ12A", ocho.plusHours(13)));
        parqueadero.registrarEntrada(new Vehiculo("BICI1", TipoVehiculo.BICICLETA, false), ocho);
        System.out.println(parqueadero.registrarSalida("BICI1", ocho.plusHours(10)));
        try {
            parqueadero.registrarSalida("NOEXISTE", ocho);
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }
}
