package pedidos;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Archivos en almacenamiento de objetos, no en el disco de la instancia.
 * Las credenciales salen de la cadena por defecto del SDK (variables de entorno, rol, identidad federada):
 * en el código y en la configuración no hay ninguna clave.
 */
@RestController
@ConditionalOnProperty("app.archivos.bucket")
public class ControladorArchivos {
    private final S3Client s3;
    private final String bucket;

    public ControladorArchivos(@Value("${app.archivos.bucket}") String bucket,
                               @Value("${app.archivos.endpoint:}") String endpoint) {
        S3ClientBuilder constructor = S3Client.builder();
        if (!endpoint.isBlank()) {
            constructor.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        this.s3 = constructor.build();
        this.bucket = bucket;
    }

    @PostMapping("/api/archivos")
    public Map<String, Object> subir(@RequestParam MultipartFile archivo) throws IOException {
        s3.putObject(b -> b.bucket(bucket).key(archivo.getOriginalFilename()),
                RequestBody.fromBytes(archivo.getBytes()));
        return Map.of("bucket", bucket, "clave", archivo.getOriginalFilename(), "bytes", archivo.getSize());
    }

    @GetMapping("/api/archivos/{clave}")
    public ResponseEntity<byte[]> bajar(@PathVariable String clave) {
        return ResponseEntity.ok(s3.getObjectAsBytes(b -> b.bucket(bucket).key(clave)).asByteArray());
    }
}
