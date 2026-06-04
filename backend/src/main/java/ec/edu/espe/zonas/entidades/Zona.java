package ec.edu.espe.zonas.entidades;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name="zonas")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Zona {
    
    /// TICK-1A-23-200526

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 32)
    private String nombre;
    
    @Column
    private String descripcion;
    
    @Column(unique = true, nullable = false, length = 12)
    private String codigo;
    
    @Column 
    private int estado;  // 1. Activo 0. Inactivo
    

    @Column
    private int capacidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoZona tipo;

    @OneToMany(mappedBy = "zona", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Espacio> espacios;
    
    @Column
    private LocalDateTime fechaCreacion;
    
    @Column
    private LocalDateTime fechaModificacion;
}