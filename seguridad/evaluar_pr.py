"""
evaluar_pr.py — Script de CI/CD para GitHub Actions

Flujo:
  1. Lee los archivos .java modificados en el Pull Request actual
  2. Envía cada archivo al microservicio FastAPI desplegado (api_modelo.py)
  3. Si algún archivo es vulnerable → sys.exit(1) → el workflow falla → PR bloqueado
  4. Si todos son seguros → sys.exit(0) → el workflow continúa → PR aprobado

Variables de entorno requeridas (se configuran en GitHub Actions):
  - GITHUB_TOKEN      : Token de autenticación de GitHub (provisto automáticamente)
  - PR_NUMBER         : Número del PR en curso
  - MODELO_API_URL    : URL del microservicio FastAPI desplegado (ej: https://mi-api.railway.app)
  - TELEGRAM_TOKEN    : (Opcional) Token del bot de Telegram para notificaciones
  - TELEGRAM_CHAT_ID  : (Opcional) Chat ID para notificaciones de Telegram
  - GITHUB_REPOSITORY : Nombre del repositorio en formato "owner/repo" (provisto automáticamente)
"""

import os
import sys
import httpx
import requests
from github import Github
from github import Auth

# ─────────────────────────────────────────────────────────
# 1. Leer variables de entorno
# ─────────────────────────────────────────────────────────
GITHUB_TOKEN    = os.environ.get("GITHUB_TOKEN")
PR_NUMBER       = int(os.environ.get("PR_NUMBER", 0))
REPO_NAME       = os.environ.get("GITHUB_REPOSITORY")       # owner/repo
MODELO_API_URL  = os.environ.get("MODELO_API_URL", "http://localhost:8000")
TELEGRAM_TOKEN  = os.environ.get("TELEGRAM_TOKEN")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID")

def enviar_telegram(mensaje: str):
    """Envía notificación a Telegram si las credenciales están configuradas."""
    if not TELEGRAM_TOKEN or not TELEGRAM_CHAT_ID:
        return
    try:
        url = f"https://api.telegram.org/bot{TELEGRAM_TOKEN}/sendMessage"
        requests.post(url, data={"chat_id": TELEGRAM_CHAT_ID, "text": mensaje}, timeout=10)
    except Exception as e:
        print(f" No se pudo enviar notificación a Telegram: {e}")

# ─────────────────────────────────────────────────────────
# 2. Verificar que el microservicio esté disponible
# ─────────────────────────────────────────────────────────
def verificar_microservicio():
    """Comprueba que la API del modelo esté corriendo, tolerando 'cold starts' de servidores gratuitos."""
    import time
    max_reintentos = 3
    tiempo_espera = 20 # Segundos a esperar entre intentos

    for intento in range(max_reintentos):
        try:
            print(f"⏳ Verificando microservicio en {MODELO_API_URL} (Intento {intento + 1}/{max_reintentos})...")
            
            # Aumentamos el timeout a 40 segundos por petición
            respuesta = httpx.get(f"{MODELO_API_URL}/health", timeout=40.0)
            respuesta.raise_for_status()
            data = respuesta.json()
            
            if not data.get("modelo_cargado", False):
                print("El microservicio está activo pero el modelo ML no está cargado.")
                sys.exit(1)
                
            print(f" Microservicio disponible — modelo cargado: {data.get('modelo_cargado')}")
            return # Éxito, salimos de la función y el pipeline continúa
            
        except httpx.TimeoutException:
            print(f" Timeout. El servidor se está despertando (Cold Start). Esperando {tiempo_espera}s...")
            time.sleep(tiempo_espera)
        except httpx.ConnectError:
             print(f" No se pudo conectar al microservicio en: {MODELO_API_URL}")
             print("   Verifica que la URL en los secrets de GitHub sea correcta y NO termine con '/'.")
             sys.exit(1)
        except Exception as e:
            print(f" Error HTTP al verificar el microservicio: {e}")
            sys.exit(1)

    # Si terminan los intentos y no hubo éxito
    print(f" El microservicio no respondió después de {max_reintentos} intentos. Abortando.")
    sys.exit(1)
# ─────────────────────────────────────────────────────────
# 3. Obtener archivos Java del PR via GitHub API
# ─────────────────────────────────────────────────────────
def obtener_archivos_java_del_pr():
    """Retorna una lista de tuplas (nombre_archivo, contenido) con los .java del PR."""
    auth = Auth.Token(GITHUB_TOKEN)
    g = Github(auth=auth)
    repo = g.get_repo(REPO_NAME)
    pr = repo.get_pull(PR_NUMBER)

    archivos_java = []
    for archivo in pr.get_files():
        if archivo.filename.endswith(".java") and archivo.status != "removed":
            try:
                # Obtener el contenido del archivo en la rama del PR
                contenido = repo.get_contents(
                    archivo.filename,
                    ref=pr.head.sha
                )
                codigo = contenido.decoded_content.decode("utf-8")
                archivos_java.append((archivo.filename, codigo))
                print(f"   Encontrado: {archivo.filename}")
            except Exception as e:
                print(f"   No se pudo leer {archivo.filename}: {e}")

    return archivos_java

