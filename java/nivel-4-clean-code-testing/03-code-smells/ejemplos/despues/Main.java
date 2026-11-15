public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente("Ana", TipoCliente.FRECUENTE,
                new Direccion("Cra 10 # 20-30", "Tunja", "150001"));
        System.out.println(cliente.etiquetaDeEnvio());
        System.out.println(cliente.descuentoSobre(200_000));
    }
}

enum TipoCliente {
    REGULAR(0), FRECUENTE(5), CORPORATIVO(12);

    private final int porcentajeDescuento;

    TipoCliente(int porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }

    long descuentoSobre(long valor) {
        return valor * porcentajeDescuento / 100;
    }
}

record Direccion(String calle, String ciudad, String codigoPostal) {
    String enDosLineas() {
        return calle + "\n" + ciudad + " " + codigoPostal;
    }
}

record Cliente(String nombre, TipoCliente tipo, Direccion direccion) {

    String etiquetaDeEnvio() {
        return nombre + "\n" + direccion.enDosLineas();
    }

    long descuentoSobre(long valor) {
        return tipo.descuentoSobre(valor);
    }
}
