"""Validate completeness of native component evidence; never approve all of G3."""
from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parents[1]
def evaluate(result):
 expected_sql={x['id'] for name in ['baseline_cases.json','unified_cases.json'] for x in json.loads((ROOT/'tests'/name).read_text())}
 required_races={'N01_TUTOR','N02_STUDENT','N03_ADJACENT','N04_REVOKE_PUBLISH','N05_ONE_TIME_TOKEN'}
 errors=[]
 if result.get('engine')!='Native PostgreSQL':errors.append('ENGINE_NOT_NATIVE')
 try:
  if int(result['runtime']['version_num'])//10000!=17:errors.append('SERVER_MAJOR_NOT_17')
 except (ValueError,KeyError,TypeError):errors.append('MISSING_SERVER_VERSION')
 cases=result.get('cases',[]);races=result.get('concurrency',[])
 if {x.get('id') for x in cases}!=expected_sql or len(cases)!=len(expected_sql):errors.append('SQL_CASE_SET_INCOMPLETE_OR_DUPLICATE')
 if {x.get('id') for x in races}!=required_races or len(races)!=len(required_races):errors.append('NATIVE_CASE_SET_INCOMPLETE_OR_DUPLICATE')
 if any(x.get('status')!='PASS' for x in cases+races):errors.append('CASE_FAILED')
 for x in races:
  pids=x.get('sessionPids',[])
  if len(pids)!=2 or len(set(pids))!=2:errors.append('DISTINCT_CONNECTIONS_MISSING:'+str(x.get('id')))
  if x.get('id')!='N03_ADJACENT' and x.get('lockWaitObserved') is not True:errors.append('LOCK_WAIT_MISSING:'+str(x.get('id')))
 n05=next((x for x in races if x.get('id')=='N05_ONE_TIME_TOKEN'),{})
 if n05 and n05.get('outcome')!='COMMITTED':errors.append('TOKEN_SECOND_CALL_DID_NOT_COMMIT')
 return {'status':'DB_COMPONENT_PASS' if not errors else 'NOT_ACCEPTED','errors':errors,'expected_sql_cases':len(expected_sql),'expected_native_cases':len(required_races),'g3_status':'NOT_CLOSED_REQUIRES_TEAM_AND_USER_EVIDENCE','limitations':['Not HTTP ACL, load, AI, Android or restore validation','Result completeness check does not replace independent review of the run']}
if __name__=='__main__':
 path=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'evidence/native_results.json'
 try:result=evaluate(json.loads(path.read_text()))
 except (OSError,ValueError,TypeError) as e:result={'status':'NOT_ACCEPTED','errors':['UNREADABLE_OR_INVALID_EVIDENCE'],'errorClass':type(e).__name__}
 (ROOT/'evidence/native_evaluation.json').write_text(json.dumps(result,indent=2))
 print(json.dumps(result,indent=2));sys.exit(0 if result['status']=='DB_COMPONENT_PASS' else 1)
