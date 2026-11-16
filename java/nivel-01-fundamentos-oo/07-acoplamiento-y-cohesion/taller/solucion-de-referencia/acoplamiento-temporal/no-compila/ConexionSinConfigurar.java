import correo.Conexion;

/** Debe fallar al compilar: el constructor de Conexion no es público; solo Configuracion.conectar() la crea. */
class ConexionSinConfigurar { void f() { new Conexion(null).enviar("a@b.example", "x"); } }
