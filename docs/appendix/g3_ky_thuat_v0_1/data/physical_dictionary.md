# Từ điển vật lý ứng viên

Sinh từ 001–005 thực thi trên PostgreSQL nhúng. FK và CHECK trong physical_dictionary.json. Quyền HTTP không được suy ra từ FK.

## account_credentials

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| account_id | uuid | NO |  |
| password_hash | text | NO |  |
| hash_policy | character varying | NO |  |
| changed_at | timestamp with time zone | NO | now() |

## accounts

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| role | text | NO |  |
| status | text | NO |  |
| email_digest | text | YES |  |
| created_at | timestamp with time zone | NO | now() |
| display_name | character varying | YES |  |
| email_ciphertext | bytea | YES |  |
| auth_epoch | bigint | NO | 1 |
| email_verified_at | timestamp with time zone | YES |  |
| revision | bigint | NO | 1 |

## admin_assignments

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| account_id | uuid | NO |  |
| capability | text | NO |  |
| granted_by | uuid | NO |  |
| granted_at | timestamp with time zone | NO | now() |
| revoked_at | timestamp with time zone | YES |  |

## admin_mfa_enrollments

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| account_id | uuid | NO |  |
| secret_handle | text | NO |  |
| enrolled_at | timestamp with time zone | NO |  |
| revoked_at | timestamp with time zone | YES |  |

## ai_attempts

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| job_id | uuid | NO |  |
| attempt_number | integer | NO |  |
| fencing_token | bigint | NO |  |
| provider_request_id | text | YES |  |
| state | text | NO |  |
| estimated_cost_vnd | bigint | NO |  |
| actual_cost_vnd | bigint | YES |  |

## ai_jobs

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| context_id | uuid | NO |  |
| tutor_id | uuid | NO |  |
| policy_version | character varying | NO |  |
| state | text | NO |  |
| base_plan_version_id | uuid | YES |  |
| base_plan_revision | bigint | YES |  |
| result_plan_version_id | uuid | YES |  |
| created_at | timestamp with time zone | NO | now() |
| deadline_at | timestamp with time zone | NO |  |
| fencing_token | bigint | NO | 0 |
| lease_until | timestamp with time zone | YES |  |
| worker_id | text | YES |  |
| failure_code | text | YES |  |

## ai_policies

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| version | character varying | NO |  |
| enabled | boolean | NO | false |
| provider | text | YES |  |
| model | text | YES |  |
| money_cap_vnd | bigint | YES |  |
| class_day_limit | integer | NO | 5 |
| tutor_day_limit | integer | NO | 20 |
| global_running_limit | integer | NO | 2 |
| tutor_running_limit | integer | NO | 1 |
| queue_limit | integer | NO | 20 |
| timeout_seconds | integer | NO | 120 |

## ai_reservations

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| attempt_id | uuid | NO |  |
| class_id | uuid | NO |  |
| tutor_id | uuid | NO |  |
| local_date | date | NO |  |
| policy_version | character varying | NO |  |
| reserved_vnd | bigint | NO |  |
| state | text | NO |  |

## answer_releases

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| plan_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| released_by | uuid | NO |  |
| released_at | timestamp with time zone | NO |  |
| homework_closed_at | timestamp with time zone | NO |  |

## assignment_answer_releases

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| assignment_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| released_by | uuid | NO |  |
| released_at | timestamp with time zone | NO |  |
| closed_at_snapshot | timestamp with time zone | NO |  |
| command_id | uuid | NO |  |

## assignment_private_answers

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| assignment_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| answer_text | text | NO |  |

## assignment_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| assignment_id | uuid | NO |  |
| class_id | uuid | NO |  |
| version | integer | NO |  |
| source_plan_version_id | uuid | NO |  |
| question | text | NO |  |
| due_at | timestamp with time zone | YES |  |
| status | text | NO |  |
| approved_by | uuid | YES |  |
| approved_at | timestamp with time zone | YES |  |
| issued_at | timestamp with time zone | YES |  |
| superseded_at | timestamp with time zone | YES |  |

