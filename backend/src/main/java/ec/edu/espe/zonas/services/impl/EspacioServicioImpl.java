package ec.edu.espe.zonas.services.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.Zona;
import ec.edu.espe.zonas.repositorios.EspacioRepositorio;
import ec.edu.espe.zonas.repositorios.ZonaRepositorio;
import ec.edu.espe.zonas.services.EspacioServicio;
import ec.edu.espe.zonas.utils.UtilsMappers;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EspacioServicioImpl implements EspacioServicio {

    private final EspacioRepositorio repositorioEspacio;
    private final ZonaRepositorio repositorioZona;
    private final UtilsMappers mapper;

    @Override
    @Transactional(readOnly = true)
    public List<EspacioResponseDto> obtenerEspacios() {

        return repositorioEspacio.findAll().stream().map(mapper::toResponseDto).collect(Collectors.toList());

    }

    @Override
    public EspacioResponseDto crearEspacio(EspacioRequestDto dto) {
     Zona objZona = repositorioZona.findById(dto.getIdZona())
                .orElseThrow(() -> new RuntimeException("Zona no encontrado con id: " + dto.getIdZona()));

        // Validar que no se supere la capacidad de la zona
        int espaciosActuales = objZona.getEspacios() != null ? objZona.getEspacios().size() : 0;
        if (espaciosActuales >= objZona.getCapacidad()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La zona ya alcanzó su capacidad máxima de " + objZona.getCapacidad() + " espacios");
        }

        Espacio nuevoEspacio = mapper.toEntityEspacio(dto);
        nuevoEspacio.setCodigo(generarCodigo(dto, objZona));
        nuevoEspacio.setZona(objZona);
        nuevoEspacio.setEstado(EstadoEspacio.DISPONIBLE);
        nuevoEspacio.setActivo(true);
        nuevoEspacio.setFechaCreacion(java.time.LocalDateTime.now());

        Espacio espacioSaved = repositorioEspacio.save(nuevoEspacio);

        return mapper.toResponseDto(espacioSaved);
    }

    @Override
    public EspacioResponseDto actualizarEspacio(UUID idEspacio, EspacioRequestDto dto) {

        Espacio objEspacio = repositorioEspacio.findById(idEspacio)
                .orElseThrow(() -> new RuntimeException("Espacio no encontrado con id: " + idEspacio));

        objEspacio.setDescripcion(dto.getDescripcion());
        objEspacio.setTipo(dto.getTipo());
        // objEspacio.setEstado(dto.getEstado());

        objEspacio.setFechaModificacion(java.time.LocalDateTime.now());

        Espacio espacioActualizado = repositorioEspacio.save(objEspacio);
        return mapper.toResponseDto(espacioActualizado);
    }

    @Override
    public void eliminarEspacio(UUID idEspacio) {
        Espacio objEspacio = repositorioEspacio.findById(idEspacio)
                .orElseThrow(() -> new RuntimeException("Espacio no encontrado con id: " + idEspacio));
        objEspacio.setActivo(false);
        objEspacio.setEstado(EstadoEspacio.MANTENIMIENTO);
        objEspacio.setFechaModificacion(java.time.LocalDateTime.now());
        repositorioEspacio.save(objEspacio);
    }

    @Override
    public EspacioResponseDto cambiarEstado(UUID idEspacio, EstadoEspacio estado) {
        Espacio objEspacio = repositorioEspacio.findById(idEspacio)
                .orElseThrow(() -> new RuntimeException("Espacio no encontrado con id: " + idEspacio));
        objEspacio.setEstado(estado);
        objEspacio.setFechaModificacion(java.time.LocalDateTime.now());
        Espacio espacioActualizado = repositorioEspacio.save(objEspacio);
        return mapper.toResponseDto(espacioActualizado);
    }

    @Override
    public List<EspacioResponseDto> obtenerEspaciosPorEstado(EstadoEspacio estado) {
        return repositorioEspacio.findByEstado(estado).stream()
                .map(mapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EspacioResponseDto> obtenerEspaciosPorZonaPorEstado(UUID idZona, EstadoEspacio estado) {
        Zona objZona = repositorioZona.findById(idZona)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con id: " + idZona));
        return repositorioEspacio.findByZonaAndEstado(objZona.getId(), estado).stream()
                .map(mapper::toResponseDto)
                .collect(Collectors.toList());
    }

    // Genera un codigo con formato: ESP-AUTO-01-ZON-REG01
    // Sigue el mismo patron que ZonaServicioImpl.generarCodigo()
    private String generarCodigo(EspacioRequestDto request, Zona zona) {
        String prefijo = "ESP";
        String tipoNombre = request.getTipo().name();
        long siguiente = repositorioEspacio.countByTipo(request.getTipo()) + 1;
        String numero = String.format("%02d", siguiente);
        String zonaAbrev = zona.getCodigo().replace("ZON-", "").replace("-", "");
        return prefijo + "-" + tipoNombre + "-" + numero + "-ZON-" + zonaAbrev;
    }
}