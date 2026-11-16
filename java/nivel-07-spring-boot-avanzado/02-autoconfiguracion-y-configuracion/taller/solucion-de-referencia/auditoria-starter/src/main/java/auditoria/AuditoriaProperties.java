package auditoria;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auditoria")
public record AuditoriaProperties(String url) {
    public AuditoriaProperties {
        if (url == null) url = "http://localhost/auditoria";
    }
}
