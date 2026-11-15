public class Main {

    public static void main(String[] args) {
        Empleado ana = new Empleado(4_000_000, TipoContrato.FIJO, 3, true);
        Empleado luis = new Empleado(1_500_000, TipoContrato.POR_HORAS, 0, false);

        Nomina nomina = new Nomina();
        System.out.println(nomina.pagoNeto(ana));
        System.out.println(nomina.pagoNeto(luis));
    }
}

enum TipoContrato { FIJO, POR_HORAS }

class Empleado {

    private final long salarioBase;
    private final TipoContrato contrato;
    private final int aniosAntiguedad;
    private final boolean trabajaRemoto;

    Empleado(long salarioBase, TipoContrato contrato, int aniosAntiguedad, boolean trabajaRemoto) {
        this.salarioBase = salarioBase;
        this.contrato = contrato;
        this.aniosAntiguedad = aniosAntiguedad;
        this.trabajaRemoto = trabajaRemoto;
    }

    long salarioBase() { return salarioBase; }
    TipoContrato contrato() { return contrato; }
    int aniosAntiguedad() { return aniosAntiguedad; }
    boolean trabajaRemoto() { return trabajaRemoto; }
}

class Nomina {

    private static final long TOPE_AUXILIO_TRANSPORTE = 2_847_000;
    private static final long AUXILIO_TRANSPORTE = 200_000;
    private static final long AUXILIO_CONECTIVIDAD = 200_000;
    private static final int PORCENTAJE_SALUD = 4;
    private static final int PORCENTAJE_PENSION = 4;
    private static final int PORCENTAJE_BONO_ANTIGUEDAD = 5;

    long pagoNeto(Empleado empleado) {
        return empleado.salarioBase()
                + bonoAntiguedad(empleado)
                + auxilios(empleado)
                - deducciones(empleado);
    }

    private long bonoAntiguedad(Empleado empleado) {
        if (empleado.contrato() != TipoContrato.FIJO) {
            return 0;
        }
        int tramos = 0;
        if (empleado.aniosAntiguedad() > 2) {
            tramos++;
        }
        if (empleado.aniosAntiguedad() > 5) {
            tramos++;
        }
        return porcentaje(empleado.salarioBase(), PORCENTAJE_BONO_ANTIGUEDAD) * tramos;
    }

    private long auxilios(Empleado empleado) {
        long total = 0;
        if (empleado.trabajaRemoto()) {
            total += AUXILIO_CONECTIVIDAD;
        }
        if (empleado.salarioBase() <= TOPE_AUXILIO_TRANSPORTE) {
            total += AUXILIO_TRANSPORTE;
        }
        return total;
    }

    private long deducciones(Empleado empleado) {
        return porcentaje(empleado.salarioBase(), PORCENTAJE_SALUD)
                + porcentaje(empleado.salarioBase(), PORCENTAJE_PENSION);
    }

    private long porcentaje(long valor, int porcentaje) {
        return valor * porcentaje / 100;
    }
}
