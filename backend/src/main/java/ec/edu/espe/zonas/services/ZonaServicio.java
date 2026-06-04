package ec.edu.espe.zonas.services;

import java.util.List;
import java.util.UUID;

import ec.edu.espe.zonas.dtos.ZonaRequestDto;
import ec.edu.espe.zonas.dtos.ZonaResponseDto;


public interface ZonaServicio {
    
    List<ZonaResponseDto> listarZonas();
    ZonaResponseDto crearZona(ZonaRequestDto request);

    ZonaResponseDto actualizarZona(UUID idZona, ZonaRequestDto request);

    void activarDesactivar(UUID idZona);

}
