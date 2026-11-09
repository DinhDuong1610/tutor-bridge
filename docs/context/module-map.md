# Bản đồ mô-đun

Theo [ADR-0001](../decisions/adr/0001-nguyen-khoi-chia-mo-dun.md). Chỉ mô-đun sở hữu mới được ghi và truy vấn trực tiếp các bảng của mình. Mô-đun khác đi qua API công khai của mô-đun đó.

Thiết kế gốc liệt kê mô-đun ở mức nhóm. Việc gán từng bảng dưới đây là suy ra từ F01–F06, gồm cả 3 bảng của V6 và 2 bảng của V9.

| Mô-đun | Package | Luồng | Tài liệu | Bảng |
|---|---|---|---|---|
| Identity & Profiles | `identity` | F01 | UC01–UC03 | 15 |
| Matching & Classes | `classes` | F02, F03 | UC04–UC08 | 18 |
| Materials & Planning | `materials` | F04 | UC10, UC11 | 23 |
| Learning | `learning` | F05 | UC09, UC12, UC13 | 12 |
| Support & Privacy | `privacy` | F06 | UC14, UC16 | 10 |
| Platform | `platform` | Dùng chung | UC15, FR-37 | 4 |

Tổng 82 bảng.

## Bảng theo mô-đun
**identity:** `accounts`, `auth_sessions`, `account_credentials`, `auth_challenges`, `admin_assignments`, `admin_mfa_enrollments`, `students`, `student_links`, `student_invitations`, `student_link_events`, `tutor_profiles`, `tutor_verifications`, `tutor_evidence_uploads`, `terms_acceptances`, `auth_rate_counters`

**classes:** `calendars`, `availability_windows`, `learning_needs`, `need_versions`, `matching_runs`, `matching_candidates`, `class_offers`, `offer_sessions`, `classes`, `class_goal_versions`, `lesson_sessions`, `resource_bookings`, `schedule_change_requests`, `session_schedule_history`, `class_end_records`, `embedding_configs`, `tutor_profile_embeddings`, `need_version_embeddings`

**materials:** `materials`, `material_versions`, `source_segments`, `material_grants`, `revocation_ledger`, `preparation_contexts`, `context_sources`, `context_logs`, `ai_policies`, `ai_jobs`, `ai_attempts`, `quota_counters`, `budget_ledgers`, `ai_reservations`, `worker_slots`, `lesson_plans`, `plan_versions`, `plan_activities`, `plan_dependencies`, `plan_citations`, `private_plan_answers`, `plan_publications`, `answer_releases`

**learning:** `lesson_log_versions`, `lesson_log_heads`, `attendance_decisions`, `confirmation_reminders`, `assignments`, `assignment_versions`, `assignment_private_answers`, `assignment_answer_releases`, `submission_attempts`, `submission_attachments`, `submission_review_versions`, `evaluation_events`

**privacy:** `support_cases`, `support_messages`, `support_case_events`, `support_grant_requests`, `support_access_grants`, `privacy_requests`, `privacy_store_tasks`, `privacy_minimal_holds`, `privacy_ledger_exports`, `deletion_ledger`

**platform:** `command_receipts`, `outbox_events`, `notifications`, `audit_events`

## Phụ thuộc cho phép
```text
privacy ──> materials, learning, classes, identity, platform
learning ─> materials, classes, identity, platform
materials > classes, identity, platform
classes ──> identity, platform
identity ─> platform
```

Mũi tên chỉ một chiều. Khi mô-đun thấp cần báo mô-đun cao, dùng sự kiện qua outbox, không gọi ngược. Ví dụ `classes` kết thúc lớp, `materials` và `learning` nhận sự kiện để chặn tạo mới.

## Thành phần ngoài backend
| Thành phần | Trách nhiệm | Không được |
|---|---|---|
| `ai-worker/` | Trích xuất tệp, embedding, soạn nháp | Chạm DB, tự công bố |
| `mobile/` | Android cho phụ huynh, gia sư, học sinh P1 | Chứa khóa AI, tự quyết định quyền |
| `web/` | Quản trị, giới thiệu | Chứa học liệu riêng ở phần công khai |
| `packages/api-client` | API client sinh từ OpenAPI | Sửa tay code sinh ra |
| `packages/shared` | Code dùng chung không có React: Problem, `Idempotency-Key`, định dạng ngày giờ và tiền | Chứa React, gọi mạng, chứa bí mật |
