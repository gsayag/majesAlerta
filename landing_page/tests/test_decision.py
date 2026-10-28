import unittest
from datetime import timedelta

from analytics import now, harm, critical_reasons, priority, value_band
from insights import decision_alerts


def record(rid, subtype='persona', value='bajo', lat=-16.357, lng=-72.187, status='validado', **more):
    r = dict(id=rid, lat=lat, lng=lng, subtype=subtype, status=status, value_band=value,
             occurred_at=(now() - timedelta(hours=2)).isoformat(), created_at=now().isoformat(),
             weapon=False, injury=False, description='Caso ficticio para pruebas.')
    r.update(more)
    return r


class DecisionTests(unittest.TestCase):
    def test_teacher_example_car_alerts_but_two_glasses_do_not(self):
        # Zona A: dos robos de lentes. Zona B (lejos): un robo de auto.
        glasses = [record('l1'), record('l2')]
        car = record('auto', subtype='vehiculo', value='muy_alto', lat=-16.372, lng=-72.170)
        alerts = decision_alerts(glasses + [car], 7)
        self.assertEqual([a['id'] for a in alerts['individual']], ['auto'])
        self.assertEqual(len(alerts['zones']), 1)
        zone = alerts['zones'][0]
        self.assertEqual(zone['count'], 1)
        self.assertTrue(zone['only_by_harm'])
        self.assertEqual(zone['level'], 'media')

    def test_many_low_value_thefts_are_watched_not_high(self):
        rows = [record(f'l{i}') for i in range(3)]
        zone = decision_alerts(rows, 7)['zones'][0]
        self.assertEqual(zone['level'], 'vigilancia')
        self.assertEqual(zone['harm'], 3)

    def test_zone_ranking_uses_harm_not_count(self):
        low = [record(f'l{i}') for i in range(4)]
        severe = [record(f'v{i}', subtype='vehiculo', value='muy_alto', lat=-16.372, lng=-72.170) for i in range(2)]
        zones = decision_alerts(low + severe, 7)['zones']
        self.assertEqual(zones[0]['count'], 2)
        self.assertEqual(zones[0]['rank_by_count'], 2)
        self.assertEqual(zones[0]['rank_by_harm'], 1)

    def test_vehicle_without_value_is_assumed_high(self):
        car = record('c', subtype='vehiculo', value='sin_dato')
        self.assertEqual(value_band(car), 'alto')
        self.assertIn('Robo de vehículo', critical_reasons(car))

    def test_harm_parts_add_up(self):
        r = record('x', value='alto', weapon=True, injury=True)
        h = harm(r)
        self.assertEqual(h['score'], 1 + 3 + 3 + 4)
        self.assertEqual(sum(p['points'] for p in h['parts']), h['score'])

    def test_pending_reports_alert_but_do_not_count_in_zones(self):
        car = record('c', subtype='vehiculo', value='muy_alto', status='recibido')
        alerts = decision_alerts([car], 7)
        self.assertEqual(alerts['individual_pending'], 1)
        self.assertEqual(alerts['zones'], [])

    def test_discarded_reports_never_alert(self):
        car = record('c', subtype='vehiculo', value='muy_alto', status='descartado')
        self.assertEqual(decision_alerts([car], 7)['individual_total'], 0)

    def test_value_raises_priority(self):
        cheap, costly = record('a'), record('b', value='muy_alto')
        self.assertGreater(priority(costly, [cheap, costly])['score'], priority(cheap, [cheap, costly])['score'])


class MigrationTests(unittest.TestCase):
    def test_old_database_gets_value_column(self):
        import sqlite3, tempfile, os
        from store import init_db, all_reports, create_report
        db = os.path.join(tempfile.mkdtemp(), 'old.sqlite3')
        con = sqlite3.connect(db)
        con.execute('''CREATE TABLE reports (id TEXT PRIMARY KEY, tracking TEXT UNIQUE NOT NULL, subtype TEXT NOT NULL,
            occurred_at TEXT NOT NULL, created_at TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL,
            description TEXT NOT NULL, weapon INTEGER NOT NULL, injury INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'recibido', synthetic INTEGER NOT NULL DEFAULT 1, duplicate_of TEXT)''')
        con.execute("INSERT INTO reports VALUES ('A','T','persona','2026-10-01T10:00:00-05:00','2026-10-01T10:05:00-05:00',-16.357,-72.187,'Caso antiguo ficticio.',0,0,'validado',1,NULL)")
        con.commit(); con.close()
        init_db(db)
        self.assertEqual(all_reports(db)[0]['value_band'], 'sin_dato')
        new = create_report(db, dict(subtype='vehiculo', occurred_at=now().isoformat(), lat=-16.357, lng=-72.187,
                                     description='Caso ficticio nuevo.', weapon=False, injury=False, value_band='alto'))
        self.assertEqual(new['value_band'], 'alto')


if __name__ == '__main__':
    unittest.main()
