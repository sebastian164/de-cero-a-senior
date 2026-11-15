// El import de la clase Karate cambió entre la serie 1 y la 2: tómalo de la documentación de tu versión.
class BitacoraTest {

    @Karate.Test
    Karate bitacora() {
        return Karate.run("bitacora").relativeTo(getClass());
    }
}
