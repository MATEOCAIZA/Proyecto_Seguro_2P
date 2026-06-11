package ec.edu.espe.zonas.services.impl;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;
import ec.edu.espe.zonas.entidades.Zona;
import ec.edu.espe.zonas.repositorios.EspacioRepositorio;
import ec.edu.espe.zonas.repositorios.ZonaRepositorio;
import ec.edu.espe.zonas.utils.UtilsMappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EspacioServicioImpl — Pruebas Unitarias")
class EspacioServicioImplTest {

    @Mock
    private EspacioRepositorio repositorioEspacio;

    @Mock
    private ZonaRepositorio repositorioZona;

    @Mock
    private UtilsMappers mapper;

    @InjectMocks
    private EspacioServicioImpl espacioServicio;

    private UUID idZona;
    private UUID idEspacio;
    private Zona zonaFija;
    private Espacio espacioFijo;
    private EspacioRequestDto requestValido;
    private EspacioResponseDto responseValido;

    @BeforeEach
    void setUp() {
        idZona = UUID.randomUUID();
        idEspacio = UUID.randomUUID();

        zonaFija = Zona.builder()
                .id(idZona)
                .nombre("Zona VIP")
                .codigo("ZON-VIP-01")
                .capacidad(2)
                .estado(1)
                .espacios(new ArrayList<>())
                .build();

        espacioFijo = Espacio.builder()
                .id(idEspacio)
                .codigo("ESP-AUTO-01-ZON-VIP01")
                .descripcion("Espacio para auto")
                .tipo(TipoEspacio.AUTO)
                .activo(true)
                .estado(EstadoEspacio.DISPONIBLE)
                .zona(zonaFija)
                .fechaCreacion(LocalDateTime.now())
                .build();

        requestValido = EspacioRequestDto.builder()
                .idZona(idZona)
                .descripcion("Espacio para auto")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.DISPONIBLE)
                .build();

