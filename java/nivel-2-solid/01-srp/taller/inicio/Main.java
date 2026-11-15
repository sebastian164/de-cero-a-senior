public class Main {

    public static void main(String[] args) {
        RegistroUsuarios registro = new RegistroUsuarios();

        registro.registrar("Laura Gómez", "laura@correo.example", "clave1234");
        System.out.println("Usuarios registrados: " + registro.totalUsuarios());

        try {
            registro.registrar("Laura Gómez", "LAURA@correo.example", "otraClave99");
        } catch (IllegalStateException e) {
            System.out.println("Registro rechazado: " + e.getMessage());
        }

        try {
            registro.registrar("Pedro", "pedro@correo.example", "corta");
        } catch (IllegalArgumentException e) {
            System.out.println("Registro rechazado: " + e.getMessage());
        }
    }
}
