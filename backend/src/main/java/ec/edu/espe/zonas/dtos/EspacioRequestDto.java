package ec.edu.espe.zonas.dtos;

import java.util.UUID;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EspacioRequestDto {

    @NotNull(message = "El id de zona es obligatorio")
    private UUID idZona;

    private String codigo;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 1, max = 100, message = "La descripción debe tener entre 1 y 100 caracteres")
    private String descripcion;

    @NotNull(message = "El tipo de espacio es obligatorio")
    private TipoEspacio tipo;

    private EstadoEspacio estado;
}