## assignments

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| session_id | uuid | NO |  |
| current_issued_version_id | uuid | YES |  |
| state | text | NO |  |
| revision | bigint | NO | 1 |
| closed_at | timestamp with time zone | YES |  |
| closure_reason | character varying | YES |  |

## attendance_decisions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| log_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| decision_revision | integer | NO |  |
| actor_id | uuid | NO |  |
| decision | text | NO |  |
| note | character varying | NO | ''::character varying |
| decided_at | timestamp with time zone | NO |  |
| command_id | uuid | NO |  |

## audit_events

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| actor_id | uuid | YES |  |
| command_id | uuid | YES |  |
| event_type | character varying | NO |  |
| object_type | character varying | NO |  |
| object_id | uuid | NO |  |
| outcome | character varying | NO |  |
| trace_id | character varying | NO |  |
| created_at | timestamp with time zone | NO | now() |

## auth_challenges

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| account_id | uuid | NO |  |
| purpose | text | NO |  |
| token_digest | character | NO |  |
| created_at | timestamp with time zone | NO | now() |
| expires_at | timestamp with time zone | NO |  |
| consumed_at | timestamp with time zone | YES |  |

## auth_sessions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| account_id | uuid | NO |  |
| token_digest | text | NO |  |
| expires_at | timestamp with time zone | NO |  |
| revoked_at | timestamp with time zone | YES |  |
| issued_epoch | bigint | NO | 1 |
| created_at | timestamp with time zone | NO | now() |
| last_seen_at | timestamp with time zone | NO | now() |
| admin_mfa_verified_at | timestamp with time zone | YES |  |

## availability_windows

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| calendar_id | uuid | NO |  |
| during | tstzrange | NO |  |
| kind | text | NO |  |

## budget_ledgers

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| policy_version | character varying | NO |  |
| reserved_vnd | bigint | NO | 0 |
| spent_vnd | bigint | NO | 0 |

## calendars

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| owner_kind | text | NO |  |
| owner_id | uuid | NO |  |
| version | bigint | NO |  |
| effective | boolean | NO | false |
| timezone | text | NO | 'Asia/Ho_Chi_Minh'::text |

## class_end_records

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| class_id | uuid | NO |  |
| actor_id | uuid | NO |  |
| reason | character varying | NO |  |
| ended_at | timestamp with time zone | NO |  |
| ended_revision | bigint | NO |  |
| command_id | uuid | NO |  |

## class_goal_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| version | integer | NO |  |
| goal | character varying | NO |  |
| status | text | NO |  |
| proposed_by | uuid | NO |  |
| parent_approved_at | timestamp with time zone | YES |  |
| tutor_approved_at | timestamp with time zone | YES |  |

## class_offers

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| need_id | uuid | NO |  |
| need_version_id | uuid | NO |  |
| tutor_id | uuid | NO |  |
| profile_id | uuid | NO |  |
| tutor_calendar_id | uuid | NO |  |
| student_calendar_id | uuid | NO |  |
| run_id | uuid | NO |  |
| revision | bigint | NO | 1 |
| state | text | NO |  |
| goal_snapshot | character varying | NO |  |
| duration_minutes | integer | NO |  |
| frequency | integer | NO |  |
| cycle_start_date | date | NO |  |
| rate_vnd_60 | integer | NO |  |
| price_vnd_session | integer | NO |  |
| sent_at | timestamp with time zone | NO |  |
| expires_at | timestamp with time zone | NO |  |
| concluded_at | timestamp with time zone | YES |  |
| reason_code | text | YES |  |

## classes

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| originating_need_id | uuid | NO |  |
| accepted_offer_id | uuid | NO |  |
| student_id | uuid | NO |  |
| tutor_id | uuid | NO |  |
| status | text | NO |  |
| revision | bigint | NO | 1 |
| duration_minutes | integer | NO |  |
| price_vnd_session | integer | NO |  |
| ended_at | timestamp with time zone | YES |  |
| created_at | timestamp with time zone | NO | now() |

