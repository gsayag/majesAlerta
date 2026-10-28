import http.client
import json
import tempfile
import threading
import unittest
from datetime import datetime, timedelta
from pathlib import Path

from analytics import LIMA, CENTER, dbscan, distance, duplicate_candidates, priority, statistics, now
from server import AppServer, validate_report
from store import init_db, all_reports, create_report, update_status


def record(rid, lat=-16.357, lng=-72.187, status='validado', event=None, **more):
    result = dict(id=rid,lat=lat,lng=lng,subtype='persona',status=status,
                  occurred_at=(event or now()).isoformat(),weapon=False,injury=False,
                  description='Caso ficticio para pruebas.',created_at=now().isoformat())
    result.update(more)
    return result


class AnalyticsTests(unittest.TestCase):
    def test_haversine_in_meters(self):
        a=record('a',lat=0,lng=0);b=record('b',lat=0,lng=.001)
        self.assertAlmostEqual(distance(a,b),111.195,delta=.1)
        self.assertEqual(distance(a,a),0)

    def test_dbscan_cluster_and_noise(self):
        points=[record(str(i),lat=-16.357+i*.0001) for i in range(4)]
        points.append(record('far',lat=-16.375,lng=-72.205))
        labels=dbscan(points)
        self.assertEqual(len(set(labels[:4])),1)
        self.assertNotEqual(labels[0],-1)
        self.assertEqual(labels[-1],-1)
        self.assertEqual(dbscan([]),[])

    def test_public_stats_only_reviewed_and_suppression(self):
        rows=[record(str(i)) for i in range(3)]+[record('pending',status='recibido'),record('duplicate',status='duplicado'),record('small',lat=-16.371,lng=-72.173)]
        stats=statistics(rows)
        self.assertEqual(stats['total'],4)
        self.assertEqual(stats['visible_reports'],3)
        self.assertEqual(stats['suppressed_reports'],1)
        self.assertNotIn('description',json.dumps(stats))
        self.assertNotIn('pending',json.dumps(stats))
        self.assertNotAlmostEqual(stats['cells'][0]['lat'],rows[0]['lat'],places=5)

    def test_filters_and_previous_period(self):
        clock=datetime(2026,10,24,19,0,tzinfo=LIMA)
        rows=[record('a',event=clock),record('b',event=clock-timedelta(days=7)),record('c',event=clock,subtype='negocio')]
        s=statistics(rows,7,'persona','3',clock)
        self.assertEqual(s['total'],1);self.assertEqual(s['previous_total'],1)
        self.assertEqual(s['change_pct'],0);self.assertEqual(len(s['timeline']),7)
        self.assertEqual(sum(d['count'] for d in s['timeline']),1)
        self.assertEqual(sum(s['hours']),1)

    def test_priority_explanation_matches_score(self):
        r=record('new',weapon=True,injury=True,value_band='muy_alto')
        peers=[record(str(i),event=now()-timedelta(hours=1)) for i in range(10)]
        p=priority(r,peers+[r])
        self.assertEqual(p['score'],100)
        self.assertEqual(sum(x['points'] for x in p['parts']),p['score'])
        self.assertEqual(p['level'],'alta')

    def test_duplicate_rule_needs_space_time_and_subtype(self):
        r=record('a')
        rows=[record('b'),record('c',subtype='negocio'),record('d',event=now()-timedelta(hours=1)),record('e',lat=-16.37)]
        self.assertEqual(duplicate_candidates(r,rows),['b'])

    def test_empty_stats(self):
        s=statistics([])
        self.assertEqual(s['total'],0);self.assertIsNone(s['peak_band']);self.assertIsNone(s['change_pct'])


