package ec.edu.espe.zonas.dtos;

import ec.edu.espe.zonas.entidades.TipoZona;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonaRequestDto {
    
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min =1, max = 32, message = "El nombre debe tener entre 1 y 32 caracteres")
    private String nombre;

    private String descripcion;
    @Min(1)
    @Max(100)
    private int capacidad;

    @Enumerated(EnumType.STRING)
    private TipoZona tipo;




}
