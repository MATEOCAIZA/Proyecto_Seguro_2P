package ec.edu.espe.zonas.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.dtos.VulnerabilidadDto;
import ec.edu.espe.zonas.services.AnalisisMensajeriaServicio;
import ec.edu.espe.zonas.services.AnalisisServicio;
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

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas unitarias del controlador AnalisisController.
 *
 * Estrategia: MockMvcBuilders.standaloneSetup levanta solo el controlador (sin BD ni
 * contexto de Spring Boot). AnalisisServicio es un mock de Mockito.
 *
 * Escenarios cubiertos:
 *  - POST /codigo con código seguro → respuesta con esSeguro=true
 *  - POST /codigo con código vulnerable → respuesta con vulnerabilidades
 *  - POST /codigo con body vacío → validación 400
 *  - POST /codigo cuando el modelo ML no está disponible → 503
 *  - GET  /health cuando microservicio disponible → mensaje ok
 *  - GET  /health cuando microservicio no disponible → mensaje de error
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalisisController — Pruebas Unitarias")
class AnalisisControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalisisServicio analisisServicio;

    @Mock
    private AnalisisMensajeriaServicio analisisMensajeriaServicio;

    @InjectMocks
    private AnalisisController analisisController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Datos de prueba reutilizables ────────────────────────────────────────

    private AnalisisRequestDto requestValido;

    private static final String CODIGO_JAVA_SIMPLE = """
            public class Ejemplo {
                public void saludar() {
                    System.out.println("Hola mundo");
                }
            }
            """;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(analisisController)
                .setValidator(validator)
                .build();

        requestValido = AnalisisRequestDto.builder()
                .codigoFuente(CODIGO_JAVA_SIMPLE)
                .nombreArchivo("Ejemplo.java")
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  POST /api/v1/analisis/codigo
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /codigo → 200 cuando el código es seguro (sin vulnerabilidades)")
    void analizarCodigo_codigoSeguro_debeRetornar200ConEsSeguroTrue() throws Exception {
        // Arrange: el microservicio ML dice que el código es seguro
        AnalisisResponseDto respuestaSegura = AnalisisResponseDto.builder()
                .status("completado")
                .archivo("Ejemplo.java")
                .esSeguro(true)
                .totalMetodosAnalizados(1)
                .vulnerabilidadesDetectadas(Collections.emptyList())
                .build();

        when(analisisServicio.analizarCodigo(any(AnalisisRequestDto.class))).thenReturn(respuestaSegura);

        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.es_seguro", is(true)))
                .andExpect(jsonPath("$.status", is("completado")))
                .andExpect(jsonPath("$.archivo", is("Ejemplo.java")))
                .andExpect(jsonPath("$.total_metodos_analizados", is(1)))
                .andExpect(jsonPath("$.vulnerabilidades_detectadas", hasSize(0)));

        verify(analisisServicio, times(1)).analizarCodigo(any(AnalisisRequestDto.class));
    }

    @Test
    @DisplayName("POST /codigo → 200 cuando se detectan vulnerabilidades (esSeguro=false)")
    void analizarCodigo_codigoVulnerable_debeRetornar200ConVulnerabilidades() throws Exception {
        // Arrange: el microservicio ML detecta un método vulnerable
        VulnerabilidadDto vulnerabilidad = VulnerabilidadDto.builder()
                .metodo("ejecutarQuery")
                .probabilidadVulnerable(87.5)
                .build();

        AnalisisResponseDto respuestaVulnerable = AnalisisResponseDto.builder()
                .status("completado")
                .archivo("DaoInseguro.java")
                .esSeguro(false)
                .totalMetodosAnalizados(2)
                .vulnerabilidadesDetectadas(List.of(vulnerabilidad))
                .build();

        when(analisisServicio.analizarCodigo(any(AnalisisRequestDto.class))).thenReturn(respuestaVulnerable);

        AnalisisRequestDto requestVulnerable = AnalisisRequestDto.builder()
                .codigoFuente("public class DaoInseguro { public void ejecutarQuery(String sql) { Runtime.exec(sql); } }")
                .nombreArchivo("DaoInseguro.java")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestVulnerable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.es_seguro", is(false)))
                .andExpect(jsonPath("$.total_metodos_analizados", is(2)))
                .andExpect(jsonPath("$.vulnerabilidades_detectadas", hasSize(1)))
                .andExpect(jsonPath("$.vulnerabilidades_detectadas[0].metodo", is("ejecutarQuery")))
                .andExpect(jsonPath("$.vulnerabilidades_detectadas[0].probabilidad_vulnerable", is(87.5)));
    }

    @Test
    @DisplayName("POST /codigo → 400 cuando el código fuente está vacío (validación @NotBlank)")
    void analizarCodigo_codigoVacio_debeRetornar400() throws Exception {
        // Arrange: body con codigoFuente en blanco viola @NotBlank
        AnalisisRequestDto requestInvalido = AnalisisRequestDto.builder()
                .codigoFuente("")
                .nombreArchivo("Vacio.java")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        // El servicio NO debe ser invocado si la validación falla
        verify(analisisServicio, never()).analizarCodigo(any());
    }

    @Test
    @DisplayName("POST /codigo → 503 cuando el microservicio ML no está disponible")
    void analizarCodigo_microservicioNoDisponible_debeRetornar503() throws Exception {
        // Arrange: el servicio lanza excepción de indisponibilidad
        when(analisisServicio.analizarCodigo(any(AnalisisRequestDto.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo conectar al microservicio de análisis"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /codigo → 502 cuando el microservicio ML devuelve un error HTTP")
    void analizarCodigo_errorEnMicroservicio_debeRetornar502() throws Exception {
        // Arrange: el WebClient recibió un 4xx/5xx del FastAPI
        when(analisisServicio.analizarCodigo(any(AnalisisRequestDto.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "El microservicio de análisis devolvió un error"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido)))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("POST /codigo → 400 cuando el body es nulo / no tiene content-type JSON")
    void analizarCodigo_sinBody_debeRetornar400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(analisisServicio, never()).analizarCodigo(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/analisis/health
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /health → 200 con microservicio_disponible=true cuando el ML está activo")
    void verificarEstado_microservicioDisponible_debeRetornar200ConDisponibleTrue() throws Exception {
        // Arrange
        when(analisisServicio.verificarDisponibilidad()).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/v1/analisis/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.microservicio_disponible", is(true)))
                .andExpect(jsonPath("$.mensaje",
                        is("El microservicio de análisis está operativo.")));

        verify(analisisServicio, times(1)).verificarDisponibilidad();
    }

    @Test
    @DisplayName("GET /health → 200 con microservicio_disponible=false cuando el ML no responde")
    void verificarEstado_microservicioNoDisponible_debeRetornar200ConDisponibleFalse() throws Exception {
        // Arrange
        when(analisisServicio.verificarDisponibilidad()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/v1/analisis/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.microservicio_disponible", is(false)))
                .andExpect(jsonPath("$.mensaje",
                        is("El microservicio de análisis no está disponible.")));
    }

    @Test
    @DisplayName("POST /codigo/async → 202 Aceptado al encolar la solicitud de análisis")
    void analizarCodigoAsync_debeRetornar202Aceptado() throws Exception {
        // Arrange
        doNothing().when(analisisMensajeriaServicio).enviarSolicitudAnalisis(any(AnalisisRequestDto.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/analisis/codigo/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ENCOLADO")))
                .andExpect(jsonPath("$.archivo", is("Ejemplo.java")));

        verify(analisisMensajeriaServicio, times(1)).enviarSolicitudAnalisis(any(AnalisisRequestDto.class));
    }
}
