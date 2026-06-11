package ec.edu.espe.zonas.services.impl;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalisisServicioImpl — Pruebas Unitarias")
class AnalisisServicioImplTest {

    @Mock
    private WebClient webClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private AnalisisServicioImpl analisisServicio;

    private AnalisisRequestDto requestDto;
    private AnalisisResponseDto responseDto;

    @BeforeEach
    void setUp() {
        requestDto = AnalisisRequestDto.builder()
                .nombreArchivo("Test.java")
                .codigoFuente("public class Test {}")
                .build();

        responseDto = AnalisisResponseDto.builder()
                .status("ok")
                .archivo("Test.java")
                .esSeguro(true)
                .totalMetodosAnalizados(1)
                .vulnerabilidadesDetectadas(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("analizarCodigo — Debe retornar DTO cuando la llamada HTTP es exitosa")
    void analizarCodigo_exitoso_debeRetornarDto() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/analizar-codigo")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(requestDto)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AnalisisResponseDto.class)).thenReturn(Mono.just(responseDto));

        AnalisisResponseDto result = analisisServicio.analizarCodigo(requestDto);

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertTrue(result.getEsSeguro());
        assertEquals("Test.java", result.getArchivo());
    }

    @Test
    @DisplayName("analizarCodigo — Debe lanzar BAD_GATEWAY si el microservicio devuelve error HTTP")
    void analizarCodigo_webClientResponseException_debeLanzarBadGateway() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/analizar-codigo")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(requestDto)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AnalisisResponseDto.class)).thenReturn(Mono.error(
                new WebClientResponseException(400, "Bad Request", null, null, null)
        ));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            analisisServicio.analizarCodigo(requestDto);
        });

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("El microservicio de análisis devolvió un error"));
    }

    @Test
    @DisplayName("analizarCodigo — Debe lanzar SERVICE_UNAVAILABLE ante cualquier otro error inesperado")
    void analizarCodigo_errorInesperado_debeLanzarServiceUnavailable() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/analizar-codigo")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(requestDto)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AnalisisResponseDto.class)).thenReturn(Mono.error(new RuntimeException("Connection Timeout")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            analisisServicio.analizarCodigo(requestDto);
        });

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertTrue(exception.getReason().contains("No se pudo conectar al microservicio de análisis"));
    }

    @Test
    @DisplayName("verificarDisponibilidad — Debe retornar true si el modelo está cargado")
    void verificarDisponibilidad_modeloCargado_debeRetornarTrue() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        @SuppressWarnings("unchecked")
        Mono<Map> monoMap = Mono.just(Map.of("modelo_cargado", true));
        when(responseSpec.bodyToMono(Map.class)).thenReturn(monoMap);

        boolean result = analisisServicio.verificarDisponibilidad();

        assertTrue(result);
    }

    @Test
    @DisplayName("verificarDisponibilidad — Debe retornar false si el modelo no está cargado")
    void verificarDisponibilidad_modeloNoCargado_debeRetornarFalse() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        @SuppressWarnings("unchecked")
        Mono<Map> monoMap = Mono.just(Map.of("modelo_cargado", false));
        when(responseSpec.bodyToMono(Map.class)).thenReturn(monoMap);

        boolean result = analisisServicio.verificarDisponibilidad();

        assertFalse(result);
    }

    @Test
    @DisplayName("verificarDisponibilidad — Debe retornar false si hay una excepción al conectar")
    void verificarDisponibilidad_excepcion_debeRetornarFalse() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new RuntimeException("Network Error")));

        boolean result = analisisServicio.verificarDisponibilidad();

        assertFalse(result);
    }
}
