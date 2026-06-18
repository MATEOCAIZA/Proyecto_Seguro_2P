package ec.edu.espe.zonas.services.impl;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.springframework.stereotype.Service;

/**
 * ⚠️ ARCHIVO CON VULNERABILIDADES INTENCIONALES ⚠️
 *
 * Este servicio contiene múltiples vulnerabilidades de seguridad
 * diseñadas para ser detectadas por el modelo ML de análisis de código.
 *
 * NO USAR EN PRODUCCIÓN — Solo para fines de prueba y demostración
 * del pipeline de seguridad DevSecOps.
 *
 * Vulnerabilidades incluidas:
 *   - CWE-89:  SQL Injection (concatenación de queries)
 *   - CWE-78:  OS Command Injection (Runtime.exec)
 *   - CWE-502: Deserialización insegura (ObjectInputStream.readObject)
 *   - CWE-79:  Cross-Site Scripting / XSS (PrintWriter sin escape)
 *   - CWE-327: Criptografía débil (MD5 / DES)
 *   - CWE-611: XML External Entity (XXE)
 *   - CWE-94:  Inyección de código (ScriptEngine.eval)
 *   - CWE-330: Uso de Random inseguro
 */
@Service
public class ReporteServicioImpl {

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 1: SQL Injection (CWE-89)
    // Concatena entrada del usuario directamente en la query SQL
    // sin usar PreparedStatement ni parametrización.
    // ═══════════════════════════════════════════════════════════════
    public List<String> buscarEspaciosPorNombre(String nombreUsuario) {
        List<String> resultados = new ArrayList<>();
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost/zonas");
            Statement stmt = conn.createStatement();

            // ❌ VULNERABLE: Concatenación directa de input del usuario en SQL
            String query = "SELECT * FROM espacios WHERE nombre = '" + nombreUsuario + "'";
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                resultados.add(rs.getString("nombre"));
            }
            rs.close();
            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultados;
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 2: OS Command Injection (CWE-78)
    // Ejecuta comandos del sistema operativo con input del usuario
    // directamente, permitiendo la inyección de comandos arbitrarios.
    // ═══════════════════════════════════════════════════════════════
    public String generarReporteConComando(String parametroUsuario) {
        StringBuilder output = new StringBuilder();
        try {
            // ❌ VULNERABLE: Runtime.exec con input del usuario sin sanitizar
            Runtime runtime = Runtime.getRuntime();
            Process proceso = runtime.exec("cmd /c dir " + parametroUsuario);

            InputStream is = proceso.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String linea;
            while ((linea = reader.readLine()) != null) {
                output.append(linea).append("\n");
            }
            proceso.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 3: Deserialización insegura (CWE-502)
    // Lee un objeto serializado de un flujo de entrada sin validar
    // el tipo, lo que permite ejecución remota de código (RCE).
    // ═══════════════════════════════════════════════════════════════
    public Object cargarConfiguracion(byte[] datos) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(datos);
            // ❌ VULNERABLE: ObjectInputStream.readObject sin filtro de tipo
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object config = ois.readObject();
            ois.close();
            return config;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 4: XSS Reflejado (CWE-79)
    // Escribe directamente el input del usuario en la respuesta HTTP
    // sin aplicar encoding ni escape de caracteres HTML.
    // ═══════════════════════════════════════════════════════════════
    public void escribirRespuestaDirecta(PrintWriter writer, String entradaUsuario) {
        // ❌ VULNERABLE: PrintWriter.getWriter con datos sin escapar
        writer.println("<html><body>");
        writer.println("<h1>Resultado de búsqueda</h1>");
        writer.println("<p>Usted buscó: " + entradaUsuario + "</p>");
        writer.println("</body></html>");
        writer.flush();
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 5: Criptografía débil (CWE-327)
    // Usa MD5 (algoritmo de hash roto) y DES (cifrado obsoleto)
    // que no proveen seguridad real contra ataques modernos.
    // ═══════════════════════════════════════════════════════════════
    public String hashearPassword(String password) {
        try {
            // ❌ VULNERABLE: MD5 no es seguro para hashing de contraseñas
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(password.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] cifrarDatos(String datos, String clave) {
        try {
            // ❌ VULNERABLE: DES es un cifrado débil y obsoleto
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            return cipher.doFinal(datos.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 6: XXE - XML External Entity (CWE-611)
    // Parsea XML de entrada sin deshabilitar la resolución de
    // entidades externas, permitiendo lectura de archivos del server.
    // ═══════════════════════════════════════════════════════════════
    public Object procesarXml(InputStream xmlInput) {
        try {
            // ❌ VULNERABLE: DocumentBuilderFactory sin deshabilitar entidades externas
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            var documento = builder.parse(xmlInput);
            return documento.getDocumentElement();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 7: Inyección de código (CWE-94)
    // Evalúa expresiones JavaScript provenientes del usuario usando
    // ScriptEngine.eval, permitiendo ejecución arbitraria de código.
    // ═══════════════════════════════════════════════════════════════
    public Object evaluarExpresion(String expresionUsuario) {
        try {
            // ❌ VULNERABLE: ScriptEngine.eval con input del usuario
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            Object resultado = engine.eval(expresionUsuario);
            return resultado;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 8: Uso de Random inseguro (CWE-330)
    // Genera tokens de sesión con java.util.Random en vez de
    // SecureRandom, lo que hace los tokens predecibles.
    // ═══════════════════════════════════════════════════════════════
    public String generarTokenSesion() {
        // ❌ VULNERABLE: java.util.Random no es criptográficamente seguro
        Random random = new Random();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            token.append(Integer.toHexString(random.nextInt(16)));
        }
        return token.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 9: SQL Injection con DELETE (CWE-89)
    // Otra variante de SQL Injection usando sentencia DELETE
    // con concatenación directa del parámetro del usuario.
    // ═══════════════════════════════════════════════════════════════
    public void eliminarRegistrosPorFiltro(String filtroUsuario) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost/zonas");
            Statement stmt = conn.createStatement();

            // ❌ VULNERABLE: DELETE con concatenación directa
            String query = "DELETE FROM reportes WHERE categoria = '" + filtroUsuario + "'";
            stmt.executeUpdate(query);

            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 10: Conexión de red insegura (CWE-319)
    // Abre una conexión Socket sin cifrado TLS/SSL para enviar
    // datos sensibles en texto plano.
    // ═══════════════════════════════════════════════════════════════
    public void enviarDatosSinCifrar(String host, int puerto, String datosSensibles) {
        try {
            // ❌ VULNERABLE: Socket sin TLS — datos viajan en texto plano
            Socket socket = new Socket(host, puerto);
            OutputStream os = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(os, true);
            writer.println(datosSensibles);
            writer.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 11: Command Injection con ProcessBuilder (CWE-78)
    // Usa ProcessBuilder con input del usuario sin validación,
    // otra variante del OS Command Injection.
    // ═══════════════════════════════════════════════════════════════
    public String ejecutarComandoSistema(String comando) {
        StringBuilder output = new StringBuilder();
        try {
            // ❌ VULNERABLE: ProcessBuilder con comando del usuario
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            Process proceso = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proceso.getInputStream())
            );
            String linea;
            while ((linea = reader.readLine()) != null) {
                output.append(linea).append("\n");
            }
            proceso.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // VULNERABILIDAD 12: Lectura de variables de entorno (CWE-526)
    // Expone información sensible del entorno del servidor al
    // usuario sin restricciones.
    // ═══════════════════════════════════════════════════════════════
    public String obtenerVariableEntorno(String nombreVariable) {
        // ❌ VULNERABLE: Expone variables de entorno del sistema
        String valor = System.getenv(nombreVariable);
        return valor != null ? valor : "No definida";
    }
}
