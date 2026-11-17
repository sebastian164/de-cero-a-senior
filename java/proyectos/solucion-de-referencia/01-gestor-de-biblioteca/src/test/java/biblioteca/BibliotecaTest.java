package biblioteca;

import biblioteca.Biblioteca.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.*;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.*;

class BibliotecaTest {
    @TempDir Path dir;

    static class RelojMutable extends Clock {
        Instant ahora = Instant.parse("2026-01-01T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return ahora; }
        void avanzarDias(int d) { ahora = ahora.plus(Duration.ofDays(d)); }
    }

    RelojMutable reloj = new RelojMutable();

    Biblioteca nueva() {
        Biblioteca b = new Biblioteca(new AlmacenArchivo(dir.resolve("b.tsv")), reloj);
        b.registrarSocio("ana", "Ana"); b.registrarSocio("beto", "Beto");
        for (int i = 1; i <= 5; i++) b.agregarLibro("L" + i, "Libro " + i, 1);
        return b;
    }

    @Test void maximo_tres_prestamos() {
        Biblioteca b = nueva();
        b.prestar("L1", "ana"); b.prestar("L2", "ana"); b.prestar("L3", "ana");
        assertThatThrownBy(() -> b.prestar("L4", "ana")).isInstanceOf(ReglaViolada.class).hasMessageContaining("Máximo 3");
    }

    @Test void multa_por_retraso_bloquea_nuevos_prestamos_hasta_pagarla() {
        Biblioteca b = nueva();
        b.prestar("L1", "ana");
        reloj.avanzarDias(14 + 4);
        assertThat(b.devolver("L1", "ana")).isEqualTo(4 * 50);
        assertThatThrownBy(() -> b.prestar("L2", "ana")).hasMessageContaining("multas");
        b.pagarMulta("ana");
        assertThat(b.prestar("L2", "ana")).isEqualTo(Resultado.PRESTADO);
    }

    @Test void libro_agotado_va_a_lista_de_espera_y_se_entrega_al_devolver() {
        Biblioteca b = nueva();
        b.prestar("L1", "ana");
        assertThat(b.prestar("L1", "beto")).isEqualTo(Resultado.EN_LISTA_DE_ESPERA);
        assertThat(b.listaDeEspera("L1")).containsExactly("beto");
        b.devolver("L1", "ana");
        assertThat(b.prestamosDe("beto")).extracting(Prestamo::isbn).containsExactly("L1");
        assertThat(b.listaDeEspera("L1")).isEmpty();
    }

    @Test void los_datos_sobreviven_al_cierre_del_programa() {
        Biblioteca b = nueva();
        b.prestar("L1", "ana"); b.prestar("L1", "beto");
        Biblioteca reabierta = new Biblioteca(new AlmacenArchivo(dir.resolve("b.tsv")), reloj);
        assertThat(reabierta.prestamosDe("ana")).hasSize(1);
        assertThat(reabierta.listaDeEspera("L1")).containsExactly("beto");
    }

    @Test void cambiar_el_almacenamiento_no_toca_las_reglas() {
        Estado[] guardado = {new Estado(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of())};
        Almacen memoria = new Almacen() {
            public Estado cargar() { return guardado[0]; }
            public void guardar(Estado e) { guardado[0] = e; }
        };
        Biblioteca b = new Biblioteca(memoria, reloj);
        b.registrarSocio("ana", "Ana"); b.agregarLibro("X", "X", 1);
        assertThat(b.prestar("X", "ana")).isEqualTo(Resultado.PRESTADO);
        assertThat(guardado[0].prestamos()).hasSize(1);
    }

    @Test void la_consola_traduce_comandos() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Biblioteca b = new Biblioteca(new AlmacenArchivo(dir.resolve("c.tsv")), reloj);
        new Consola(b, new PrintStream(buf)).ejecutar(new Scanner("socio s1 Sara\nlibro I1 Java 1\nprestar I1 s1\nprestar I1 s1\nprestar I1 nadie\n"));
        assertThat(buf.toString()).contains("PRESTADO").contains("EN_LISTA_DE_ESPERA").contains("NO: Socio inexistente");
    }
}
