package ec.edu.espe.zonas.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.TipoZona;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ZonaResponseDto {
    private UUID idZona;
    private String nombre;

    private String codigo;

    private String descripcion;

    private int estado;

    private TipoZona tipo;

    private List<Espacio> espacios;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaModificacion;

    private int capacidad;

}
