from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import joblib
import javalang
import pandas as pd
import uvicorn
import os

from feature_extractor import (
    extraer_caracteristicas_metodo,
    calcular_ratios,
    extraer_codigo_fuente_metodo,
    FEATURE_COLUMNS
)

# 1. Inicializar la API y cargar el modelo en memoria al arrancar
app = FastAPI(
    title="API de Seguridad CI/CD",
    description="Microservicio de Minería de Datos para análisis de vulnerabilidades en código Java",
    version="1.0.0"
)

# CORS — Permite que el backend Spring Boot (y GitHub Actions) consuman esta API
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # En producción, reemplazar por la URL del backend Java
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Cargar modelo al iniciar la aplicación
modelo_rf = None
try:
    modelo_rf = joblib.load('modelo_mineria_seguro.pkl')
    print("Modelo cargado en memoria exitosamente.")
except Exception as e:
    print(f"Error al cargar el modelo: {e}")

# 2. Definir el formato que Java nos va a enviar
class PeticionCodigo(BaseModel):
    codigo_fuente: str
    nombre_archivo: str = "Desconocido.java"

# ─────────────────────────────────────────────────────────────────────────────
# MÓDULO FORENSE: Mapeo de patrones de código → CWE
# Si el Juez (ML) detecta vulnerabilidad, el Forense deduce el CWE concreto
# basándose en los tokens peligrosos presentes en el código fuente del MÉTODO
# (no del archivo completo, ver extraer_codigo_fuente_metodo).
# ─────────────────────────────────────────────────────────────────────────────
CWE_MAPPING = [
    {
        "cwe_id": "CWE-78",
        "nombre": "OS Command Injection",
        "patrones": ["exec", "Runtime", "ProcessBuilder", "getRuntime"],
        "descripcion": "Ejecución arbitraria de comandos del sistema operativo."
    },
    {
        "cwe_id": "CWE-89",
        "nombre": "SQL Injection",
        "patrones": ["executeQuery", "createQuery", "nativeQuery",
                      "createNativeQuery", "Statement"],
        "descripcion": "Inyección de SQL que permite manipular la base de datos."
    },
    {
        "cwe_id": "CWE-319",
        "nombre": "Cleartext Transmission of Sensitive Information",
        "patrones": ["Socket", "getOutputStream", "HttpURLConnection",
                      "URL", "openConnection", "getInputStream"],
        "descripcion": "Transmisión de datos sensibles sin cifrado."
    },
    {
        "cwe_id": "CWE-200",
        "nombre": "Exposure of Sensitive Information",
        "patrones": ["getenv", "System.getProperty", "printStackTrace",
                      "getPassword", "getSecret", "getKey"],
        "descripcion": "Exposición de información confidencial del entorno o sistema."
    },
    {
        "cwe_id": "CWE-22",
        "nombre": "Path Traversal",
        "patrones": ["File", "FileInputStream", "FileOutputStream",
                      "FileReader", "FileWriter", "Paths.get", "resolve"],
        "descripcion": "Acceso a rutas de archivos fuera del directorio permitido."
    },
    {
        "cwe_id": "CWE-502",
        "nombre": "Deserialization of Untrusted Data",
        "patrones": ["ObjectInputStream", "readObject", "XMLDecoder", "readUnshared"],
        "descripcion": "Deserialización de datos controlados por el atacante, permitiendo ejecución de código."
    },
    {
        "cwe_id": "CWE-611",
        "nombre": "Improper Restriction of XML External Entity Reference (XXE)",
        "patrones": ["DocumentBuilderFactory", "SAXParserFactory", "XMLInputFactory"],
        "descripcion": "Procesamiento de XML inseguro que permite lectura de archivos locales o SSRF."
    },
    {
        "cwe_id": "CWE-327",
        "nombre": "Use of a Broken or Risky Cryptographic Algorithm",
        "patrones": ["MessageDigest.getInstance(\"MD5\")", "Cipher.getInstance(\"DES\")", "MessageDigest.getInstance(\"SHA-1\")"],
        "descripcion": "Uso de algoritmos criptográficos obsoletos o inseguros (ej. MD5, DES)."
    },
    {
        "cwe_id": "CWE-330",
        "nombre": "Use of Insufficiently Random Values",
        "patrones": ["java.util.Random", "Math.random()"],
        "descripcion": "Uso de generadores de números pseudoaleatorios débiles en contextos de seguridad (se recomienda SecureRandom)."
    },
    {
        "cwe_id": "CWE-94",
        "nombre": "Improper Control of Generation of Code (Code Injection)",
        "patrones": ["ScriptEngine", "eval", "SpelExpressionParser", "ExpressionParser"],
        "descripcion": "Inyección de código dinámico o expresiones (ej. Spring Expression Language)."
    },
    {
        "cwe_id": "CWE-79",
        "nombre": "Improper Neutralization of Input During Web Page Generation (XSS)",
        "patrones": ["getWriter().print", "PrintWriter", "ModelAndView", "<script>"],
        "descripcion": "Cross-Site Scripting (XSS). Reflejo de datos no sanitizados hacia el cliente."
    }
]


