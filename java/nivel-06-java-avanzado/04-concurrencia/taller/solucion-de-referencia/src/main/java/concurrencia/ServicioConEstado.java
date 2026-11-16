package concurrencia;

/** Simula un @Service singleton con un atributo mutable usado como "variable de trabajo". */
public class ServicioConEstado {
    private String clienteActual;                 // compartido por todas las peticiones

    public String saludar(String cliente) {
        clienteActual = cliente;
        Thread.onSpinWait();                      // cualquier trabajo intermedio (consulta, formato...)
        for (int i = 0; i < 1_000; i++) Thread.onSpinWait();
        return "Hola " + clienteActual;           // puede ser el cliente de OTRA petición
    }
}
