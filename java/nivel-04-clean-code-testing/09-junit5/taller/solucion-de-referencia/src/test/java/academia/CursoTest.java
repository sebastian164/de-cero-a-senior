package academia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Curso de la lección 02 del Nivel 1 (versión mejorada). */
class CursoTest {

    @Nested
    @DisplayName("Crear un curso")
    class Crear {

        @Test
        void conservaElNombre() {
            assertThat(new Curso("Arquitectura de software", 2).nombre()).isEqualTo("Arquitectura de software");
        }

        @ParameterizedTest(name = "nombre [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t\n"})
        void sinNombreSeRechaza(String nombre) {
            assertThatThrownBy(() -> new Curso(nombre, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El curso necesita nombre");
        }

        @ParameterizedTest(name = "cupo {0}")
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void conCupoQueNoEsPositivoSeRechaza(int cupo) {
            assertThatThrownBy(() -> new Curso("Java", cupo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El cupo debe ser mayor que cero");
        }

        @ParameterizedTest(name = "cupo {0}")
        @ValueSource(ints = {1, 2, 40})
        void unCursoNuevoTieneTodosLosCuposLibres(int cupo) {
            Curso curso = new Curso("Java", cupo);

            assertThat(curso.cuposLibres()).isEqualTo(cupo);
            assertThat(curso.estudiantes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Un curso de cupo 2")
    class ConCupoDos {

        private Curso curso;

        @BeforeEach
        void crear() {
            curso = new Curso("Arquitectura de software", 2);
        }

        @Test
        void inscribirAgregaAlEstudianteYOcupaUnCupo() {
            curso.inscribir("Ana");

            assertThat(curso.estudiantes()).containsExactly("Ana");
            assertThat(curso.cuposLibres()).isEqualTo(1);
        }

        @Test
        void unEstudianteNoSePuedeInscribirDosVeces() {
            curso.inscribir("Ana");

            assertThatThrownBy(() -> curso.inscribir("Ana"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Ana ya está inscrito");
        }

        @Test
        void laListaDeEstudiantesNoSePuedeModificarDesdeFuera() {
            assertThatThrownBy(() -> curso.estudiantes().add("Colado"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(curso.cuposLibres()).isEqualTo(2);
        }

        @Test
        void laListaDeEstudiantesEsUnaVistaQueMuestraLasInscripcionesPosteriores() {
            var estudiantes = curso.estudiantes();

            curso.inscribir("Ana");

            assertThat(estudiantes).containsExactly("Ana");
        }

        @Nested
        @DisplayName("lleno con Ana y Luis")
        class Lleno {

            @BeforeEach
            void llenar() {
                curso.inscribir("Ana");
                curso.inscribir("Luis");
            }

            @Test
            void noQuedanCuposLibres() {
                assertThat(curso.cuposLibres()).isZero();
                assertThat(curso.estudiantes()).containsExactly("Ana", "Luis");
            }

            @Test
            void rechazaAUnEstudianteNuevo() {
                assertThatThrownBy(() -> curso.inscribir("Marta"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("El curso está lleno");
            }

            @Test
            void unInscritoQueRepiteRecibeElMotivoDeDuplicadoYNoElDeLleno() {
                assertThatThrownBy(() -> curso.inscribir("Ana")).hasMessage("Ana ya está inscrito");
            }
        }
    }
}
