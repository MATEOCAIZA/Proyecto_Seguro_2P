package ec.edu.espe.zonas.services;

import java.util.List;
import java.util.UUID;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.EstadoEspacio;

public interface EspacioServicio {
    
    List<EspacioResponseDto> obtenerEspacios();

    EspacioResponseDto crearEspacio(EspacioRequestDto dto);

    EspacioResponseDto actualizarEspacio(UUID Espacio,EspacioRequestDto dto);

    void eliminarEspacio(UUID idEspacio);

    EspacioResponseDto cambiarEstado(UUID idEspacio, EstadoEspacio estado);

    List<EspacioResponseDto> obtenerEspaciosPorEstado(EstadoEspacio estado);

    List<EspacioResponseDto> obtenerEspaciosPorZonaPorEstado(UUID idZona, EstadoEspacio estado);

}
