import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Métricas personalizadas ────────────────────────────────────────────────
const zonasCreadas    = new Counter('zonas_creadas_total');
const tasaExito       = new Rate('tasa_exito_crear_zona');
const tiempoRespuesta = new Trend('tiempo_respuesta_crear_zona', true);

// ── Configuración del escenario de carga ─────────────────────────────────
export const options = {
  scenarios: {
    // Escenario 1: Carga gradual (rampa de subida)
    carga_gradual: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },   // Sube a 10 usuarios en 30s
        { duration: '1m',  target: 10 },   // Mantiene 10 usuarios por 1 min
        { duration: '30s', target: 50 },   // Sube a 50 usuarios en 30s
        { duration: '1m',  target: 50 },   // Mantiene 50 usuarios por 1 min
        { duration: '30s', target: 0  },   // Baja a 0 usuarios (enfriamiento)
      ],
    },
  },

  // ── Umbrales de aceptación ───────────────────────────────────────────────
  thresholds: {
    // 95% de peticiones deben responder en menos de 500ms
    'http_req_duration': ['p(95)<500'],
    // Al menos 95% de peticiones deben tener éxito (2xx)
    'tasa_exito_crear_zona': ['rate>0.95'],
    // Tiempo de respuesta promedio menor a 300ms
    'tiempo_respuesta_crear_zona': ['avg<300'],
  },
};

// ── URL base del backend ──────────────────────────────────────────────────
const BASE_URL = 'http://localhost:8080';

// ── Datos de prueba corregidos con los Enums del Backend ──────────────────
const TIPOS_ZONA = ['EXTERNA', 'PREFERENCIAL', 'REGULAR', 'VIP', 'INTERNA'];

function generarNombreUnico() {
  return `Zona-k6-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
}

// ── Función principal (ejecutada por cada VU en cada iteración) ───────────
export default function () {
  const payload = JSON.stringify({
    nombre:      generarNombreUnico(),
    descripcion: 'Zona creada por prueba de rendimiento k6',
    tipo:        TIPOS_ZONA[Math.floor(Math.random() * TIPOS_ZONA.length)],
    capacidad:   Math.floor(Math.random() * 100) + 10,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags:    { endpoint: 'crear_zona' },
  };

  const inicio = Date.now();
  const res    = http.post(`${BASE_URL}/api/v1/zonas/`, payload, params);
  const duracion = Date.now() - inicio;

  // ── Registrar métricas ────────────────────────────────────────────────
  tiempoRespuesta.add(duracion);

  // Intentar parsear el cuerpo de forma segura solo si la respuesta tiene contenido
  let bodyJson = {};
  try {
    bodyJson = res.body ? JSON.parse(res.body) : {};
  } catch (e) {
    // Si no es un JSON válido, queda vacío
  }

  const exito = check(res, {
    'status es 201 (Created)':          (r) => r.status === 201,
    'respuesta contiene idZona':        () => bodyJson.idZona !== undefined,
    'respuesta contiene nombre':        () => bodyJson.nombre !== undefined,
    'tiempo de respuesta < 500ms':      (r) => r.timings.duration < 500,
  });

  tasaExito.add(exito);

  if (exito) {
    zonasCreadas.add(1);
  } else {
    console.warn(`❌ Fallo crear zona | status=${res.status} | body=${res.body}`);
  }

  sleep(1); // Pausa de 1 segundo entre iteraciones por VU
}

// ── Resumen final personalizado ───────────────────────────────────────────
export function handleSummary(data) {
  const durP95 = data.metrics['http_req_duration']?.values?.['p(95)']?.toFixed(2) ?? 'N/A';
  const durAvg = data.metrics['http_req_duration']?.values?.['avg']?.toFixed(2)   ?? 'N/A';
  const total  = data.metrics['http_reqs']?.values?.['count']                      ?? 0;
  const creadas = data.metrics['zonas_creadas_total']?.values?.['count']           ?? 0;
  const exito  = ((data.metrics['tasa_exito_crear_zona']?.values?.['rate'] ?? 0) * 100).toFixed(2);

  console.log('\n═══════════════════════════════════════════════════════');
  console.log('       RESULTADO — CREAR ZONA (POST /api/v1/zonas/)    ');
  console.log('═══════════════════════════════════════════════════════');
  console.log(`  Total peticiones enviadas : ${total}`);
  console.log(`  Zonas creadas con éxito  : ${creadas}`);
  console.log(`  Tasa de éxito            : ${exito}%`);
  console.log(`  Tiempo promedio (avg)    : ${durAvg} ms`);
  console.log(`  Percentil 95 (p95)       : ${durP95} ms`);
  console.log('═══════════════════════════════════════════════════════\n');

  return {
    'stdout': JSON.stringify(data, null, 2),
    'resultados/crear_zona_resultado.json': JSON.stringify(data, null, 2),
  };
}