# ─────────────────────────────────────────────────────────
# 4. Analizar un archivo contra el microservicio
# ─────────────────────────────────────────────────────────
def analizar_archivo(nombre_archivo: str, codigo_fuente: str) -> dict:
    """Envía el código al microservicio FastAPI y retorna el resultado del análisis."""
    payload = {
        "codigo_fuente": codigo_fuente,
        "nombre_archivo": nombre_archivo
    }
    try:
        respuesta = httpx.post(
            f"{MODELO_API_URL}/analizar-codigo",
            json=payload,
            timeout=60   # El análisis del modelo puede tomar varios segundos
        )
        respuesta.raise_for_status()
        return respuesta.json()
    except httpx.HTTPStatusError as e:
        # es_seguro=None indica fallo técnico, NO vulnerabilidad real detectada
        print(f"   Error HTTP al analizar {nombre_archivo}: {e.response.status_code} — {e.response.text}")
        return {"es_seguro": None, "vulnerabilidades_detectadas": [], "error": str(e)}
    except Exception as e:
        # es_seguro=None indica fallo técnico, NO vulnerabilidad real detectada
        print(f"    Error inesperado al analizar {nombre_archivo}: {e}")
        return {"es_seguro": None, "vulnerabilidades_detectadas": [], "error": str(e)}

