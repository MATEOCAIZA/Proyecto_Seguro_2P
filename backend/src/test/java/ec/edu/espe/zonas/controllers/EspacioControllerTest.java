package ec.edu.espe.zonas.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;
import ec.edu.espe.zonas.services.EspacioServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas unitarias del controlador EspacioController.
 *
 * Estrategia: MockMvcBuilders.standaloneSetup levanta solo el controlador (sin BD ni
 * contexto de Spring Boot). EspacioServicio es un mock de Mockito.
 * Se validan: códigos HTTP, cuerpos JSON y delegación al servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EspacioController — Pruebas Unitarias")
class EspacioControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EspacioServicio espacioServicio;

    @InjectMocks
    private EspacioController espacioController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Datos de prueba reutilizables ────────────────────────────────────────

    private UUID idEspacioFijo;
    private UUID idZonaFijo;
    private EspacioResponseDto espacioResponseFijo;
    private EspacioRequestDto espacioRequestValido;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(espacioController)
                .setValidator(validator)
                .build();

        idEspacioFijo = UUID.randomUUID();
        idZonaFijo    = UUID.randomUUID();

        espacioResponseFijo = EspacioResponseDto.builder()
                .id(idEspacioFijo)
                .codigo("ESP-A1")
                .descripcion("Sala A1")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.DISPONIBLE)
                .activo(true)
                .idZona(idZonaFijo)
                .nombreZona("Zona Norte")
                .fechaCreacion(LocalDateTime.now())
                .build();

        espacioRequestValido = EspacioRequestDto.builder()
                .idZona(idZonaFijo)
                .descripcion("Sala A1")
                .codigo("ESP-A1")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.DISPONIBLE)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/espacios/
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/v1/espacios/ → 200 con lista de espacios")
    void listarEspacios_debeRetornar200ConLista() throws Exception {
        // Arrange
        when(espacioServicio.obtenerEspacios()).thenReturn(List.of(espacioResponseFijo));

        // Act & Assert
        mockMvc.perform(get("/api/v1/espacios/"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo", is("ESP-A1")))
                .andExpect(jsonPath("$[0].estado", is("DISPONIBLE")))
                .andExpect(jsonPath("$[0].activo", is(true)));

        verify(espacioServicio, times(1)).obtenerEspacios();
    }

    @Test
    @DisplayName("GET /api/v1/espacios/ → 200 con lista vacía")
    void listarEspacios_sinEspacios_debeRetornarListaVacia() throws Exception {
        // Arrange
        when(espacioServicio.obtenerEspacios()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/v1/espacios/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  POST /api/v1/espacios/
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /api/v1/espacios/ → 201 al crear un espacio válido")
    void crearEspacio_conRequestValido_debeRetornar201() throws Exception {
        // Arrange
        when(espacioServicio.crearEspacio(any(EspacioRequestDto.class))).thenReturn(espacioResponseFijo);

        // Act & Assert
        mockMvc.perform(post("/api/v1/espacios/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(espacioRequestValido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", is("ESP-A1")))
                .andExpect(jsonPath("$.tipo", is("AUTO")));

        verify(espacioServicio, times(1)).crearEspacio(any(EspacioRequestDto.class));
    }

    @Test
    @DisplayName("POST /api/v1/espacios/ → 404 cuando la zona referenciada no existe")
    void crearEspacio_zonaInexistente_debeRetornar404() throws Exception {
        // Arrange
        when(espacioServicio.crearEspacio(any(EspacioRequestDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/espacios/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(espacioRequestValido)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PUT /api/v1/espacios/{idEspacio}
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /api/v1/espacios/{id} → 200 al actualizar un espacio")
    void actualizarEspacio_conRequestValido_debeRetornar200() throws Exception {
        // Arrange
        EspacioResponseDto actualizado = EspacioResponseDto.builder()
                .id(idEspacioFijo)
                .codigo("ESP-A1")
                .descripcion("Sala A1 Modificada")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.DISPONIBLE)
                .activo(true)
                .idZona(idZonaFijo)
                .fechaModificacion(LocalDateTime.now())
                .build();

        when(espacioServicio.actualizarEspacio(eq(idEspacioFijo), any(EspacioRequestDto.class)))
                .thenReturn(actualizado);

        // Act & Assert
        mockMvc.perform(put("/api/v1/espacios/{idEspacio}", idEspacioFijo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(espacioRequestValido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion", is("Sala A1 Modificada")));

        verify(espacioServicio, times(1)).actualizarEspacio(eq(idEspacioFijo), any(EspacioRequestDto.class));
    }

    @Test
    @DisplayName("PUT /api/v1/espacios/{id} → 404 cuando el espacio no existe")
    void actualizarEspacio_espacioInexistente_debeRetornar404() throws Exception {
        // Arrange
        when(espacioServicio.actualizarEspacio(eq(idEspacioFijo), any(EspacioRequestDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        // Act & Assert
        mockMvc.perform(put("/api/v1/espacios/{idEspacio}", idEspacioFijo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(espacioRequestValido)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DELETE /api/v1/espacios/{idEspacio}
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /api/v1/espacios/{id} → 204 al eliminar un espacio existente")
    void eliminarEspacio_espacioExistente_debeRetornar204() throws Exception {
        // Arrange
        doNothing().when(espacioServicio).eliminarEspacio(idEspacioFijo);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/espacios/{idEspacio}", idEspacioFijo))
                .andExpect(status().isNoContent());

        verify(espacioServicio, times(1)).eliminarEspacio(idEspacioFijo);
    }

    @Test
    @DisplayName("DELETE /api/v1/espacios/{id} → 404 cuando el espacio no existe")
    void eliminarEspacio_espacioInexistente_debeRetornar404() throws Exception {
        // Arrange
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Espacio no encontrado"))
                .when(espacioServicio).eliminarEspacio(idEspacioFijo);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/espacios/{idEspacio}", idEspacioFijo))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATCH /api/v1/espacios/{idEspacio}/estado/{estado}
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /api/v1/espacios/{id}/estado/OCUPADO → 200 al cambiar estado")
    void cambiarEstado_estadoValido_debeRetornar200() throws Exception {
        // Arrange
        EspacioResponseDto ocupado = EspacioResponseDto.builder()
                .id(idEspacioFijo)
                .codigo("ESP-A1")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.OCUPADO)
                .activo(true)
                .idZona(idZonaFijo)
                .build();

        when(espacioServicio.cambiarEstado(eq(idEspacioFijo), eq(EstadoEspacio.OCUPADO))).thenReturn(ocupado);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/espacios/{idEspacio}/estado/{estado}",
                        idEspacioFijo, EstadoEspacio.OCUPADO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("OCUPADO")));

        verify(espacioServicio, times(1)).cambiarEstado(idEspacioFijo, EstadoEspacio.OCUPADO);
    }

    @Test
    @DisplayName("PATCH /api/v1/espacios/{id}/estado/{estado} → 404 cuando el espacio no existe")
    void cambiarEstado_espacioInexistente_debeRetornar404() throws Exception {
        // Arrange
        when(espacioServicio.cambiarEstado(eq(idEspacioFijo), any(EstadoEspacio.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        // Act & Assert
        mockMvc.perform(patch("/api/v1/espacios/{idEspacio}/estado/{estado}",
                        idEspacioFijo, EstadoEspacio.DISPONIBLE))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/espacios/estado/{estado}
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/v1/espacios/estado/DISPONIBLE → 200 filtrando por estado")
    void listarPorEstado_estadoDisponible_debeRetornar200() throws Exception {
        // Arrange
        when(espacioServicio.obtenerEspaciosPorEstado(EstadoEspacio.DISPONIBLE))
                .thenReturn(List.of(espacioResponseFijo));

        // Act & Assert
        mockMvc.perform(get("/api/v1/espacios/estado/{estado}", EstadoEspacio.DISPONIBLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado", is("DISPONIBLE")));

        verify(espacioServicio, times(1)).obtenerEspaciosPorEstado(EstadoEspacio.DISPONIBLE);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/espacios/zona/{idZona}/estado/{estado}
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/v1/espacios/zona/{idZona}/estado/DISPONIBLE → 200 filtrando por zona y estado")
    void listarPorZonaYEstado_debeRetornar200() throws Exception {
        // Arrange
        when(espacioServicio.obtenerEspaciosPorZonaPorEstado(idZonaFijo, EstadoEspacio.DISPONIBLE))
                .thenReturn(List.of(espacioResponseFijo));

        // Act & Assert
        mockMvc.perform(get("/api/v1/espacios/zona/{idZona}/estado/{estado}",
                        idZonaFijo, EstadoEspacio.DISPONIBLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo", is("ESP-A1")));

        verify(espacioServicio, times(1))
                .obtenerEspaciosPorZonaPorEstado(idZonaFijo, EstadoEspacio.DISPONIBLE);
    }
}
