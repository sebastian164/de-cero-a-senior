package taller.integrador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {

    /** Todo el ensamblaje en un lugar (raíz de composición). */
    static final class Sistema {
        final Entrega.Sesiones sesiones = new Entrega.Sesiones();
        final Finalizacion.AvisoEstudiante aviso = new Finalizacion.AvisoEstudiante();
        final Finalizacion.Estadisticas estadisticas = new Finalizacion.Estadisticas();
        final Finalizacion.Recomendaciones recomendaciones = new Finalizacion.Recomendaciones(Map.of("JAVA-1", List.of("JAVA-2", "SPRING-1")));
        final Finalizacion.PerfilPublico perfil = new Finalizacion.PerfilPublico();
        final List<String> errores = new ArrayList<>();
        final Pagos.SdkPagosCo sdkCo = new Pagos.SdkPagosCo();
        final Plataforma plataforma;

        Sistema() {
            Finalizacion.Publicador publicador = new Finalizacion.Publicador(errores::add);
            publicador.suscribir(aviso);
            publicador.suscribir(estadisticas);
            publicador.suscribir(recomendaciones);
            publicador.suscribir(perfil);                                  // Parte 3, cambio 5

            Promociones.Cotizador cotizador = new Promociones.Cotizador(List.of(
                    new Promociones.Lanzamiento("JAVA-1", 20, LocalDate.of(2025, 12, 31)),
                    new Promociones.Paquete(3, 25),
                    new Promociones.Cupon("BIENVENIDA", 10),
                    new Promociones.DosPorUno()));                         // Parte 3, cambio 3

            Pagos.PasarelasPorPais pasarelas = new Pagos.PasarelasPorPais()
                    .registrar("CO", "COP", new Pagos.AdaptadorCo(sdkCo))
                    .registrar("MX", "MXN", new Pagos.AdaptadorMx(new Pagos.SdkPagosMx()))
                    .registrar("US", "USD", new Pagos.AdaptadorUs(new Pagos.SdkPagosUs()));   // Parte 3, cambio 1

            Entrega.Cadenas entrega = new Entrega.Cadenas(List.of(
                    new Entrega.SesionValida(sesiones),
                    new Entrega.InscripcionActiva(),
                    new Entrega.LeccionDesbloqueada(),
                    new Entrega.LimiteDispositivos(2),
                    new Entrega.CupoTallerEnVivo()));                      // Parte 3, cambio 2

            plataforma = new Plataforma(cotizador, pasarelas, entrega, new Finalizacion.Certificados(), publicador,
                    new Examenes.Repositorio());
        }
    }

    static Catalogo.Curso cursoJava() {
        return new Catalogo.Curso("JAVA-1", "Java desde cero", "PROGRAMACION", 200_000, List.of(
                new Catalogo.Modulo("Fundamentos", List.of(
                        new Catalogo.Video("L1", "Qué es Java", 12),
                        new Catalogo.Lectura("L2", "Instalar el JDK", 5))),
                new Catalogo.Modulo("Práctica", List.of(
                        new Catalogo.TallerEnVivo("L3", "Taller en vivo: primer programa", 90, 30),
                        new Catalogo.Examen("L4", "Examen final", List.of("¿Qué es la JVM?", "¿Qué hace javac?"))))));
    }

    public static void main(String[] args) {
        Sistema s = new Sistema();
        Plataforma p = s.plataforma;
        LocalDate hoy = LocalDate.of(2025, 6, 1);

        Catalogo.Curso java = cursoJava();
        System.out.println("1. Curso creado: " + java.nombre() + ", " + java.lecciones().size() + " lecciones, "
                + java.minutosEstimados() + " min estimados");
        java.lecciones().forEach(l -> System.out.println("   - " + Catalogo.describir(l)));

        Estudiante ana = new Estudiante("E1", "Ana", "ana@correo.example", "CO", true);
        Inscripcion ins = p.inscribir(ana, java);
        System.out.println("2. Inscrita: " + ins.id() + " estado " + ins.estado());

        Promociones.Cotizacion cot = p.cotizar(List.of(java), Optional.of("BIENVENIDA"), hoy);
        System.out.println("3. Cotización: bruto $" + cot.bruto() + ", descuento $" + cot.descuento() + " " + cot.aplicadas()
                + ", neto $" + cot.neto());
        System.out.println("   Pago: " + p.pagar(List.of(ins), Optional.of("BIENVENIDA"), hoy) + " -> estado " + ins.estado());

        s.sesiones.abrir("tok-ana", "E1");
        System.out.println("4. Lección 3 antes de tiempo: " + p.verLeccion("tok-ana", ins, "L3", "portatil", hoy).detalle());
        for (String l : List.of("L1", "L2", "L3")) {
            p.completarLeccion("tok-ana", ins, l, "portatil", hoy);
            System.out.println("   completada " + l);
        }
        Examenes.Progreso progreso = Examenes.Progreso.nuevo(ins.id(), "L4").responder(0, "La máquina virtual");
        p.guardarExamen(progreso);
        System.out.println("   examen guardado en la pregunta " + progreso.preguntaActual() + "; se retoma: "
                + p.retomarExamen(ins, "L4").orElseThrow().respuestas());

        Optional<Finalizacion.Certificado> cert = p.completarLeccion("tok-ana", ins, "L4", "celular", hoy);
        System.out.println("5. Completado: " + cert.orElseThrow() + " estado " + ins.estado());
        System.out.println("   " + s.aviso.enviados);
        System.out.println("   estadísticas " + s.estadisticas.completadosPorCurso + ", recomendaciones "
                + s.recomendaciones.desbloqueadas + ", perfil público " + s.perfil.publicaciones);

        System.out.println("Parte 3:");
        Estudiante bob = new Estudiante("E2", "Bob", "bob@mail.example", "US", false);
        Inscripcion insBob = p.inscribir(bob, java);
        System.out.println("  pasarela US: " + p.pagar(List.of(insBob), Optional.empty(), hoy));
        Catalogo.Curso python = new Catalogo.Curso("PY-1", "Python", "PROGRAMACION", 150_000, List.of());
        System.out.println("  2x1 misma categoría: " + p.cotizar(List.of(java, python), Optional.empty(), LocalDate.of(2026, 1, 15)));
        Estudiante caro = new Estudiante("E3", "Caro", "caro@mail.example", "MX", false);
        Inscripcion prueba = p.inscribir(caro, java);
        prueba.iniciarPrueba(hoy);
        s.sesiones.abrir("tok-caro", "E3");
        System.out.println("  prueba día 7: " + p.verLeccion("tok-caro", prueba, "L1", "tablet", hoy.plusDays(6)).detalle());
        System.out.println("  prueba día 8: " + p.verLeccion("tok-caro", prueba, "L1", "tablet", hoy.plusDays(7)).detalle());
        if (!s.errores.isEmpty()) System.out.println("Errores de oyentes: " + s.errores);
    }
}
