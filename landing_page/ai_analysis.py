"""Interpretación de los datos con IA: dónde y cómo actuar.

DBSCAN y el índice de daño dicen *dónde* se concentran los hechos. Este módulo
le entrega a Claude un resumen agregado (nunca descripciones, identificadores
ni coordenadas exactas) y le pide recomendaciones accionables por zona.

La IA es opcional: requiere `pip install anthropic` y credenciales
(ANTHROPIC_API_KEY o `ant auth login`). Sin eso, se usa un análisis por reglas
con el mismo formato, para que el demo funcione sin internet.
"""
import hashlib
import json
import os
import threading
import time
from collections import Counter, defaultdict

from analytics import TYPES, VALID_STATUSES, now, parse_time, selected
from insights import cell_info, cell_key, decision_alerts, enhanced_statistics
from legal import FAMILIES, classify

try:
    import anthropic
except ImportError:  # el demo funciona sin dependencias externas
    anthropic = None

MODEL = os.environ.get('MAJES_AI_MODEL', 'claude-opus-5-5')
CACHE_SECONDS = 30 * 60
BANDS = ['00:00–05:59', '06:00–11:59', '12:00–17:59', '18:00–23:59']
WEEKDAYS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo']

SYSTEM = """Eres analista de prevención del delito para la Municipalidad Provincial de Caylloma, distrito de Majes (Arequipa, Perú). Trabajas con el Serenazgo, la Policía Nacional (comisaría de El Pedregal), juntas vecinales y comerciantes.

Recibirás un resumen agregado de reportes ciudadanos de hurtos y robos ya revisados por personas. Las zonas son celdas aproximadas de unos 640 × 670 m, no barrios oficiales. El índice de daño pondera el valor de lo sustraído, la violencia o amenaza, el uso de arma y las lesiones; ordena mejor que contar reportes, porque un robo de vehículo pesa más que dos hurtos menores. Las figuras legales (Código Penal, arts. 185-189 y 444) son sugerencias de clasificación, no calificaciones fiscales.

Tu tarea es interpretar los datos y decirle a quien administra dónde y cómo actuar primero:
- Prioriza como máximo 5 zonas. Usa exactamente los nombres de zona recibidos.
- Para cada zona da un diagnóstico breve que cite las cifras (reportes, índice de daño, franja, figura legal predominante) y de 2 a 4 acciones preventivas concretas, con responsable (Serenazgo, PNP, Municipalidad, juntas vecinales, comerciantes) y momento (días y franja).
- Ajusta la acción al patrón: los robos con violencia piden presencia policial y patrullaje en la franja crítica; los hurtos piden prevención situacional (iluminación, cámaras, cierre de accesos, campañas de cuidado de pertenencias); los hechos contra vehículos piden control de estacionamientos y puntos de control; los de vivienda, coordinación con juntas vecinales.
- Propón un indicador simple para comprobar en 4 semanas si la acción funcionó.
- Revisa las acciones ya registradas y su resultado medido. No repitas una acción que ya está en curso en la misma zona: si funciona, recomienda mantenerla; si los reportes aumentaron o no se distingue efecto, propón ajustarla o cambiarla. Recuerda que el resultado es descriptivo y no prueba causalidad.
- Señala los patrones generales (días, horas, tendencia frente al periodo anterior) y las limitaciones: muestras pequeñas, reportes sin revisar y que los reportes ciudadanos tienen subregistro.

No predigas quién cometerá delitos, no señales personas ni grupos sociales y no recomiendes medidas que vulneren derechos. Si los datos son escasos, dilo y recomienda recolectar más antes de mover recursos. Escribe en español claro y directo, sin jerga."""

SCHEMA = {
    'type': 'object',
    'properties': {
        'resumen': {'type': 'string'},
        'zonas_prioritarias': {'type': 'array', 'items': {
            'type': 'object',
            'properties': {
                'zona': {'type': 'string'},
                'prioridad': {'type': 'string', 'enum': ['alta', 'media', 'baja']},
                'diagnostico': {'type': 'string'},
                'acciones': {'type': 'array', 'items': {
                    'type': 'object',
                    'properties': {'accion': {'type': 'string'}, 'responsable': {'type': 'string'},
                                   'cuando': {'type': 'string'}},
                    'required': ['accion', 'responsable', 'cuando'], 'additionalProperties': False}},
                'indicador': {'type': 'string'},
            },
            'required': ['zona', 'prioridad', 'diagnostico', 'acciones', 'indicador'],
            'additionalProperties': False}},
        'patrones': {'type': 'array', 'items': {'type': 'string'}},
        'limitaciones': {'type': 'array', 'items': {'type': 'string'}},
    },
    'required': ['resumen', 'zonas_prioritarias', 'patrones', 'limitaciones'],
    'additionalProperties': False,
}

