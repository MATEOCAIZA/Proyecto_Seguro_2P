package ec.edu.espe.zonas.services.impl;

import ec.edu.espe.zonas.dtos.ZonaRequestDto;
import ec.edu.espe.zonas.dtos.ZonaResponseDto;
import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoZona;
import ec.edu.espe.zonas.entidades.Zona;
import ec.edu.espe.zonas.repositorios.ZonaRepositorio;
import ec.edu.espe.zonas.services.EspacioServicio;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ZonaServicioImpl — Pruebas Unitarias")
class ZonaServicioImplTest {

    @Mock
    private ZonaRepositorio repositorioZona;

    @Mock
    private EspacioServicio servicioEspacio;

    @InjectMocks
    private ZonaServicioImpl zonaServicio;

    private UUID idZona;
    private Zona zonaFija;
    private ZonaRequestDto requestValido;

    @BeforeEach
    void setUp() {
        idZona = UUID.randomUUID();
        
        zonaFija = Zona.builder()
                .id(idZona)
                .nombre("Zona VIP 1")
                .codigo("ZON-VIP-01")
                .descripcion("Zona VIP de prueba")
                .capacidad(10)
                .estado(1)
                .tipo(TipoZona.VIP)
                .espacios(new ArrayList<>())
                .fechaCreacion(LocalDateTime.now())
                .build();

        requestValido = ZonaRequestDto.builder()
                .nombre("Zona VIP 1")
                .descripcion("Zona VIP de prueba")
                .capacidad(10)
                .tipo(TipoZona.VIP)
                .build();
    }

    @Test
    @DisplayName("listarZonas — Debe retornar lista de ZonaResponseDto")
    void listarZonas_debeRetornarLista() {
        when(repositorioZona.findAll()).thenReturn(List.of(zonaFija));

        List<ZonaResponseDto> result = zonaServicio.listarZonas();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Zona VIP 1", result.get(0).getNombre());
        assertEquals("ZON-VIP-01", result.get(0).getCodigo());
        verify(repositorioZona, times(1)).findAll();
    }

    @Test
    @DisplayName("crearZona — Debe crear y retornar ZonaResponseDto cuando el nombre no existe")
    void crearZona_nombreNoExiste_debeCrearYRetornarDto() {
        when(repositorioZona.existsByNombre(requestValido.getNombre())).thenReturn(false);
        when(repositorioZona.countByTipo(TipoZona.VIP)).thenReturn(0L);
        when(repositorioZona.save(any(Zona.class))).thenAnswer(invocation -> {
            Zona saved = invocation.getArgument(0);
            saved.setId(idZona);
            return saved;
        });

        ZonaResponseDto response = zonaServicio.crearZona(requestValido);

        assertNotNull(response);
        assertEquals(idZona, response.getIdZona());
        assertEquals("Zona VIP 1", response.getNombre());
        assertEquals("ZON-VIP-01", response.getCodigo());
        assertEquals(1, response.getEstado());
        verify(repositorioZona, times(1)).existsByNombre(requestValido.getNombre());
        verify(repositorioZona, times(1)).countByTipo(TipoZona.VIP);
        verify(repositorioZona, times(1)).save(any(Zona.class));
    }

