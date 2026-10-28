"""SQLite local y datos sintéticos reproducibles para la demostración."""
import json
import random
import secrets
import sqlite3
from contextlib import contextmanager
from datetime import timedelta
from pathlib import Path
from analytics import now, TYPES

COLUMNS = ('id', 'tracking', 'subtype', 'occurred_at', 'created_at', 'lat', 'lng', 'description',
           'weapon', 'injury', 'status', 'synthetic', 'duplicate_of', 'value_band',
           'force', 'circumstances', 'legal_class')
# Columnas agregadas después de la primera versión: se crean al abrir bases antiguas.
MIGRATIONS = {'value_band': "TEXT NOT NULL DEFAULT 'sin_dato'", 'force': "TEXT NOT NULL DEFAULT 'sin_dato'",
              'circumstances': "TEXT NOT NULL DEFAULT '[]'", 'legal_class': 'TEXT'}
INSERT = f"INSERT INTO reports ({','.join(COLUMNS)}) VALUES ({','.join('?' * len(COLUMNS))})"

# Valor típico por modalidad para los datos sintéticos.
SEED_VALUES = {'persona': (['bajo', 'medio', 'alto'], [62, 30, 8]),
               'negocio': (['bajo', 'medio', 'alto', 'muy_alto'], [30, 40, 24, 6]),
               'vivienda': (['bajo', 'medio', 'alto', 'muy_alto'], [20, 40, 30, 10]),
               'vehiculo': (['alto', 'muy_alto'], [45, 55])}
SEED_FACTS = {'dos_o_mas': .30, 'casa_habitada': .50, 'destreza': .12, 'vehiculo_medio': .15, 'uso_momentaneo': .03}


@contextmanager
def connect(path):
    con = sqlite3.connect(path, timeout=10)
    con.row_factory = sqlite3.Row
    con.execute('PRAGMA foreign_keys=ON')
    try:
        with con:
            yield con
    finally:
        con.close()


def init_db(path, seed=True):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with connect(path) as con:
        con.executescript('''
          CREATE TABLE IF NOT EXISTS reports (
            id TEXT PRIMARY KEY, tracking TEXT UNIQUE NOT NULL, subtype TEXT NOT NULL,
            occurred_at TEXT NOT NULL, created_at TEXT NOT NULL, lat REAL NOT NULL,
            lng REAL NOT NULL, description TEXT NOT NULL, weapon INTEGER NOT NULL,
            injury INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'recibido',
            synthetic INTEGER NOT NULL DEFAULT 1, duplicate_of TEXT,
            FOREIGN KEY (duplicate_of) REFERENCES reports(id));
          CREATE TABLE IF NOT EXISTS history (
            id INTEGER PRIMARY KEY AUTOINCREMENT, report_id TEXT NOT NULL,
            status TEXT NOT NULL, timestamp TEXT NOT NULL, note TEXT NOT NULL,
            FOREIGN KEY (report_id) REFERENCES reports(id));
          CREATE INDEX IF NOT EXISTS reports_time ON reports(occurred_at);
          CREATE TABLE IF NOT EXISTS evidence (
            report_id TEXT PRIMARY KEY, content BLOB NOT NULL,
            FOREIGN KEY (report_id) REFERENCES reports(id));
        ''')
        existing = {c[1] for c in con.execute('PRAGMA table_info(reports)')}
        for column, definition in MIGRATIONS.items():
            if column not in existing:
                con.execute(f'ALTER TABLE reports ADD COLUMN {column} {definition}')
        count = con.execute('SELECT count(*) FROM reports').fetchone()[0]
        if count or not seed:
            return
        rng = random.Random(20261024)
        clock = now()
        centers = [(-16.3567, -72.1875), (-16.3490, -72.1800), (-16.3665, -72.1970), (-16.371, -72.174)]
        for i in range(180):
            lat, lng = rng.choices(centers, [45, 26, 20, 9])[0]
            lat += rng.gauss(0, .0010)
            lng += rng.gauss(0, .0011)
            day = rng.choices([rng.randrange(0, 30), rng.randrange(30, 84)], [68, 32])[0]
            hour = rng.choices([rng.randrange(0, 6), rng.randrange(6, 12), rng.randrange(12, 18), rng.randrange(18, 24)], [12, 14, 30, 44])[0]
            event = (clock - timedelta(days=day)).replace(hour=hour, minute=rng.randrange(60), second=0, microsecond=0)
            if event > clock:
                event = clock - timedelta(minutes=rng.randrange(10, 180))
            status = rng.choices(['validado', 'cerrado', 'recibido'], [60, 24, 16])[0]
            rid = f'MJ-DEMO-{i + 1:04d}'
            subtype = rng.choice(list(TYPES))
            weapon, injury = int(rng.random() < .28), int(rng.random() < .12)
            # Generador aparte para no alterar la secuencia original de los datos sintéticos.
            extra = random.Random(i)
            bands, weights = SEED_VALUES[subtype]
            value = extra.choices(bands, weights)[0]
            if injury:
                force = 'violencia'
            elif weapon:
                force = 'amenaza'
            else:
                force = extra.choices(['sin_violencia', 'amenaza', 'violencia'], [62, 23, 15])[0]
            facts = [k for k, p in SEED_FACTS.items() if extra.random() < p]
            if subtype != 'vivienda' and 'casa_habitada' in facts:
                facts.remove('casa_habitada')
            row = (rid, secrets.token_hex(12).upper(), subtype, event.isoformat(),
                   (event + timedelta(minutes=5)).isoformat(), lat, lng,
                   'Registro ficticio para evaluar visualización y análisis. No corresponde a un hecho real.',
                   weapon, injury, status, 1, None, value, force, json.dumps(facts), None)
            con.execute(INSERT, row)
            con.execute('INSERT INTO history(report_id,status,timestamp,note) VALUES (?,?,?,?)',
                        (rid, 'recibido', event.isoformat(), 'Registro sintético de demostración.'))
            if status != 'recibido':
                con.execute('INSERT INTO history(report_id,status,timestamp,note) VALUES (?,?,?,?)',
                            (rid, status, (event + timedelta(minutes=10)).isoformat(), 'Revisión simulada.'))


