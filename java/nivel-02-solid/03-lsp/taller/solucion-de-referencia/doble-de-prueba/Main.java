import java.sql.Connection;

/**
 * Punto 4: ejercita el doble real del repo, hikaricp/06-el-pool-en-un-servicio-hexagonal/ejemplo/src/ConexionesDePrueba.java.
 * Se compila junto con ese archivo (se toma del repo, sin copiarlo).
 * El doble devuelve null en todo método que no anota: un código que consulte getAutoCommit() explota.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        ConexionesDePrueba conexiones = new ConexionesDePrueba();
        Connection doble = conexiones.pool().getConnection();
        doble.setAutoCommit(false);          // void: funciona
        doble.commit();
        doble.close();
        System.out.println("anotado por el doble: " + conexiones.anotado());
        try {
            boolean auto = doble.getAutoCommit();
            System.out.println("getAutoCommit = " + auto);
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("getAutoCommit con el doble: NullPointerException (el real devuelve un boolean)");
        }
    }
}
