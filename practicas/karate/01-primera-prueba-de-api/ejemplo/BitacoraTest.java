// En la serie 1 la clase era com.intuit.karate.junit5.Karate; en la serie 2 cambió de paquete.
import io.karatelabs.junit6.Karate;

class BitacoraTest {

    @Karate.Test
    Karate bitacora() {
        return Karate.run("bitacora").relativeTo(getClass());
    }
}
