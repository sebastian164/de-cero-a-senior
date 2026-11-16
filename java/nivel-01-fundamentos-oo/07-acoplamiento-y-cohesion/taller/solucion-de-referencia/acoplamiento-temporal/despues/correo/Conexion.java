package correo;

/** Paso 3: solo existe una Conexion si se conectó; por eso enviar no puede llegar antes. */
public final class Conexion {
    private final Configuracion configuracion;

    Conexion(Configuracion configuracion) {   // constructor de paquete: solo Configuracion.conectar() la crea
        this.configuracion = configuracion;
    }

    public String enviar(String para, String texto) {
        String linea = "Enviado a " + para + " por " + configuracion.servidor() + ":" + configuracion.puerto() + ": " + texto;
        System.out.println(linea);
        return linea;
    }
}
