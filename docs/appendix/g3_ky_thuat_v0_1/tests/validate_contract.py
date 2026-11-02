from pathlib import Path
import json,re
from openapi_spec_validator import validate
from pglast import parse_sql
from jsonschema import Draft202012Validator,RefResolver
P=Path(__file__).resolve().parents[1]
spec=json.loads((P/'api/openapi.json').read_text());validate(spec)
refs=[]
def walk(o):
 if isinstance(o,dict):
  if '$ref' in o:
   assert o['$ref'].startswith('#/'),o['$ref']
   target=spec
   for key in o['$ref'][2:].split('/'):target=target[key.replace('~1','/').replace('~0','~')]
   refs.append(o['$ref'])
  for v in o.values():walk(v)
 elif isinstance(o,list):
  for v in o:walk(v)
walk(spec)
ops=[]
for path,pi in spec['paths'].items():
 for method,o in pi.items():
  if method not in ['get','post','put','patch','delete']:continue
  ops.append(o['operationId'])
  names=re.findall(r'\{([^}]+)\}',path)
  assert set(names)=={p['name'] for p in o.get('parameters',[]) if p.get('in')=='path'},path
assert len(ops)==len(set(ops))
assert spec['components']['schemas']['ReviewInput']['required']==['checkedMath','checkedSources','checkedDuration','resolvedWarnings']
assert spec['components']['schemas']['SubmissionReviewInput']['required']==['feedback']
schemas=spec['components']['schemas']
assert 'privateDraftAnswer' not in schemas['Publication']['properties']
for name in ['Assignment','Submission','SupportCase','Student','TutorPublic']:
 assert not ({'answer','answerText','privateAnswer','privateDraftAnswer','password','passwordHash','accessToken','tokenDigest'} & set(schemas[name]['properties'])),name
# Positive/negative payloads verify meaningful schema boundaries.
resolver=RefResolver.from_schema(spec)
examples=[('RegistrationInput',{'email':'parent@example.invalid','password':'a'*15,'displayName':'Sample','role':'PARENT','termsVersion':'1'},True),
('RegistrationInput',{'email':'x@example.invalid','password':'a'*15,'displayName':'Sample','role':'ADMIN','termsVersion':'1'},False),
('RegistrationInput',{'email':'x@example.invalid','password':'short','displayName':'Sample','role':'TUTOR','termsVersion':'1'},False),
('StudentProfileInput',{'displayName':'Sample','grade':8,'shortGoal':'Goal','ownerId':'x'},False),
('StudentPatchInput',{},False),
('ApproveSupportGrantInput',{'expiresAt':'2030-01-01T00:00:00Z','capability':'USE_AI'},False),
('SubmissionReviewInput',{'feedback':'Reviewed'},True),
('ReviewInput',{'feedback':'Reviewed'},False)]
for name,payload,valid in examples:
 errs=list(Draft202012Validator(schemas[name],resolver=resolver).iter_errors(payload))
 assert (not errs)==valid,(name,payload)
results={'openapi':'VALID_3_1_1','operations':len(ops),'paths':len(spec['paths']),'schemas':len(schemas),'resolved_refs':len(refs),'unique_operation_ids':True,'schema_boundary_examples_passed':len(examples),'sql_statements':{p.name:len(parse_sql(p.read_text())) for p in sorted((P/'sql').glob('*.sql'))},'limitations':'Structural validation and sample payload checks; no generated-client or HTTP backend execution.'}
(P/'evidence/static_validation.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))
