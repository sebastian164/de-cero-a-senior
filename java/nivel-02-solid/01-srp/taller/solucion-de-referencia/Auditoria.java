import java.time.LocalDateTime;

/** Actor: Cumplimiento. Qué eventos quedan registrados y con qué formato. */
public class Auditoria {

    public void registroUsuario(String correo) {
        System.out.println("[AUDITORIA] " + LocalDateTime.now() + " REGISTRO_USUARIO correo=" + correo);
    }
}
