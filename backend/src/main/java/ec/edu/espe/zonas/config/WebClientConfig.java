package ec.edu.espe.zonas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración del cliente HTTP reactivo (WebClient) para comunicarse
 * con el microservicio FastAPI de análisis de vulnerabilidades.
 */
@Configuration
public class WebClientConfig {

    @Value("${analisis.microservicio.url}")
    private String microservicioUrl;

    @Bean
    public WebClient analisisWebClient() {
        return WebClient.builder()
                .baseUrl(microservicioUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