class APITests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory()
        cls.db=str(Path(cls.tmp.name)/'test.sqlite3')
        init_db(cls.db,seed=False)
        cls.server=AppServer(('127.0.0.1',0),cls.db,'test-password')
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.port=cls.server.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join();cls.tmp.cleanup()

    def request(self,path,data=None,cookie=None,headers=None):
        con=http.client.HTTPConnection('127.0.0.1',self.port,timeout=5)
        h={'Content-Type':'application/json',**(headers or {})}
        if cookie:h['Cookie']=cookie
        con.request('POST' if data is not None else 'GET',path,body=json.dumps(data) if data is not None else None,headers=h)
        res=con.getresponse();body=res.read();rh=dict(res.getheaders());code=res.status;con.close()
        if 'application/json' in rh.get('Content-Type',''):body=json.loads(body)
        return code,body,rh

    def payload(self):
        return dict(subtype='persona',occurred_at=now().isoformat(),description='Caso ficticio de un robo para la prueba.',lat=CENTER['lat'],lng=CENTER['lng'],weapon=False,injury=False,consent=True)

    def login(self):
        status,_,headers=self.request('/api/login',{'password':'test-password'})
        self.assertEqual(status,200)
        self.assertIn('HttpOnly',headers['Set-Cookie']);self.assertIn('SameSite=Strict',headers['Set-Cookie'])
        return headers['Set-Cookie'].split(';')[0]

    def test_end_to_end_report_review_track_and_public_stats(self):
        before=self.request('/api/stats')[1]['total']
        status,created,_=self.request('/api/reports',self.payload())
        self.assertEqual(status,201);self.assertEqual(len(created['tracking']),24)
        self.assertEqual(self.request('/api/stats')[1]['total'],before)
        cookie=self.login()
        status,_,_=self.request('/api/admin/status',{'id':created['id'],'status':'validado','note':'Datos revisados en la prueba.'},cookie)
        self.assertEqual(status,200)
        self.assertEqual(self.request('/api/stats')[1]['total'],before+1)
        code,tracked,_=self.request('/api/track',{'code':created['tracking']})
        self.assertEqual(code,200);self.assertEqual(tracked['status'],'validado')
        self.assertEqual([h['status'] for h in tracked['history']],['recibido','validado'])
        self.assertNotIn('description',tracked);self.assertNotIn('lat',tracked)
        self.assertEqual(self.request('/api/stats?days=invalid')[0],400)

    def test_admin_requires_authentication(self):
        self.assertEqual(self.request('/api/admin/reports')[0],401)
        self.assertEqual(self.request('/api/admin/status',{'id':'anything','status':'validado','note':'test note'})[0],401)
        self.assertEqual(self.request('/api/login',{'password':'wrong'})[0],401)
        self.assertEqual(self.request('/api/admin/evidence/anything')[0],401)

    def test_reject_invalid_inputs(self):
        for key,value in [('subtype','hurto'),('lat',float('nan')),('lng',0),('description','corto'),('consent',False),('weapon','yes'),('occurred_at',(now()+timedelta(days=1)).isoformat()),('photo','invalid')]:
            data=self.payload();data[key]=value
            with self.subTest(key=key):self.assertEqual(self.request('/api/reports',data)[0],400)
        self.assertEqual(self.request('/api/reports',self.payload(),headers={'Sec-Fetch-Site':'cross-site'})[0],403)

    def test_tracking_is_not_sequential_id(self):
        created=self.request('/api/reports',self.payload())[1]
        self.assertEqual(self.request('/api/track',{'code':created['id']})[0],404)

    def test_csv_aggregates_and_route_boundaries(self):
        code,body,_=self.request('/api/export.csv')
        self.assertEqual(code,200);self.assertIn('celda_ilustrativa',body.decode('utf-8-sig'))
        for url in ['/server.py','/data/demo.sqlite3','/../server.py','/%2e%2e/server.py','/api/reports']:
            with self.subTest(url=url):self.assertEqual(self.request(url)[0],404)

    def test_image_storage_is_private(self):
        import base64
        # A minimal JPEG envelope is sufficient to exercise storage and authorization.
        # Browsers re-encode actual images before sending; this is not an image decoder test.
        image=b'\xff\xd8\xff\xe0test\xff\xd9'
        data=self.payload();data['photo']=base64.b64encode(image).decode()
        created=self.request('/api/reports',data)[1]
        url='/api/admin/evidence/'+created['id']
        self.assertEqual(self.request(url)[0],401)
        code,body,_=self.request(url,cookie=self.login())
        self.assertEqual(code,200);self.assertEqual(body,image)

    def test_logout_revokes_session(self):
        cookie=self.login()
        self.assertEqual(self.request('/api/admin/session',cookie=cookie)[0],200)
        self.request('/api/logout',{},cookie)
        self.assertEqual(self.request('/api/admin/session',cookie=cookie)[0],401)

    def test_new_private_metrics_require_session(self):
        self.assertEqual(self.request('/api/admin/metrics')[0],401)
        status,data,_=self.request('/api/admin/metrics',cookie=self.login())
        self.assertEqual(status,200)
        self.assertIn('quality',data)
        self.assertNotIn('description',json.dumps(data))

    def test_laboratory_is_controlled_and_does_not_change_database(self):
        before=len(all_reports(self.db))
        status,data,_=self.request('/api/evaluate?size=100&eps=250&min_samples=4&seed=42')
        self.assertEqual(status,200);self.assertEqual(len(data['points']),100)
        self.assertEqual(len(all_reports(self.db)),before)
        self.assertEqual(self.request('/api/evaluate?size=100000')[0],400)
        self.assertEqual(self.request('/api/evaluate?eps=nan')[0],400)



class PersistenceTests(unittest.TestCase):
    def test_reopen_preserves_data_and_audit(self):
        with tempfile.TemporaryDirectory() as tmp:
            db=str(Path(tmp)/'data.sqlite3');init_db(db,seed=False)
            report=create_report(db,{**record('x'),'evidence':None})
            update_status(db,report['id'],'validado','Validación de prueba.')
            init_db(db,seed=True)
            loaded=all_reports(db)
            self.assertEqual(len(loaded),1);self.assertEqual(loaded[0]['status'],'validado')

    def test_seed_is_one_time_and_only_demo(self):
        with tempfile.TemporaryDirectory() as tmp:
            db=str(Path(tmp)/'seed.sqlite3');init_db(db);init_db(db)
            rows=all_reports(db)
            self.assertEqual(len(rows),180)
            self.assertTrue(all(r['synthetic']==1 and r['subtype'] in {'persona','negocio','vivienda','vehiculo'} for r in rows))


if __name__=='__main__':
    unittest.main()
