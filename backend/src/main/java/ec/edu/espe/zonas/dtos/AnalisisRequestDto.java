package ec.edu.espe.zonas.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de petición enviado al microservicio FastAPI.
 * Corresponde al modelo PeticionCodigo de api_modelo.py
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalisisRequestDto {

    /** Código fuente Java completo a analizar */
    @NotBlank(message = "El código fuente no puede estar vacío")
    @JsonProperty("codigo_fuente")
    private String codigoFuente;

    /** Nombre del archivo para identificarlo en el reporte */
    @JsonProperty("nombre_archivo")
    @Builder.Default
    private String nombreArchivo = "Desconocido.java";
}
