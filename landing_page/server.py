#!/usr/bin/env python3
"""Servidor local del prototipo Majes Alerta. Python 3.10+, sin pip."""
import argparse
import base64
import csv
import hmac
import io
import json
import math
import mimetypes
import os
import secrets
import time
from http import cookies
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

from analytics import (BOUNDS, CENTER, TYPES, STATUSES, VALUE_BANDS, now, parse_time, statistics, priority,
                       duplicate_candidates, harm, critical_reasons)
from insights import enhanced_statistics, management_metrics
from legal import LEGAL_CLASSES, FAMILIES, FORCES, CIRCUMSTANCES, classify, family as legal_family
from evaluation import evaluate
from ai_analysis import ai_available, analyze
from store import init_db, all_reports, create_report, update_status, track, connect

ROOT = Path(__file__).resolve().parent
WEB = ROOT / 'web'
MAX_BODY = 1600000


class APIError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


def validate_report(data):
    if data.get('consent') is not True:
        raise ValueError('Confirma que usas datos ficticios en esta demostración.')
    subtype = data.get('subtype')
    if subtype not in TYPES:
        raise ValueError('Selecciona una modalidad de robo válida.')
    description = data.get('description', '')
    if not isinstance(description, str) or not 12 <= len(description.strip()) <= 1200:
        raise ValueError('La descripción debe contener entre 12 y 1200 caracteres.')
    try:
        lat, lng = float(data['lat']), float(data['lng'])
    except (TypeError, KeyError, ValueError):
        raise ValueError('Selecciona una ubicación en el mapa.')
    if not math.isfinite(lat) or not math.isfinite(lng) or not (BOUNDS['south'] <= lat <= BOUNDS['north'] and BOUNDS['west'] <= lng <= BOUNDS['east']):
        raise ValueError('La ubicación debe estar dentro del área ilustrativa de El Pedregal, Majes.')
    try:
        event = parse_time(data['occurred_at'])
    except (ValueError, TypeError, KeyError, OverflowError):
        raise ValueError('Indica una fecha y hora válidas.')
    age = (now() - event).total_seconds()
    if age < -120 or age > 366 * 86400:
        raise ValueError('Usa una fecha pasada de los últimos 366 días.')
    if type(data.get('weapon')) is not bool or type(data.get('injury')) is not bool:
        raise ValueError('Los indicadores de arma y lesión deben ser sí o no.')
    value = data.get('value_band', 'sin_dato')
    if value not in VALUE_BANDS:
        raise ValueError('Selecciona un rango válido para el valor de lo sustraído.')
    force = data.get('force', 'sin_dato')
    if force not in FORCES:
        raise ValueError('Indica si hubo violencia o amenaza.')
    facts = data.get('circumstances', [])
    # 'organizacion' solo lo determina la revisión, no quien reporta.
    if not isinstance(facts, list) or any(f not in CIRCUMSTANCES or f == 'organizacion' for f in facts):
        raise ValueError('Circunstancias no válidas.')
    evidence = None
    if data.get('photo'):
        try:
            evidence = base64.b64decode(data['photo'], validate=True)
        except (ValueError, TypeError):
            raise ValueError('La imagen no es válida.')
        if len(evidence) > 1000000 or not evidence.startswith(b'\xff\xd8\xff') or not evidence.endswith(b'\xff\xd9'):
            raise ValueError('Solo se admite una imagen JPEG de hasta 1 MB.')
    return dict(subtype=subtype, description=description.strip(), occurred_at=event.isoformat(),
                lat=lat, lng=lng, weapon=data['weapon'], injury=data['injury'], value_band=value,
                force=force, circumstances=sorted(set(facts)), evidence=evidence)


class AppServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, db, password):
        self.db, self.password, self.sessions, self.attempts = db, password, {}, {}
        self.maps_key = os.environ.get('GOOGLE_MAPS_API_KEY', '')
        maps_file = ROOT / 'maps.local.json'
        if maps_file.exists():
            try:
                self.maps_key = self.maps_key or str(json.loads(maps_file.read_text(encoding='utf-8-sig')).get('google_maps_api_key',''))
            except (ValueError, OSError):
                print('No se pudo leer maps.local.json. Se usará OpenStreetMap.')
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    server_version = 'MajesDemo/2.0'

    def log_message(self, fmt, *args):
        # No registrar cuerpos ni códigos privados de seguimiento.
        print('%s %s' % (self.command, self.path.split('?')[0]))

    def respond(self, status, body, kind='application/json; charset=utf-8', headers=None):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False).encode('utf-8')
        elif isinstance(body, str):
            body = body.encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'strict-origin-when-cross-origin')
        self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' https://maps.googleapis.com https://maps.gstatic.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: https://tile.openstreetmap.org https://*.googleapis.com https://*.gstatic.com https://*.google.com; connect-src 'self' https://*.googleapis.com https://*.gstatic.com https://*.google.com; worker-src 'self' blob:; frame-src https://*.google.com; frame-ancestors 'self'; base-uri 'self'; form-action 'self'")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def body(self):
        if self.headers.get('Sec-Fetch-Site') == 'cross-site':
            raise APIError(403, 'Solicitud de otro sitio no permitida.')
        if self.headers.get('Content-Type', '').split(';')[0] != 'application/json':
            raise APIError(415, 'Se requiere application/json.')
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= MAX_BODY:
                raise APIError(413, 'El contenido excede el tamaño permitido.')
            value = json.loads(self.rfile.read(length))
            if not isinstance(value, dict):
                raise ValueError()
            return value
        except (ValueError, UnicodeDecodeError):
            raise APIError(400, 'JSON no válido.')

    def session(self):
        jar = cookies.SimpleCookie()
        try:
            jar.load(self.headers.get('Cookie', ''))
            token = jar['majes_session'].value
        except (KeyError, cookies.CookieError):
            return None
        if self.server.sessions.get(token, 0) > time.time():
            return token
        return None

    def require_admin(self):
        if not self.session():
            raise APIError(401, 'Ingresa al acceso de demostración para revisar reportes.')

    def filters(self, query):
        try:
            days = int(query.get('days', ['30'])[0])
        except ValueError:
            raise APIError(400, 'Periodo no válido.')
        subtype = query.get('subtype', ['all'])[0]
        band = query.get('band', ['all'])[0]
        if days not in {7, 30, 90} or subtype not in {'all', *TYPES} or band not in {'all', '0', '1', '2', '3'}:
            raise APIError(400, 'Filtro no válido.')
        return days, subtype, band

    def family_filter(self, query):
        value = query.get('family', ['all'])[0]
        if value not in {'all', *FAMILIES}:
            raise APIError(400, 'Filtro no válido.')
        return value

    def reports(self, query):
        """Reportes de la base, filtrados por familia (hurtos/robos) si se pidió."""
        rows = all_reports(self.server.db)
        fam = self.family_filter(query)
        return rows if fam == 'all' else [r for r in rows if legal_family(r) == fam]

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        try:
            url = urlparse(self.path)
            path, query = url.path, parse_qs(url.query)
            if path == '/api/config':
                return self.respond(200, {'bounds': BOUNDS, 'center': CENTER, 'types': TYPES, 'statuses': STATUSES, 'value_bands': VALUE_BANDS,
                                          'legal_classes': LEGAL_CLASSES, 'families': FAMILIES, 'forces': FORCES,
                                          'circumstances': CIRCUMSTANCES, 'ai_available': ai_available(), 'now': now().isoformat(), 'demo': True, 'google_maps_api_key': self.server.maps_key, 'map_provider': 'google' if self.server.maps_key else 'local', 'version': '2.0'})
            if path == '/api/health':
                return self.respond(200, {'ok': True, 'mode': 'local-demo'})
            if path in {'/api/stats', '/api/export.csv'}:
                result = enhanced_statistics(self.reports(query), *self.filters(query))
                if path.endswith('.csv'):
                    stream = io.StringIO(newline='')
                    writer = csv.writer(stream)
                    writer.writerow(['celda_ilustrativa', 'latitud_centro', 'longitud_centro', 'reportes_validados_ficticios', 'indice_dano', 'periodo_dias'])
                    for cell in result['cells']:
                        writer.writerow([cell['name'], round(cell['lat'], 4), round(cell['lng'], 4), cell['count'], cell['harm'], result['days']])
                    return self.respond(200, '\ufeff' + stream.getvalue(), 'text/csv; charset=utf-8', {'Content-Disposition': 'attachment; filename="majes_datos_agregados_demo.csv"'})
                return self.respond(200, result)
            if path == '/api/evaluate':
                try:
                    params = [int(query.get(k, [v])[0]) for k,v in [('size','300'),('eps','250'),('min_samples','4'),('seed','42')]]
                    result = evaluate(*params)
                except (ValueError, TypeError):
                    raise APIError(400, 'Usa 100, 300, 600 o 1000 puntos; radio de 50 a 1000 m y mínimo de 3 a 30.')
                return self.respond(200, result)
            if path.startswith('/api/admin/'):
                self.require_admin()
                if path == '/api/admin/metrics':
                    with connect(self.server.db) as con:
                        histories = [dict(h) for h in con.execute('SELECT report_id,status,timestamp FROM history')]
                        evidence = {r[0] for r in con.execute('SELECT report_id FROM evidence')}
                    result = management_metrics(self.reports(query), histories, evidence, *self.filters(query))
                    return self.respond(200, result)
                if path == '/api/admin/session':
                    return self.respond(200, {'authenticated': True})
                if path == '/api/admin/reports':
                    rows = all_reports(self.server.db)
                    with connect(self.server.db) as con:
                        photo_ids = {row[0] for row in con.execute('SELECT report_id FROM evidence')}
                    reports = [{**{k: v for k, v in r.items() if k != 'tracking'},
                                'priority': priority(r, rows), 'candidates': duplicate_candidates(r, rows),
                                'harm': harm(r), 'critical': critical_reasons(r), 'legal': classify(r),
                                'has_evidence': r['id'] in photo_ids} for r in rows]
                    return self.respond(200, {'reports': reports})
                if path.startswith('/api/admin/evidence/'):
                    rid = unquote(path.rsplit('/', 1)[-1])
                    with connect(self.server.db) as con:
                        photo = con.execute('SELECT content FROM evidence WHERE report_id=?', (rid,)).fetchone()
                    if photo:
                        return self.respond(200, photo[0], 'image/jpeg')
                    raise APIError(404, 'No hay imagen para este reporte.')
            if path.startswith('/api/'):
                raise APIError(404, 'Ruta no encontrada.')
            routes = {'/', '/reportar', '/panel', '/gestion', '/seguimiento', '/gestion-datos', '/analisis', '/laboratorio', '/metodologia'}
            file = WEB / ('index.html' if path in routes else unquote(path).lstrip('/'))
            file = file.resolve()
            if WEB not in file.parents or not file.is_file():
                raise APIError(404, 'Archivo no encontrado.')
            kind = {'.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml'}.get(file.suffix) or mimetypes.guess_type(str(file))[0] or 'application/octet-stream'
            return self.respond(200, file.read_bytes(), kind)
        except APIError as e:
            self.respond(e.status, {'error': e.message})
        except (ValueError, TypeError, OverflowError):
            self.respond(400, {'error': 'Solicitud no válida.'})

    def do_POST(self):
        try:
            path = urlparse(self.path).path
            data = self.body()
            if path == '/api/reports':
                report = create_report(self.server.db, validate_report(data))
                return self.respond(201, {'id': report['id'], 'tracking': report['tracking'], 'status': report['status']})
            if path == '/api/track':
                code = data.get('code', '')
                if not isinstance(code, str) or len(code) != 24:
                    raise APIError(404, 'Código no encontrado. Revisa los 24 caracteres.')
                found = track(self.server.db, code.strip().upper())
                if not found:
                    raise APIError(404, 'Código no encontrado.')
                return self.respond(200, found)
            if path == '/api/login':
                ip = self.client_address[0]
                recent = [t for t in self.server.attempts.get(ip, []) if t > time.time() - 60]
                self.server.attempts[ip] = recent
                if len(recent) >= 10:
                    raise APIError(429, 'Demasiados intentos. Espera un minuto.')
                password = data.get('password', '')
                if not isinstance(password, str) or not hmac.compare_digest(password.encode(), self.server.password.encode()):
                    recent.append(time.time())
                    raise APIError(401, 'Contraseña de demostración incorrecta.')
                token = secrets.token_urlsafe(32)
                self.server.sessions[token] = time.time() + 8 * 3600
                secure = '; Secure' if self.headers.get('X-Forwarded-Proto') == 'https' else ''
                return self.respond(200, {'ok': True}, headers={'Set-Cookie': 'majes_session=' + token + '; HttpOnly; SameSite=Strict; Path=/; Max-Age=28800' + secure})
            if path == '/api/logout':
                token = self.session()
                if token:
                    self.server.sessions.pop(token, None)
                return self.respond(200, {'ok': True}, headers={'Set-Cookie': 'majes_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0'})
            if path == '/api/admin/ai-analysis':
                self.require_admin()
                query = {k: [str(data[k])] for k in ('days', 'subtype', 'band', 'family') if k in data}
                days, subtype, band = self.filters(query)
                fam = self.family_filter(query)
                result = analyze(self.reports(query), days, subtype, band, fam, refresh=data.get('refresh') is True)
                return self.respond(200, result)
            if path == '/api/admin/status':
                self.require_admin()
                rid, status, note = data.get('id'), data.get('status'), data.get('note', '')
                legal_class = data.get('legal_class') or None
                if legal_class is not None and legal_class not in LEGAL_CLASSES:
                    raise ValueError('Figura legal no válida.')
                original = data.get('duplicate_of') if status == 'duplicado' else None
                if not isinstance(rid, str) or status not in STATUSES:
                    raise ValueError('Reporte o estado no válido.')
                if not isinstance(note, str) or not 6 <= len(note.strip()) <= 400:
                    raise ValueError('Escribe una nota de revisión de 6 a 400 caracteres.')
                if status == 'duplicado' and not isinstance(original, str):
                    raise ValueError('Indica el identificador del reporte original.')
                update_status(self.server.db, rid, status, note.strip(), original, legal_class)
                return self.respond(200, {'ok': True})
            raise APIError(404, 'Ruta no encontrada.')
        except APIError as e:
            self.respond(e.status, {'error': e.message})
        except (ValueError, TypeError, OverflowError) as e:
            self.respond(400, {'error': str(e) or 'Solicitud no válida.'})


def main():
    parser = argparse.ArgumentParser(description='Majes Alerta - demo local')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=int(os.environ.get('PORT', '8000')))
    parser.add_argument('--db', default=str(ROOT / 'data' / 'demo.sqlite3'))
    parser.add_argument('--empty', action='store_true', help='Crear la base sin reportes sintéticos (solo en una base nueva).')
    parser.add_argument('--strictPort', action='store_true', help=argparse.SUPPRESS)
    args = parser.parse_args()
    init_db(args.db, seed=not args.empty)
    server = AppServer((args.host, args.port), args.db, os.environ.get('DEMO_ADMIN_PASSWORD', 'MajesDemo2026!'))
    print(f'Majes Alerta listo en http://{args.host}:{args.port}', flush=True)
    print('Demo universitario con datos ficticios. Ctrl+C para salir.', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\nServidor detenido.')
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
