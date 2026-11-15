public class Main {

    public static void main(String[] args) {
        Reserva reserva = Reserva.builder("Ana", "Hotel Duitama")
                .adultos(2)
                .noches(3)
                .conDesayuno()
                .observaciones("piso alto")
                .build();
        System.out.println(reserva);

        try {
            Reserva.builder("Luis", "Hotel Paipa").adultos(0).build();
        } catch (IllegalStateException e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
    }
}

final class Reserva {

    private final String huesped;
    private final String hotel;
    private final int adultos;
    private final int ninos;
    private final int noches;
    private final boolean desayuno;
    private final String observaciones;

    private Reserva(Builder builder) {
        this.huesped = builder.huesped;
        this.hotel = builder.hotel;
        this.adultos = builder.adultos;
        this.ninos = builder.ninos;
        this.noches = builder.noches;
        this.desayuno = builder.desayuno;
        this.observaciones = builder.observaciones;
    }

    /** Los datos obligatorios se piden al empezar: no se pueden olvidar. */
    static Builder builder(String huesped, String hotel) {
        return new Builder(huesped, hotel);
    }

    @Override
    public String toString() {
        return huesped + " en " + hotel + ": " + adultos + " adultos, " + ninos + " niños, "
                + noches + " noches" + (desayuno ? ", con desayuno" : "")
                + (observaciones.isEmpty() ? "" : " (" + observaciones + ")");
    }

    static final class Builder {

        private final String huesped;
        private final String hotel;
        private int adultos = 1;
        private int ninos = 0;
        private int noches = 1;
        private boolean desayuno = false;
        private String observaciones = "";

        private Builder(String huesped, String hotel) {
            this.huesped = huesped;
            this.hotel = hotel;
        }

        Builder adultos(int adultos) { this.adultos = adultos; return this; }
        Builder ninos(int ninos) { this.ninos = ninos; return this; }
        Builder noches(int noches) { this.noches = noches; return this; }
        Builder conDesayuno() { this.desayuno = true; return this; }
        Builder observaciones(String observaciones) { this.observaciones = observaciones; return this; }

        /** Las reglas que involucran varios campos se validan una vez, aquí. */
        Reserva build() {
            if (adultos < 1) {
                throw new IllegalStateException("Debe haber al menos un adulto");
            }
            if (noches < 1) {
                throw new IllegalStateException("La reserva debe ser de al menos una noche");
            }
            return new Reserva(this);
        }
    }
}
