# 📋 Tareas de Integración: FastAPI ↔ Spring Boot

> **Estrategia:** Opción B — FastAPI desplegada como servidor HTTP externo.
> El backend Java consume el microservicio vía HTTP con WebClient.

---

## Prioridad 1 — Python (Microservicio FastAPI)

- `[ ]` **P1.1** — Agregar `CORS` y health-check a `seguridad/api_modelo.py` para que el backend Java pueda consumirlo correctamente
- `[ ]` **P1.2** — Actualizar `seguridad/requirements.txt` con las dependencias necesarias (`fastapi`, `uvicorn`, `httpx`, `python-multipart`)
- `[ ]` **P1.3** — Crear `seguridad/evaluar_pr.py` — script que llama al microservicio FastAPI desplegado para analizar archivos Java del PR

---

## Prioridad 2 — Backend Java: Configuración

- `[ ]` **P2.1** — Modificar `backend/pom.xml` para agregar `spring-boot-starter-webflux` (WebClient)
- `[ ]` **P2.2** — Modificar `backend/src/main/resources/application.yaml` para agregar la propiedad `analisis.microservicio.url`
- `[ ]` **P2.3** — Crear `backend/.../config/WebClientConfig.java` — Bean de `WebClient` apuntando al microservicio

---

## Prioridad 3 — Backend Java: DTOs

- `[ ]` **P3.1** — Crear `backend/.../dtos/AnalisisRequestDto.java`
- `[ ]` **P3.2** — Crear `backend/.../dtos/AnalisisResponseDto.java`
- `[ ]` **P3.3** — Crear `backend/.../dtos/VulnerabilidadDto.java` (objeto anidado en la respuesta)

---

## Prioridad 4 — Backend Java: Servicio

- `[ ]` **P4.1** — Crear `backend/.../services/AnalisisServicio.java` (interfaz)
- `[ ]` **P4.2** — Crear `backend/.../services/impl/AnalisisServicioImpl.java` (implementación con WebClient)

---

## Prioridad 5 — Backend Java: Controlador

- `[ ]` **P5.1** — Crear `backend/.../controllers/AnalisisController.java` — endpoint `POST /api/v1/analisis/codigo`

---

## Prioridad 6 — CI/CD: Workflow

- `[ ]` **P6.1** — Actualizar `.github/workflows/main.yml` para incluir la URL del microservicio desplegado como variable de entorno

---

## Estado

| ID | Archivo | Estado |
|----|---------|--------|
| P1.1 | `seguridad/api_modelo.py` | ✅ Completado |
| P1.2 | `seguridad/requirements.txt` | ✅ Completado |
| P1.3 | `seguridad/evaluar_pr.py` | ✅ Completado |
| P2.1 | `backend/pom.xml` | ✅ Completado |
| P2.2 | `backend/application.yaml` | ✅ Completado |
| P2.3 | `backend/.../WebClientConfig.java` | ✅ Completado |
| P3.1 | `backend/.../AnalisisRequestDto.java` | ✅ Completado |
| P3.2 | `backend/.../AnalisisResponseDto.java` | ✅ Completado |
| P3.3 | `backend/.../VulnerabilidadDto.java` | ✅ Completado |
| P4.1 | `backend/.../AnalisisServicio.java` | ✅ Completado |
| P4.2 | `backend/.../AnalisisServicioImpl.java` | ✅ Completado |
| P5.1 | `backend/.../AnalisisController.java` | ✅ Completado |
| P6.1 | `.github/workflows/main.yml` | ✅ Completado |

---

## ⚠️ Pendiente por parte del usuario

> Antes de que el pipeline funcione en GitHub Actions, debes configurar este **secret** en tu repo:
> - `MODELO_API_URL` → URL pública del microservicio FastAPI desplegado (ej: `https://mi-api.railway.app`)
>
> GitHub → Settings → Secrets and variables → Actions → New repository secret

