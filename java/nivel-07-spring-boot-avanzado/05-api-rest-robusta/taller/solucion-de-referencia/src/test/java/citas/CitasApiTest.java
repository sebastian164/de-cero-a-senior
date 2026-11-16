package citas;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CitasApiTest {

    @Autowired MockMvc mvc;

    static final LocalDateTime EN_3_DIAS = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.HOURS);

    static String cuerpo(long medico, String paciente, LocalDateTime inicio) {
        return """
                {"medicoId": %d, "pacienteId": 7, "pacienteNombre": "%s", "inicio": "%s", "motivo": "control"}
                """.formatted(medico, paciente, inicio);
    }

    ResultActions crear(String clave, String json) throws Exception {
        return mvc.perform(post("/v1/citas").header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    long crearOk(long medico, String paciente, LocalDateTime inicio) throws Exception {
        String loc = crear(UUID.randomUUID().toString(), cuerpo(medico, paciente, inicio))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(loc.substring(loc.lastIndexOf('/') + 1));
    }

    @Test
    void creaCon201YLocation() throws Exception {
        crear("k1", cuerpo(1, "Ana", EN_3_DIAS))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/v1/citas/\\d+")))
                .andExpect(jsonPath("$.pacienteNombre").value("Ana"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
    }

    @Test
    void validacionDeFormatoConProblemDetails() throws Exception {
        crear("k2", """
                {"medicoId": -1, "pacienteNombre": "", "inicio": null}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.citas.example/errores/validacion"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/v1/citas"))
                .andExpect(jsonPath("$.errores.medicoId").exists())
                .andExpect(jsonPath("$.errores.pacienteId").exists())
                .andExpect(jsonPath("$.errores.pacienteNombre").exists())
                .andExpect(jsonPath("$.errores.inicio").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void sinIdempotencyKeyEs400() throws Exception {
        mvc.perform(post("/v1/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, "Ana", EN_3_DIAS)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void reglasDeNegocioYConflictos() throws Exception {
        crear("k3", cuerpo(1, "Ana", LocalDateTime.now().minusDays(1).withNano(0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/cita-en-el-pasado")));
        crearOk(1, "Ana", EN_3_DIAS);
        crear("k4", cuerpo(1, "Luis", EN_3_DIAS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de estado"));
        mvc.perform(get("/v1/citas/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la cita 999"));
    }

    @Test
    void idempotencia() throws Exception {
        String json = cuerpo(2, "Eva", EN_3_DIAS);
        String primera = crear("misma-clave", json).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getHeader("Location");
        // reintento con la misma clave y el mismo cuerpo: misma cita, no se duplica
        crear("misma-clave", json).andExpect(status().isCreated())
                .andExpect(header().string("Location", primera))
                .andExpect(header().string("Idempotent-Replayed", "true"));
        mvc.perform(get("/v1/citas").param("medicoId", "2")).andExpect(jsonPath("$.page.totalElements").value(1));
        // misma clave con otro cuerpo: 422
        crear("misma-clave", cuerpo(2, "Otro", EN_3_DIAS.plusHours(1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/clave-reutilizada")));
    }

    @Test
    void dosPeticionesSimultaneasConLaMismaClaveCreanUnaSolaCitaYNingun500() throws Exception {
        String json = cuerpo(5, "Carrera", EN_3_DIAS);
        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> tarea = () -> {
                barrera.await();
                return crear("clave-carrera", json).andReturn().getResponse().getStatus();
            };
            var a = pool.submit(tarea);
            var b = pool.submit(tarea);
            var estados = java.util.List.of(a.get(), b.get());
            assertThat(estados).noneMatch(e -> e >= 500).allMatch(e -> e == 201 || e == 409);
            assertThat(estados).contains(201);
        } finally {
            pool.shutdownNow();
        }
        mvc.perform(get("/v1/citas").param("medicoId", "5")).andExpect(jsonPath("$.page.totalElements").value(1));
        // tras la carrera, el reintento recibe la cita ya creada
        crear("clave-carrera", json).andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void paginacionFiltroYOrden() throws Exception {
        for (int i = 0; i < 25; i++) crearOk(i % 2 == 0 ? 10 : 11, "p" + i, EN_3_DIAS.plusHours(i));
        mvc.perform(get("/v1/citas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(25))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get("/v1/citas").param("medicoId", "11").param("size", "3").param("sort", "inicio,desc"))
                .andExpect(jsonPath("$.page.totalElements").value(12))
                .andExpect(jsonPath("$.content[*].pacienteNombre").value(contains("p23", "p21", "p19")));
        mvc.perform(get("/v1/citas").param("desde", EN_3_DIAS.plusDays(1).toLocalDate().toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/citas").param("estado", "INVENTADO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelacionComoSubrecurso() throws Exception {
        long id = crearOk(3, "Ana", EN_3_DIAS);
        mvc.perform(post("/v1/citas/{id}/cancelacion", id)).andExpect(status().isNoContent());
        mvc.perform(get("/v1/citas/{id}", id)).andExpect(jsonPath("$.estado").value("CANCELADA"));
        mvc.perform(post("/v1/citas/{id}/cancelacion", id)).andExpect(status().isConflict());
        long pronto = crearOk(3, "Luis", LocalDateTime.now().plusHours(5).withNano(0));
        mvc.perform(post("/v1/citas/{id}/cancelacion", pronto))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/cancelacion-tardia")));
        mvc.perform(post("/v1/citas/999/cancelacion")).andExpect(status().isNotFound());
    }

    @Test
    void versionadoV1SigueFuncionandoJuntoAV2() throws Exception {
        long id = crearOk(4, "Ana", EN_3_DIAS);
        mvc.perform(get("/v1/citas/{id}", id))
                .andExpect(jsonPath("$.pacienteNombre").value("Ana"))
                .andExpect(jsonPath("$.fecha").value(EN_3_DIAS.toLocalDate().toString()))
                .andExpect(jsonPath("$.paciente").doesNotExist());
        mvc.perform(get("/v2/citas/{id}", id))
                .andExpect(jsonPath("$.paciente.nombre").value("Ana"))
                .andExpect(jsonPath("$.paciente.id").value(7))
                .andExpect(jsonPath("$.inicio").value(org.hamcrest.Matchers.endsWith("-05:00")))
                .andExpect(jsonPath("$.pacienteNombre").doesNotExist());
        mvc.perform(get("/v2/citas")).andExpect(jsonPath("$.content[0].paciente.nombre").value("Ana"));
    }

    @Test
    void documentacionOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API de citas médicas"))
                .andExpect(jsonPath("$.paths['/v1/citas'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/citas/{id}/cancelacion'].post").exists())
                .andExpect(jsonPath("$.paths['/v2/citas/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/citas'].post.parameters[?(@.name=='Idempotency-Key')].required").value(contains(true)));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