def all_reports(path):
    with connect(path) as con:
        return [dict(row) for row in con.execute('SELECT * FROM reports ORDER BY occurred_at DESC')]


def create_report(path, data):
    rid = 'MJ-' + secrets.token_hex(4).upper()
    tracking = secrets.token_hex(12).upper()
    created = now().isoformat()
    with connect(path) as con:
        con.execute(INSERT, (rid, tracking, data['subtype'], data['occurred_at'], created, data['lat'], data['lng'],
                             data['description'], int(data['weapon']), int(data['injury']), 'recibido', 1, None,
                             data.get('value_band', 'sin_dato'), data.get('force', 'sin_dato'),
                             json.dumps(data.get('circumstances', [])), None))
        con.execute('INSERT INTO history(report_id,status,timestamp,note) VALUES (?,?,?,?)',
                    (rid, 'recibido', created, 'Reporte recibido. Pendiente de revisión humana.'))
        if data.get('evidence'):
            con.execute('INSERT INTO evidence VALUES (?,?)', (rid, data['evidence']))
        return dict(con.execute('SELECT * FROM reports WHERE id=?', (rid,)).fetchone())


def update_status(path, rid, status, note, duplicate_of=None, legal_class=None):
    with connect(path) as con:
        current = con.execute('SELECT * FROM reports WHERE id=?', (rid,)).fetchone()
        if not current:
            raise ValueError('Reporte no encontrado.')
        if duplicate_of:
            target = con.execute('SELECT * FROM reports WHERE id=?', (duplicate_of,)).fetchone()
            if not target or target['id'] == rid or target['status'] in {'descartado', 'duplicado'}:
                raise ValueError('Selecciona un reporte original válido.')
        con.execute('UPDATE reports SET status=?,duplicate_of=?,legal_class=COALESCE(?,legal_class) WHERE id=?',
                    (status, duplicate_of, legal_class, rid))
        con.execute('INSERT INTO history(report_id,status,timestamp,note) VALUES (?,?,?,?)', (rid, status, now().isoformat(), note))


def track(path, code):
    with connect(path) as con:
        row = con.execute('SELECT id,status,created_at FROM reports WHERE tracking=?', (code,)).fetchone()
        if not row:
            return None
        events = [dict(e) for e in con.execute('SELECT status,timestamp FROM history WHERE report_id=? ORDER BY id', (row['id'],))]
        return {**dict(row), 'history': events}
