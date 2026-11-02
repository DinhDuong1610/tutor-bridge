-- Synthetic fixture from TB G3 03 v0.1; caller rolls back.
-- Do not load into production.

SET search_path=tutorbridge,public;
CREATE FUNCTION pg_temp.tid(s text) RETURNS uuid LANGUAGE sql IMMUTABLE AS $$SELECT md5(s)::uuid$$;
INSERT INTO accounts(id,role,status) VALUES
(pg_temp.tid('parent'),'PARENT','ACTIVE'),(pg_temp.tid('tutor'),'TUTOR','ACTIVE');
INSERT INTO students(id,parent_id,display_name) VALUES(pg_temp.tid('student'),pg_temp.tid('parent'),'Synthetic student');
INSERT INTO tutor_profiles(id,tutor_id,version,status,description,experience_months,rate_vnd_60,current)
VALUES(pg_temp.tid('profile'),pg_temp.tid('tutor'),1,'APPROVED',repeat('x',50),24,100000,true);
INSERT INTO calendars(id,owner_kind,owner_id,version,effective) VALUES
(pg_temp.tid('scal'),'STUDENT',pg_temp.tid('student'),1,true),(pg_temp.tid('tcal'),'TUTOR',pg_temp.tid('tutor'),1,true);
INSERT INTO learning_needs(id,student_id,status) VALUES(pg_temp.tid('need'),pg_temp.tid('student'),'CONFIRMED');
INSERT INTO need_versions(id,need_id,version,goal,budget_vnd_session,duration_minutes,frequency,start_date,confirmed_at,student_calendar_id)
VALUES(pg_temp.tid('nv'),pg_temp.tid('need'),1,repeat('Goal ',5),200000,90,1,'2030-01-07','2030-01-01T01:00Z',pg_temp.tid('scal'));
UPDATE learning_needs SET current_version_id=pg_temp.tid('nv') WHERE id=pg_temp.tid('need');
INSERT INTO matching_runs(id,need_version_id,mode,algorithm_version,config_version,status,expires_at)
VALUES(pg_temp.tid('run'),pg_temp.tid('nv'),'B','test','test','COMPLETED','2030-01-02T00:00Z');
INSERT INTO class_offers(id,need_id,need_version_id,tutor_id,profile_id,tutor_calendar_id,student_calendar_id,run_id,state,goal_snapshot,duration_minutes,frequency,cycle_start_date,rate_vnd_60,price_vnd_session,sent_at,expires_at)
VALUES(pg_temp.tid('offer'),pg_temp.tid('need'),pg_temp.tid('nv'),pg_temp.tid('tutor'),pg_temp.tid('profile'),pg_temp.tid('tcal'),pg_temp.tid('scal'),pg_temp.tid('run'),'ACCEPTED',repeat('Goal ',5),90,1,'2030-01-07',100000,150000,'2030-01-01T01:00Z','2030-01-03T01:00Z');
INSERT INTO offer_sessions(offer_id,ordinal,during)
SELECT pg_temp.tid('offer'),n,tstzrange('2030-01-07T11:00Z'::timestamptz+(n-1)*interval '7 days','2030-01-07T12:30Z'::timestamptz+(n-1)*interval '7 days','[)') FROM generate_series(1,4)n;
INSERT INTO classes(id,originating_need_id,accepted_offer_id,student_id,tutor_id,status,duration_minutes,price_vnd_session)
VALUES(pg_temp.tid('class'),pg_temp.tid('need'),pg_temp.tid('offer'),pg_temp.tid('student'),pg_temp.tid('tutor'),'WAITING',90,150000);
INSERT INTO lesson_sessions(id,class_id,ordinal,during,state)
SELECT pg_temp.tid('lesson'||ordinal),pg_temp.tid('class'),ordinal,during,'SCHEDULED' FROM offer_sessions WHERE offer_id=pg_temp.tid('offer') ORDER BY ordinal;
SET CONSTRAINTS ALL IMMEDIATE;

