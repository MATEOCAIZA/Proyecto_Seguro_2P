from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import joblib
import javalang
import pandas as pd
import uvicorn
import os

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

# 3. Funciones de Extracción (Reglas de negocio del modelo)
# Lista más específica a Java puro (evitando choques con JPA)
FUNCIONES_PELIGROSAS = [
    'exec', 'prepareStatement', 'getOutputStream',
    'Socket', 'Runtime', 'getenv', 'ProcessBuilder',
    'ObjectInputStream', 'readObject', # Deserialización insegura
    'DocumentBuilderFactory', 'SAXParserFactory', # XXE (XML External Entities)
    'MessageDigest', 'Cipher', 'Random', # Criptografía débil
    'ScriptEngine', 'eval', # Inyección de código / EL
    'PrintWriter', 'getWriter' # Potencial XSS reflejado
]
# ─────────────────────────────────────────────────────────────────────────────
# MÓDULO FORENSE: Mapeo de patrones de código → CWE
# Si el Juez (ML) detecta vulnerabilidad, el Forense deduce el CWE concreto
# basándose en los tokens peligrosos presentes en el código fuente del método.
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
        "patrones": ["executeQuery", "prepareStatement", "createQuery",
                      "nativeQuery", "createNativeQuery", "Statement"],
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
    # --- NUEVOS CWE AGREGADOS ---
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
    El análisis es simple (búsqueda de tokens) para ser rápido y determinista.
    """
    coincidencias = []
    for regla in CWE_MAPPING:
        score = sum(1 for patron in regla["patrones"] if patron in codigo_metodo)
        if score > 0:
            coincidencias.append((score, regla))

    if not coincidencias:
        return None

    # Retornar el CWE con más coincidencias de patrones
    coincidencias.sort(key=lambda x: x[0], reverse=True)
    mejor = coincidencias[0][1]
    return {
        "cwe_id": mejor["cwe_id"],
        "nombre": mejor["nombre"],
        "descripcion": mejor["descripcion"]
    }

def calcular_profundidad_ast(nodo):
    if not hasattr(nodo, 'children') or not nodo.children:
        return 1
    prof = 0
    for hijo in nodo.children:
        if isinstance(hijo, list):
            for item in hijo:
                if isinstance(item, javalang.tree.Node):
                    prof = max(prof, calcular_profundidad_ast(item))
        elif isinstance(hijo, javalang.tree.Node):
            prof = max(prof, calcular_profundidad_ast(hijo))
            
    # Novedad: Limitar la profundidad para no penalizar lambdas o builders de Spring Boot
    profundidad_real = 1 + prof
    return min(profundidad_real, 12) # 12 es un techo seguro

def extraer_features(metodo_nodo):
    total_nodos = len(list(metodo_nodo.filter(javalang.tree.Node)))
    profundidad = calcular_profundidad_ast(metodo_nodo)
    llamadas_peligrosas = sum(
        1 for _, inv in metodo_nodo.filter(javalang.tree.MethodInvocation)
        if inv.member in FUNCIONES_PELIGROSAS
    )
    total_llamadas = len(list(metodo_nodo.filter(javalang.tree.MethodInvocation)))
    num_ifs = len(list(metodo_nodo.filter(javalang.tree.IfStatement)))
    num_loops = (
        len(list(metodo_nodo.filter(javalang.tree.ForStatement))) +
        len(list(metodo_nodo.filter(javalang.tree.WhileStatement))) +
        len(list(metodo_nodo.filter(javalang.tree.DoStatement)))
    )
    num_catches = len(list(metodo_nodo.filter(javalang.tree.CatchClause)))
    num_throws = len(list(metodo_nodo.filter(javalang.tree.ThrowStatement)))
    num_variables = len(list(metodo_nodo.filter(javalang.tree.LocalVariableDeclaration)))

    return {
        'total_nodos': total_nodos,
        'ast_depth': profundidad,
        'llamadas_peligrosas': llamadas_peligrosas,
        'total_llamadas': total_llamadas,
        'num_ifs': num_ifs,
        'num_loops': num_loops,
        'num_catches': num_catches,
        'num_throws': num_throws,
        'num_variables': num_variables,
        'num_literales': len(list(metodo_nodo.filter(javalang.tree.Literal)))
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
        # Parsear el código Java recibido
        arbol = javalang.parse.parse(peticion.codigo_fuente)
        metodos_analizados = []

        for _, metodo in arbol.filter(javalang.tree.MethodDeclaration):
            features = extraer_features(metodo)
            features['metodo_nombre'] = metodo.name
            # Preservar el código fuente del método para el análisis forense de CWE
            # Extraemos las líneas del método usando las posiciones del AST
            features['_codigo_metodo'] = peticion.codigo_fuente  # Se filtra antes de predecir
            metodos_analizados.append(features)

        if not metodos_analizados:
            return {
                "status": "ok",
                "archivo": peticion.nombre_archivo,
                "es_seguro": True,
                "mensaje": "No se encontraron métodos analizables en el archivo.",
                "vulnerabilidades_detectadas": []
            }

        # Convertir a DataFrame y calcular ratios
        df = pd.DataFrame(metodos_analizados)
        df['ratio_peligrosas'] = df['llamadas_peligrosas'] / (df['total_llamadas'] + 1)
        df['complejidad_relativa'] = (df['num_ifs'] + df['num_loops']) / (df['total_nodos'] + 1)
        df['densidad_variables'] = df['num_variables'] / (df['total_nodos'] + 1)
        df['ratio_manejo_errores'] = (df['num_catches'] + df['num_throws']) / (df['total_nodos'] + 1)

        # Separar nombres de métodos y código fuente antes de predecir
        nombres_metodos = df['metodo_nombre'].tolist()
        codigos_metodos = df['_codigo_metodo'].tolist()  # Para el análisis forense
        X = df.drop(columns=['metodo_nombre', '_codigo_metodo'])

        # Predicción basada en probabilidades para ajustar la sensibilidad
        probabilidades = modelo_rf.predict_proba(X)

        vulnerabilidades = []
        UMBRAL_VULNERABILIDAD = 0.70  # 70% de certeza requerida para clasificar como VULNERABLE

        for i, prob in enumerate(probabilidades):
            prob_vulnerable = prob[1]  # Probabilidad de pertenecer a la clase 1 (Vulnerable)

            # Solo se reporta como riesgo si supera el umbral del 70%
            if prob_vulnerable >= UMBRAL_VULNERABILIDAD:
                # ── MÓDULO FORENSE ──────────────────────────────────────────
                # El Juez (ML) decidió que es vulnerable → el Forense deduce el CWE
                cwe_info = deducir_cwe(codigos_metodos[i])
                # ─────────────────────────────────────────────────────────────

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

        # Respuesta estructurada para el pipeline de CI/CD
        es_seguro = len(vulnerabilidades) == 0
        return {
            "status": "completado",
            "archivo": peticion.nombre_archivo,
            "es_seguro": es_seguro,
            "total_metodos_analizados": len(metodos_analizados),
            "vulnerabilidades_detectadas": vulnerabilidades
        }

    except javalang.parser.JavaSyntaxError as e:
        raise HTTPException(
            status_code=400,
            detail=f"Error de sintaxis en '{peticion.nombre_archivo}': El código Java enviado no es válido."
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error interno al analizar '{peticion.nombre_archivo}': {str(e)}")
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
