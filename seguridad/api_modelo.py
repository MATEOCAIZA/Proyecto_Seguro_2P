from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import joblib
import javalang
import pandas as pd
import uvicorn
import os
from cwe_forense import deducir_cwe

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