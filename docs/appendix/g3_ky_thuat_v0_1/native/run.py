"""Native PostgreSQL 17 test runner. Unified candidate runner, NOT executed in the authoring environment.
Only connects to this kit's loopback disposable database. Not a runtime app role.
"""
from pathlib import Path
from datetime import datetime,timezone
import os,json,hashlib,uuid,time,threading
try:
 import psycopg
except ImportError:
 psycopg=None
ROOT=Path(__file__).resolve().parents[1]

def tid(s):return str(uuid.UUID(hashlib.md5(s.encode()).hexdigest()))
def connect():
 if psycopg is None:raise RuntimeError('Install native/requirements.txt before the native run')
 password=(ROOT/'secrets/review_password.txt').read_text().strip()
 conn=psycopg.connect(host='127.0.0.1',port=int(os.environ.get('TB_REVIEW_PORT','55434')),dbname='tb_g3_unified',user='tb_review',password=password,connect_timeout=5,autocommit=True)
 conn.execute('SET search_path=tutorbridge,public')
 return conn
def sql(conn,code):
 with conn.cursor() as cur:cur.execute(code,prepare=False)
def restore(conn):
 sql(conn,"UPDATE tutorbridge.resource_bookings b SET during=s.during FROM tutorbridge.lesson_sessions s WHERE b.session_id=s.id;")
def race_verdict(saw_block,outcome,expected_code):
 if not saw_block:return 'FAIL'
 if expected_code is None:return 'PASS' if outcome.get('outcome')=='COMMITTED' else 'FAIL'
 return 'PASS' if outcome.get('outcome')=='ROLLED_BACK' and outcome.get('sqlstate')==expected_code else 'FAIL'

def lock_race(primary_sql,second_sql,expected_code):
 a,b,observer=connect(),connect(),connect();actual={};start=threading.Event()
 try:
  sql(a,"BEGIN; SET LOCAL lock_timeout='8s'; SET LOCAL statement_timeout='10s';")
  sql(a,primary_sql)
  pid_a=a.info.backend_pid;pid_b=b.info.backend_pid
  def other():
   try:
    sql(b,"BEGIN; SET LOCAL lock_timeout='8s'; SET LOCAL statement_timeout='10s';")
    start.set();sql(b,second_sql);sql(b,'COMMIT;');actual.update(sqlstate=None,outcome='COMMITTED')
   except Exception as e:
    actual.update(sqlstate=getattr(e,'sqlstate',None),errorClass=type(e).__name__,outcome='ERROR')
    try:
     sql(b,'ROLLBACK;');actual['outcome']='ROLLED_BACK'
    except Exception as rollback_error:actual['rollbackErrorClass']=type(rollback_error).__name__
  t=threading.Thread(target=other,daemon=True);t.start();start.wait(2)
  saw_block=False;deadline=time.monotonic()+5
  while time.monotonic()<deadline:
   row=observer.execute('SELECT %s=ANY(pg_blocking_pids(%s))',(pid_a,pid_b)).fetchone()
   if row and row[0]:saw_block=True;break
   if not t.is_alive():break
   time.sleep(.05)
  sql(a,'COMMIT;');t.join(12)
  if t.is_alive():raise RuntimeError('Second database session did not finish')
  return {'status':race_verdict(saw_block,actual,expected_code),'actualSqlstate':actual.get('sqlstate'),'expectedSqlstate':expected_code,'outcome':actual.get('outcome','MISSING'),'lockWaitObserved':saw_block,'sessionPids':[pid_a,pid_b]}
 finally:
  for con in [a,b,observer]:
   try:sql(con,'ROLLBACK;')
   except Exception:pass
   con.close()