    @Test
    @DisplayName("crearZona — Debe lanzar ResponseStatusException CONFLICT cuando el nombre existe")
    void crearZona_nombreExiste_debeLanzarConflict() {
        when(repositorioZona.existsByNombre(requestValido.getNombre())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            zonaServicio.crearZona(requestValido);
        });

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Ya existe una zona con ese nombre", exception.getReason());
        verify(repositorioZona, times(1)).existsByNombre(requestValido.getNombre());
        verify(repositorioZona, never()).save(any(Zona.class));
    }

    @Test
    @DisplayName("actualizarZona — Debe actualizar y retornar ZonaResponseDto cuando la zona existe")
    void actualizarZona_zonaExiste_debeActualizarYRetornarDto() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));
        when(repositorioZona.save(any(Zona.class))).thenReturn(zonaFija);

        ZonaRequestDto updateRequest = ZonaRequestDto.builder()
                .nombre("Zona VIP Modificada")
                .descripcion("Nueva descripción")
                .capacidad(15)
                .build();

        ZonaResponseDto response = zonaServicio.actualizarZona(idZona, updateRequest);

        assertNotNull(response);
        assertEquals("Zona VIP Modificada", response.getNombre());
        assertEquals("Nueva descripción", response.getDescripcion());
        assertEquals(15, response.getCapacidad());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioZona, times(1)).save(any(Zona.class));
    }

    @Test
    @DisplayName("actualizarZona — Debe lanzar ResponseStatusException NOT_FOUND cuando la zona no existe")
    void actualizarZona_zonaNoExiste_debeLanzarNotFound() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            zonaServicio.actualizarZona(idZona, requestValido);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Zona no encontrada", exception.getReason());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioZona, never()).save(any(Zona.class));
    }

    @Test
    @DisplayName("activarDesactivar — Debe lanzar ResponseStatusException NOT_FOUND si la zona no existe")
    void activarDesactivar_zonaNoExiste_debeLanzarNotFound() {
        when(repositorioZona.findById(idZona)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            zonaServicio.activarDesactivar(idZona);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Zona no encontrada", exception.getReason());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(repositorioZona, never()).save(any(Zona.class));
    }

    @Test
    @DisplayName("activarDesactivar — Debe desactivar zona activa si no hay espacios ocupados")
    void activarDesactivar_zonaActivaSinEspaciosOcupados_debeDesactivar() {
        Espacio espacio = Espacio.builder().id(UUID.randomUUID()).activo(true).build();
        zonaFija.setEspacios(new ArrayList<>(List.of(espacio)));
        zonaFija.setEstado(1); // Activo

        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));
        when(servicioEspacio.obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.OCUPADO))
                .thenReturn(Collections.emptyList());
        when(repositorioZona.save(any(Zona.class))).thenReturn(zonaFija);

        zonaServicio.activarDesactivar(idZona);

        assertEquals(0, zonaFija.getEstado());
        assertFalse(espacio.isActivo());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(servicioEspacio, times(1)).obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.OCUPADO);
        verify(repositorioZona, times(1)).save(zonaFija);
    }

    @Test
    @DisplayName("activarDesactivar — Debe lanzar ResponseStatusException CONFLICT al desactivar zona activa con espacios ocupados")
    void activarDesactivar_zonaActivaConEspaciosOcupados_debeLanzarConflict() {
        zonaFija.setEstado(1); // Activo

        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));
        when(servicioEspacio.obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.OCUPADO))
                .thenReturn(List.of(mock(ec.edu.espe.zonas.dtos.EspacioResponseDto.class)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            zonaServicio.activarDesactivar(idZona);
        });

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("No se puede desactivar la zona: existen espacios ocupados", exception.getReason());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(servicioEspacio, times(1)).obtenerEspaciosPorZonaPorEstado(idZona, EstadoEspacio.OCUPADO);
        verify(repositorioZona, never()).save(any(Zona.class));
    }

    @Test
    @DisplayName("activarDesactivar — Debe activar zona inactiva y activar todos sus espacios")
    void activarDesactivar_zonaInactiva_debeActivar() {
        Espacio espacio = Espacio.builder().id(UUID.randomUUID()).activo(false).build();
        zonaFija.setEspacios(new ArrayList<>(List.of(espacio)));
        zonaFija.setEstado(0); // Inactivo

        when(repositorioZona.findById(idZona)).thenReturn(Optional.of(zonaFija));
        when(repositorioZona.save(any(Zona.class))).thenReturn(zonaFija);

        zonaServicio.activarDesactivar(idZona);

        assertEquals(1, zonaFija.getEstado());
        assertTrue(espacio.isActivo());
        verify(repositorioZona, times(1)).findById(idZona);
        verify(servicioEspacio, never()).obtenerEspaciosPorZonaPorEstado(any(), any());
        verify(repositorioZona, times(1)).save(zonaFija);
    }
}
