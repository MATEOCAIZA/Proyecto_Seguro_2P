# ─────────────────────────────────────────────────────────────────────────────
# MÓDULO FORENSE: Mapeo de patrones de código → CWE
#
# Cada regla tiene dos listas:
#   - patrones_primarios : tokens de alta señal, peso 3 cada uno.
#                          Su presencia sola es suficiente para identificar el CWE.
#   - patrones_secundarios: tokens de apoyo/contexto, peso 1 cada uno.
#                          Refuerzan el score pero no identifican solos.
#
# El CWE con mayor score ponderado gana.  Si ninguno supera el umbral
# mínimo (score >= 2) se devuelve None ("Patrón no identificado").
# ─────────────────────────────────────────────────────────────────────────────
CWE_MAPPING = [
    # ── Inyección ─────────────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-89",
        "nombre": "SQL Injection",
        # Señales directas de construcción dinámica de queries
        "patrones_primarios": [
            "executeQuery", "executeUpdate", "execute(",
            "createNativeQuery", "nativeQuery",
            "Statement", "createStatement",
            # Concatenación de palabras SQL en strings (detectado por feature_extractor)
            "\" + ", "' + ",                 # concatenación de string con variable
            "SELECT ", "INSERT INTO", "UPDATE ", "DELETE FROM", "DROP TABLE"
        ],
        # Contexto que refuerza el diagnóstico
        "patrones_secundarios": [
            "createQuery", "EntityManager", "Query",
            "getConnection", "DriverManager", "DataSource",
            "sql", "query", "where"
        ],
        "descripcion": (
            "Construcción dinámica de query SQL sin parametrización. "
            "Permite a un atacante manipular la lógica de la base de datos. "
            "Usar PreparedStatement con setString()/setInt() o un ORM parametrizado."
        )
    },
    {
        "cwe_id": "CWE-78",
        "nombre": "OS Command Injection",
        "patrones_primarios": [
            "Runtime.getRuntime().exec", "runtime.exec",
            "ProcessBuilder", ".exec(",
            "getRuntime", "Runtime.exec"
        ],
        "patrones_secundarios": [
            "Process", "waitFor", "getInputStream",
            "cmd", "bash", "sh ", "/bin/", "command"
        ],
        "descripcion": (
            "Ejecución de comandos del sistema con datos controlados por el usuario. "
            "Permite tomar control del servidor. Evitar exec() con input del usuario; "
            "usar listas de argumentos con ProcessBuilder sin shell=true."
        )
    },
    {
        "cwe_id": "CWE-94",
        "nombre": "Code Injection / Expression Language Injection",
        "patrones_primarios": [
            "ScriptEngine", "ScriptEngineManager",
            "eval(", "engine.eval",
            "SpelExpressionParser", "ExpressionParser",
            "parseExpression", "ELProcessor"
        ],
        "patrones_secundarios": [
            "Invocable", "ScriptContext",
            "getValue", "setValue", "expression"
        ],
        "descripcion": (
            "Inyección de código dinámico o expresiones EL/SpEL. "
            "Permite ejecutar código arbitrario en el servidor. "
            "Nunca evaluar expresiones construidas con input del usuario."
        )
    },
    {
        "cwe_id": "CWE-79",
        "nombre": "Cross-Site Scripting (XSS)",
        "patrones_primarios": [
            "getWriter().print", "getWriter().write",
            "response.getWriter", "PrintWriter",
            "out.print(", "out.write(",
            "<script>", "innerHTML", "document.write"
        ],
        "patrones_secundarios": [
            "HttpServletResponse", "ModelAndView",
            "getParameter", "getHeader",
            "html", "script", "javascript"
        ],
        "descripcion": (
            "Reflejo de datos no sanitizados en respuesta HTML. "
            "Permite inyección de scripts en el navegador del cliente. "
            "Escapar toda salida con OWASP Java Encoder o similar."
        )
    },
    # ── Exposición de datos ───────────────────────────────────────────────────
    {
        "cwe_id": "CWE-200",
        "nombre": "Exposure of Sensitive Information",
        "patrones_primarios": [
            "printStackTrace", "e.getMessage()",
            "System.out.print", "System.err.print",
            "getenv(", "System.getProperty(",
            "getPassword", "getSecret", "getPrivateKey", "getToken"
        ],
        "patrones_secundarios": [
            "Logger", "log.info", "log.debug", "log.error",
            "password", "secret", "token", "key", "credential",
            "API_KEY", "DB_PASS"
        ],
        "descripcion": (
            "Exposición de información sensible (contraseñas, tokens, stack traces) "
            "en logs, respuestas o variables de entorno. "
            "Nunca loguear datos sensibles ni exponer stack traces al cliente."
        )
    },
    {
        "cwe_id": "CWE-312",
        "nombre": "Cleartext Storage of Sensitive Information",
        "patrones_primarios": [
            "FileWriter", "BufferedWriter",
            "ObjectOutputStream", "writeObject",
            "SharedPreferences", "Properties"
        ],
        "patrones_secundarios": [
            "password", "secret", "token", "key",
            "write(", "store(", "save("
        ],
        "descripcion": (
            "Almacenamiento de información sensible en texto plano en disco o archivos. "
            "Cifrar los datos en reposo con AES-256 o usar un gestor de secretos."
        )
    },
    # ── Transmisión insegura ──────────────────────────────────────────────────
    {
        "cwe_id": "CWE-319",
        "nombre": "Cleartext Transmission of Sensitive Information",
        "patrones_primarios": [
            "new Socket(", "ServerSocket",
            "HttpURLConnection", "URL(",
            "openConnection", "http://"
        ],
        "patrones_secundarios": [
            "getOutputStream", "getInputStream",
            "DataOutputStream", "BufferedReader",
            "connect(", "send("
        ],
        "descripcion": (
            "Transmisión de datos sensibles sin cifrado TLS/SSL. "
            "Usar HTTPS (HttpsURLConnection), SSLSocket o librerías como OkHttp con TLS."
        )
    },
    # ── Path Traversal ────────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-22",
        "nombre": "Path Traversal",
        "patrones_primarios": [
            "new File(", "FileInputStream(", "FileOutputStream(",
            "FileReader(", "FileWriter(",
            "Paths.get(", "Path.of(",
            "../", "..\\"
        ],
        "patrones_secundarios": [
            "getParameter", "getPath", "resolve(",
            "upload", "download", "filename", "filepath"
        ],
        "descripcion": (
            "Acceso a archivos fuera del directorio permitido mediante rutas manipuladas. "
            "Validar y normalizar rutas con toRealPath() y verificar que estén "
            "dentro del directorio base permitido."
        )
    },
    # ── Deserialización ───────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-502",
        "nombre": "Deserialization of Untrusted Data",
        "patrones_primarios": [
            "ObjectInputStream", "readObject()",
            "XMLDecoder", "readUnshared",
            "fromXML(", "readValue("
        ],
        "patrones_secundarios": [
            "deserialize", "Serializable",
            "XStream", "ObjectMapper",
            "ByteArrayInputStream", "base64"
        ],
        "descripcion": (
            "Deserialización de datos controlados por el atacante. "
            "Puede permitir ejecución remota de código (RCE). "
            "Usar allowlists de clases o formatos seguros como JSON con esquema validado."
        )
    },
    # ── XXE ───────────────────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-611",
        "nombre": "XML External Entity (XXE)",
        "patrones_primarios": [
            "DocumentBuilderFactory", "SAXParserFactory",
            "XMLInputFactory", "TransformerFactory",
            "SchemaFactory"
        ],
        "patrones_secundarios": [
            "parse(", "newInstance()",
            "setFeature", "setExpandEntityReferences",
            "DOCTYPE", "ENTITY"
        ],
        "descripcion": (
            "Procesamiento XML sin deshabilitar entidades externas. "
            "Permite leer archivos locales o ejecutar SSRF. "
            "Deshabilitar DTDs con factory.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true)."
        )
    },
    # ── Criptografía débil ────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-327",
        "nombre": "Use of a Broken or Risky Cryptographic Algorithm",
        "patrones_primarios": [
            "\"MD5\"", "\"SHA-1\"", "\"DES\"",
            "\"RC4\"", "\"3DES\"", "\"Blowfish\"",
            "MD5", "SHA1", "DESKeySpec"
        ],
        "patrones_secundarios": [
            "MessageDigest", "Cipher", "getInstance(",
            "KeyGenerator", "SecretKeySpec",
            "encrypt", "decrypt", "hash"
        ],
        "descripcion": (
            "Uso de algoritmos criptográficos obsoletos (MD5, SHA-1, DES, RC4). "
            "Usar SHA-256/SHA-3 para hashes y AES-256-GCM para cifrado."
        )
    },
    {
        "cwe_id": "CWE-330",
        "nombre": "Use of Insufficiently Random Values",
        "patrones_primarios": [
            "new Random()", "Math.random()",
            "new java.util.Random", "rand.nextInt"
        ],
        "patrones_secundarios": [
            "seed", "token", "session",
            "password", "nonce", "salt", "key"
        ],
        "descripcion": (
            "Uso de java.util.Random o Math.random() en contextos de seguridad. "
            "No son criptográficamente seguros. Usar java.security.SecureRandom."
        )
    },
    # ── Autenticación / Sesiones ──────────────────────────────────────────────
    {
        "cwe_id": "CWE-287",
        "nombre": "Improper Authentication",
        "patrones_primarios": [
            "request.getSession(true)",
            "setAuthenticated(true)",
            "permitAll()", "anonymous()",
            "hasRole(\"ADMIN\") || true"
        ],
        "patrones_secundarios": [
            "HttpSession", "getSession",
            "SecurityContext", "Authentication",
            "login", "auth", "bypass"
        ],
        "descripcion": (
            "Autenticación débil o bypasseable. "
            "Verificar siempre credenciales antes de establecer sesión autenticada."
        )
    },
    {
        "cwe_id": "CWE-384",
        "nombre": "Session Fixation",
        "patrones_primarios": [
            "getSession(false)",
            "JSESSIONID",
            "session.setAttribute",
            "invalidate()"
        ],
        "patrones_secundarios": [
            "HttpSession", "Cookie",
            "setMaxAge", "login", "logout"
        ],
        "descripcion": (
            "No se regenera el ID de sesión tras autenticación exitosa. "
            "Llamar a request.changeSessionId() (Servlet 3.1+) después del login."
        )
    },
    # ── SSRF ──────────────────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-918",
        "nombre": "Server-Side Request Forgery (SSRF)",
        "patrones_primarios": [
            "new URL(", "URL url =",
            "RestTemplate", "WebClient",
            "HttpClient", "OkHttpClient",
            "openStream()", "openConnection()"
        ],
        "patrones_secundarios": [
            "getParameter", "getHeader",
            "uri", "endpoint", "target",
            "fetch", "redirect", "forward"
        ],
        "descripcion": (
            "El servidor realiza peticiones HTTP a una URL controlada por el usuario. "
            "Validar y restringir URLs destino con una allowlist de dominios/IPs permitidos."
        )
    },
    # ── Logging inseguro ──────────────────────────────────────────────────────
    {
        "cwe_id": "CWE-117",
        "nombre": "Improper Output Neutralization for Logs (Log Injection)",
        "patrones_primarios": [
            "log.info(", "log.warn(", "log.error(", "log.debug(",
            "logger.info(", "logger.warn(", "logger.error(",
            "LOGGER.info(", "LOGGER.error("
        ],
        "patrones_secundarios": [
            "getParameter", "getHeader", "getRemoteAddr",
            "username", "input", "request",
            "\\n", "\\r", "%0a", "%0d"
        ],
        "descripcion": (
            "Escritura de datos del usuario directamente en logs sin sanitizar. "
            "Permite falsificación de entradas de log. "
            "Sanitizar input eliminando saltos de línea antes de loguear."
        )
    },
]


