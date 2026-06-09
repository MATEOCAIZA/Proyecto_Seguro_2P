package ec.edu.espe.zonas.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

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

    private int totalEspacios;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaModificacion;

    private int capacidad;

}