_cache = {}
_lock = threading.Lock()


def ai_available():
    """El SDK está instalado. Las credenciales se comprueban al llamar."""
    return anthropic is not None


def build_context(rows, days=30, subtype='all', band='all', family='all', clock=None, actions=None):
    """Resumen agregado que se envía a la IA. Sin texto libre ni datos individuales."""
    clock = clock or now()
    s = enhanced_statistics(rows, days, subtype, band, clock)
    alerts = decision_alerts(rows, days, subtype, band, clock)
    current = selected([r for r in rows if r['status'] in VALID_STATUSES], days, subtype, band, clock)
    by_cell = defaultdict(list)
    for r in current:
        by_cell[cell_info(cell_key(r))['name']].append(r)
    recurrence = {c['name']: c for c in s['recurrence']}

    zones = []
    for z in alerts['zones'][:12]:
        items = by_cell.get(z['name'], [])
        hours = Counter(parse_time(r['occurred_at']).hour // 6 for r in items)
        kinds = Counter(r['subtype'] for r in items)
        figures = Counter(classify(r)['label'] for r in items)
        rec = recurrence.get(z['name'])
        zones.append({
            'zona': z['name'], 'reportes': z['count'], 'indice_dano': z['harm'], 'nivel_por_reglas': z['level'],
            'eventos_graves': z['critical'], 'robos': z['robos'], 'hurtos': z['hurtos'],
            'figuras_legales': dict(figures.most_common()),
            'franja_predominante': BANDS[hours.most_common(1)[0][0]] if hours else None,
            'modalidad_predominante': TYPES[kinds.most_common(1)[0][0]] if kinds else None,
            'semanas_con_3_o_mas': rec['publishable_weeks'] if rec else 0,
            'cambio_vs_periodo_anterior': rec['change'] if rec else None,
            '_mapa': {'lat': z['lat'], 'lng': z['lng'], 'step': z['step']},
        })
    slots = sorted(((s['matrix'][d][h], d, h) for d in range(7) for h in range(24)), reverse=True)
    return {
        'area': 'El Pedregal, distrito de Majes (Arequipa) · área ilustrativa',
        'datos_ficticios': True,
        'periodo': {'desde': s['period']['start'], 'hasta': s['period']['end'], 'dias': days},
        'filtros': {'modalidad': TYPES.get(subtype, 'Todas'), 'franja': BANDS[int(band)] if band != 'all' else 'Todas',
                    'tipo': FAMILIES.get(family, 'Hurtos y robos')},
        'totales': {'validados': s['total'], 'periodo_anterior': s['previous_total'], 'cambio_pct': s['change_pct']},
        'figuras_legales': [{'figura': f"{x['label']} ({x['article']})", 'reportes': x['count']} for x in s['legal'] if x['count']],
        'modalidades': [{'modalidad': m['label'], 'reportes': m['count']} for m in s['modalities']],
        'franjas_horarias': [{'franja': BANDS[i], 'reportes': n} for i, n in enumerate(s['hours'])],
        'horas_pico': [{'dia': WEEKDAYS[d], 'hora': f'{h:02d}:00', 'reportes': n} for n, d, h in slots[:5] if n],
        'zonas': zones,
        'agrupaciones_dbscan': [{'nombre': c['name'], 'reportes': c['count'], 'desde': c['first_date'], 'hasta': c['last_date'],
                                 'modalidad_principal': max(c['modalities'], key=lambda m: m['count'])['label'],
                                 'franja_principal': BANDS[c['hours'].index(max(c['hours']))]}
                                for c in s['cluster_details']],
        'eventos_graves': {'total': alerts['individual_total'], 'sin_revisar': alerts['individual_pending']},
        'acciones_registradas': [{'zona': a['zone'], 'accion': a['title'], 'responsable': a['responsible'],
                                  'estado': a['status'], 'desde': a['start_date'], 'hasta': a['end_date'],
                                  'franja': a['band_label'], 'resultado': a['evaluation']['verdict'],
                                  'reportes_antes': a['evaluation'].get('before'), 'reportes_durante': a['evaluation'].get('after')}
                                 for a in (actions or []) if a['status'] != 'cancelada'][:15],
    }


def _public(context):
    """Contexto sin las claves internas que solo usa el mapa."""
    return {**context, 'zonas': [{k: v for k, v in z.items() if not k.startswith('_')} for z in context['zonas']]}


def ask_claude(context):
    client = anthropic.Anthropic()
    response = client.beta.messages.create(
        model=MODEL,
        max_tokens=16000,
        # Si el modelo declina la solicitud, la API la reintenta con el modelo recomendado.
        betas=['server-side-fallback-2026-07-01'],
        fallbacks='default',
        output_config={'effort': 'high', 'format': {'type': 'json_schema', 'schema': SCHEMA}},
        system=SYSTEM,
        messages=[{'role': 'user', 'content': 'Datos agregados del periodo, en JSON:\n'
                   + json.dumps(_public(context), ensure_ascii=False, indent=1)}],
    )
    if response.stop_reason == 'refusal':
        raise RuntimeError('La IA declinó la solicitud.')
    if response.stop_reason == 'max_tokens':
        raise RuntimeError('La respuesta de la IA quedó incompleta.')
    text = next(b.text for b in response.content if b.type == 'text')
    return json.loads(text), response.model


def rules_analysis(context):
    """Mismo formato que la IA, con reglas fijas. Se usa cuando la IA no está disponible."""
    zones = []
    for z in context['zonas'][:5]:
        if z['nivel_por_reglas'] == 'vigilancia' and len(zones) >= 3:
            continue
        robos = z['robos'] > z['hurtos']
        band = z['franja_predominante'] or 'la franja con más hechos'
        actions = []
        if robos:
            actions.append({'accion': 'Patrullaje mixto Serenazgo-PNP con presencia visible', 'responsable': 'Serenazgo y PNP',
                            'cuando': f'Todos los días, {band}'})
        else:
            actions.append({'accion': 'Campaña de prevención y cuidado de pertenencias con vecinos y comercios',
                            'responsable': 'Municipalidad y juntas vecinales', 'cuando': 'Próximas 2 semanas'})
        if band.startswith('18') or band.startswith('00'):
            actions.append({'accion': 'Revisar y reparar el alumbrado público de la zona', 'responsable': 'Municipalidad',
                            'cuando': 'Esta semana'})
        kind = z['modalidad_predominante'] or ''
        if 'Vehículo' in kind:
            actions.append({'accion': 'Puntos de control y estacionamientos vigilados', 'responsable': 'PNP y Serenazgo',
                            'cuando': band})
        elif 'vivienda' in kind:
            actions.append({'accion': 'Activar alarma comunitaria y rondas con la junta vecinal', 'responsable': 'Juntas vecinales',
                            'cuando': band})
        elif 'negocio' in kind:
            actions.append({'accion': 'Coordinar con comerciantes un canal de alerta y cámaras', 'responsable': 'Comerciantes y Serenazgo',
                            'cuando': 'Horario de atención'})
        if z['semanas_con_3_o_mas'] >= 2:
            actions.append({'accion': 'Mantener la intervención al menos 4 semanas: el patrón es recurrente',
                            'responsable': 'Municipalidad', 'cuando': 'Próximas 4 semanas'})
        current = [a for a in context['acciones_registradas'] if a['zona'] == z['zona'] and a['estado'] in {'en_curso', 'planificada'}]
        if current:
            a = current[0]
            keep = 'Mantener' if 'más que en el resto' in a['resultado'] else 'Revisar y ajustar'
            actions.insert(0, {'accion': f"{keep}: {a['accion']} ({a['resultado']})", 'responsable': a['responsable'],
                               'cuando': f"Hasta {a['hasta']}"})
        level = 'alta' if z['nivel_por_reglas'] == 'alta' else 'media' if z['nivel_por_reglas'] == 'media' else 'baja'
        figure = next(iter(z['figuras_legales']), 'sin figura')
        zones.append({
            'zona': z['zona'], 'prioridad': level,
            'diagnostico': f"{z['reportes']} reportes con índice de daño {z['indice_dano']}; "
                           f"{z['robos']} robos y {z['hurtos']} hurtos (predomina {figure}). "
                           f"Franja con más hechos: {band}." + (f" {z['eventos_graves']} eventos graves." if z['eventos_graves'] else ''),
            'acciones': actions[:4],
            'indicador': f"Reportes validados en {z['zona']} durante {band} en las próximas 4 semanas, frente a los {z['reportes']} actuales.",
        })
    t = context['totales']
    patterns = []
    if context['horas_pico']:
        p = context['horas_pico'][0]
        patterns.append(f"El momento con más hechos es el {p['dia']} a las {p['hora']} ({p['reportes']} reportes).")
    if t['cambio_pct'] is not None:
        patterns.append(f"Frente al periodo anterior, los reportes cambiaron {t['cambio_pct']:+.1f}% ({t['periodo_anterior']} → {t['validados']}).")
    if context['figuras_legales']:
        top = max(context['figuras_legales'], key=lambda x: x['reportes'])
        patterns.append(f"La figura legal más frecuente es {top['figura']} con {top['reportes']} reportes.")
    limits = ['Análisis por reglas fijas: no interpreta el contexto como lo haría la IA.',
              'Datos ficticios del demo; los reportes ciudadanos tienen subregistro.']
    if context['eventos_graves']['sin_revisar']:
        limits.append(f"Hay {context['eventos_graves']['sin_revisar']} eventos graves sin revisar que aún no cuentan en las zonas.")
    if t['validados'] < 20:
        limits.append('Hay pocos reportes validados: conviene recolectar más antes de mover recursos.')
    summary = (f"{t['validados']} reportes validados entre {context['periodo']['desde']} y {context['periodo']['hasta']}. "
               + (f"La zona prioritaria es {zones[0]['zona']}." if zones else 'Ninguna zona supera los umbrales.'))
    return {'resumen': summary, 'zonas_prioritarias': zones, 'patrones': patterns, 'limitaciones': limits}


def _fingerprint(rows, actions):
    data = [sorted((r['id'], r['status'], r.get('legal_class') or '') for r in rows),
            sorted((a['id'], a['status']) for a in actions or [])]
    return hashlib.sha256(json.dumps(data).encode()).hexdigest()[:16]


def analyze(rows, days=30, subtype='all', band='all', family='all', refresh=False, clock=None, actions=None):
    key = (days, subtype, band, family, _fingerprint(rows, actions))
    with _lock:
        hit = _cache.get(key)
        if hit and not refresh and time.time() - hit['_at'] < CACHE_SECONDS:
            return {**{k: v for k, v in hit.items() if k != '_at'}, 'cached': True}
        context = build_context(rows, days, subtype, band, family, clock, actions)
        source, model, notice = 'reglas', None, None
        if not context['zonas']:
            analysis = rules_analysis(context)
            notice = 'No hay zonas con datos suficientes; no se consultó a la IA.'
        elif anthropic is None:
            analysis = rules_analysis(context)
            notice = 'IA no instalada: ejecuta «pip install anthropic» y configura ANTHROPIC_API_KEY.'
        else:
            try:
                analysis, model = ask_claude(context)
                source = 'claude'
                known = {z['zona'] for z in context['zonas']}
                analysis['zonas_prioritarias'] = [z for z in analysis['zonas_prioritarias'] if z['zona'] in known]
            except anthropic.AuthenticationError:
                analysis, notice = rules_analysis(context), 'Credenciales de IA no válidas o ausentes (ANTHROPIC_API_KEY o «ant auth login»).'
            except anthropic.RateLimitError:
                analysis, notice = rules_analysis(context), 'La IA está saturada. Intenta de nuevo en un minuto.'
            except anthropic.APIConnectionError:
                analysis, notice = rules_analysis(context), 'Sin conexión con la IA.'
            except anthropic.APIStatusError as e:
                analysis, notice = rules_analysis(context), f'La IA respondió con error {e.status_code}.'
            except (RuntimeError, ValueError, KeyError, TypeError, StopIteration) as e:
                analysis, notice = rules_analysis(context), f'No se pudo usar la respuesta de la IA: {e}'
            if notice:
                notice += ' Se muestra el análisis por reglas.'
        cells = {z['zona']: {'name': z['zona'], 'count': z['reportes'], **z['_mapa']} for z in context['zonas']}
        result = {'source': source, 'model': model, 'notice': notice, 'generated_at': now().isoformat(),
                  'analysis': analysis, 'context': _public(context),
                  'map_cells': [cells[z['zona']] for z in analysis['zonas_prioritarias'] if z['zona'] in cells]}
        _cache[key] = {**result, '_at': time.time()}
        return {**result, 'cached': False}
