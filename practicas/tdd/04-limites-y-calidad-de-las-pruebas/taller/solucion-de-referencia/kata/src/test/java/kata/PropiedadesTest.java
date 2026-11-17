package kata;

import static kata.Calculadora.sumar;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Propiedades que deben cumplirse siempre. jqwik genera cientos de entradas buscando un contraejemplo. */
class PropiedadesTest {

    @Property
    void elOrdenDeLosNumerosNoCambiaLaSuma(
            @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 0, max = 2000) Integer> numeros,
            @ForAll Random azar) {
        List<Integer> barajados = new ArrayList<>(numeros);
        Collections.shuffle(barajados, azar);
        assertEquals(sumar(unir(numeros, List.of(","))), sumar(unir(barajados, List.of(","))));
    }

    @Property
    void laSumaEsLaDeLosNumerosQueNoPasanDeMil(
            @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 0, max = 2000) Integer> numeros) {
        int esperado = 0;
        for (int numero : numeros) {
            esperado += numero <= 1000 ? numero : 0;
        }
        assertEquals(esperado, sumar(unir(numeros, List.of(",", "\n"))));
    }

    @Property
    void unNumeroMayorQueMilNoCambiaLaSuma(
            @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 0, max = 2000) Integer> numeros,
            @ForAll @IntRange(min = 1001, max = 1_000_000) int grande) {
        List<Integer> conGrande = new ArrayList<>(numeros);
        conGrande.add(grande);
        assertEquals(sumar(unir(numeros, List.of(","))), sumar(unir(conGrande, List.of(","))));
    }

    @Property
    void unSeparadorDeclaradoSumaLoMismoQueLaComa(
            @ForAll @Size(min = 1, max = 10) List<@IntRange(min = 0, max = 1000) Integer> numeros,
            @ForAll("separadores") String separador) {
        String entrada = "//[" + separador + "]\n" + unir(numeros, List.of(separador));
        assertEquals(sumar(unir(numeros, List.of(","))), sumar(entrada));
    }

    @Property
    void variosSeparadoresDeclaradosSumanLoMismoQueLaComa(
            @ForAll @Size(min = 1, max = 10) List<@IntRange(min = 0, max = 1000) Integer> numeros,
            @ForAll("separadores") String uno,
            @ForAll("separadores") String otro) {
        String entrada = "//[" + uno + "][" + otro + "]\n" + unir(numeros, List.of(uno, otro));
        assertEquals(sumar(unir(numeros, List.of(","))), sumar(entrada));
    }

    /** Separadores de uno a cuatro símbolos: nada que sea un dígito, un signo menos, un corchete o un salto de línea. */
    @Provide
    Arbitrary<String> separadores() {
        return Arbitraries.strings().withChars("*%;#@!$&+=?^~|.()").ofMinLength(1).ofMaxLength(4);
    }

    /** Une los números alternando los separadores dados. */
    private static String unir(List<Integer> numeros, List<String> separadores) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < numeros.size(); i++) {
            if (i > 0) {
                texto.append(separadores.get(i % separadores.size()));
            }
            texto.append(numeros.get(i));
        }
        return texto.toString();
    }

}
