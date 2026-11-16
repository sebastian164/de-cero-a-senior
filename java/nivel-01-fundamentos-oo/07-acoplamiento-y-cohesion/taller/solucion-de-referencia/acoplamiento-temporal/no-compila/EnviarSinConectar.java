import correo.Configuracion;

/** Debe fallar al compilar: Configuracion no tiene enviar(); hay que conectar primero. */
class EnviarSinConectar { void f() { new Configuracion("smtp.ejemplo.test", 25).enviar("a@b.example", "x"); } }
