package ec.edu.espe.zonas.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de respuesta recibido desde el microservicio FastAPI tras el análisis.
 * Corresponde al JSON de retorno del endpoint POST /analizar-codigo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalisisResponseDto {

    /** Estado del análisis: "ok" o "completado" */
    @JsonProperty("status")
    private String status;

    /** Nombre del archivo analizado */
    @JsonProperty("archivo")
    private String archivo;

    /** true si ningún método fue detectado como vulnerable */
    @JsonProperty("es_seguro")
    private Boolean esSeguro;

    /** Total de métodos analizados en el archivo */
    @JsonProperty("total_metodos_analizados")
    private Integer totalMetodosAnalizados;

    /** Mensaje informativo (presente cuando no hay métodos analizables) */
    @JsonProperty("mensaje")
    private String mensaje;

    /** Lista de métodos vulnerables detectados (vacía si es_seguro=true) */
    @JsonProperty("vulnerabilidades_detectadas")
    private List<VulnerabilidadDto> vulnerabilidadesDetectadas;
}
