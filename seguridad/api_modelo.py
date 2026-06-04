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
    print("✅ Modelo cargado en memoria exitosamente.")
except Exception as e:
    print(f"❌ Error al cargar el modelo: {e}")

# 2. Definir el formato que Java nos va a enviar
class PeticionCodigo(BaseModel):
    codigo_fuente: str
    nombre_archivo: str = "Desconocido.java"

# 3. Funciones de Extracción (Reglas de negocio del modelo)
FUNCIONES_PELIGROSAS = [
    'exec', 'executeQuery', 'execute', 'prepareStatement',
    'getOutputStream', 'Socket', 'Runtime', 'getenv'
]

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
    return 1 + prof

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

# 5. Endpoint principal — Donde Java se conecta para analizar código
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

        # Separar nombres de métodos antes de predecir
        nombres_metodos = df['metodo_nombre'].tolist()
        X = df.drop(columns=['metodo_nombre'])

        # Predicción del modelo
        predicciones = modelo_rf.predict(X)
        probabilidades = modelo_rf.predict_proba(X)

        vulnerabilidades = []
        for i, pred in enumerate(predicciones):
            if pred == 1:
                vulnerabilidades.append({
                    "metodo": nombres_metodos[i],
                    "probabilidad_vulnerable": round(float(probabilidades[i][1]) * 100, 2)
                })

        # Respuesta estructurada para el backend Java
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