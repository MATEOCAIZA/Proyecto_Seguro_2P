package ec.edu.espe.zonas.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que representa un método vulnerable detectado por el modelo ML.
 * Corresponde a los objetos dentro de "vulnerabilidades_detectadas" de la respuesta FastAPI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VulnerabilidadDto {

    /** Nombre del método Java detectado como vulnerable */
    @JsonProperty("metodo")
    private String metodo;

    /** Porcentaje de probabilidad de vulnerabilidad (0.0 - 100.0) */
    @JsonProperty("probabilidad_vulnerable")
    private Double probabilidadVulnerable;
}
