"""
Módulo compartido de extracción de features AST para el modelo de
detección de vulnerabilidades.

IMPORTANTE: este archivo debe ser importado tanto por el script de
generación del dataset de entrenamiento como por el microservicio
FastAPI (api_modelo.py). Si en algún momento necesitas cambiar una
feature, una función peligrosa o el cap del AST depth, hazlo SOLO
aquí — nunca dupliques esta lógica en otro archivo, porque eso es
justo lo que causó las inconsistencias anteriores entre entrenamiento
y producción.
"""
import javalang

# Funciones consideradas indicio de vulnerabilidad.
# NOTA: 'prepareStatement' NO va aquí — es la forma SEGURA de hacer
# queries SQL en Java (usa parámetros bindeados). Se cuenta como
# sanitización, no como peligro (ver FUNCIONES_SANITIZACION).
FUNCIONES_PELIGROSAS = [
    'exec', 'getOutputStream', 'Socket', 'Runtime', 'getenv', 'ProcessBuilder',
    'ObjectInputStream', 'readObject',                 # Deserialización insegura
    'DocumentBuilderFactory', 'SAXParserFactory',       # XXE
    'MessageDigest', 'Cipher', 'Random',                # Criptografía débil
    'ScriptEngine', 'eval',                             # Inyección de código / EL
    'PrintWriter', 'getWriter'                          # Potencial XSS reflejado
]

# Funciones que indican buenas prácticas defensivas
FUNCIONES_SANITIZACION = [
    'prepareStatement', 'setString', 'setInt',   # Parametrización SQL
    'escapeHtml', 'encodeForHTML',               # Escape de salida
    'matches', 'compile',                         # Validación con regex
    'isValid', 'validate'                         # Validación genérica
]

PALABRAS_SQL = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'DROP']

AST_DEPTH_MAX = 12  # Techo para no penalizar lambdas / builders de Spring Boot

# Orden EXACTO de columnas que el modelo espera al predecir.
# Debe coincidir con el orden de X usado en el entrenamiento.
FEATURE_COLUMNS = [
    'total_nodos', 'ast_depth', 'llamadas_peligrosas', 'total_llamadas',
    'num_ifs', 'num_loops', 'num_catches', 'num_throws', 'num_variables',
    'num_literales', 'llamadas_sanitizacion', 'tiene_sanitizacion',
    'concatenacion_sql_sospechosa',
    'ratio_peligrosas', 'complejidad_relativa', 'densidad_variables',
    'ratio_manejo_errores'
]


def calcular_profundidad_ast(nodo):
    """Calcula recursivamente la profundidad del AST de un método (cap a 12)."""
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
    return min(1 + prof, AST_DEPTH_MAX)


def extraer_caracteristicas_metodo(metodo_nodo):
    """Extrae el diccionario de features base (13) de un método del AST."""
    total_nodos = len(list(metodo_nodo.filter(javalang.tree.Node)))
    profundidad = calcular_profundidad_ast(metodo_nodo)

    llamadas_peligrosas = 0
    llamadas_sanitizacion = 0
    total_llamadas = 0
    for _, invocacion in metodo_nodo.filter(javalang.tree.MethodInvocation):
        total_llamadas += 1
        if invocacion.member in FUNCIONES_PELIGROSAS:
            llamadas_peligrosas += 1
        if invocacion.member in FUNCIONES_SANITIZACION:
            llamadas_sanitizacion += 1

    num_ifs = len(list(metodo_nodo.filter(javalang.tree.IfStatement)))
    num_loops = (
        len(list(metodo_nodo.filter(javalang.tree.ForStatement))) +
        len(list(metodo_nodo.filter(javalang.tree.WhileStatement))) +
        len(list(metodo_nodo.filter(javalang.tree.DoStatement)))
    )
    num_catches = len(list(metodo_nodo.filter(javalang.tree.CatchClause)))
    num_throws = len(list(metodo_nodo.filter(javalang.tree.ThrowStatement)))
    num_variables = len(list(metodo_nodo.filter(javalang.tree.LocalVariableDeclaration)))

    literales = list(metodo_nodo.filter(javalang.tree.Literal))
    num_literales = len(literales)
    concatenacion_sql_sospechosa = sum(
        1 for _, lit in literales
        if any(p in str(lit.value).upper() for p in PALABRAS_SQL)
    )

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
        'num_literales': num_literales,
        'llamadas_sanitizacion': llamadas_sanitizacion,
        'tiene_sanitizacion': 1 if llamadas_sanitizacion > 0 else 0,
        'concatenacion_sql_sospechosa': concatenacion_sql_sospechosa
    }


def calcular_ratios(df):
    """Agrega las 4 features de ratio (in-place) a un DataFrame con las 13 base."""
    df['ratio_peligrosas'] = df['llamadas_peligrosas'] / (df['total_llamadas'] + 1)
    df['complejidad_relativa'] = (df['num_ifs'] + df['num_loops']) / (df['total_nodos'] + 1)
    df['densidad_variables'] = df['num_variables'] / (df['total_nodos'] + 1)
    df['ratio_manejo_errores'] = (df['num_catches'] + df['num_throws']) / (df['total_nodos'] + 1)
    return df


def extraer_codigo_fuente_metodo(codigo_fuente: str, metodo_nodo) -> str:
    """
    Extrae solo el bloque de código del método específico (no el archivo
    completo), usando la línea de inicio del nodo AST y conteo de llaves
    para hallar el final. Esto evita que el módulo forense de CWE analice
    código de OTROS métodos del mismo archivo.
    """
    if not metodo_nodo.position:
        return codigo_fuente  # fallback si javalang no reporta posición

    lineas = codigo_fuente.split('\n')
    inicio = metodo_nodo.position.line - 1  # javalang es 1-indexed

    contador_llaves = 0
    abierto = False
    fin = len(lineas)

    for i in range(inicio, len(lineas)):
        contador_llaves += lineas[i].count('{') - lineas[i].count('}')
        if '{' in lineas[i]:
            abierto = True
        if abierto and contador_llaves <= 0:
            fin = i + 1
            break

    return '\n'.join(lineas[inicio:fin])