def deducir_cwe(codigo_metodo: str) -> dict | None:
    """
    Analiza el código fuente de un método para identificar el CWE más probable.

    Scoring ponderado:
      - Cada patrón_primario encontrado  → +3 puntos  (señal de alta certeza)
      - Cada patrón_secundario encontrado → +1 punto   (señal de contexto)

    Solo se retorna un resultado si el score ponderado es >= 3 (al menos
    un patrón primario coincide). Esto reduce falsos positivos donde solo
    hay tokens de contexto genéricos sin el patrón de vulnerabilidad real.
    """
    PESO_PRIMARIO   = 3
    PESO_SECUNDARIO = 1
    SCORE_MINIMO    = 3  # Exige al menos un patrón primario

    coincidencias = []
    for regla in CWE_MAPPING:
        score = 0
        score += sum(
            PESO_PRIMARIO
            for p in regla["patrones_primarios"]
            if p in codigo_metodo
        )
        score += sum(
            PESO_SECUNDARIO
            for p in regla["patrones_secundarios"]
            if p in codigo_metodo
        )
        if score >= SCORE_MINIMO:
            coincidencias.append((score, regla))

    if not coincidencias:
        return None

    # Ordenar por score descendente; en empate, el primero en la lista
    # tiene prioridad (orden de especificidad del CWE_MAPPING)
    coincidencias.sort(key=lambda x: x[0], reverse=True)
    mejor = coincidencias[0][1]
    return {
        "cwe_id":      mejor["cwe_id"],
        "nombre":      mejor["nombre"],
        "descripcion": mejor["descripcion"]
    }
