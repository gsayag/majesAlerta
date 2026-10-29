import http.client
import json
import tempfile
import threading
import unittest
from datetime import date, datetime, timedelta
from pathlib import Path

from analytics import LIMA
import actions
import ai_analysis
from server import AppServer
from store import init_db

TODAY = date(2026, 10, 28)
ZONE = 'Celda E4'  # celda de CENTER (-16.357, -72.187)
IN_ZONE = dict(lat=-16.357, lng=-72.187)
OUTSIDE = dict(lat=-16.372, lng=-72.170)


def report(rid, day, where=IN_ZONE, hour=20, status='validado'):
    t = datetime(day.year, day.month, day.day, hour, 0, tzinfo=LIMA)
    return dict(id=rid, subtype='persona', status=status, occurred_at=t.isoformat(), weapon=False, injury=False,
                value_band='bajo', force='sin_violencia', **where)


def action(start, end=None, band='all'):
    return dict(zone=ZONE, start_date=start.isoformat(), end_date=(end or start + timedelta(days=27)).isoformat(), band=band)


class ValidationTests(unittest.TestCase):
    def test_valid_action_defaults_to_four_weeks(self):
        a = actions.validate({'zone': ZONE, 'title': 'Patrullaje mixto', 'responsible': 'Serenazgo'}, TODAY)
        self.assertEqual(a['end_date'], (TODAY + timedelta(days=27)).isoformat())
        self.assertEqual(a['status'], 'en_curso')

    def test_future_action_is_planned(self):
        a = actions.validate({'zone': ZONE, 'title': 'Patrullaje mixto', 'responsible': 'Serenazgo',
                              'start_date': (TODAY + timedelta(days=3)).isoformat()}, TODAY)
        self.assertEqual(a['status'], 'planificada')

    def test_rejects_invalid_input(self):
        base = {'zone': ZONE, 'title': 'Patrullaje mixto', 'responsible': 'Serenazgo'}
        for bad in [{'zone': 'Celda Z99'}, {'zone': 'Barrio X'}, {'title': 'x'}, {'band': '7'},
                    {'start_date': '2026-10-20', 'end_date': '2026-10-01'}, {'start_date': 'ayer'}]:
            with self.assertRaises(ValueError, msg=bad):
                actions.validate({**base, **bad}, TODAY)


class EvaluationTests(unittest.TestCase):
    start = TODAY - timedelta(days=13)  # lleva 14 días

    def before(self, n, where=IN_ZONE, prefix='b'):
        return [report(f'{prefix}{i}', self.start - timedelta(days=1 + i % 13), where) for i in range(n)]

    def after(self, n, where=IN_ZONE, prefix='a'):
        return [report(f'{prefix}{i}', self.start + timedelta(days=i % 13), where) for i in range(n)]

    def test_drop_larger_than_rest_of_area_is_improvement(self):
        rows = self.before(8) + self.after(2) + self.before(10, OUTSIDE, 'rb') + self.after(10, OUTSIDE, 'ra')
        e = actions.evaluate(action(self.start), rows, TODAY)
        self.assertEqual((e['before'], e['after']), (8, 2))
        self.assertEqual(e['state'], 'mejora')

    def test_drop_matching_rest_of_area_is_not_attributed(self):
        rows = self.before(8) + self.after(4) + self.before(10, OUTSIDE, 'rb') + self.after(5, OUTSIDE, 'ra')
        self.assertEqual(actions.evaluate(action(self.start), rows, TODAY)['state'], 'similar')

    def test_increase_asks_to_review(self):
        rows = self.before(2) + self.after(6)
        self.assertEqual(actions.evaluate(action(self.start), rows, TODAY)['state'], 'empeora')

    def test_too_early_and_too_few(self):
        self.assertEqual(actions.evaluate(action(TODAY - timedelta(days=2)), self.before(9), TODAY)['state'], 'pronto')
        self.assertEqual(actions.evaluate(action(self.start), self.before(2) + self.after(1), TODAY)['state'], 'pocos_datos')
        self.assertEqual(actions.evaluate(action(TODAY + timedelta(days=1)), [], TODAY)['state'], 'sin_iniciar')

    def test_windows_have_equal_length(self):
        e = actions.evaluate(action(self.start), [], TODAY)
        self.assertEqual(e['days'], 14)
        self.assertEqual(e['window_before'], [(self.start - timedelta(days=14)).isoformat(), (self.start - timedelta(days=1)).isoformat()])

    def test_band_and_status_filters(self):
        rows = self.before(6) + [report('noche', self.start, hour=3), report('pend', self.start, status='recibido')]
        e = actions.evaluate(action(self.start, band='3'), rows, TODAY)
        self.assertEqual(e['after'], 0)  # 03:00 no es la franja 18-24 y el pendiente no cuenta


class ActionsAPITests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.db = str(Path(cls.tmp.name) / 'test.sqlite3')
        init_db(cls.db, seed=True)
        cls.server = AppServer(('127.0.0.1', 0), cls.db, 'test-password')
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown(); cls.server.server_close(); cls.thread.join(); cls.tmp.cleanup()

    def request(self, path, data=None, cookie=None):
        con = http.client.HTTPConnection('127.0.0.1', self.server.server_address[1], timeout=10)
        headers = {'Content-Type': 'application/json', **({'Cookie': cookie} if cookie else {})}
        con.request('POST' if data is not None else 'GET', path, body=json.dumps(data) if data is not None else None, headers=headers)
        res = con.getresponse(); code, body = res.status, json.loads(res.read()); con.close()
        return code, body

    def test_flow_requires_login_creates_updates_and_feeds_ai(self):
        self.assertEqual(self.request('/api/admin/actions')[0], 401)
        self.assertEqual(self.request('/api/admin/actions', {'zone': ZONE})[0], 401)
        con = http.client.HTTPConnection('127.0.0.1', self.server.server_address[1])
        con.request('POST', '/api/login', body=json.dumps({'password': 'test-password'}), headers={'Content-Type': 'application/json'})
        cookie = con.getresponse().getheader('Set-Cookie').split(';')[0]; con.close()

        code, created = self.request('/api/admin/actions', {'zone': ZONE, 'title': 'Patrullaje mixto nocturno',
                                                             'responsible': 'Serenazgo y PNP', 'band': '3', 'source': 'ia'}, cookie)
        self.assertEqual(code, 201)
        code, data = self.request('/api/admin/actions', cookie=cookie)
        self.assertEqual(code, 200)
        self.assertIn(ZONE, data['zones'])
        a = next(x for x in data['actions'] if x['id'] == created['id'])
        self.assertEqual(a['evaluation']['state'], 'pronto')

        self.assertEqual(self.request('/api/admin/actions/update', {'id': a['id'], 'status': 'volando'}, cookie)[0], 400)
        self.assertEqual(self.request('/api/admin/actions/update', {'id': a['id'], 'status': 'finalizada', 'notes': 'Hecho'}, cookie)[0], 200)

        saved, ai_analysis.anthropic = ai_analysis.anthropic, None  # los tests nunca gastan créditos de la API
        try:
            code, ai = self.request('/api/admin/ai-analysis', {'days': 30, 'refresh': True}, cookie)
        finally:
            ai_analysis.anthropic = saved
        self.assertEqual(code, 200)
        self.assertEqual(ai['context']['acciones_registradas'][0]['zona'], ZONE)


if __name__ == '__main__':
    unittest.main()
