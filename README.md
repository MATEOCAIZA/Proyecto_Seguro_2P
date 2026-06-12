# 🛡️ DevSecOps — Sistema de Gestión de Zonas y Espacios

> **Proyecto de Software Seguro — 2do Parcial**  
> Pipeline de CI/CD con análisis de vulnerabilidades basado en Machine Learning, backend Spring Boot y pruebas de rendimiento con K6.

---

## 📋 Tabla de Contenidos

- [Visión General](#-visión-general)
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
- [Pipeline DevSecOps (5 Nodos)](#-pipeline-devsecops-5-nodos)
- [Módulo de Seguridad (Python / ML)](#-módulo-de-seguridad-python--ml)
- [Backend (Spring Boot)](#-backend-spring-boot)
- [Pruebas](#-pruebas)
- [Despliegue](#-despliegue)
- [Configuración de Secrets](#-configuración-de-secrets)
- [Estructura del Proyecto](#-estructura-del-proyecto)

---

## 🔭 Visión General

Este repositorio implementa un sistema completo de **DevSecOps** que combina:

1. **Un backend REST** (Spring Boot + Java 21) para gestionar zonas y espacios físicos.
2. **Un microservicio de seguridad** (Python + FastAPI) que aloja un modelo de **Minería de Datos / Random Forest** entrenado para detectar vulnerabilidades en código Java.
3. **Un pipeline de CI/CD** (GitHub Actions) de 5 nodos que garantiza que ningún código vulnerable llegue a producción sin pasar por análisis automático de seguridad, pruebas unitarias y pruebas de rendimiento.

El principio central es **"Shift Left Security"**: la seguridad se valida en el momento del Pull Request, antes de que el código sea mergeado.

---

## 🏛️ Arquitectura del Sistema

```
┌──────────────────────────────────────────────────────────────┐
│                      GitHub Actions                          │
│                                                              │
│  PR → test  ┌─────────────────────────────────────────────┐ │
│             │  Nodo 1: Seguridad ML  (evaluar_pr.py)       │ │
│             │  ↓ bloquea si hay vulnerabilidades           │ │
│             │                                              │ │
│             │  Nodo 2 ────────── Nodo 3                   │ │
│             │  JUnit + JaCoCo    K6 Performance           │ │
│             │  (en paralelo)     (en paralelo)            │ │
│             │         ↓                                    │ │
│             │  Nodo 4: Merge automático → rama 'test'      │ │
│             └─────────────────────────────────────────────┘ │
│                                                              │
│  Push → main  ┌──────────────────────────────────────────┐  │
│               │  Nodo 5: Docker Build + Deploy en Render  │  │
│               └──────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘

         │                              │
         ▼                              ▼
┌─────────────────┐          ┌──────────────────────┐
│  Backend Java   │◄────────►│  Microservicio Python │
│  Spring Boot    │  WebClient│  FastAPI + ML Model  │
│  Puerto: 8080   │          │  Puerto: 8000         │
└─────────────────┘          └──────────────────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │
│   Base de Datos │
└─────────────────┘
```

---

## 🔄 Pipeline DevSecOps (5 Nodos)

El workflow se define en `.github/workflows/main.yml` y se activa de la siguiente manera:

| Evento | Rama destino | Nodos que ejecuta |
|--------|-------------|-------------------|
| `pull_request` | `test` | Nodos 1 → 2 + 3 → 4 |
| `push` | `main` | Nodo 5 únicamente |

### Nodo 1 — Revisión de Seguridad (ML) 🔍

**Objetivo:** Analizar cada archivo `.java` modificado en el PR usando el modelo predictivo.

**Flujo:**
1. Descarga el código del PR mediante la API de GitHub.
2. Envía cada archivo `.java` al microservicio FastAPI (`evaluar_pr.py`).
3. Si algún método supera el **70% de probabilidad de ser vulnerable**, el script:
   - Publica un **comentario detallado** en el PR con tabla de CWEs.
   - Aplica la etiqueta `fixing-required` al PR.
   - Crea un **Issue automático** en el repositorio.
   - Envía una **alerta a Telegram** con el detalle completo.
   - Termina con `sys.exit(1)` → **el merge queda bloqueado**.
4. Si todo es seguro: notifica a Telegram y termina con `sys.exit(0)`.

> ⚠️ **El Nodo 1 es el guardián del pipeline.** Los Nodos 2 y 3 solo se ejecutan si el Nodo 1 pasa exitosamente.

---

### Nodo 2 — Pruebas JUnit 5 + Cobertura JaCoCo 🧪

**Objetivo:** Ejecutar la suite de pruebas unitarias del backend Spring Boot.

- Usa **H2 (base de datos en memoria)** para no necesitar PostgreSQL real en CI.
- Genera el **reporte HTML de cobertura JaCoCo** y lo publica como artefacto descargable en GitHub Actions.
- Si falla, notifica automáticamente al canal de **Telegram**.
- Corre **en paralelo** con el Nodo 3.

---

### Nodo 3 — Pruebas de Rendimiento K6 ⚡

**Objetivo:** Verificar que el backend responde correctamente bajo carga concurrente.

- Levanta un contenedor **PostgreSQL 16** como servicio de GitHub Actions.
- Compila el backend y lo arranca en background (`java -jar ... &`).
- Ejecuta los scripts K6:
  - `crear_zona.js` — Prueba de carga para crear zonas.
  - `crear_espacio.js` — Prueba de carga para crear espacios (usa el UUID de una zona existente).
- Los resultados en JSON se publican como artefacto.
- Corre **en paralelo** con el Nodo 2.

---

### Nodo 4 — Convergencia: Merge automático a `test` 🔀

**Objetivo:** Una vez que los Nodos 2 y 3 completan con éxito, hacer el merge automático del PR.

- Usa `gh pr merge` con la opción `--auto`.
- Notifica el éxito completo del pipeline en Telegram con un resumen de los 3 nodos.

---

### Nodo 5 — Build Docker y Despliegue en Render 🚀

**Objetivo:** Construir la imagen de producción y actualizar el servicio en Render.

- Se activa **únicamente** con un `push` a `main`.
- Construye el `.jar` con Maven (`mvnw package -DskipTests`).
- Hace login en **Docker Hub** y sube la imagen `latest`.
- Dispara el **Deploy Hook de Render** para actualizar el servicio en producción.
- Notifica el resultado (éxito o fallo) por Telegram.

---

## 🤖 Módulo de Seguridad (Python / ML)

Ubicado en la carpeta `seguridad/`.

### `api_modelo.py` — Microservicio FastAPI

Servidor HTTP que expone el modelo de Machine Learning como una API REST.

#### Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/health` | Verifica que el servicio y el modelo estén cargados |
| `POST` | `/analizar-codigo` | Analiza un archivo Java y retorna las vulnerabilidades detectadas |

#### Cómo funciona el análisis

1. **Parseo AST**: El código Java recibido se parsea con `javalang` para construir el Árbol de Sintaxis Abstracta.
2. **Extracción de features**: Por cada método se extraen 10 métricas estructurales:

   | Feature | Descripción |
   |---------|-------------|
   | `total_nodos` | Total de nodos en el AST del método |
   | `ast_depth` | Profundidad máxima del árbol (cap: 12) |
   | `llamadas_peligrosas` | Llamadas a funciones de riesgo (`exec`, `Runtime`, etc.) |
   | `total_llamadas` | Total de invocaciones de métodos |
   | `num_ifs` | Cantidad de sentencias `if` |
   | `num_loops` | Cantidad de bucles (`for`, `while`, `do`) |
   | `num_catches` | Bloques `catch` |
   | `num_throws` | Sentencias `throw` |
   | `num_variables` | Declaraciones de variables locales |
   | `num_literales` | Literales en el código |

3. **Predicción ML**: El modelo Random Forest (`modelo_mineria_seguro.pkl`) predice la probabilidad de que cada método sea vulnerable. Solo se reporta si la probabilidad supera el **70%** (umbral configurable).

4. **Módulo Forense (CWE)**: Si el modelo marca un método como vulnerable, un segundo análisis heurístico identifica el CWE específico:

   | CWE | Nombre | Patrones detectados |
   |-----|--------|---------------------|
   | CWE-78 | OS Command Injection | `exec`, `Runtime`, `ProcessBuilder` |
   | CWE-89 | SQL Injection | `prepareStatement`, `executeQuery`, `createNativeQuery` |
   | CWE-319 | Cleartext Transmission | `Socket`, `getOutputStream`, `HttpURLConnection` |
   | CWE-200 | Exposure of Sensitive Information | `getenv`, `getPassword`, `printStackTrace` |
   | CWE-22 | Path Traversal | `FileInputStream`, `FileReader`, `Paths.get` |

#### Respuesta de ejemplo
```json
{
  "status": "completado",
  "archivo": "MiClase.java",
  "es_seguro": false,
  "total_metodos_analizados": 3,
  "vulnerabilidades_detectadas": [
    {
      "metodo": "ejecutarComando",
      "probabilidad_vulnerable": 94.5,
      "cwe": "CWE-78",
      "cwe_nombre": "OS Command Injection",
      "cwe_descripcion": "Ejecución arbitraria de comandos del sistema operativo."
    }
  ]
}
```

### `evaluar_pr.py` — Orquestador del Pipeline

Script ejecutado por GitHub Actions en el Nodo 1. Es el puente entre GitHub y el microservicio FastAPI.

**Variables de entorno requeridas:**

| Variable | Fuente | Descripción |
|----------|--------|-------------|
| `GITHUB_TOKEN` | Automática | Token de autenticación de GitHub Actions |
| `PR_NUMBER` | Automática | Número del Pull Request en curso |
| `GITHUB_REPOSITORY` | Automática | `owner/repo` del repositorio |
| `MODELO_API_URL` | Secret | URL del microservicio FastAPI desplegado |
| `TELEGRAM_TOKEN` | Secret | Token del bot de Telegram |
| `TELEGRAM_CHAT_ID` | Secret | ID del chat para notificaciones |

### Dependencias Python (`requirements.txt`)

```
scikit-learn==1.8.0
pandas
joblib
javalang
fastapi
uvicorn[standard]
httpx
requests
PyGithub
```

---

## ☕ Backend (Spring Boot)

Ubicado en la carpeta `backend/`. API REST construida con **Spring Boot 4.0.6** y **Java 21**.

### Stack Tecnológico

| Componente | Tecnología |
|------------|------------|
| Framework | Spring Boot 4.0.6 |
| Lenguaje | Java 21 |
| Persistencia | Spring Data JPA + PostgreSQL |
| Validación | Jakarta Validation (`@Valid`) |
| HTTP Cliente | Spring WebFlux (WebClient) |
| Reducción de código | Lombok |
| Build | Maven Wrapper (`mvnw`) |
| Tests unitarios | JUnit 5 + Mockito + MockMvc |
| Cobertura | JaCoCo 0.8.12 |
| Base de datos en tests | H2 (en memoria) |
| Contenedorización | Docker (eclipse-temurin:21-jdk-alpine) |

### Modelo de Dominio

#### `Zona` (tabla: `zonas`)

Representa un área física (sala, laboratorio, auditorio, etc.).

| Campo | Tipo | Restricciones |
|-------|------|---------------|
| `id` | UUID | PK, generado automáticamente |
| `nombre` | String | Único, máx. 32 caracteres |
| `descripcion` | String | Opcional |
| `codigo` | String | Único, máx. 30 caracteres |
| `estado` | int | `1` = Activo, `0` = Inactivo |
| `capacidad` | int | Capacidad máxima de la zona |
| `tipo` | TipoZona (enum) | Define la categoría de la zona |
| `fechaCreacion` | LocalDateTime | Timestamp de creación |
| `fechaModificacion` | LocalDateTime | Timestamp de última modificación |

#### `Espacio` (tabla: `espacios`)

Representa un espacio individual dentro de una zona.

| Campo | Tipo | Restricciones |
|-------|------|---------------|
| `id` | UUID | PK, generado automáticamente |
| `codigo` | String | Único, máx. 50 caracteres |
| `descripcion` | String | Opcional, máx. 100 caracteres |
| `tipo` | TipoEspacio (enum) | Categoría del espacio |
| `activo` | boolean | Si el espacio está habilitado |
| `estado` | EstadoEspacio (enum) | Estado operativo actual |
| `zona` | Zona | FK — Zona a la que pertenece |
| `fechaCreacion` | LocalDateTime | Timestamp de creación |
| `fechaModificacion` | LocalDateTime | Timestamp de última modificación |

### Endpoints REST

#### Zonas — `GET /api/v1/zonas/`

| Método | Endpoint | Descripción | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/v1/zonas/` | Listar todas las zonas | `200 OK` |
| `POST` | `/api/v1/zonas/` | Crear una nueva zona | `201 Created` |
| `PUT` | `/api/v1/zonas/{idZona}` | Actualizar datos de una zona | `200 OK` |
| `PATCH` | `/api/v1/zonas/{idZona}/estado` | Activar o desactivar una zona | `200 OK` |

#### Espacios — `GET /api/v1/espacios/`

| Método | Endpoint | Descripción | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/v1/espacios/` | Listar todos los espacios | `200 OK` |
| `POST` | `/api/v1/espacios/` | Crear un nuevo espacio | `201 Created` |
| `PUT` | `/api/v1/espacios/{idEspacio}` | Actualizar un espacio | `200 OK` |
| `DELETE` | `/api/v1/espacios/{idEspacio}` | Eliminar un espacio | `204 No Content` |
| `PATCH` | `/api/v1/espacios/{idEspacio}/estado/{estado}` | Cambiar el estado de un espacio | `200 OK` |
| `GET` | `/api/v1/espacios/estado/{estado}` | Filtrar espacios por estado | `200 OK` |
| `GET` | `/api/v1/espacios/zona/{idZona}/estado/{estado}` | Filtrar espacios por zona y estado | `200 OK` |

#### Análisis de Seguridad — `POST /api/v1/analisis/`

| Método | Endpoint | Descripción | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/v1/analisis/codigo` | Analizar un fragmento de código Java | `200 OK` |
| `GET` | `/api/v1/analisis/health` | Verificar disponibilidad del microservicio ML | `200 OK` |

### Capas de la Aplicación

```
controllers/         ← Capa REST (Spring MVC)
├── ZonaController
├── EspacioController
└── AnalisisController

services/            ← Lógica de negocio (interfaces + implementaciones)
├── ZonaServicio / ZonaServicioImpl
├── EspacioServicio / EspacioServicioImpl
└── AnalisisServicio / AnalisisServicioImpl

entidades/           ← Modelo JPA
├── Zona, Espacio
└── enums: TipoZona, TipoEspacio, EstadoEspacio

dtos/                ← Objetos de transferencia de datos
├── ZonaRequestDto, ZonaResponseDto
├── EspacioRequestDto, EspacioResponseDto
└── AnalisisRequestDto, AnalisisResponseDto, VulnerabilidadDto

config/
└── WebClientConfig  ← Bean de WebClient que apunta al microservicio ML

utils/
└── UtilsMappers     ← Conversión entre entidades y DTOs
```

---

## 🧪 Pruebas

### Pruebas Unitarias (JUnit 5 + Mockito)

Ubicadas en `backend/src/test/java/`.

| Clase de Test | Qué prueba |
|---------------|-----------|
| `ZonaServicioImplTest` | Lógica de negocio para zonas (CRUD, activar/desactivar) |
| `EspacioServicioImplTest` | Lógica de negocio para espacios (CRUD, filtros por estado) |
| `AnalisisServicioImplTest` | Integración con el microservicio Python vía WebClient |
| `ZonaControllerTest` | Endpoints REST de zonas con MockMvc |
| `EspacioControllerTest` | Endpoints REST de espacios con MockMvc |
| `AnalisisControllerTest` | Endpoints REST de análisis con MockMvc |
| `UtilsMappersTest` | Conversiones de entidad ↔ DTO |

Para ejecutar localmente:
```bash
cd backend
./mvnw clean test
```

El reporte de cobertura JaCoCo se genera en: `backend/target/site/jacoco/index.html`

> **Nota:** JaCoCo excluye de cobertura las clases de `entidades/`, `dtos/`, `config/` y la clase `ZonasApplication` para enfocarse solo en la lógica de negocio.

### Pruebas de Rendimiento (K6)

Ubicadas en `backend/src/test/k6/`.

| Script | Qué prueba | Condición de éxito |
|--------|-----------|-------------------|
| `crear_zona.js` | Carga concurrente en `POST /api/v1/zonas/` | p95 < 500ms, error rate < 1% |
| `crear_espacio.js` | Carga concurrente en `POST /api/v1/espacios/` | p95 < 500ms, error rate < 1% |

Para ejecutar localmente (requiere K6 instalado y backend corriendo):
```bash
k6 run backend/src/test/k6/crear_zona.js
k6 run -e ZONA_ID=<uuid> backend/src/test/k6/crear_espacio.js
```

---

## 🐳 Despliegue

### Dockerfile del Backend

```dockerfile
FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

**Build manual:**
```bash
cd backend
./mvnw package -DskipTests
docker build -t mi-backend:latest .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
  -e SPRING_DATASOURCE_USERNAME=user \
  -e SPRING_DATASOURCE_PASSWORD=pass \
  mi-backend:latest
```

### Microservicio Python (local)

```bash
cd seguridad
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # Linux/Mac
pip install -r requirements.txt
uvicorn api_modelo:app --host 0.0.0.0 --port 8000 --reload
```

La API estará disponible en: `http://localhost:8000`  
Documentación automática (Swagger): `http://localhost:8000/docs`

---

## 🔐 Configuración de Secrets

Los siguientes secrets deben configurarse en **GitHub → Settings → Secrets and Variables → Actions**:

| Secret | Descripción | Obligatorio |
|--------|-------------|-------------|
| `MODELO_API_URL` | URL completa del microservicio FastAPI (sin `/` al final). Ej: `https://mi-api.railway.app` | ✅ Sí |
| `TELEGRAM_TOKEN` | Token del bot de Telegram (`@BotFather`) | ✅ Sí |
| `TELEGRAM_CHAT_ID` | ID del chat o grupo donde se envían las notificaciones | ✅ Sí |
| `DOCKER_USERNAME` | Usuario de Docker Hub | ✅ Nodo 5 |
| `DOCKER_PASSWORD` | Contraseña o token de Docker Hub | ✅ Nodo 5 |
| `RENDER_DEPLOY_HOOK_URL` | URL del Deploy Hook de Render | ✅ Nodo 5 |

> `GITHUB_TOKEN` es provisto automáticamente por GitHub Actions y **no necesita configurarse**.

### Etiqueta requerida en el repositorio

El Nodo 1 aplica la etiqueta `fixing-required` cuando detecta vulnerabilidades. Asegúrate de que exista en el repositorio:

**GitHub → Issues → Labels → New label** → `fixing-required`

---

## 📁 Estructura del Proyecto

```
Proyecto_Seguro_2P/
│
├── .github/
│   └── workflows/
│       └── main.yml              ← Pipeline DevSecOps (5 nodos)
│
├── seguridad/                    ← Módulo de Seguridad (Python)
│   ├── api_modelo.py             ← Microservicio FastAPI + Modelo ML + Módulo Forense CWE
│   ├── evaluar_pr.py             ← Orquestador del análisis de PR (GitHub Actions)
│   ├── modelo_mineria_seguro.pkl ← Modelo Random Forest pre-entrenado (~28 MB)
│   └── requirements.txt          ← Dependencias Python
│
└── backend/                      ← API REST (Spring Boot + Java 21)
    ├── Dockerfile                ← Imagen de producción (eclipse-temurin:21-jdk-alpine)
    ├── pom.xml                   ← Dependencias Maven + plugins JaCoCo
    ├── mvnw / mvnw.cmd           ← Maven Wrapper
    └── src/
        ├── main/
        │   ├── java/ec/edu/espe/zonas/
        │   │   ├── ZonasApplication.java
        │   │   ├── config/       ← WebClientConfig (cliente HTTP al ML)
        │   │   ├── controllers/  ← ZonaController, EspacioController, AnalisisController
        │   │   ├── dtos/         ← Request/Response DTOs + VulnerabilidadDto
        │   │   ├── entidades/    ← Zona, Espacio + enums
        │   │   ├── services/     ← Interfaces + implementaciones de negocio
        │   │   └── utils/        ← UtilsMappers (entidad ↔ DTO)
        │   └── resources/
        │       └── application.properties
        └── test/
            ├── java/             ← JUnit 5 + Mockito + MockMvc tests
            └── k6/               ← Scripts de pruebas de rendimiento K6
                ├── crear_zona.js
                └── crear_espacio.js
```

---

## 📊 Flujo Completo: Pull Request al Deploy

```
Developer → crea PR hacia 'test'
         │
         ▼
[Nodo 1] Análisis de Seguridad ML
         ├── ❌ Vulnerable? → Comenta en PR + Crea Issue + Telegram 🚨 → BLOQUEADO
         └── ✅ Seguro? → continúa...
         │
         ├──────────────────────┐
         ▼                      ▼
[Nodo 2] JUnit + JaCoCo   [Nodo 3] K6 Performance
         │                      │
         └──────────┬───────────┘
                    ▼
         [Nodo 4] Merge automático a 'test' + Telegram ✅
                    │
                    ▼ (merge manual: test → main)
         [Nodo 5] Docker Build + Docker Hub + Render Deploy + Telegram 🚀
```

---

## 👥 Autores

**Proyecto de Software Seguro — ESPE**  
Ingeniería en Tecnologías de la Información

---

*Este proyecto fue desarrollado como ejercicio académico de DevSecOps, integrando prácticas de seguridad en todas las fases del ciclo de desarrollo de software.*