def main():
 result={'executedAt':datetime.now(timezone.utc).isoformat(),'engine':'Native PostgreSQL','cases':[],'concurrency':[],'limitations':['Direct DB test sessions only; no Spring Boot replicas or HTTP ACL tests','Not load, security, Android or restore validation']}
 with connect() as conn:
  identity=conn.execute('SELECT current_database(),version(),current_setting(\'server_version_num\')').fetchone()
  if identity[0]!='tb_g3_unified' or int(identity[2])//10000!=17:raise RuntimeError('Requires this kit disposable database on PostgreSQL major 17')
  result['runtime']={'version':identity[1],'version_num':identity[2]}
  if conn.execute("SELECT to_regnamespace('tutorbridge')").fetchone()[0] is not None:raise RuntimeError('Database must be fresh; existing schema is preserved. Use a fresh test container.')
  for rel in sorted((ROOT/'sql').glob('*.sql')):sql(conn,rel.read_text())
  sql(conn,'BEGIN;');sql(conn,(ROOT/'tests/baseline_fixture.sql').read_text())
  sql(conn,(ROOT/'tests/unified_fixture.sql').read_text())
  all_cases=json.loads((ROOT/'tests/baseline_cases.json').read_text())+json.loads((ROOT/'tests/unified_cases.json').read_text())
  for c in all_cases:
   sql(conn,'SAVEPOINT review_case;');stage='setup';got=None;status='FAIL'
   try:
    if c['setup']:sql(conn,c['setup'])
    stage='action';sql(conn,c['sql']);status='PASS' if c['expectedSqlstate'] is None else 'FAIL'
   except Exception as e:
    got=getattr(e,'sqlstate',None);status='PASS' if stage=='action' and got==c['expectedSqlstate'] else 'FAIL'
   finally:sql(conn,'ROLLBACK TO SAVEPOINT review_case; RELEASE SAVEPOINT review_case;')
   result['cases'].append({'id':c['id'],'status':status,'actualSqlstate':got,'expectedSqlstate':c['expectedSqlstate']})
  sql(conn,'COMMIT;')
  # Privileged component tests deliberately update bookings directly. This is forbidden for the future app role.
  for kind in ['TUTOR','STUDENT']:
   template="UPDATE tutorbridge.resource_bookings SET during=tstzrange('2040-01-07T11:00Z','2040-01-07T12:30Z','[)') WHERE session_id='{}'::uuid AND resource_kind='{}';"
   r=lock_race(template.format(tid('lesson1'),kind),template.format(tid('lesson2'),kind),'23P01');r['id']='N01_TUTOR' if kind=='TUTOR' else 'N02_STUDENT';result['concurrency'].append(r);restore(conn)
  # Two adjacent ranges should both commit; no lock-wait assertion is appropriate here.
  with connect() as a,connect() as b:
   a.execute("UPDATE tutorbridge.resource_bookings SET during=tstzrange('2040-01-07T11:00Z','2040-01-07T12:30Z','[)') WHERE session_id=%s AND resource_kind='TUTOR'",(tid('lesson1'),))
   b.execute("UPDATE tutorbridge.resource_bookings SET during=tstzrange('2040-01-07T12:30Z','2040-01-07T14:00Z','[)') WHERE session_id=%s AND resource_kind='TUTOR'",(tid('lesson2'),))
   boundary_pids=[a.info.backend_pid,b.info.backend_pid]
  result['concurrency'].append({'id':'N03_ADJACENT','status':'PASS','sessionPids':boundary_pids,'method':'Two separate DB sessions, sequential commits; boundary check only'});restore(conn)
  sql(conn,"INSERT INTO tutorbridge.plan_dependencies VALUES('"+tid('pv')+"','"+tid('mv1')+"','"+tid('class')+"',1); UPDATE tutorbridge.plan_versions SET status='APPROVED',warnings_resolved=true,approved_by='"+tid('tutor')+"',approved_at=now() WHERE id='"+tid('pv')+"';")
  revoke="UPDATE tutorbridge.material_versions SET state='REVOKED',access_epoch=access_epoch+1 WHERE id='"+tid('mv1')+"';"
  publish="INSERT INTO tutorbridge.plan_publications(plan_id,class_id,plan_version_id,published_by,published_at) VALUES('"+tid('plan')+"','"+tid('class')+"','"+tid('pv')+"','"+tid('tutor')+"',now());"
  r=lock_race(revoke,publish,'23514');r['id']='N04_REVOKE_PUBLISH';result['concurrency'].append(r)
  # N05 requires two connections and a real observed row-lock wait.
  sql(conn,"INSERT INTO tutorbridge.auth_challenges(id,account_id,purpose,token_digest,expires_at) VALUES(md5('native-token')::uuid,md5('parent')::uuid,'RESET_PASSWORD',repeat('c',64),now()+interval '15 minutes');")
  primary="SELECT tutorbridge.consume_auth_challenge(repeat('c',64),'RESET_PASSWORD');"
  secondary="DO $$ BEGIN IF tutorbridge.consume_auth_challenge(repeat('c',64),'RESET_PASSWORD') IS NOT NULL THEN RAISE EXCEPTION 'replayed token returned an account'; END IF; END $$;"
  r=lock_race(primary,secondary,None);r['id']='N05_ONE_TIME_TOKEN';result['concurrency'].append(r)
 result['counts']={'passed':sum(x['status']=='PASS' for x in result['cases']+result['concurrency']),'total':len(result['cases'])+len(result['concurrency'])}
 (ROOT/'evidence/native_results.json').write_text(json.dumps(result,indent=2))
 print(json.dumps(result,indent=2))
 if result['counts']['passed']!=result['counts']['total']:raise SystemExit(1)
if __name__=='__main__':main()
