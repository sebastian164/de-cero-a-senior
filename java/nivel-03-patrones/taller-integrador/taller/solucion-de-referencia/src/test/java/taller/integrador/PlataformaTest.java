package taller.integrador;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlataformaTest {

    LocalDate hoy = LocalDate.of(2025, 6, 1);
    Main.Sistema s = new Main.Sistema();
    Plataforma p = s.plataforma;
    Catalogo.Curso java = Main.cursoJava();
    Catalogo.Curso python = new Catalogo.Curso("PY-1", "Python", "PROGRAMACION", 150_000, List.of());
    Catalogo.Curso diseno = new Catalogo.Curso("UX-1", "UX", "DISENO", 100_000, List.of());

    Inscripcion pagada(Estudiante e) {
        Inscripcion i = p.inscribir(e, java);
        assertTrue(p.pagar(List.of(i), Optional.empty(), hoy).aprobado());
        s.sesiones.abrir("tok-" + e.id(), e.id());
        return i;
    }

    @Test
    void escenarioCompleto() {
        Estudiante ana = new Estudiante("E1", "Ana", "ana@x.example", "CO", true);
        Inscripcion i = p.inscribir(ana, java);
        assertEquals(EstadoInscripcion.PENDIENTE_PAGO, i.estado());
        assertEquals(140_000, p.cotizar(List.of(java), Optional.of("BIENVENIDA"), hoy).neto());
        assertTrue(p.pagar(List.of(i), Optional.of("BIENVENIDA"), hoy).aprobado());
        assertEquals(List.of("E1 14000000 COP"), s.sdkCo.llamadas, "el adaptador convirtió a centavos");
        assertEquals(EstadoInscripcion.ACTIVA, i.estado());
        s.sesiones.abrir("t", "E1");
        for (String l : List.of("L1", "L2", "L3")) assertTrue(p.completarLeccion("t", i, l, "pc", hoy).isEmpty());
        Finalizacion.Certificado c = p.completarLeccion("t", i, "L4", "pc", hoy).orElseThrow();
        assertEquals("CERT-1", c.codigo());
        assertEquals(EstadoInscripcion.FINALIZADA, i.estado());
        assertEquals(1, s.aviso.enviados.size());
        assertEquals(Map.of("JAVA-1", 1), s.estadisticas.completadosPorCurso);
        assertEquals(List.of("JAVA-2", "SPRING-1"), s.recomendaciones.desbloqueadas.get("E1"));
        assertEquals(List.of("Ana completó Java desde cero"), s.perfil.publicaciones);
    }

    // ---------- Promociones ----------

    @Test
    void acumulablesSeSuman() {
        var c = p.cotizar(List.of(java), Optional.of("BIENVENIDA"), hoy);
        assertEquals(60_000, c.descuento());   // 20 % lanzamiento + 10 % cupón
    }

    @Test
    void exclusivaGanaSoloSiEsMejor() {
        Catalogo.Curso datos = new Catalogo.Curso("DATA-1", "Datos", "DATOS", 120_000, List.of());
        LocalDate sinLanzamiento = LocalDate.of(2026, 1, 1);
        // Paquete (25 % de 420.000 = 105.000) supera al cupón (10 % = 42.000): solo paquete.
        var paquete = p.cotizar(List.of(java, diseno, datos), Optional.of("BIENVENIDA"), sinLanzamiento);
        assertEquals(List.of("Paquete 3+ cursos -25%"), paquete.aplicadas());
        assertEquals(105_000, paquete.descuento());
        // Con lanzamiento vigente: 20 % de java (40.000) + cupón (42.000) = 82.000 < 105.000: sigue el paquete.
        assertEquals(List.of("Paquete 3+ cursos -25%"), p.cotizar(List.of(java, diseno, datos), Optional.of("BIENVENIDA"), hoy).aplicadas());
        // Dos exclusivas: 2x1 (150.000 gratis) supera al paquete (112.500): solo el 2x1, nunca ambas.
        var dos = p.cotizar(List.of(java, python, diseno), Optional.empty(), sinLanzamiento);
        assertEquals(List.of("2x1 misma categoría"), dos.aplicadas());
        assertEquals(150_000, dos.descuento());
        // Un solo curso con lanzamiento y cupón: las acumulables ganan (no hay exclusivas aplicables).
        assertEquals(List.of("Lanzamiento JAVA-1 -20%", "Cupón BIENVENIDA"),
                p.cotizar(List.of(java), Optional.of("BIENVENIDA"), hoy).aplicadas());
    }

    @Test
    void dosPorUnoMismaCategoria() {
        var c = p.cotizar(List.of(java, python), Optional.empty(), LocalDate.of(2026, 1, 1));
        assertEquals(List.of("2x1 misma categoría"), c.aplicadas());
        assertEquals(150_000, c.descuento(), "el más barato del par es gratis");
        assertEquals(0, new Promociones.DosPorUno().descuento(new Promociones.Carrito(List.of(java, diseno), Optional.empty(), hoy)));
    }

    // ---------- Estados ----------

    @Test
    void transicionesDeInscripcion() {
        assertEquals(EstadoInscripcion.ACTIVA, EstadoInscripcion.PENDIENTE_PAGO.aplicar(EstadoInscripcion.Evento.PAGAR));
        assertThrows(IllegalStateException.class, () -> EstadoInscripcion.FINALIZADA.aplicar(EstadoInscripcion.Evento.REEMBOLSAR));
        assertEquals(EstadoInscripcion.REEMBOLSADA, EstadoInscripcion.SUSPENDIDA.aplicar(EstadoInscripcion.Evento.REEMBOLSAR));
        assertEquals(EnumSet.of(EstadoInscripcion.PENDIENTE_PAGO, EstadoInscripcion.EN_PRUEBA, EstadoInscripcion.SUSPENDIDA),
                EstadoInscripcion.origenesDe(EstadoInscripcion.ACTIVA));
    }

    @Test
    void periodoDePruebaDeSieteDias() {
        Estudiante e = new Estudiante("E9", "Eva", "e@x.example", "MX", false);
        Inscripcion i = p.inscribir(e, java);
        i.iniciarPrueba(hoy);
        s.sesiones.abrir("t9", "E9");
        assertTrue(p.verLeccion("t9", i, "L1", "a", hoy.plusDays(6)).entregada());
        assertFalse(p.verLeccion("t9", i, "L1", "a", hoy.plusDays(7)).entregada());
        i.aplicar(EstadoInscripcion.Evento.PAGAR);
        assertTrue(p.verLeccion("t9", i, "L1", "a", hoy.plusDays(30)).entregada());
    }

    // ---------- Pasarelas ----------

    @Test
    void pasarelasPorPais() {
        Estudiante mx = new Estudiante("M", "M", "m@x.example", "MX", false);
        Estudiante us = new Estudiante("U", "U", "u@x.example", "US", false);
        Estudiante pe = new Estudiante("P", "P", "p@x.example", "PE", false);
        Catalogo.Curso barato = new Catalogo.Curso("B", "B", "X", 40_000, List.of());
        assertTrue(p.pagar(List.of(p.inscribir(mx, barato)), Optional.empty(), hoy).aprobado());
        assertEquals("Rechazado por riesgo", p.pagar(List.of(p.inscribir(mx, java)), Optional.empty(), hoy).motivo());
        assertTrue(p.pagar(List.of(p.inscribir(us, java)), Optional.empty(), hoy).aprobado());
        Inscripcion cara = p.inscribir(us, new Catalogo.Curso("C", "C", "X", 900_000, List.of()));
        assertEquals("Tarjeta rechazada", p.pagar(List.of(cara), Optional.empty(), hoy).motivo());
        assertEquals(EstadoInscripcion.PENDIENTE_PAGO, cara.estado(), "pago rechazado no activa");
        assertEquals("No hay pasarela para PE", p.pagar(List.of(p.inscribir(pe, barato)), Optional.empty(), hoy).motivo());
    }

    // ---------- Entrega ----------

    @Test
    void verificacionesDeEntrega() {
        Inscripcion i = pagada(new Estudiante("E1", "Ana", "a@x.example", "CO", true));
        assertEquals("Sesión inválida", p.verLeccion("otro", i, "L1", "pc", hoy).detalle());
        assertEquals("Lección bloqueada: completa antes 'Qué es Java'", p.verLeccion("tok-E1", i, "L2", "pc", hoy).detalle());
        assertTrue(p.verLeccion("tok-E1", i, "L1", "pc", hoy).entregada());
        assertTrue(p.verLeccion("tok-E1", i, "L1", "cel", hoy).entregada());
        assertEquals("Límite de 2 dispositivos", p.verLeccion("tok-E1", i, "L1", "tablet", hoy).detalle());
        Inscripcion sinPagar = p.inscribir(new Estudiante("E5", "X", "x@x.example", "CO", false), java);
        s.sesiones.abrir("t5", "E5");
        assertEquals("Inscripción sin acceso (PENDIENTE_PAGO)", p.verLeccion("t5", sinPagar, "L1", "pc", hoy).detalle());
    }

    @Test
    void cupoDelTallerEnVivo() {
        Catalogo.Curso conCupo = new Catalogo.Curso("T", "T", "X", 1_000, List.of(new Catalogo.Modulo("M",
                List.of(new Catalogo.TallerEnVivo("V1", "En vivo", 60, 1)))));
        Estudiante a = new Estudiante("A", "A", "a@x.example", "CO", false);
        Estudiante b = new Estudiante("B", "B", "b@x.example", "CO", false);
        Inscripcion ia = p.inscribir(a, conCupo), ib = p.inscribir(b, conCupo);
        p.pagar(List.of(ia), Optional.empty(), hoy);
        p.pagar(List.of(ib), Optional.empty(), hoy);
        s.sesiones.abrir("ta", "A");
        s.sesiones.abrir("tb", "B");
        assertTrue(p.verLeccion("ta", ia, "V1", "pc", hoy).entregada());
        assertTrue(p.verLeccion("ta", ia, "V1", "pc", hoy).entregada(), "quien ya tiene cupo vuelve a entrar");
        assertEquals("Taller en vivo sin cupo", p.verLeccion("tb", ib, "V1", "pc", hoy).detalle());
    }

    // ---------- Examen y perfil ----------

    @Test
    void examenSeGuardaYSeRetomaSinCompartirEstado() {
        Inscripcion i = pagada(new Estudiante("E1", "Ana", "a@x.example", "CO", true));
        Examenes.Progreso uno = Examenes.Progreso.nuevo(i.id(), "L4").responder(0, "JVM");
        p.guardarExamen(uno);
        Examenes.Progreso dos = uno.responder(1, "compila");
        assertEquals(Map.of(0, "JVM"), p.retomarExamen(i, "L4").orElseThrow().respuestas(), "el guardado no cambió");
        assertEquals(2, dos.preguntaActual());
        assertTrue(p.retomarExamen(i, "OTRO").isEmpty());
    }

    @Test
    void perfilPublicoSoloConAutorizacion() {
        Inscripcion i = pagada(new Estudiante("E7", "Sin permiso", "s@x.example", "CO", false));
        for (String l : List.of("L1", "L2", "L3", "L4")) p.completarLeccion("tok-E7", i, l, "pc", hoy);
        assertEquals(EstadoInscripcion.FINALIZADA, i.estado());
        assertTrue(s.perfil.publicaciones.isEmpty());
        assertEquals(1, s.aviso.enviados.size());
    }

    @Test
    void unOyenteQueFallaNoImpideCertificar() {
        Finalizacion.Publicador pub = new Finalizacion.Publicador(s.errores::add);
        Finalizacion.Estadisticas est = new Finalizacion.Estadisticas();
        pub.suscribir(e -> { throw new IllegalStateException("caído"); });
        pub.suscribir(est);
        Inscripcion i = new Inscripcion("X", new Estudiante("E", "E", "e@x.example", "CO", false), java);
        pub.publicar(Finalizacion.CursoCompletado.de(i, new Finalizacion.Certificado("C", "E", "J", hoy)));
        assertEquals(1, est.completadosPorCurso.get("JAVA-1"));
        assertEquals(1, s.errores.size());
    }

    // ---------- Revisión: evento inmutable, efectos de la cadena, ciclo administrativo ----------

    @Test
    void elEventoNoExponeLaEntidadMutable() {
        var componentes = Finalizacion.CursoCompletado.class.getRecordComponents();
        assertTrue(Arrays.stream(componentes).noneMatch(c -> c.getType() == Inscripcion.class || c.getType() == Estudiante.class),
                "el evento lleva datos, no entidades");
        // Un oyente solo puede leer: tras completar, la inscripción sigue FINALIZADA y nadie la tocó.
        Inscripcion i = pagada(new Estudiante("E8", "Leo", "l@x.example", "CO", true));
        for (String l : List.of("L1", "L2", "L3", "L4")) p.completarLeccion("tok-E8", i, l, "pc", hoy);
        assertEquals(EstadoInscripcion.FINALIZADA, i.estado());
        assertEquals(List.of("Leo completó Java desde cero"), s.perfil.publicaciones);
    }

    @Test
    void unRechazoPosteriorNoDejaDispositivoNiCupoRegistrado() {
        Catalogo.Curso conCupo = new Catalogo.Curso("T", "T", "X", 1_000, List.of(new Catalogo.Modulo("M",
                List.of(new Catalogo.TallerEnVivo("V1", "En vivo", 60, 1)))));
        Estudiante a = new Estudiante("A", "A", "a@x.example", "CO", false);
        Estudiante b = new Estudiante("B", "B", "b@x.example", "CO", false);
        Inscripcion ia = p.inscribir(a, conCupo), ib = p.inscribir(b, conCupo);
        p.pagar(List.of(ia), Optional.empty(), hoy);
        p.pagar(List.of(ib), Optional.empty(), hoy);
        s.sesiones.abrir("ta", "A");
        s.sesiones.abrir("tb", "B");
        assertTrue(p.verLeccion("ta", ia, "V1", "pc", hoy).entregada());
        // B es rechazado por cupo con dos dispositivos distintos: si se registraran, el tercero chocaría con el límite.
        assertEquals("Taller en vivo sin cupo", p.verLeccion("tb", ib, "V1", "pc", hoy).detalle());
        assertEquals("Taller en vivo sin cupo", p.verLeccion("tb", ib, "V1", "cel", hoy).detalle());
        assertEquals("Taller en vivo sin cupo", p.verLeccion("tb", ib, "V1", "tablet", hoy).detalle(),
                "no dice 'Límite de 2 dispositivos': los rechazos no registraron dispositivos");
    }

    @Test
    void suspenderReactivarYReembolsar() {
        Inscripcion i = pagada(new Estudiante("E6", "Sol", "s@x.example", "CO", false));
        p.suspender(i);
        assertEquals(EstadoInscripcion.SUSPENDIDA, i.estado());
        assertEquals("Inscripción sin acceso (SUSPENDIDA)", p.verLeccion("tok-E6", i, "L1", "pc", hoy).detalle());
        p.reactivar(i);
        assertTrue(p.verLeccion("tok-E6", i, "L1", "pc", hoy).entregada());
        p.reembolsar(i);
        assertEquals(EstadoInscripcion.REEMBOLSADA, i.estado());
        assertEquals("Inscripción sin acceso (REEMBOLSADA)", p.verLeccion("tok-E6", i, "L1", "pc", hoy).detalle());
        assertThrows(IllegalStateException.class, () -> p.reactivar(i), "REEMBOLSADA es final");
    }
}
