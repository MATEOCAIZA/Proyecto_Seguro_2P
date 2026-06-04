package ec.edu.espe.zonas.services.impl;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.services.AnalisisServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

/**
 * Implementación del servicio de análisis de vulnerabilidades.
 * Usa WebClient para consumir el microservicio FastAPI Python vía HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalisisServicioImpl implements AnalisisServicio {

    /** Bean configurado en WebClientConfig con la URL base del microservicio */
    private final WebClient analisisWebClient;

    /** Timeout para la llamada al modelo (puede tardar varios segundos) */
    private static final Duration TIMEOUT_ANALISIS = Duration.ofSeconds(60);
    private static final Duration TIMEOUT_HEALTH   = Duration.ofSeconds(10);

    /**
     * Envía el código fuente Java al microservicio FastAPI y retorna el análisis.
     * Lanza ResponseStatusException si el microservicio falla o no está disponible.
     */
    @Override
    public AnalisisResponseDto analizarCodigo(AnalisisRequestDto request) {
        log.info("Enviando archivo '{}' al microservicio ML para análisis...", request.getNombreArchivo());

        try {
            AnalisisResponseDto respuesta = analisisWebClient
                    .post()
                    .uri("/analizar-codigo")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AnalisisResponseDto.class)
                    .timeout(TIMEOUT_ANALISIS)
                    .block();

            if (respuesta != null) {
                log.info("Análisis completado para '{}' — ¿Es seguro? {}",
                        request.getNombreArchivo(), respuesta.getEsSeguro());
            }
            return respuesta;

        } catch (WebClientResponseException e) {
            log.error("Error HTTP {} del microservicio al analizar '{}': {}",
                    e.getStatusCode(), request.getNombreArchivo(), e.getResponseBodyAsString());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "El microservicio de análisis devolvió un error: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("Error inesperado al comunicarse con el microservicio ML: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar al microservicio de análisis. Verifique que esté corriendo."
            );
        }
    }

    /**
     * Verifica que el microservicio esté activo y el modelo ML cargado.
     */
    @Override
    public boolean verificarDisponibilidad() {
        try {
            Map<?, ?> respuesta = analisisWebClient
                    .get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT_HEALTH)
                    .block();

            boolean modeloCargado = Boolean.TRUE.equals(
                    respuesta != null ? respuesta.get("modelo_cargado") : false
            );
            log.info("Health check del microservicio ML: disponible={}", modeloCargado);
            return modeloCargado;

        } catch (Exception e) {
            log.warn("Microservicio ML no disponible: {}", e.getMessage());
            return false;
        }
    }
}
