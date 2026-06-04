package ec.edu.espe.zonas.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EspacioResponseDto {

    private UUID id;

    private String codigo;

    private String descripcion;

    private TipoEspacio tipo;

    private EstadoEspacio estado;

    private boolean activo;

    private UUID idZona;

    private String nombreZona;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaModificacion;
    
}