        responseValido = EspacioResponseDto.builder()
                .id(idEspacio)
                .codigo("ESP-AUTO-01-ZON-VIP01")
                .descripcion("Espacio para auto")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.DISPONIBLE)
                .activo(true)
                .idZona(idZona)
                .nombreZona("Zona VIP")
                .build();
    }

    @Test
    @DisplayName("obtenerEspacios — Debe retornar lista de EspacioResponseDto")
    void obtenerEspacios_debeRetornarLista() {
        when(repositorioEspacio.findAll()).thenReturn(List.of(espacioFijo));
        when(mapper.toResponseDto(espacioFijo)).thenReturn(responseValido);

        List<EspacioResponseDto> result = espacioServicio.obtenerEspacios();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ESP-AUTO-01-ZON-VIP01", result.get(0).getCodigo());
        verify(repositorioEspacio, times(1)).findAll();
        verify(mapper, times(1)).toResponseDto(espacioFijo);
    }

    @Test
    @DisplayName("crearEspacio — Debe lanzar NOT_FOUND si la zona no existe")
    void crearEspacio_zonaNoExiste_debeLanzarNotFound() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.crearEspacio(requestValido);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Zona no encontrada", exception.getReason());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioEspacio, never()).save(any());
    }

    @Test
    @DisplayName("crearEspacio — Debe lanzar CONFLICT si la zona está a máxima capacidad")
    void crearEspacio_zonaCapacidadMaxima_debeLanzarConflict() {
        // Llenamos la zona con 2 espacios ya que la capacidad es 2
        zonaFija.getEspacios().add(new Espacio());
        zonaFija.getEspacios().add(new Espacio());

        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.crearEspacio(requestValido);
        });

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("La zona ya alcanzo su capacidad maxima", exception.getReason());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioEspacio, never()).save(any());
    }

    @Test
    @DisplayName("crearEspacio — Debe guardar y retornar DTO cuando sea exitoso")
    void crearEspacio_exitoso_debeGuardarYRetornarDto() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));
        when(mapper.toEntityEspacio(requestValido)).thenReturn(new Espacio());
        when(repositorioEspacio.countByTipo(TipoEspacio.AUTO)).thenReturn(0L);
        when(repositorioEspacio.save(any(Espacio.class))).thenReturn(espacioFijo);
        when(mapper.toResponseDto(espacioFijo)).thenReturn(responseValido);

        EspacioResponseDto result = espacioServicio.crearEspacio(requestValido);

        assertNotNull(result);
        assertEquals("ESP-AUTO-01-ZON-VIP01", result.getCodigo());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioEspacio, times(1)).countByTipo(TipoEspacio.AUTO);
        verify(repositorioEspacio, times(1)).save(any(Espacio.class));
        verify(mapper, times(1)).toResponseDto(espacioFijo);
    }

    @Test
    @DisplayName("actualizarEspacio — Debe lanzar NOT_FOUND si el espacio no existe")
    void actualizarEspacio_espacioNoExiste_debeLanzarNotFound() {
        when(repositorioEspacio.findById(idEspacio)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.actualizarEspacio(idEspacio, requestValido);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Espacio no encontrado", exception.getReason());
        verify(repositorioEspacio, times(1)).findById(idEspacio);
        verify(repositorioEspacio, never()).save(any());
    }

    @Test
    @DisplayName("actualizarEspacio — Debe actualizar campos y retornar DTO si existe")
    void actualizarEspacio_exitoso_debeActualizarYRetornarDto() {
        when(repositorioEspacio.findById(idEspacio)).thenReturn(Optional.of(espacioFijo));
        when(repositorioEspacio.save(any(Espacio.class))).thenReturn(espacioFijo);
        when(mapper.toResponseDto(espacioFijo)).thenReturn(responseValido);

        EspacioRequestDto requestUpdate = EspacioRequestDto.builder()
                .descripcion("Descripción actualizada")
                .tipo(TipoEspacio.MOTO)
                .build();

        EspacioResponseDto result = espacioServicio.actualizarEspacio(idEspacio, requestUpdate);

        assertNotNull(result);
        verify(repositorioEspacio, times(1)).findById(idEspacio);
        verify(repositorioEspacio, times(1)).save(espacioFijo);
        assertEquals(TipoEspacio.MOTO, espacioFijo.getTipo());
        assertEquals("Descripción actualizada", espacioFijo.getDescripcion());
    }

    @Test
    @DisplayName("eliminarEspacio — Debe lanzar NOT_FOUND si el espacio no existe")
    void eliminarEspacio_espacioNoExiste_debeLanzarNotFound() {
        when(repositorioEspacio.findById(idEspacio)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.eliminarEspacio(idEspacio);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Espacio no encontrado", exception.getReason());
        verify(repositorioEspacio, times(1)).findById(idEspacio);
        verify(repositorioEspacio, never()).save(any());
    }

    @Test
    @DisplayName("eliminarEspacio — Debe marcar como inactivo y estado MANTENIMIENTO")
    void eliminarEspacio_exitoso_debeCambiarEstado() {
        when(repositorioEspacio.findById(idEspacio)).thenReturn(Optional.of(espacioFijo));
        when(repositorioEspacio.save(any(Espacio.class))).thenReturn(espacioFijo);

        espacioServicio.eliminarEspacio(idEspacio);

        assertFalse(espacioFijo.isActivo());
        assertEquals(EstadoEspacio.MANTENIMIENTO, espacioFijo.getEstado());
        verify(repositorioEspacio, times(1)).findById(idEspacio);
        verify(repositorioEspacio, times(1)).save(espacioFijo);
    }

    @Test
    @DisplayName("cambiarEstado — Debe lanzar BAD_REQUEST si el estado es nulo")
    void cambiarEstado_estadoNulo_debeLanzarBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.cambiarEstado(idEspacio, null);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("El estado no puede ser nulo", exception.getReason());
        verify(repositorioEspacio, never()).findById(any());
    }

    @Test
    @DisplayName("cambiarEstado — Debe lanzar NOT_FOUND si el espacio no existe")
    void cambiarEstado_espacioNoExiste_debeLanzarNotFound() {
        when(repositorioEspacio.findById(idEspacio)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.cambiarEstado(idEspacio, EstadoEspacio.OCUPADO);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Espacio no encontrado", exception.getReason());
        verify(repositorioEspacio, times(1)).findById(idEspacio);
        verify(repositorioEspacio, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado — Debe cambiar el estado exitosamente si existe")
    void cambiarEstado_exitoso_debeCambiarEstado() {
        when(repositorioEspacio.findById(idEspacio)).thenReturn(Optional.of(espacioFijo));
        when(repositorioEspacio.save(any(Espacio.class))).thenReturn(espacioFijo);
        when(mapper.toResponseDto(espacioFijo)).thenReturn(responseValido);

        espacioServicio.cambiarEstado(idEspacio, EstadoEspacio.RESERVADO);

        assertEquals(EstadoEspacio.RESERVADO, espacioFijo.getEstado());
        verify(repositorioEspacio, times(1)).findById(idEspacio);
        verify(repositorioEspacio, times(1)).save(espacioFijo);
    }

    @Test
    @DisplayName("obtenerEspaciosPorEstado — Debe retornar lista de EspacioResponseDto")
    void obtenerEspaciosPorEstado_debeRetornarLista() {
        when(repositorioEspacio.findByEstado(EstadoEspacio.DISPONIBLE)).thenReturn(List.of(espacioFijo));
        when(mapper.toResponseDto(espacioFijo)).thenReturn(responseValido);

        List<EspacioResponseDto> result = espacioServicio.obtenerEspaciosPorEstado(EstadoEspacio.DISPONIBLE);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repositorioEspacio, times(1)).findByEstado(EstadoEspacio.DISPONIBLE);
        verify(mapper, times(1)).toResponseDto(espacioFijo);
    }

    @Test
    @DisplayName("obtenerEspaciosPorZonaPorEstado — Debe lanzar NOT_FOUND si la zona no existe")
    void obtenerEspaciosPorZonaPorEstado_zonaNoExiste_debeLanzarNotFound() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            espacioServicio.obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.DISPONIBLE);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Zona no encontrada", exception.getReason());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioEspacio, never()).findByZonaAndEstado(any(), any());
    }

    @Test
    @DisplayName("obtenerEspaciosPorZonaPorEstado — Debe retornar lista si la zona existe")
    void obtenerEspaciosPorZonaPorEstado_zonaExiste_debeRetornarLista() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));
        when(repositorioEspacio.findByZonaAndEstado(idZona, EstadoEspacio.DISPONIBLE)).thenReturn(List.of(espacioFijo));
        when(mapper.toResponseDto(espacioFijo)).thenReturn(responseValido);

        List<EspacioResponseDto> result = espacioServicio.obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.DISPONIBLE);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioEspacio, times(1)).findByZonaAndEstado(idZona, EstadoEspacio.DISPONIBLE);
        verify(mapper, times(1)).toResponseDto(espacioFijo);
    }
}
