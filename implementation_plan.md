# Integración del Microservicio Python con el Backend Java (Spring Boot)

## Contexto del Proyecto

El objetivo es construir un pipeline DevSecOps donde:
1. Un desarrollador abre un **Pull Request** en GitHub
2. GitHub Actions activa el análisis del modelo ML (FastAPI en Python)
3. Si el código es **vulnerable** → el PR es bloqueado
4. Si es **seguro** → se ejecutan las pruebas Java y el PR puede mergearse

El `api_modelo.py` ya expone un endpoint `POST /analizar-codigo` que recibe código fuente Java y retorna si es vulnerable. El **backend Spring Boot** (`ec.edu.espe.zonas`) necesita un cliente HTTP para consumir ese microservicio.

---

## Arquitectura de Integración

```
GitHub PR  →  GitHub Actions  →  evaluar_pr.py  →  FastAPI (api_modelo.py)
                                                          ↓
                                               Resultado: es_seguro
                                                          ↓
                                       Si vulnerable → falla el workflow → PR bloqueado
                                       Si seguro    → mvn clean test → PR aprobado
```

> [!IMPORTANT]
> La integración **principal** ocurre en el pipeline de GitHub Actions (en Python), NO en el backend Java de Spring Boot directamente. El backend Java puede tener un endpoint opcional para invocar el análisis on-demand desde una UI o API externa.

---

## Capas a Implementar

### Capa 1 — Script Python `evaluar_pr.py` (el más crítico, falta en el repo)
El workflow `main.yml` llama a `python evaluar_pr.py` pero este archivo **no existe aún**. Este script:
- Descarga los archivos `.java` modificados en el PR usando la GitHub API
- Envía cada archivo al endpoint del modelo
- Si alguno es vulnerable → falla con `sys.exit(1)` → el workflow se detiene

### Capa 2 — Cliente HTTP en Java (Spring Boot)
Servicio Java que consume el microservicio FastAPI, útil para:
- Exponer un endpoint REST desde el backend para análisis on-demand
- Reutilizar la lógica de análisis en futuras features

---

## Cambios Propuestos

### Seguridad (Python)

#### [NEW] `seguridad/evaluar_pr.py`
Script que se ejecuta en GitHub Actions. Lee el PR, obtiene archivos Java, llama al microservicio y falla si hay vulnerabilidades.

---

### Backend Java (Spring Boot)

#### [MODIFY] `backend/pom.xml`
Agregar dependencia de `spring-boot-starter-webflux` (WebClient reactivo, no bloquea el hilo) para hacer llamadas HTTP al microservicio Python.

#### [NEW] `backend/src/main/java/ec/edu/espe/zonas/config/WebClientConfig.java`
Bean de configuración que crea el `WebClient` apuntando a la URL del microservicio Python (configurable via `application.yaml`).

#### [NEW] `backend/src/main/java/ec/edu/espe/zonas/dtos/AnalisisRequestDto.java`
DTO con los campos `codigoFuente` y `nombreArchivo` para la petición al modelo.

#### [NEW] `backend/src/main/java/ec/edu/espe/zonas/dtos/AnalisisResponseDto.java`
DTO de respuesta: `status`, `esSeguro`, `vulnerabilidadesDetectadas`.

#### [NEW] `backend/src/main/java/ec/edu/espe/zonas/services/AnalisisServicio.java`
Interfaz del servicio de análisis.

#### [NEW] `backend/src/main/java/ec/edu/espe/zonas/services/impl/AnalisisServicioImpl.java`
Implementación que usa `WebClient` para llamar a `POST /analizar-codigo` en el microservicio Python.

#### [NEW] `backend/src/main/java/ec/edu/espe/zonas/controllers/AnalisisController.java`
Endpoint REST `POST /api/v1/analisis/codigo` para que una UI pueda pedir análisis on-demand.

#### [MODIFY] `backend/src/main/resources/application.yaml`
Agregar la propiedad `analisis.microservicio.url` con la URL del servicio Python.

---

## Open Questions

> [!IMPORTANT]
> **¿Dónde correrá el microservicio Python en producción?**
> - Opción A: Solo en GitHub Actions (modo script directo, sin servidor FastAPI)
> - Opción B: Desplegado como servidor HTTP externo (Railway, Render, servidor propio)
>
> Esto afecta si el backend Java necesita WebClient o no.

> [!NOTE]
> Para el pipeline de CI/CD en GitHub Actions, el `evaluar_pr.py` puede llamar directamente al modelo cargando el `.pkl` localmente, **sin necesitar que la FastAPI esté corriendo como servidor**. Esto es más simple y robusto para CI.

---

## Verificación

1. Correr `evaluar_pr.py` localmente con un archivo Java de prueba
2. Verificar que el endpoint Java `POST /api/v1/analisis/codigo` responde correctamente con la FastAPI corriendo
3. Abrir un PR de prueba en GitHub y verificar que el workflow bloquea si el código es vulnerable
