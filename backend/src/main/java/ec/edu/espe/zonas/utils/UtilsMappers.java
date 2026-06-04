package ec.edu.espe.zonas.utils;

import org.springframework.stereotype.Component;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.Espacio;

@Component
public class UtilsMappers {
    
    public EspacioResponseDto toResponseDto(Espacio objEspacio){

        if(objEspacio == null) return null;
        return EspacioResponseDto.builder()
                .id(objEspacio.getId())
                .codigo(objEspacio.getCodigo())
                .descripcion(objEspacio.getDescripcion())
                .tipo(objEspacio.getTipo())
                .activo(objEspacio.isActivo())
                .estado(objEspacio.getEstado())
                .idZona(objEspacio.getZona().getId())
                .nombreZona(objEspacio.getZona().getNombre())
                .fechaCreacion(objEspacio.getFechaCreacion())
                .fechaModificacion(objEspacio.getFechaModificacion())
                .build();

    }


    public Espacio toEntityEspacio(EspacioRequestDto requestDto){
        if(requestDto==null) return null;

        return Espacio.builder()
                .codigo(requestDto.getCodigo())
                .descripcion(requestDto.getDescripcion())
                .tipo(requestDto.getTipo())
                .estado(requestDto.getEstado())
                .build();

    }




}
