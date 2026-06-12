package ec.edu.espe.zonas.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.edu.espe.zonas.dtos.ZonaRequestDto;
import ec.edu.espe.zonas.dtos.ZonaResponseDto;
import ec.edu.espe.zonas.entidades.TipoZona;
import ec.edu.espe.zonas.services.ZonaServicio;
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
 * Pruebas unitarias del controlador ZonaController.
 *
 * Estrategia: MockMvcBuilders.standaloneSetup levanta solo el controlador (sin BD ni
 * contexto de Spring Boot), y @Mock sustituye ZonaServicio con un mock de Mockito.
 * Se validan códigos HTTP, cuerpos JSON y llamadas al servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ZonaController — Pruebas Unitarias")
class ZonaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ZonaServicio zonaServicio;

    @InjectMocks
    private ZonaController zonaController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Datos de prueba reutilizables ────────────────────────────────────────

    private UUID idZonaFijo;
    private ZonaResponseDto zonaResponseFija;
    private ZonaRequestDto zonaRequestValido;

    @BeforeEach
    void setUp() {
        // Configurar validador de Bean Validation para que @NotBlank etc. funcionen
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(zonaController)
                .setValidator(validator)
                .build();

        idZonaFijo = UUID.randomUUID();

        zonaResponseFija = ZonaResponseDto.builder()
                .idZona(idZonaFijo)
                .nombre("Zona Norte")
                .codigo("ZON-REG-01")
                .descripcion("Zona de prueba")
                .tipo(TipoZona.REGULAR)
                .capacidad(50)
                .estado(1)
                .totalEspacios(0)
                .fechaCreacion(LocalDateTime.now())
                .build();

        zonaRequestValido = ZonaRequestDto.builder()
                .nombre("Zona Norte")
                .descripcion("Zona de prueba")
                .tipo(TipoZona.REGULAR)
                .capacidad(50)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/zonas/
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/v1/zonas/ → 200 con lista de zonas")
    void listarZonas_debeRetornar200ConLista() throws Exception {
        // Arrange
        when(zonaServicio.listarZonas()).thenReturn(List.of(zonaResponseFija));

        // Act & Assert
        mockMvc.perform(get("/api/v1/zonas/"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre", is("Zona Norte")))
                .andExpect(jsonPath("$[0].codigo", is("ZON-REG-01")))
                .andExpect(jsonPath("$[0].estado", is(1)));

        verify(zonaServicio, times(1)).listarZonas();
    }

    @Test
    @DisplayName("GET /api/v1/zonas/ → 200 con lista vacía cuando no hay zonas")
    void listarZonas_debeRetornar200ConListaVacia() throws Exception {
        // Arrange
        when(zonaServicio.listarZonas()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/v1/zonas/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  POST /api/v1/zonas/
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /api/v1/zonas/ → 201 cuando la zona se crea exitosamente")
    void crearZona_conRequestValido_debeRetornar201() throws Exception {
        // Arrange
        when(zonaServicio.crearZona(any(ZonaRequestDto.class))).thenReturn(zonaResponseFija);

        // Act & Assert
        mockMvc.perform(post("/api/v1/zonas/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zonaRequestValido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre", is("Zona Norte")))
                .andExpect(jsonPath("$.codigo", is("ZON-REG-01")))
                .andExpect(jsonPath("$.tipo", is("REGULAR")));

        verify(zonaServicio, times(1)).crearZona(any(ZonaRequestDto.class));
    }

    @Test
    @DisplayName("POST /api/v1/zonas/ → 400 cuando el nombre está vacío (validación)")
    void crearZona_conNombreVacio_debeRetornar400() throws Exception {
        // Arrange: nombre vacío viola @NotBlank
        ZonaRequestDto requestInvalido = ZonaRequestDto.builder()
                .nombre("")
                .tipo(TipoZona.REGULAR)
                .capacidad(10)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/zonas/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(zonaServicio, never()).crearZona(any());
    }

    @Test
    @DisplayName("POST /api/v1/zonas/ → 409 cuando el nombre ya existe (conflicto)")
    void crearZona_conNombreDuplicado_debeRetornar409() throws Exception {
        // Arrange
        when(zonaServicio.crearZona(any(ZonaRequestDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "YA EXISTE EL NOMBRE"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/zonas/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zonaRequestValido)))
                .andExpect(status().isConflict());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PUT /api/v1/zonas/{idZona}
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /api/v1/zonas/{id} → 200 cuando la zona se actualiza")
    void actualizarZona_conRequestValido_debeRetornar200() throws Exception {
        // Arrange
        ZonaResponseDto zonaActualizada = ZonaResponseDto.builder()
                .idZona(idZonaFijo)
                .nombre("Zona Norte Actualizada")
                .codigo("ZON-REG-01")
                .tipo(TipoZona.REGULAR)
                .capacidad(80)
                .estado(1)
                .totalEspacios(0)
                .fechaModificacion(LocalDateTime.now())
                .build();

        when(zonaServicio.actualizarZona(eq(idZonaFijo), any(ZonaRequestDto.class)))
                .thenReturn(zonaActualizada);

        // Act & Assert
        mockMvc.perform(put("/api/v1/zonas/{idZona}", idZonaFijo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zonaRequestValido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Zona Norte Actualizada")));

        verify(zonaServicio, times(1)).actualizarZona(eq(idZonaFijo), any(ZonaRequestDto.class));
    }

    @Test
    @DisplayName("PUT /api/v1/zonas/{id} → 404 cuando la zona no existe")
    void actualizarZona_zonaInexistente_debeRetornar404() throws Exception {
        // Arrange
        when(zonaServicio.actualizarZona(eq(idZonaFijo), any(ZonaRequestDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));

        // Act & Assert
        mockMvc.perform(put("/api/v1/zonas/{idZona}", idZonaFijo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zonaRequestValido)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATCH /api/v1/zonas/{idZona}/estado
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /api/v1/zonas/{id}/estado → 200 al activar/desactivar zona")
    void activarDesactivar_zonaExistente_debeRetornar200() throws Exception {
        // Arrange
        doNothing().when(zonaServicio).activarDesactivar(idZonaFijo);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/zonas/{idZona}/estado", idZonaFijo))
                .andExpect(status().isOk());

        verify(zonaServicio, times(1)).activarDesactivar(idZonaFijo);
    }

    @Test
    @DisplayName("PATCH /api/v1/zonas/{id}/estado → 404 cuando la zona no existe")
    void activarDesactivar_zonaInexistente_debeRetornar404() throws Exception {
        // Arrange
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"))
                .when(zonaServicio).activarDesactivar(idZonaFijo);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/zonas/{idZona}/estado", idZonaFijo))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/zonas/{id}/estado → 409 cuando existen espacios ocupados")
    void activarDesactivar_espaciosOcupados_debeRetornar409() throws Exception {
        // Arrange
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede desactivar la zona: existen espacios OCUPADOS"))
                .when(zonaServicio).activarDesactivar(idZonaFijo);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/zonas/{idZona}/estado", idZonaFijo))
                .andExpect(status().isConflict());
    }
}