## command_receipts

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| actor_id | uuid | NO |  |
| command_type | character varying | NO |  |
| idempotency_key | character varying | NO |  |
| payload_sha256 | character | NO |  |
| state | text | NO |  |
| result_type | character varying | YES |  |
| result_id | uuid | YES |  |
| error_code | text | YES |  |
| created_at | timestamp with time zone | NO | now() |
| expires_at | timestamp with time zone | NO |  |

## confirmation_reminders

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| log_version_id | uuid | NO |  |
| recipient_id | uuid | NO |  |
| reminder_kind | text | NO |  |
| outbox_id | uuid | NO |  |
| created_at | timestamp with time zone | NO |  |

## context_logs

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| context_id | uuid | NO |  |
| log_version_id | uuid | NO |  |
| class_id | uuid | NO |  |

## context_sources

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| context_id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| source_epoch | bigint | NO |  |

## deletion_ledger

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| subject_type | character varying | NO |  |
| subject_digest | character | NO |  |
| requested_at | timestamp with time zone | NO |  |
| active_due_at | timestamp with time zone | NO |  |
| completed_at | timestamp with time zone | YES |  |

## evaluation_events

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| outbox_event_id | uuid | NO |  |
| event_type | character varying | NO |  |
| actor_pseudonym | character varying | NO |  |
| subject_pseudonym | character varying | NO |  |
| flow_version | character varying | NO |  |
| object_version | character varying | NO |  |
| outcome_code | character varying | NO |  |
| occurred_at | timestamp with time zone | NO |  |

## learning_needs

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| student_id | uuid | NO |  |
| revision | bigint | NO | 1 |
| status | text | NO |  |
| created_at | timestamp with time zone | NO | now() |
| current_version_id | uuid | YES |  |

## lesson_log_heads

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| session_id | uuid | NO |  |
| class_id | uuid | NO |  |
| current_published_version_id | uuid | YES |  |
| revision | bigint | NO | 1 |

## lesson_log_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| session_id | uuid | NO |  |
| class_id | uuid | NO |  |
| version | integer | NO |  |
| professional_feedback | character varying | NO |  |
| tutor_confirmed_at | timestamp with time zone | YES |  |
| parent_attendance_confirmed_at | timestamp with time zone | YES |  |
| disputed | boolean | NO | false |
| status | text | NO | 'DRAFT'::text |
| reported_attendance | text | YES |  |
| actual_during | tstzrange | YES |  |
| taught_content | character varying | NO | ''::character varying |
| next_plan | character varying | NO | ''::character varying |
| published_by | uuid | YES |  |
| published_at | timestamp with time zone | YES |  |

## lesson_plans

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| session_id | uuid | NO |  |
| revision | bigint | NO | 1 |

## lesson_sessions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| ordinal | integer | NO |  |
| during | tstzrange | NO |  |
| state | text | NO |  |
| revision | bigint | NO | 1 |
| makeup_for_id | uuid | YES |  |

## matching_candidates

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| run_id | uuid | NO |  |
| tutor_id | uuid | NO |  |
| profile_id | uuid | NO |  |
| tutor_calendar_id | uuid | NO |  |
| rank | smallint | NO |  |
| s | numeric | YES |  |
| e | numeric | NO |  |
| a | numeric | NO |  |
| score | numeric | NO |  |
| quoted_price | integer | NO |  |
| hard_filters_passed | boolean | NO |  |

## matching_runs

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| need_version_id | uuid | NO |  |
| mode | text | NO |  |
| algorithm_version | character varying | NO |  |
| config_version | character varying | NO |  |
| status | text | NO |  |
| created_at | timestamp with time zone | NO | now() |
| expires_at | timestamp with time zone | NO |  |

## material_grants

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| audience | text | NO |  |
| account_id | uuid | YES |  |
| capability | text | NO |  |
| basis | character varying | NO |  |
| granted_by | uuid | NO |  |
| expires_at | timestamp with time zone | YES |  |
| revoked_at | timestamp with time zone | YES |  |

