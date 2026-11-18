package banco.web;

public class CuentaNoEncontrada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CuentaNoEncontrada(String cuentaId) {
        super("no existe la cuenta " + cuentaId);
    }
}