def deducir_cwe(codigo_metodo: str) -> dict | None:
    """
    Analiza el código fuente de un método para detectar patrones de CWE.
    Retorna el CWE más probable o None si no se identifica ninguno.
    """
    coincidencias = []
    for regla in CWE_MAPPING:
        score = sum(1 for patron in regla["patrones"] if patron in codigo_metodo)
        if score > 0:
            coincidencias.append((score, regla))

    if not coincidencias:
        return None

    coincidencias.sort(key=lambda x: x[0], reverse=True)
    mejor = coincidencias[0][1]
    return {
        "cwe_id": mejor["cwe_id"],
        "nombre": mejor["nombre"],
        "descripcion": mejor["descripcion"]
    }


# 4. Health Check — Para que el backend Java verifique disponibilidad
@app.get("/health")
async def health_check():
    return {
        "status": "ok",
        "modelo_cargado": modelo_rf is not None,
        "version": "1.0.0"
    }

# 5. Endpoint principal — Donde Java/GitHub Actions se conecta para analizar código
@app.post("/analizar-codigo")
async def analizar_codigo(peticion: PeticionCodigo):
    if modelo_rf is None:
        raise HTTPException(status_code=503, detail="El modelo ML no está disponible. Contacte al administrador.")

    try:
        arbol = javalang.parse.parse(peticion.codigo_fuente)
        metodos_analizados = []

        for _, metodo in arbol.filter(javalang.tree.MethodDeclaration):
            # Las mismas 13 features base que se usaron al entrenar el modelo
            features = extraer_caracteristicas_metodo(metodo)
            features['metodo_nombre'] = metodo.name
            # Código SOLO del método (no del archivo completo) para el forense
            features['_codigo_metodo'] = extraer_codigo_fuente_metodo(
                peticion.codigo_fuente, metodo
            )
            metodos_analizados.append(features)

        if not metodos_analizados:
            return {
                "status": "ok",
                "archivo": peticion.nombre_archivo,
                "es_seguro": True,
                "mensaje": "No se encontraron métodos analizables en el archivo.",
                "vulnerabilidades_detectadas": []
            }

        df = pd.DataFrame(metodos_analizados)
        df = calcular_ratios(df)  # Agrega las 4 features de ratio

        nombres_metodos = df['metodo_nombre'].tolist()
        codigos_metodos = df['_codigo_metodo'].tolist()

        # Seleccionar columnas en el orden EXACTO usado al entrenar
        X = df[FEATURE_COLUMNS]

        probabilidades = modelo_rf.predict_proba(X)

        vulnerabilidades = []
        UMBRAL_VULNERABILIDAD = 0.70

        for i, prob in enumerate(probabilidades):
            prob_vulnerable = prob[1]

            if prob_vulnerable >= UMBRAL_VULNERABILIDAD:
                cwe_info = deducir_cwe(codigos_metodos[i])

                entry = {
                    "metodo": nombres_metodos[i],
                    "probabilidad_vulnerable": round(float(prob_vulnerable) * 100, 2),
                }
                if cwe_info:
                    entry["cwe"] = cwe_info["cwe_id"]
                    entry["cwe_nombre"] = cwe_info["nombre"]
                    entry["cwe_descripcion"] = cwe_info["descripcion"]
                else:
                    entry["cwe"] = None
                    entry["cwe_nombre"] = "Patrón no identificado"
                    entry["cwe_descripcion"] = "El modelo detectó riesgo pero no se identificó un patrón CWE conocido."

                vulnerabilidades.append(entry)

        es_seguro = len(vulnerabilidades) == 0
        return {
            "status": "completado",
            "archivo": peticion.nombre_archivo,
            "es_seguro": es_seguro,
            "total_metodos_analizados": len(metodos_analizados),
            "vulnerabilidades_detectadas": vulnerabilidades
        }

    except javalang.parser.JavaSyntaxError:
        raise HTTPException(
            status_code=400,
            detail=f"Error de sintaxis en '{peticion.nombre_archivo}': El código Java enviado no es válido."
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error interno al analizar '{peticion.nombre_archivo}': {str(e)}")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)