## material_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| material_id | uuid | NO |  |
| class_id | uuid | NO |  |
| version | integer | NO |  |
| object_key | text | NO |  |
| media_type | text | NO |  |
| size_bytes | integer | NO |  |
| sha256 | character | NO |  |
| revision | bigint | NO | 1 |
| state | text | NO |  |
| pdf_pages | integer | YES |  |
| extracted_characters | integer | YES |  |
| extraction_version | character varying | YES |  |
| teacher_reviewed_at | timestamp with time zone | YES |  |
| access_epoch | bigint | NO | 1 |

## materials

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| owner_id | uuid | NO |  |
| title | character varying | NO |  |

## need_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| need_id | uuid | NO |  |
| version | integer | NO |  |
| goal | character varying | YES |  |
| budget_vnd_session | integer | YES |  |
| duration_minutes | integer | YES |  |
| frequency | integer | YES |  |
| start_date | date | YES |  |
| confirmed_at | timestamp with time zone | YES |  |
| student_calendar_id | uuid | YES |  |

## notifications

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| recipient_id | uuid | NO |  |
| event_id | uuid | NO |  |
| template_code | character varying | NO |  |
| target_type | character varying | YES |  |
| target_id | uuid | YES |  |
| read_at | timestamp with time zone | YES |  |

## offer_sessions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| offer_id | uuid | NO |  |
| ordinal | smallint | NO |  |
| during | tstzrange | NO |  |

## outbox_events

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| aggregate_type | character varying | NO |  |
| aggregate_id | uuid | NO |  |
| event_type | character varying | NO |  |
| payload | jsonb | NO |  |
| dedupe_key | text | NO |  |
| created_at | timestamp with time zone | NO | now() |
| available_at | timestamp with time zone | NO | now() |
| delivered_at | timestamp with time zone | YES |  |
| attempts | integer | NO | 0 |
| lease_until | timestamp with time zone | YES |  |

## plan_activities

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| plan_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| ordinal | integer | NO |  |
| kind | text | NO |  |
| content | text | NO |  |
| minutes | integer | NO |  |

## plan_citations

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| plan_version_id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| segment_id | uuid | NO |  |
| activity_id | uuid | YES |  |
| cited_excerpt | character varying | NO |  |

## plan_dependencies

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| plan_version_id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| source_epoch | bigint | NO |  |

## plan_publications

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| plan_id | uuid | NO |  |
| class_id | uuid | NO |  |
| plan_version_id | uuid | NO |  |
| revision | bigint | NO | 1 |
| published_by | uuid | NO |  |
| published_at | timestamp with time zone | NO |  |

## plan_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| plan_id | uuid | NO |  |
| class_id | uuid | NO |  |
| version | integer | NO |  |
| context_id | uuid | YES |  |
| origin | text | NO |  |
| status | text | NO |  |
| revision | bigint | NO | 1 |
| title | character varying | NO |  |
| goal | character varying | NO |  |
| homework | text | NO | ''::text |
| warnings_resolved | boolean | NO | false |
| created_by | uuid | NO |  |
| created_at | timestamp with time zone | NO | now() |
| approved_by | uuid | YES |  |
| approved_at | timestamp with time zone | YES |  |

## preparation_contexts

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| session_id | uuid | NO |  |
| goal_version_id | uuid | NO |  |
| topic | character varying | NO |  |
| duration_minutes | integer | NO |  |
| config_version | character varying | NO |  |
| created_by | uuid | NO |  |
| created_at | timestamp with time zone | NO | now() |

## privacy_ledger_exports

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| deletion_ledger_id | uuid | NO |  |
| state | text | NO |  |
| exported_at | timestamp with time zone | YES |  |
| external_receipt | character varying | YES |  |

## privacy_minimal_holds

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| request_id | uuid | NO |  |
| approved_by | uuid | NO |  |
| minimal_scope | character varying | NO |  |
| reason | character varying | NO |  |
| created_at | timestamp with time zone | NO | now() |
| review_at | timestamp with time zone | NO |  |
| notified_at | timestamp with time zone | NO |  |
| released_at | timestamp with time zone | YES |  |

