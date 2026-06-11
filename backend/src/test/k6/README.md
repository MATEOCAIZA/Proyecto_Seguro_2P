# Pruebas de Rendimiento con k6

Scripts de carga para los endpoints de creación del backend.

## Estructura

```
src/test/k6/
├── crear_zona.js          ← POST /api/v1/zonas/
├── crear_espacio.js       ← POST /api/v1/espacios/
├── resultados/            ← Se genera automáticamente al correr los tests
│   ├── crear_zona_resultado.json
│   └── crear_espacio_resultado.json
└── README.md
```

## Requisitos

1. Tener **k6** instalado: https://grafana.com/docs/k6/latest/set-up/install-k6/
   ```powershell
   # En Windows con Chocolatey:
   choco install k6

   # O descargar el instalador desde:
   # https://github.com/grafana/k6/releases/latest
   ```

2. El **backend Spring Boot debe estar corriendo** en `http://localhost:8080`

---

## Cómo ejecutar

### Prueba: Crear Zona

```powershell
# Desde la carpeta backend/
k6 run src/test/k6/crear_zona.js
```

### Prueba: Crear Espacio

El endpoint de espacio requiere un `idZona` existente en la base de datos.

**Opción 1** — Pasar el UUID como variable de entorno (recomendado):
```powershell
k6 run -e ZONA_ID=<uuid-de-zona-existente> src/test/k6/crear_espacio.js
```

**Opción 2** — Editar el script directamente:
```javascript
// En crear_espacio.js, línea 26:
const ID_ZONA_EXISTENTE = '550e8400-e29b-41d4-a716-446655440000'; // ← pon tu UUID aquí
```

Para obtener un UUID válido, con el backend corriendo:
```powershell
Invoke-RestMethod http://localhost:8080/api/v1/zonas/
```

---

## Escenario de carga configurado

Ambos scripts usan el mismo patrón de carga gradual:

| Fase          | Duración | Usuarios virtuales |
|---------------|----------|--------------------|
| Calentamiento | 30s      | 0 → 10             |
| Carga media   | 1 min    | 10                 |
| Pico          | 30s      | 10 → 50            |
| Carga alta    | 1 min    | 50                 |
| Enfriamiento  | 30s      | 50 → 0             |
| **Total**     | **3:30** |                    |

---

## Umbrales de aceptación

| Métrica                  | Criterio         |
|--------------------------|------------------|
| `http_req_duration p(95)` | < 500 ms        |
| Tasa de éxito            | > 95%            |
| Tiempo promedio (avg)    | < 300 ms         |

Si algún umbral falla, k6 termina con **exit code 99**.

---

## Guardar resultados

Los resultados JSON se guardan automáticamente en `resultados/`.

Para generar también un reporte HTML:
```powershell
k6 run --out json=resultados/raw.json src/test/k6/crear_zona.js
```
