import correo.Configuracion;
import correo.Conexion;


public class Main {
    public static void main(String[] args) {
        Conexion conexion = new Configuracion("smtp.ejemplo.test", 25).conectar();
        conexion.enviar("ana@correo.example", "Hola");
    }
}