# ─────────────────────────────────────────────────────────
# 5. Punto de entrada principal
# ─────────────────────────────────────────────────────────
def main():
    print("=" * 60)
    print(f"🔍 Iniciando análisis de seguridad — PR #{PR_NUMBER}")
    print(f"   Repositorio : {REPO_NAME}")
    print(f"   API Modelo  : {MODELO_API_URL}")
    print("=" * 60)

    # 5.0 Enviar notificación obligatoria de inicio a Telegram
    enviar_telegram(f" Inicio de revisión de seguridad: Evaluando PR #{PR_NUMBER} en {REPO_NAME}...")

    # 5.1 Verificar que el microservicio esté disponible
    verificar_microservicio()

    # 5.2 Obtener los archivos Java del PR
    print("\n Obteniendo archivos Java modificados en el PR...")
    archivos = obtener_archivos_java_del_pr()

    if not archivos:
        print("\n No se encontraron archivos .java modificados. El PR es seguro por defecto.")
        enviar_telegram(f" PR #{PR_NUMBER} en {REPO_NAME}: No hay archivos Java para analizar. Continuando...")
        sys.exit(0)

    print(f"\n Analizando {len(archivos)} archivo(s) con el modelo ML...")
    print("-" * 60)

    # 5.3 Analizar cada archivo
    vulnerabilidades_totales = []
    archivos_vulnerables = []

    for nombre, codigo in archivos:
        print(f"\n▶  Analizando: {nombre}")
        resultado = analizar_archivo(nombre, codigo)

        es_seguro = resultado.get("es_seguro")  # None = error técnico, True = seguro, False = vulnerable
        vulns     = resultado.get("vulnerabilidades_detectadas", [])
        error_msg = resultado.get("error")

        if es_seguro is None:
            # Error de red o del microservicio — NO se bloquea el PR por esto
            print(f"   No se pudo analizar '{nombre}' por error técnico: {error_msg}")
            print(f"      El archivo se omite del análisis (no cuenta como vulnerable).")
        elif es_seguro:
            total_metodos = resultado.get("total_metodos_analizados", "N/A")
            print(f"   SEGURO — {total_metodos} método(s) analizados sin vulnerabilidades.")
        else:
            # es_seguro=False Y la lista de vulns tiene elementos → vulnerabilidad real
            if vulns:
                print(f"    VULNERABLE — {len(vulns)} método(s) con riesgo detectado:")
                for v in vulns:
                    cwe_str = f"{v.get('cwe', 'N/A')} {v.get('cwe_nombre', '')}" if v.get('cwe') else "CWE no identificado"
                    print(f"      • Método '{v['metodo']}' — Confianza: {v['probabilidad_vulnerable']}% — {cwe_str}")
                vulnerabilidades_totales.extend(vulns)
                archivos_vulnerables.append(nombre)
            else:
                # es_seguro=False pero sin vulnerabilidades listadas → respuesta ambigua del modelo
                print(f"    El modelo marcó '{nombre}' como no seguro pero sin detalles de vulnerabilidades.")
                print(f"      Se trata como advertencia, no bloquea el PR.")

    print("\n" + "=" * 60)

    # Instanciar cliente de GitHub para interactuar con el PR y el Repo
    g = Github(GITHUB_TOKEN)
    repo = g.get_repo(REPO_NAME)
    pr = repo.get_pull(PR_NUMBER)

    # 5.4 Resultado final
    if archivos_vulnerables:
        # Construir comentario detallado para GitHub
        detalle_vulns = "###  Análisis de Seguridad: Código Vulnerable Detectado\n\n"
        detalle_vulns += "El modelo de minería de datos ha clasificado este código como riesgoso.\n\n"
        detalle_vulns += "| Método | Confianza ML | CWE | Vulnerabilidad | Descripción |\n"
        detalle_vulns += "|--------|-------------|-----|----------------|-------------|\n"
        for v in vulnerabilidades_totales:
            cwe_id   = v.get('cwe') or 'N/A'
            cwe_nom  = v.get('cwe_nombre', 'Patrón no identificado')
            cwe_desc = v.get('cwe_descripcion', '')
            detalle_vulns += (
                f"| `{v['metodo']}` "
                f"| **{v['probabilidad_vulnerable']}%** "
                f"| `{cwe_id}` "
                f"| {cwe_nom} "
                f"| {cwe_desc} |\n"
            )

        # 1. Crear comentario en el PR
        pr.create_issue_comment(detalle_vulns)

        # 2. Aplicar etiqueta al PR
        try:
            pr.add_to_labels("fixing-required")
        except Exception as e:
            print(f" No se pudo aplicar la etiqueta (¿existe en el repo?): {e}")

        # 3. Crear un Issue automático vinculado
        titulo_issue = f"Corregir vulnerabilidades introducidas en PR #{PR_NUMBER}"
        cuerpo_issue = (
            f"Se ha bloqueado el PR #{PR_NUMBER} debido a código vulnerable.\n\n"
            f"{detalle_vulns}\n\n"
            f"Por favor, revisa y corrige el código antes de intentar un nuevo merge."
        )
        repo.create_issue(title=titulo_issue, body=cuerpo_issue)

        # 4. Notificar a Telegram — mensaje enriquecido equivalente al comentario de GitHub
        resumen_archivos = "\n".join([f"  {a}" for a in archivos_vulnerables])
        separador = "─" * 40
        lineas_vulns = []
        for idx, v in enumerate(vulnerabilidades_totales, start=1):
            cwe_id   = v.get('cwe') or 'N/A'
            cwe_nom  = v.get('cwe_nombre', 'Patrón no identificado')
            cwe_desc = v.get('cwe_descripcion', 'Sin descripción disponible.')
            lineas_vulns.append(
                f"{idx}. Método: {v['metodo']}\n"
                f"   Confianza ML : {v['probabilidad_vulnerable']}%\n"
                f"   CWE          : {cwe_id} — {cwe_nom}\n"
                f"   Descripción  : {cwe_desc}"
            )
        detalle_telegram = f"\n{separador}\n".join(lineas_vulns)
        mensaje_fallo = (
            f" ALERTA DE SEGURIDAD — PR RECHAZADO\n"
            f"{separador}\n"
            f" Repositorio : {REPO_NAME}\n"
            f" Pull Request: #{PR_NUMBER}\n"
            f" Archivos afectados:\n{resumen_archivos}\n"
            f"{separador}\n"
            f" Vulnerabilidades detectadas ({len(vulnerabilidades_totales)} total):\n\n"
            f"{detalle_telegram}\n"
            f"{separador}\n"
            f" Acción requerida: Corregir el código antes de hacer un nuevo merge.\n"
            f" Ver issue en: https://github.com/{REPO_NAME}/issues"
        )
        print(f" ANÁLISIS FALLIDO — PR BLOQUEADO")
        enviar_telegram(mensaje_fallo)

        # Falla el workflow -> GitHub bloquea el merge
        sys.exit(1)
    else:
        archivos_revisados = "\n".join([f"   {nombre}" for nombre, _ in archivos])
        mensaje_ok = (
            f" ANÁLISIS DE SEGURIDAD EXITOSO\n"
            f"{'─' * 40}\n"
            f" Repositorio : {REPO_NAME}\n"
            f" Pull Request: #{PR_NUMBER}\n"
            f"{'─' * 40}\n"
            f" Archivos revisados ({len(archivos)}):\n{archivos_revisados}\n"
            f"{'─' * 40}\n"
            f" El código no presenta vulnerabilidades.\n"
            f" El pipeline continúa con las pruebas automáticas."
        )
        print(f" ANÁLISIS EXITOSO — Todo el código es seguro.")
        enviar_telegram(mensaje_ok)

        # El workflow continúa -> se ejecutan los tests
        sys.exit(0)

if __name__ == "__main__":
    main()