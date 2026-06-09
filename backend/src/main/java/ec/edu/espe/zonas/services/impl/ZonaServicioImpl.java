package ec.edu.espe.zonas.services.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ec.edu.espe.zonas.dtos.ZonaRequestDto;
import ec.edu.espe.zonas.dtos.ZonaResponseDto;
import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.Zona;
import ec.edu.espe.zonas.repositorios.ZonaRepositorio;
import ec.edu.espe.zonas.services.EspacioServicio;
import ec.edu.espe.zonas.services.ZonaServicio;

@Service
public class ZonaServicioImpl implements ZonaServicio {

    private final ZonaRepositorio repositorioZona;
    private final EspacioServicio servicioEspacio;

    public ZonaServicioImpl(ZonaRepositorio repositorioZona, EspacioServicio servicioEspacio) {
        this.repositorioZona = repositorioZona;
        this.servicioEspacio = servicioEspacio;
    }


    @Override
    @Transactional(readOnly = true)
    public List<ZonaResponseDto> listarZonas(){
        return repositorioZona.findAll().stream().map(this::toResponse).collect(Collectors.toList());    
    }

    @Override
    @Transactional
    public ZonaResponseDto crearZona(ZonaRequestDto request){

        if(repositorioZona.existsByNombre(request.getNombre())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una zona con ese nombre");
        }
        Zona objZona = new Zona();
        objZona.setNombre(request.getNombre());
        objZona.setCodigo(generarCodigo(request));
        objZona.setDescripcion(request.getDescripcion());
        objZona.setTipo(request.getTipo());
        objZona.setEstado(1);
        objZona.setCapacidad(request.getCapacidad());
        objZona.setFechaCreacion(LocalDateTime.now());
        repositorioZona.save(objZona);

        return toResponse(objZona);
    }

    @Override
    @Transactional
    public ZonaResponseDto actualizarZona(UUID idZona, ZonaRequestDto request){
        Zona objZona = repositorioZona.findById(idZona)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));

        objZona.setNombre(request.getNombre());
        objZona.setDescripcion(request.getDescripcion());
        objZona.setCapacidad(request.getCapacidad());
        //objZona.setTipo(request.getTipo());
        objZona.setFechaModificacion(LocalDateTime.now());

        Zona zonaActualizada = repositorioZona.save(objZona);
        return toResponse(zonaActualizada);
    }

    @Override
    @Transactional
    public void activarDesactivar(UUID idZona){
        // Los espacios deben estar disponibles para poder ser desactivados
        Zona objZona = repositorioZona.findById(idZona)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));

        int estadoOriginal = objZona.getEstado();
        List<Espacio> espacios = objZona.getEspacios();

        if (estadoOriginal == 1) { // Estaba activo, lo voy a desactivar
            boolean existenEspaciosOcupados = !servicioEspacio
                    .obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.OCUPADO).isEmpty();

            if (existenEspaciosOcupados) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se puede desactivar la zona: existen espacios ocupados");
            }
            for (Espacio espacio : espacios) {
                espacio.setActivo(false);
            }
            objZona.setEstado(0);
        } else {
            for (Espacio espacio : espacios) {
                espacio.setActivo(true);
            }
            objZona.setEstado(1);
        }
        repositorioZona.save(objZona);
    }

    private ZonaResponseDto toResponse(Zona objZona){
        int totalEspacios = (objZona.getEspacios() != null) ? objZona.getEspacios().size() : 0;
        return ZonaResponseDto.builder()
                .idZona(objZona.getId())
                .nombre(objZona.getNombre())
                .codigo(objZona.getCodigo())
                .descripcion(objZona.getDescripcion())
                .tipo(objZona.getTipo())
                .capacidad(objZona.getCapacidad())
                .estado(objZona.getEstado())
                .totalEspacios(totalEspacios)
                .fechaCreacion(objZona.getFechaCreacion())
                .fechaModificacion(objZona.getFechaModificacion())
                .build();
    }

    // Genera un codigo con formato: ZON-TIPO-NN  (ej: ZON-REG-01)
    private String generarCodigo(ZonaRequestDto request) {
        String prefijo = "ZON";
        String tipoAbrev = request.getTipo().name().substring(0, 3).toUpperCase();
        long siguiente = repositorioZona.countByTipo(request.getTipo()) + 1;
        String numero = String.format("%02d", siguiente);
        return prefijo + "-" + tipoAbrev + "-" + numero;
    }
    
}
