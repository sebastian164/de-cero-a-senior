public record Vehiculo(String placa, TipoVehiculo tipo, boolean residente) {
    public Vehiculo {
        if (placa == null || placa.isBlank()) throw new IllegalArgumentException("Falta la placa");
        if (tipo == null) throw new IllegalArgumentException("Falta el tipo de vehículo");
        placa = placa.trim().toUpperCase();
    }
}
