package concurrencia;

import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControladorProductos {

    record ProductoDto(Long id, String nombre, BigDecimal precio, int stock) {}
    record CambioPrecio(BigDecimal precio) {}

    private final ServicioProductos servicio;

    public ControladorProductos(ServicioProductos servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/productos/{id}")
    public ResponseEntity<ProductoDto> ver(@PathVariable Long id) {
        Producto p = servicio.ver(id);
        return ResponseEntity.ok().eTag("\"" + p.getVersion() + "\"").body(dto(p));
    }

    @PutMapping("/productos/{id}/precio")
    public ResponseEntity<ProductoDto> cambiarPrecio(@PathVariable Long id, @RequestHeader("If-Match") String etag,
                                                    @RequestBody CambioPrecio cambio) {
        Producto p = servicio.editarPrecio(id, Long.parseLong(etag.replace("\"", "")), cambio.precio());
        return ResponseEntity.ok().eTag("\"" + p.getVersion() + "\"").body(dto(p));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail conflicto(ObjectOptimisticLockingFailureException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Otro usuario modificó este producto mientras lo editabas. Recarga para ver los datos actuales y vuelve a aplicar tu cambio.");
        pd.setTitle("Edición concurrente");
        pd.setType(URI.create("https://errores.ejemplo/edicion-concurrente"));
        return pd;
    }

    private static ProductoDto dto(Producto p) {
        return new ProductoDto(p.getId(), p.getNombre(), p.getPrecio(), p.getStock());
    }
}