## privacy_requests

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| requester_id | uuid | NO |  |
| scope | text | NO |  |
| subject_id | uuid | NO |  |
| state | text | NO |  |
| requested_at | timestamp with time zone | NO | now() |
| blocked_at | timestamp with time zone | NO |  |
| active_due_at | timestamp with time zone | NO |  |
| deletion_ledger_id | uuid | NO |  |
| revision | bigint | NO | 1 |
| backup_expires_at | timestamp with time zone | YES |  |
| closed_at | timestamp with time zone | YES |  |

## privacy_store_tasks

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| request_id | uuid | NO |  |
| store | text | NO |  |
| state | text | NO |  |
| attempts | integer | NO | 0 |
| last_error_code | character varying | YES |  |
| evidence_ref | character varying | YES |  |
| completed_at | timestamp with time zone | YES |  |

## private_plan_answers

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| plan_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| answer_text | text | NO |  |

## quota_counters

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| scope | text | NO |  |
| subject_id | uuid | NO |  |
| local_date | date | NO |  |
| used | integer | NO |  |

## resource_bookings

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| session_id | uuid | NO |  |
| resource_kind | text | NO |  |
| resource_id | uuid | NO |  |
| during | tstzrange | NO |  |
| active | boolean | NO |  |

## revocation_ledger

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| new_epoch | bigint | NO |  |
| reason_code | character varying | NO |  |
| revoked_by | uuid | NO |  |
| revoked_at | timestamp with time zone | NO | now() |

## schedule_change_requests

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| class_id | uuid | NO |  |
| original_session_id | uuid | NO |  |
| kind | text | NO |  |
| proposer_id | uuid | NO |  |
| expected_session_revision | bigint | NO |  |
| proposed_during | tstzrange | NO |  |
| reason | character varying | NO |  |
| state | text | NO |  |
| created_at | timestamp with time zone | NO |  |
| expires_at | timestamp with time zone | NO |  |
| responded_by | uuid | YES |  |
| responded_at | timestamp with time zone | YES |  |
| result_session_id | uuid | YES |  |
| failure_code | character varying | YES |  |
| revision | bigint | NO | 1 |

## session_schedule_history

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| session_id | uuid | NO |  |
| class_id | uuid | NO |  |
| request_id | uuid | YES |  |
| from_revision | bigint | NO |  |
| to_revision | bigint | NO |  |
| before_during | tstzrange | NO |  |
| after_during | tstzrange | NO |  |
| actor_id | uuid | NO |  |
| reason | character varying | NO |  |
| occurred_at | timestamp with time zone | NO |  |
| command_id | uuid | NO |  |

## source_segments

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| ordinal | integer | NO |  |
| locator | character varying | NO |  |
| text_content | text | NO |  |

## student_invitations

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| student_id | uuid | NO |  |
| parent_id | uuid | NO |  |
| target_email_digest | text | NO |  |
| token_digest | character | NO |  |
| created_at | timestamp with time zone | NO | now() |
| expires_at | timestamp with time zone | NO |  |
| state | text | NO |  |
| accepted_by | uuid | YES |  |
| accepted_at | timestamp with time zone | YES |  |
| revision | bigint | NO | 1 |

## student_link_events

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| student_id | uuid | NO |  |
| account_id | uuid | NO |  |
| actor_id | uuid | NO |  |
| event_kind | text | NO |  |
| link_revision | bigint | NO |  |
| occurred_at | timestamp with time zone | NO | now() |

## student_links

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| student_id | uuid | NO |  |
| account_id | uuid | NO |  |
| status | text | NO |  |
| expires_at | timestamp with time zone | YES |  |
| revision | bigint | NO | 1 |

## students

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| parent_id | uuid | NO |  |
| display_name | character varying | NO |  |
| grade | smallint | NO | 8 |
| revision | bigint | NO | 1 |
| short_goal | character varying | NO | ''::character varying |

## submission_attachments

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| submission_id | uuid | NO |  |
| class_id | uuid | NO |  |
| ordinal | smallint | NO |  |
| object_key | text | NO |  |
| media_type | text | NO |  |
| size_bytes | integer | NO |  |
| sha256 | character | NO |  |