-- A second class exists only to exercise FK/identity isolation; all data is synthetic.
SET CONSTRAINTS ALL DEFERRED;
INSERT INTO accounts(id,role,status) VALUES(pg_temp.tid('tutor2'),'TUTOR','ACTIVE');
INSERT INTO tutor_profiles(id,tutor_id,version,status,description,experience_months,rate_vnd_60,current)
VALUES(pg_temp.tid('profile2'),pg_temp.tid('tutor2'),1,'APPROVED',repeat('x',50),24,100000,true);
INSERT INTO calendars(id,owner_kind,owner_id,version,effective) VALUES(pg_temp.tid('tcal2'),'TUTOR',pg_temp.tid('tutor2'),1,true);
INSERT INTO learning_needs(id,student_id,status) VALUES(pg_temp.tid('need2'),pg_temp.tid('student'),'CONFIRMED');
INSERT INTO need_versions(id,need_id,version,goal,budget_vnd_session,duration_minutes,frequency,start_date,confirmed_at,student_calendar_id)
VALUES(pg_temp.tid('nv2'),pg_temp.tid('need2'),1,repeat('Goal ',5),200000,90,1,'2030-02-04','2030-01-29T01:00Z',pg_temp.tid('scal'));
UPDATE learning_needs SET current_version_id=pg_temp.tid('nv2') WHERE id=pg_temp.tid('need2');
INSERT INTO matching_runs(id,need_version_id,mode,algorithm_version,config_version,status,expires_at)
VALUES(pg_temp.tid('run2'),pg_temp.tid('nv2'),'B','test','test','COMPLETED','2030-01-30T00:00Z');
INSERT INTO class_offers(id,need_id,need_version_id,tutor_id,profile_id,tutor_calendar_id,student_calendar_id,run_id,state,goal_snapshot,duration_minutes,frequency,cycle_start_date,rate_vnd_60,price_vnd_session,sent_at,expires_at)
VALUES(pg_temp.tid('offer2'),pg_temp.tid('need2'),pg_temp.tid('nv2'),pg_temp.tid('tutor2'),pg_temp.tid('profile2'),pg_temp.tid('tcal2'),pg_temp.tid('scal'),pg_temp.tid('run2'),'ACCEPTED',repeat('Goal ',5),90,1,'2030-02-04',100000,150000,'2030-01-29T01:00Z','2030-01-31T01:00Z');
INSERT INTO offer_sessions(offer_id,ordinal,during)
SELECT pg_temp.tid('offer2'),n,tstzrange('2030-02-04T11:00Z'::timestamptz+(n-1)*interval '7 days','2030-02-04T12:30Z'::timestamptz+(n-1)*interval '7 days','[)') FROM generate_series(1,4)n;
INSERT INTO classes(id,originating_need_id,accepted_offer_id,student_id,tutor_id,status,duration_minutes,price_vnd_session)
VALUES(pg_temp.tid('class2'),pg_temp.tid('need2'),pg_temp.tid('offer2'),pg_temp.tid('student'),pg_temp.tid('tutor2'),'WAITING',90,150000);
INSERT INTO lesson_sessions(id,class_id,ordinal,during,state)
SELECT pg_temp.tid('lesson2-'||ordinal),pg_temp.tid('class2'),ordinal,during,'SCHEDULED' FROM offer_sessions WHERE offer_id=pg_temp.tid('offer2');
SET CONSTRAINTS ALL IMMEDIATE;
INSERT INTO class_goal_versions(id,class_id,version,goal,status,proposed_by,parent_approved_at,tutor_approved_at)
VALUES(pg_temp.tid('goal'),pg_temp.tid('class'),1,repeat('Goal ',5),'EFFECTIVE',pg_temp.tid('tutor'),now(),now()),
(pg_temp.tid('goal2'),pg_temp.tid('class2'),1,repeat('Goal ',5),'EFFECTIVE',pg_temp.tid('tutor2'),now(),now());
INSERT INTO preparation_contexts(id,class_id,session_id,goal_version_id,topic,duration_minutes,config_version,created_by)
VALUES(pg_temp.tid('ctx'),pg_temp.tid('class'),pg_temp.tid('lesson1'),pg_temp.tid('goal'),'Test topic',90,'test',pg_temp.tid('tutor')),
(pg_temp.tid('ctx2'),pg_temp.tid('class'),pg_temp.tid('lesson1'),pg_temp.tid('goal'),'Test topic2',90,'test',pg_temp.tid('tutor')),
(pg_temp.tid('ctx_other'),pg_temp.tid('class2'),pg_temp.tid('lesson2-1'),pg_temp.tid('goal2'),'Other class',90,'test',pg_temp.tid('tutor2'));
INSERT INTO materials(id,class_id,owner_id,title) VALUES(pg_temp.tid('mat'),pg_temp.tid('class'),pg_temp.tid('tutor'),'Synthetic source');
INSERT INTO material_versions(id,material_id,class_id,version,object_key,media_type,size_bytes,sha256,state,pdf_pages,extracted_characters,teacher_reviewed_at)
SELECT pg_temp.tid('mv'||n),pg_temp.tid('mat'),pg_temp.tid('class'),n,'synthetic-'||n||'.pdf','application/pdf',100,repeat('a',64),'READY',1,100,now() FROM generate_series(1,6)n;
INSERT INTO material_grants(id,material_version_id,class_id,audience,capability,basis,granted_by)
VALUES(pg_temp.tid('grant'),pg_temp.tid('mv1'),pg_temp.tid('class'),'CLASS_MEMBERS','READ_DERIVATIVE','Synthetic test authorization',pg_temp.tid('tutor'));
INSERT INTO lesson_plans(id,class_id,session_id) VALUES(pg_temp.tid('plan'),pg_temp.tid('class'),pg_temp.tid('lesson1'));
INSERT INTO plan_versions(id,plan_id,class_id,version,origin,status,title,goal,created_by)
VALUES(pg_temp.tid('pv'),pg_temp.tid('plan'),pg_temp.tid('class'),1,'MANUAL','DRAFT','Test plan','Test goal',pg_temp.tid('tutor'));
INSERT INTO plan_activities(id,plan_version_id,class_id,ordinal,kind,content,minutes)
VALUES(pg_temp.tid('activity'),pg_temp.tid('pv'),pg_temp.tid('class'),1,'PRACTICE','Synthetic activity',90);
INSERT INTO private_plan_answers(plan_version_id,class_id,answer_text) VALUES(pg_temp.tid('pv'),pg_temp.tid('class'),'Synthetic private answer');

CREATE FUNCTION pg_temp.assert_true(v boolean,msg text) RETURNS void LANGUAGE plpgsql AS $$BEGIN IF v IS DISTINCT FROM true THEN RAISE EXCEPTION 'ASSERT: %',msg;END IF; END $$;
