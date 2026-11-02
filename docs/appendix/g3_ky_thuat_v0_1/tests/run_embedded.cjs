const fs=require('fs'),path=require('path');
const K=path.resolve(__dirname,'..');
const {PGlite}=require('@electric-sql/pglite');
const {btree_gist}=require('@electric-sql/pglite/contrib/btree_gist');
(async()=>{
 const db=new PGlite({extensions:{btree_gist}}),res={engine:'PGlite embedded PostgreSQL',executedAt:new Date().toISOString(),migrations:[],cases:[],limitations:['Single embedded session; not native PostgreSQL server','Direct DB assertions; no HTTP authorization, backend, user or load tests']};
 for(const f of fs.readdirSync(K+'/sql').filter(x=>x.endsWith('.sql')).sort()){
  await db.exec(fs.readFileSync(K+'/sql/'+f,'utf8'));res.migrations.push({file:f,status:'PASS'});
 }
 res.version=(await db.query('select version()')).rows[0].version;
 const columns=(await db.query("select table_name,column_name,data_type,is_nullable,column_default from information_schema.columns where table_schema='tutorbridge' order by table_name,ordinal_position")).rows;
 const constraints=(await db.query("select c.conname,cl.relname as table_name,pg_get_constraintdef(c.oid) as definition from pg_constraint c join pg_class cl on cl.oid=c.conrelid join pg_namespace n on n.oid=cl.relnamespace where n.nspname='tutorbridge' order by cl.relname,c.conname")).rows;
 const fks=(await db.query("select cl.relname as source_table,rl.relname as target_table,c.conname from pg_constraint c join pg_class cl on cl.oid=c.conrelid join pg_class rl on rl.oid=c.confrelid join pg_namespace n on n.oid=cl.relnamespace where c.contype='f' and n.nspname='tutorbridge' order by 1,2")).rows;
 fs.mkdirSync(K+'/data',{recursive:true});fs.writeFileSync(K+'/data/physical_dictionary.json',JSON.stringify({columns,constraints,foreign_keys:fks},null,2));
 res.tables=[...new Set(columns.map(x=>x.table_name))].length;
 await db.exec('BEGIN;');await db.exec(fs.readFileSync(K+'/tests/baseline_fixture.sql','utf8'));await db.exec(fs.readFileSync(K+'/tests/unified_fixture.sql','utf8'));
 const cases=['baseline_cases.json','unified_cases.json'].flatMap(f=>JSON.parse(fs.readFileSync(K+'/tests/'+f,'utf8')));
 for(const c of cases){let actual=null,stage='setup',ok=false;
  await db.exec('SAVEPOINT casepoint;');try{
   if(c.setup)await db.exec(c.setup);stage='action';await db.exec(c.sql);ok=c.expectedSqlstate===null;
  }catch(e){actual=e.code;ok=stage==='action'&&actual===c.expectedSqlstate;if(!ok)process.stderr.write(c.id+' '+stage+' '+e.message+' '+actual+'\n');}
  await db.exec('ROLLBACK TO SAVEPOINT casepoint; RELEASE SAVEPOINT casepoint;');
  res.cases.push({id:c.id,title:c.title,status:ok?'PASS':'FAIL',expectedSqlstate:c.expectedSqlstate,actualSqlstate:actual,stage});
 }
 await db.exec('ROLLBACK;');await db.close();
 res.counts={passed:res.cases.filter(x=>x.status==='PASS').length,total:res.cases.length};
 fs.mkdirSync(K+'/evidence',{recursive:true});fs.writeFileSync(K+'/evidence/embedded_results.json',JSON.stringify(res,null,2));
 console.log(JSON.stringify({migrations:res.migrations,tables:res.tables,counts:res.counts,version:res.version}));if(res.counts.passed!==res.counts.total)process.exit(1);
})().catch(e=>{console.error(e);process.exit(1)});
