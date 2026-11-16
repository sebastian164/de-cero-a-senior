package taller.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiquidadorTest {

    HorasExtra horas = new HorasExtra(6, 4);
    long valorHora = 20_000;
    Liquidador liquidador = new Liquidador(Politicas.REGISTRO);

    @Test
    void lasTresPoliticasOriginales() {
        assertEquals(new Liquidacion.Pago(250_000), liquidador.liquidar("CO", horas, valorHora));
        assertEquals(new Liquidacion.Pago(150_000 + 140_000), liquidador.liquidar("MX", horas, valorHora));
        assertEquals(new Liquidacion.Compensacion(10), liquidador.liquidar("ES", horas, valorHora));
    }

    @Test
    void strategyYCondicionalesDanLoMismo() {
        for (String pais : new String[] {"CO", "MX", "ES"}) {
            assertEquals(LiquidadorCondicional.liquidar(pais, horas, valorHora), liquidador.liquidar(pais, horas, valorHora));
        }
    }

    @Test
    void cuartaPoliticaConTope() {
        assertEquals(new Liquidacion.Pago(10 * 30_000), liquidador.liquidar("CL", horas, valorHora));
        assertEquals(new Liquidacion.Pago(10 * 30_000 + 2 * 20_000),
                liquidador.liquidar("CL", new HorasExtra(8, 4), valorHora));
    }

    @Test
    void agregarUnaPoliticaNoModificaElLiquidador() {
        Map<String, PoliticaHorasExtra> mapa = new HashMap<>(Politicas.REGISTRO);
        mapa.put("AR", (h, v) -> new Liquidacion.Pago(h.total() * v * 2));
        assertEquals(new Liquidacion.Pago(400_000), new Liquidador(mapa).liquidar("AR", horas, valorHora));
    }

    @Test
    void paisSinPolitica() {
        assertThrows(IllegalArgumentException.class, () -> liquidador.liquidar("PE", horas, valorHora));
    }

    @Test
    void elResultadoSelladoObligaATratarDineroYTiempo() {
        String texto = switch (liquidador.liquidar("ES", horas, valorHora)) {
            case Liquidacion.Pago p -> "nómina: " + p.pesos();
            case Liquidacion.Compensacion c -> "calendario: " + c.horasLibres();
        };
        assertEquals("calendario: 10", texto);
    }
}