## submission_attempts

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| assignment_id | uuid | NO |  |
| assignment_version_id | uuid | NO |  |
| class_id | uuid | NO |  |
| student_id | uuid | NO |  |
| actor_id | uuid | NO |  |
| actor_role | text | NO |  |
| linked_student_id | uuid | YES |  |
| link_revision | bigint | YES |  |
| attempt_number | integer | NO |  |
| text_answer | character varying | NO | ''::character varying |
| received_at | timestamp with time zone | NO |  |
| due_at_snapshot | timestamp with time zone | YES |  |
| late | boolean | YES |  |
| command_id | uuid | NO |  |

## submission_review_versions

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| submission_id | uuid | NO |  |
| class_id | uuid | NO |  |
| version | integer | NO |  |
| reviewed_by | uuid | NO |  |
| feedback | character varying | NO |  |
| reviewed_at | timestamp with time zone | NO |  |
| command_id | uuid | NO |  |

## support_access_grants

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| request_id | uuid | NO |  |
| grantee_id | uuid | NO |  |
| approved_by | uuid | NO |  |
| capability | text | NO |  |
| granted_at | timestamp with time zone | NO | now() |
| expires_at | timestamp with time zone | NO |  |
| revoked_at | timestamp with time zone | YES |  |
| revision | bigint | NO | 1 |

## support_case_events

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| case_id | uuid | NO |  |
| actor_id | uuid | NO |  |
| from_state | text | NO |  |
| to_state | text | NO |  |
| reason | character varying | NO |  |
| occurred_at | timestamp with time zone | NO | now() |
| revision | bigint | NO |  |

## support_cases

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| reporter_id | uuid | NO |  |
| class_id | uuid | YES |  |
| category | character varying | NO |  |
| description | character varying | NO |  |
| state | text | NO | 'NEW'::text |
| assignee_id | uuid | YES |  |
| resolution | character varying | YES |  |
| revision | bigint | NO | 1 |
| created_at | timestamp with time zone | NO | now() |
| updated_at | timestamp with time zone | NO | now() |

## support_grant_requests

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| case_id | uuid | NO |  |
| requester_id | uuid | NO |  |
| material_version_id | uuid | NO |  |
| purpose | character varying | NO |  |
| requested_at | timestamp with time zone | NO | now() |
| requested_until | timestamp with time zone | NO |  |
| state | text | NO |  |
| revision | bigint | NO | 1 |

## support_messages

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| case_id | uuid | NO |  |
| author_id | uuid | NO |  |
| audience | text | NO |  |
| body | character varying | NO |  |
| created_at | timestamp with time zone | NO | now() |

## tutor_evidence_uploads

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| owner_id | uuid | NO |  |
| profile_id | uuid | YES |  |
| object_key | text | NO |  |
| media_type | text | NO |  |
| size_bytes | integer | NO |  |
| sha256 | character | NO |  |
| state | text | NO |  |
| expires_at | timestamp with time zone | NO |  |
| created_at | timestamp with time zone | NO | now() |

## tutor_profiles

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| tutor_id | uuid | NO |  |
| version | integer | NO |  |
| status | text | NO |  |
| description | character varying | NO |  |
| experience_months | integer | NO |  |
| rate_vnd_60 | integer | NO |  |
| subject | text | NO | 'MATH8_ALGEBRA'::text |
| current | boolean | NO | false |
| revision | bigint | NO | 1 |

## tutor_verifications

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| id | uuid | NO |  |
| profile_id | uuid | NO |  |
| scope | text | NO |  |
| status | text | NO |  |
| evidence_key | text | NO |  |
| reviewed_by | uuid | YES |  |
| reason | character varying | YES |  |
| reviewed_at | timestamp with time zone | YES |  |
| assigned_to | uuid | YES |  |
| revision | bigint | NO | 1 |

## worker_slots

| Cột | Kiểu | Cho NULL | Mặc định |
|---|---|---|---|
| slot_no | smallint | NO |  |
| job_id | uuid | YES |  |
| tutor_id | uuid | YES |  |
| fencing_token | bigint | NO | 0 |
| lease_until | timestamp with time zone | YES |  |
