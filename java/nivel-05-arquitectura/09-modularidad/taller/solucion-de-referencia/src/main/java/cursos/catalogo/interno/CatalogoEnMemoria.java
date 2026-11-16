package cursos.catalogo.interno;

import cursos.catalogo.CatalogoApi;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
class CatalogoEnMemoria implements CatalogoApi {
    private final Map<String, String> titulos = Map.of("JAVA-1", "Java desde cero", "SPRING-1", "Spring Boot");

    @Override public String tituloDe(String cursoId) { return titulos.getOrDefault(cursoId, "(curso desconocido)"); }
}
