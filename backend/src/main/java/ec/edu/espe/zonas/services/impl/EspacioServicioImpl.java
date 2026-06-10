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
    @Transactional
    public EspacioResponseDto crearEspacio(EspacioRequestDto dto) {
        Zona objZona = repositorioZona.findById(dto.getIdZona())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));

        // Validar que no se supere la capacidad de la zona
        int espaciosActuales = objZona.getEspacios() != null ? objZona.getEspacios().size() : 0;
        if (espaciosActuales >= objZona.getCapacidad()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La zona ya alcanzo su capacidad maxima");
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
    @Transactional
    public EspacioResponseDto actualizarEspacio(UUID idEspacio, EspacioRequestDto dto) {

        Espacio objEspacio = repositorioEspacio.findById(idEspacio)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        objEspacio.setDescripcion(dto.getDescripcion());
        objEspacio.setTipo(dto.getTipo());
        // objEspacio.setEstado(dto.getEstado());

        objEspacio.setFechaModificacion(java.time.LocalDateTime.now());

        Espacio espacioActualizado = repositorioEspacio.save(objEspacio);
        return mapper.toResponseDto(espacioActualizado);
    }

    @Override
    @Transactional
    public void eliminarEspacio(UUID idEspacio) {
        Espacio objEspacio = repositorioEspacio.findById(idEspacio)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));
        objEspacio.setActivo(false);
        objEspacio.setEstado(EstadoEspacio.MANTENIMIENTO);
        objEspacio.setFechaModificacion(java.time.LocalDateTime.now());
        repositorioEspacio.save(objEspacio);
    }

    @Override
    @Transactional
    public EspacioResponseDto cambiarEstado(UUID idEspacio, EstadoEspacio estado) {
        if (estado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado no puede ser nulo");
        }
        Espacio objEspacio = repositorioEspacio.findById(idEspacio)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));
        objEspacio.setEstado(estado);
        objEspacio.setFechaModificacion(java.time.LocalDateTime.now());
        Espacio espacioActualizado = repositorioEspacio.save(objEspacio);
        return mapper.toResponseDto(espacioActualizado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspacioResponseDto> obtenerEspaciosPorEstado(EstadoEspacio estado) {
        return repositorioEspacio.findByEstado(estado).stream()
                .map(mapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspacioResponseDto> obtenerEspaciosPorZonaPorEstado(UUID idZona, EstadoEspacio estado) {
        Zona objZona = repositorioZona.findById(idZona)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));
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