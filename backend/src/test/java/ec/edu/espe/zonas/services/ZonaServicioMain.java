package ec.edu.espe.zonas.services;

import ec.edu.espe.zonas.ZonasApplication;
import ec.edu.espe.zonas.dtos.ZonaRequestDto;
import ec.edu.espe.zonas.dtos.ZonaResponseDto;
import ec.edu.espe.zonas.entidades.TipoZona;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

public class ZonaServicioMain {

    public static void main(String[] args) {

        ConfigurableApplicationContext ctx =
                SpringApplication.run(ZonasApplication.class, args);

        ZonaServicio zonaServicio = ctx.getBean(ZonaServicio.class);


        System.out.println("\n--- PRUEBA 1: Crear zona REGULAR (ZON-REG-NN) ---");
        ZonaRequestDto requestRegular = ZonaRequestDto.builder()
                .nombre("Zona Visitantes ")
                .descripcion("Zona Visitantes")
                .tipo(TipoZona.REGULAR)
                .build();

        ZonaResponseDto responseRegular = zonaServicio.crearZona(requestRegular);
        System.out.println(">>> Zona creada exitosamente:");
        System.out.println("    ID          : " + responseRegular.getIdZona());
        System.out.println("    Nombre      : " + responseRegular.getNombre());
        System.out.println("    Codigo      : " + responseRegular.getCodigo());   
        System.out.println("    Tipo        : " + responseRegular.getTipo());
        System.out.println("    Estado      : " + responseRegular.getEstado());
        System.out.println("    FechaCreac. : " + responseRegular.getFechaCreacion());

        System.out.println("\n--- PRUEBA 2: Crear zona VIP (ZON-VIP-NN) ---");
        ZonaRequestDto requestVip = ZonaRequestDto.builder()
                .nombre("Zona Administrativa")
                .descripcion("Zona Administrativa")
                .tipo(TipoZona.VIP)
                .build();

        ZonaResponseDto responseVip = zonaServicio.crearZona(requestVip);
        System.out.println(">>> Zona creada exitosamente:");
        System.out.println("    ID          : " + responseVip.getIdZona());
        System.out.println("    Nombre      : " + responseVip.getNombre());
        System.out.println("    Codigo      : " + responseVip.getCodigo());   
        System.out.println("    Tipo        : " + responseVip.getTipo());
        System.out.println("    Estado      : " + responseVip.getEstado());
        System.out.println("    FechaCreac. : " + responseVip.getFechaCreacion());

        List<ZonaResponseDto> todasLasZonas = zonaServicio.listarZonas();
        System.out.println(">>> Total de zonas en la BD: " + todasLasZonas.size());
        todasLasZonas.forEach(z ->
                System.out.println("    [" + z.getCodigo() + "] " + z.getNombre()
                        + " | Tipo: " + z.getTipo()
                        + " | Estado: " + z.getEstado())
        );

        ctx.close();
    }
}
