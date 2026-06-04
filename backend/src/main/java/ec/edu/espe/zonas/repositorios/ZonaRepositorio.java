package ec.edu.espe.zonas.repositorios;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.zonas.entidades.Zona;
import ec.edu.espe.zonas.entidades.TipoZona;

public interface ZonaRepositorio extends JpaRepository<Zona, UUID> {
    boolean existsByCodigo(String codigo);

    boolean existsByNombre(String nombre);

    long countByTipo(TipoZona tipo);
}
