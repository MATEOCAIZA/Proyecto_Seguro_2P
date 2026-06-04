package ec.edu.espe.zonas.dtos;

import java.util.UUID;

import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EspacioRequestDto {
    @NotBlank(message = "El id de la zona no puede estar vacio")
    @NotNull(message = "El ida de la zona es obligatorio")
    private UUID idZona;

    private String descripcion;

    private String codigo;
    
    @Enumerated(EnumType.STRING)
    @NotBlank(message = "El tipo de espacio es obligatorio")
    private TipoEspacio tipo;

    @Enumerated(EnumType.STRING)
    private EstadoEspacio estado;

    

}
