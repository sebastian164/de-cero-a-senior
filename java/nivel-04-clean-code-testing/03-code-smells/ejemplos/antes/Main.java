public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente();
        cliente.nombre = "Ana";
        cliente.tipo = 2;
        cliente.calle = "Cra 10 # 20-30";
        cliente.ciudad = "Tunja";
        cliente.codigoPostal = "150001";
        Facturador facturador = new Facturador();
        System.out.println(facturador.etiquetaDeEnvio(cliente));
        System.out.println(facturador.descuento(cliente, 200_000));
    }
}

class Cliente {
    String nombre;
    int tipo;            // 1 = regular, 2 = frecuente, 3 = corporativo
    String calle;
    String ciudad;
    String codigoPostal;
}

class Facturador {

    String etiquetaDeEnvio(Cliente c) {
        return c.nombre + "\n" + c.calle + "\n" + c.ciudad + " " + c.codigoPostal;
    }

    long descuento(Cliente c, long valor) {
        if (c.tipo == 2) {
            return valor * 5 / 100;
        } else if (c.tipo == 3) {
            return valor * 12 / 100;
        }
        return 0;
    }
}
