package tienda.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// Fragmento del README, más dos propiedades para llegar a las cinco que reemplaza
@ConfigurationProperties(prefix = "pagos")
@Validated
public record PagosProperties(
        @NotBlank String claveApi,
        @NotNull Duration tiempoLimite,
        @Min(0) @Max(5) int reintentos,
        @NotBlank String moneda,
        @NotBlank String urlPasarela) { }
