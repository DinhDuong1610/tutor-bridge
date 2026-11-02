# TutorBridge_G3_Thiet_ke_Ky_thuat_F02_F04_v0_1

> Tài liệu lịch sử, độ ưu tiên thấp nhất. Bản Word kèm wireframe ở `docs/source-docx/g3/TutorBridge_G3_Thiet_ke_Ky_thuat_F02_F04_v0_1.docx`. Hình ảnh không có trong bản này.

NHÓM DỰ ÁN TUTORBRIDGE

HỒ SƠ THIẾT KẾ KỸ THUẬT

VÀ PROTOTYPE F02 F04

Tài liệu điều hành và triển khai giai đoạn thiết kế giải pháp

| Thông tin kiểm soát | Nội dung |
|---|---|
| Mã tài liệu | TB G3 03 |
| Phiên bản | 0.1 |
| Ngày phát hành | 29/10/2026 |
| Nhóm thực hiện | Dương Đính và Đức Huy |
| Mục đích sử dụng | Rà soát ERD vật lý, hợp đồng API và mô phỏng tương tác trước khi triển khai backend và ứng dụng. |
| Trạng thái | Phát hành để rà soát thiết kế chi tiết; ghi nhận Đính đồng ý DP01 đến DP04; chưa nghiệm thu toàn bộ G3. |

Phạm vi yêu cầu tham chiếu: TB G2 02 v1.1. Trạng thái xác nhận và điều kiện bàn giao được quản lý tại TB G2 03 v1.2.

Luồng F02 Tìm gia sư đến chốt lớp và F04 Học liệu đến bài soạn công bố. Hồ sơ gồm báo cáo này, gói kỹ thuật SQL và OpenAPI cùng prototype riêng sử dụng dữ liệu mô phỏng.

## 1 Kết luận và kiểm soát hồ sơ

Hai luồng F02 và F04 đã được cụ thể hóa thành mô hình dữ liệu vật lý, hợp đồng API và prototype có thao tác. Bộ bàn giao là cơ sở rà soát trước coding. Các kiểm tra tĩnh và kiểm tra mô hình đã thực hiện được ghi tại mục 12; kiểm thử PostgreSQL thực thi, đồng thời và tải còn là điều kiện triển khai.

Bảng 1 Thành phần hồ sơ bàn giao

| Hạng mục | Kết quả bàn giao |
|---|---|
| Báo cáo | TB G3 03 v0.1 ngày 29/10/2026; đối tượng sử dụng là nhóm dự án, người rà soát kỹ thuật và các bên đánh giá giải pháp. |
| Dữ liệu | 47 bảng, 348 cột, 88 khóa ngoại; 2 migration ứng viên PostgreSQL 17; ERD toàn bộ và 4 miền; từ điển dữ liệu. |
| API | OpenAPI 3.1.1 gồm 40 thao tác, 36 đường dẫn, 45 schema; danh mục lỗi, quyền và giao dịch. |
| Prototype | 17 màn hình, 9 kịch bản mô phỏng; không tạo lớp, tải tệp lên máy chủ hoặc gọi AI thật. |
| Trạng thái | Đính đồng ý DP01–DP04. Đức Huy chưa có xác nhận riêng. G2 tiếp tục Đạt có điều kiện; hồ sơ này không đóng toàn bộ G3. |

### 1.1 Căn cứ kiểm soát phiên bản

Bảng 2 Tài liệu tham chiếu

| Tài liệu nguồn | Phiên bản và vai trò |
|---|---|
| TB G2 02 | PRD và SRS hợp nhất v1.1; cơ sở yêu cầu sản phẩm, nghiệp vụ, quyền và NFR. |
| TB G2 03 | Báo cáo nghiệm thu G2 v1.2; trạng thái xác nhận và điều kiện còn mở. |
| TB G3 01 | Khởi động G3 và danh mục màn hình v0.1; phân công và phạm vi tổng thể. |
| TB G3 02 | Wireframe dữ liệu quyền giao dịch F02 F04 v0.1; 22 nhóm thực thể và TX01–TX16. |

Thay đổi yêu cầu sản phẩm phải cập nhật PRD/SRS qua kiểm soát thay đổi. Các lựa chọn PostgreSQL 17 và OpenAPI 3.1.1 ở đây là cơ sở tương thích được đề xuất cho triển khai, không phải kết quả đánh giá tải hay chốt nhà cung cấp.

## 2 Phạm vi và phương án cấu trúc

R0 phục vụ học kèm trực tuyến 1–1 Toán lớp 8 đại số bằng tiếng Việt. Ứng dụng Android dành cho phụ huynh và gia sư; web quản trị và giới thiệu. Học sinh đăng nhập theo lời mời ở P1. P1 là điều kiện của R0 đầy đủ; R0a chỉ là bản nội bộ. Giá học là điều kiện thỏa thuận, R0 không xử lý thanh toán.

Bảng 3 Cấu trúc thành phần giải pháp

| Thành phần | Trách nhiệm và ranh giới |
|---|---|
| Spring Boot | Backend dạng ứng dụng có mô đun: danh tính, con, gia sư, nhu cầu và matching, lớp và lịch, học liệu, bài soạn, vận hành. Một nơi kiểm quyền và điều phối giao dịch. |
| PostgreSQL | Nguồn sự thật cho phiên bản, lịch đặt, quyền, quota và receipt. Unique và exclusion bảo vệ qua nhiều API replica. Outbox và queue có thể bắt đầu trong DB. |
| Worker | Tách công việc trích xuất và AI khỏi HTTP request. Python/FastAPI là phương án; lease, fencing, deadline và ngân sách được quản lý theo hợp đồng giao dịch. |
| Kho đối tượng | Tệp gốc ở vùng riêng; upload có ticket hạn ngắn, đọc qua API xác thực. Quyền và epoch nằm trong DB; xử lý orphan và xóa bất đồng bộ có đối soát. |
| Next.js và React Native | Next.js cho web quản trị và giới thiệu; React Native cho Android theo SRS. Prototype web hiện tại chỉ phục vụ rà soát luồng và trạng thái. |

### 2.1 Nguyên tắc để mở rộng có kiểm soát

Giữ API không phụ thuộc trạng thái RAM; mọi khóa quyết định nghiệp vụ ở DB. Dùng phân trang theo cursor, công việc nền có giới hạn và projection dữ liệu theo quyền. Tách mô đun ngay từ đầu; chỉ tách dịch vụ khi có bằng chứng về tải hoặc nhu cầu vận hành. Nhà cung cấp AI, kích thước embedding và vector store chưa được khóa.

Hàng nghìn người dùng là mục tiêu phát triển. Chưa có phép đo đủ để xác nhận năng lực chịu tải. Phải kiểm thử NFR01–NFR16, đồng thời qua hai replica và khôi phục DB cùng tệp trước khi công bố khả năng vận hành.

### 2.2 Phạm vi còn cần hoàn thiện

F01, F03, F05, F06; phần bài tập và tài khoản học sinh P1; xác thực đầy đủ; cấp quyền hỗ trợ có thời hạn; cấu hình hạ tầng, bảo mật và quan sát; kiểm thử Android và người dùng thật. Các phần này giữ nguyên trong kế hoạch tổng thể, không được suy ra đã hoàn tất từ hồ sơ F02/F04.

## 3 Mô hình dữ liệu vật lý

Các nhóm E01–E22 kế thừa TB G3 02. Khóa UUID do backend sinh. Thời điểm lưu bằng timestamptz; hiển thị và ngày tính quota theo Asia/Ho_Chi_Minh. SQL và từ điển dữ liệu đi kèm mô tả đủ cột, kiểu, khóa và ràng buộc.

Bảng 4 Đối chiếu nhóm E01 đến E08

| Nhóm | Bảng PostgreSQL | Vai trò |
|---|---|---|
| E01 | accounts auth_sessions | Tài khoản và phiên; schema nền không thay mô đun auth hoàn chỉnh. |
| E02 | students student_links | Một phụ huynh quản lý hồ sơ con; liên kết học sinh riêng ở P1. |
| E03 | tutor_profiles tutor_verifications | Hồ sơ có phiên bản, phạm vi xác minh và bằng chứng riêng. |
| E04 | calendars availability_windows | Phiên bản lịch; vật chất hóa cửa sổ rảnh, ưu tiên và chặn. |
| E05 | learning_needs need_versions | Nháp, bản xác nhận và con trỏ phiên bản hiện hành. |
| E06 | matching_runs matching_candidates | S hoặc B, cấu hình và snapshot ứng viên sau lọc cứng. |
| E07 | class_offers offer_sessions | Điều khoản và toàn chuỗi lịch bất biến khi đã gửi. |
| E08 | classes | Một lớp cho một nhu cầu; snapshot giá và bên tham gia. |

## 3 Mô hình dữ liệu vật lý tiếp theo

Các nhóm E01–E22 kế thừa TB G3 02. Khóa UUID do backend sinh. Thời điểm lưu bằng timestamptz; hiển thị và ngày tính quota theo Asia/Ho_Chi_Minh. SQL và từ điển dữ liệu đi kèm mô tả đủ cột, kiểu, khóa và ràng buộc.

Bảng 5 Đối chiếu nhóm E09 đến E15

| Nhóm | Bảng PostgreSQL | Vai trò |
|---|---|---|
| E09 | class_goal_versions | Mục tiêu và xác nhận hai bên; một bản hiệu lực. |
| E10 | lesson_sessions resource_bookings | Buổi học và hai lịch đặt của gia sư, học sinh. |
| E11 | lesson_log_versions | Phiên bản nhận xét; điều kiện xác nhận và tranh chấp. |
| E12 | materials material_versions | Tệp riêng, kiểm tra, xử lý, sẵn sàng và epoch. |
| E13 | source_segments | Đoạn nguồn ổn định theo phiên bản và vị trí. |
| E14 | material_grants | Quyền gốc, tải, AI và dẫn xuất; đối tượng, căn cứ, hạn. |
| E15 | preparation_contexts context_sources context_logs | Bối cảnh cố định đúng lớp, buổi, mục tiêu và nguồn. |

## 3 Mô hình dữ liệu vật lý tiếp theo

Các nhóm E01–E22 kế thừa TB G3 02. Khóa UUID do backend sinh. Thời điểm lưu bằng timestamptz; hiển thị và ngày tính quota theo Asia/Ho_Chi_Minh. SQL và từ điển dữ liệu đi kèm mô tả đủ cột, kiểu, khóa và ràng buộc.

Bảng 6 Đối chiếu nhóm E16 đến E22

| Nhóm | Bảng PostgreSQL | Vai trò |
|---|---|---|
| E16 | ai_jobs ai_attempts worker_slots | Job, lần thử, lease, fencing và giới hạn chạy. |
| E17 | ai_policies quota_counters budget_ledgers ai_reservations | Cấu hình, lượt và tiền; mặc định AI thật tắt. |
| E18 | lesson_plans plan_versions | Aggregate, revision và phiên bản nội dung. |
| E19 | plan_activities plan_dependencies plan_citations | Hoạt động, thời lượng, nguồn phụ thuộc và dẫn chứng. |
| E20 | plan_publications private_plan_answers answer_releases | Con trỏ công bố và đáp án ở vùng quyền riêng. |
| E21 | command_receipts | Đối soát kết quả và chống lặp theo actor, command, key. |
| E22 | outbox_events notifications audit_events | Sự kiện tin cậy, thông báo trong hệ thống và dấu vết. |

Hai bảng hỗ trợ bổ sung là revocation_ledger và deletion_ledger. Chúng lưu bằng chứng thu hồi và yêu cầu xóa để đối soát bất đồng bộ, đồng thời áp lại khi khôi phục backup. Tổng cộng schema có 47 bảng; 22 mã E là nhóm nghiệp vụ, không phải số bảng.

Các sơ đồ ở mục 4 chỉ trình bày quan hệ trọng yếu. ERD toàn bộ và bốn miền ở thư mục erd của gói kỹ thuật; không dùng sơ đồ rút gọn thay cho DDL.

## 4 Quan hệ vật lý của lịch lớp

Hình 1 Quan hệ trọng yếu của nhu cầu lớp buổi và lịch đặt

Giá và điều khoản được cố định ở đề nghị và lớp. UNIQUE trên originating_need_id và accepted_offer_id ngăn tạo hai lớp từ cùng nhu cầu hoặc đề nghị. Mỗi buổi sinh hai booking; loại tài nguyên cùng ID và khoảng thời gian là khóa xung đột toàn hệ thống.

Khoảng thời gian dùng dạng đóng đầu mở cuối [start, end); hai buổi sát biên được phép, buffer trực tuyến bằng 0. Giới hạn bốn khối lịch áp dụng khi gửi và chốt chu kỳ đầu, không cấm lớp có hủy hoặc học bù sau này.

## 4 Quan hệ vật lý của học liệu và bối cảnh

Hình 2 Quan hệ trọng yếu của phiên bản nguồn quyền và bối cảnh

Mỗi đoạn nguồn gắn với phiên bản và locator ổn định. Khóa ngoại ghép ID với class_id ngăn liên kết nguồn hoặc buổi ở lớp khác. Bối cảnh cố định tối đa năm nguồn cùng epoch và các nhật ký đủ điều kiện; không trộn phiên bản mục tiêu hoặc dữ liệu chưa xác nhận.

Các cột version_id trong hình là tên rút gọn của material_version_id. Quyền xem gốc, tải gốc, dùng AI và đọc dẫn xuất là bốn khả năng độc lập. Gia sư phải ghi căn cứ chia sẻ dẫn xuất cho phạm vi lớp; không suy ra quyền này từ việc tải tệp lên.

## 4 Quan hệ vật lý của bài soạn và công bố

Hình 3 Quan hệ trọng yếu của phiên bản bài soạn và con trỏ công bố

lesson_plans là aggregate theo buổi; plan_versions quản lý nội dung. Nội dung đã duyệt bất biến. Sửa bản đã duyệt hoặc công bố tạo bản nháp mới; con trỏ công bố giữ bản hợp lệ cũ đến khi thay thế thành công.

plan_dependencies lưu mọi nguồn có ảnh hưởng đến nội dung, không chỉ nguồn có citation. Đáp án dự kiến ở private_plan_answers; API công bố không có trường đáp án. Mở đáp án cho bài tập thuộc UC12 ở P1, chưa thuộc 40 thao tác trong gói API này.

## 5 Ràng buộc và phạm vi bảo vệ

Bảng 7 Phân tầng bảo vệ bất biến

| Bất biến | Bảo vệ tại DB | Kiểm tra bắt buộc tại service |
|---|---|---|
| Một lớp mỗi nhu cầu | UNIQUE lớp theo need và offer. | Khóa need; kiểm quyền và trạng thái; hủy đề nghị cạnh tranh trong TX05. |
| Không trùng lịch | GiST exclusion; trigger tạo hai booking. | Khóa lịch/tài nguyên ổn định; kiểm rảnh cả chuỗi và phiên bản hiện hành. |
| Đủ lịch ban đầu | Constraint trigger kiểm bốn khối và hạn đề nghị. | Chốt lớp tạo đủ 4 nhân tần suất; một lỗi rollback toàn bộ. |
| Không trộn lớp | FK ghép cho buổi, nguồn, context và bài soạn. | Scope actor theo lớp; kiểm tutor/profile và chủ sở hữu tài nguyên đa hình. |
| Nội dung đã duyệt | Trigger chặn sửa; tổng phút đúng lớp khi duyệt. | Kiểm cảnh báo, ngữ nghĩa Toán, quyền nguồn và phiên bản trước duyệt. |
| Nguồn và công bố | Epoch, dependency, publication guard khóa nguồn. | Mỗi lần đọc kiểm quyền hiện hành; thu hồi tăng epoch cùng ledger và outbox. |
| Giới hạn nguồn | Guard 20 phiên bản hoạt động, 5 nguồn mỗi context. | Khóa class hoặc context trước ghi; kiểm byte thật, MIME, công thức và quyền. |
| Quota và chi phí | Bảng counter, reservation, slot và unique tutor. | Khóa hàng, reserve tiền/lượt, deadline, fencing và đối soát provider. |

Hai migration chưa được compile và thực thi trên PostgreSQL trong lần bàn giao này. Kiểm tra parser chỉ xác nhận cú pháp ngoài thân PL/pgSQL; không xác nhận trigger, tính đồng thời hay hiệu năng. Phải chạy migration, integration test và ma trận đồng thời trước khi nhập nhánh backend chính.

Role migration tách role runtime. Runtime không owner hoặc superuser, không DDL, không sửa trực tiếp booking và không cho client thực thi helper SQL. GRANT chi tiết, RLS nếu bổ sung và cấp quyền hỗ trợ theo vụ việc phải được review cùng mô đun danh tính trước triển khai.

## 6 Giao dịch chốt lớp F02

F02 giữ nguyên điều kiện giá VND trên 60 phút, ngân sách phụ huynh trên mỗi buổi; giá buổi làm tròn lên theo ceil(rate × D / 60). Thời lượng 60, 90 hoặc 120 phút; tần suất 1–3 trong mỗi khối 7 ngày; chu kỳ đầu 28 ngày.

Bảng 8 Hợp đồng giao dịch F02

| Giao dịch | Xử lý bắt buộc |
|---|---|
| TX01–TX02 | Lưu nháp và xác nhận khác nhau. Bản xác nhận cần đủ dữ liệu cứng; ngày đầu từ hôm nay đến hôm nay cộng 28 theo giờ Việt Nam; từng buổi ở tương lai. |
| TX03 | Lọc đúng phạm vi, tài khoản, hồ sơ, giá và lịch cả chuỗi trước chấm điểm. Tối đa 5 hồ sơ thật; không nới điều kiện. AI lỗi chỉ sang B khi phụ huynh chủ động chọn. |
| TX04 | Khóa nhu cầu; kiểm phiên bản và giá hiện hành; cố định mục tiêu, giá, đầy đủ buổi. Hạn phản hồi là nhỏ hơn giữa 48 giờ và giờ bắt đầu buổi đầu. Chờ chưa giữ chỗ. |
| TX05 bước 1 | Kiểm actor; khóa receipt rồi need và offer; khóa calendar và tài nguyên theo thứ tự ổn định. Tái kiểm tài khoản, phê duyệt, giá, phiên bản và tất cả khoảng lịch. |
| TX05 bước 2 | Tạo một lớp, mục tiêu đầu, toàn bộ buổi và hai booking mỗi buổi; accept đề nghị; kết thúc đề nghị cạnh tranh; đóng need; ghi outbox và receipt trong cùng transaction. |
| TX05 bước 3 | Một buổi lỗi thì rollback toàn bộ. Chỉ trả thành công sau commit. Exclusion và unique là chốt DB qua hai API replica; không thay bằng khóa RAM hoặc nút bị vô hiệu. |
| TX06 | Hủy, từ chối và hết hạn chỉ áp dụng đề nghị chờ. Chấp nhận là đồng ý nguyên bản; thay điều kiện cần đề nghị mới. |

### 6.1 Công thức và nhãn kết quả

S = 0.60s + 0.25e + 0.15a; B = 0.625e + 0.375a. s = clip((cos + 1) / 2, 0, 1); e = min(tháng kinh nghiệm, 60) / 60; a là tỷ lệ khung ưu tiên khớp, không có ưu tiên thì 0.5. Làm tròn 6 chữ số; hòa S ưu tiên s, e, ID gia sư tăng; hòa B ưu tiên e, ID tăng. Điểm không phải xác suất tiến bộ học tập.

## 7 Giao dịch học liệu và bài soạn F04

Bảng 9 Hợp đồng giao dịch F04

| Giao dịch | Xử lý và điều kiện chuyển trạng thái |
|---|---|
| TX07 | Upload riêng theo ticket hạn tối đa 60 giây. Đếm cả UPLOADING và PROCESSING trong giới hạn 20 phiên bản hoạt động mỗi lớp. Xác minh object thật trước ghi nhận hoàn tất; dọn orphan có đối soát. |
| TX08 | PDF có văn bản hoặc DOCX tối đa 10 MiB; PDF tối đa 100 trang, tối đa 200.000 ký tự trích xuất. Từ chối OCR, macro, mã hóa, tệp hỏng hoặc giả MIME. Gia sư kiểm văn bản và công thức trước READY. |
| TX09 | Khóa nguồn, thu hồi grant, tăng epoch, ghi ledger và outbox trong cùng commit. Chặn dẫn xuất ở lần đọc, duyệt, công bố và trả kết quả AI; dọn cache/index bất đồng bộ. |
| TX10 | Cố định lớp, buổi, mục tiêu, topic, thời lượng, tối đa 5 nguồn và các phiên bản nhận xét đủ xác nhận. Reserve quota, tiền và queue trước tạo job. |
| TX11 | Lease và fencing kiểm soát worker. Kiểm lại nguồn tại retrieval, gửi provider, nhận/lưu/trả kết quả. AI lỗi có soạn thủ công; kết quả muộn không đè nháp đã sửa. |
| TX12 | Nháp có revision. Bản đã duyệt hoặc công bố được sửa bằng phiên bản mới. Nguồn phụ thuộc bao phủ cả nội dung lấy từ nguồn, kể cả khi không có citation. |
| TX13–TX14 | Gia sư duyệt đúng bản sau kiểm Toán, nguồn, cảnh báo và tổng phút bằng thời lượng. Công bố kiểm lại quyền rồi thay con trỏ nguyên tử cùng outbox và receipt. |
| TX15–TX16 | Đọc theo DTO và quyền hiện hành. Đối soát thao tác chỉ trong scope actor; kết quả cũ bị mất quyền không được trả lại nội dung riêng. |

Không coi upload, extraction/index sẵn sàng, duyệt nguồn, duyệt bài và công bố là cùng trạng thái. DB và kho tệp là quy trình theo chặng có cleanup, không được mô tả là một giao dịch phân tán nguyên tử.

Lớp chờ bắt đầu hoặc đang học được soạn; lớp đã kết thúc không nhận AI hoặc công bố mới. Soạn trước buổi đầu không cần nhật ký giả. Nhật ký đưa vào context phải có gia sư xác nhận chuyên môn, phụ huynh xác nhận tham gia và không tranh chấp.

## 8 Hợp đồng API và xử lý gửi lặp

OpenAPI 3.1.1 là hợp đồng được kiểm tra cấu trúc, chưa phải endpoint hoạt động. Server trong đặc tả dùng miền example.invalid làm placeholder. Tài liệu YAML và JSON tương đương, dùng cùng 45 schema. Danh mục đủ đường dẫn và operationId nằm trong gói kỹ thuật.

Bảng 10 Danh mục nhóm API

| Nhóm API | Số thao tác | Phạm vi |
|---|---|---|
| Nhu cầu và matching | 8 | Đọc con/nhu cầu; tạo, sửa, xác nhận; tạo và đọc matching run. |
| Gia sư công khai | 1 | Chỉ hồ sơ duyệt, không bằng chứng gốc hoặc liên hệ riêng. |
| Đề nghị | 6 | Gửi, danh sách, chi tiết, accept, reject và cancel. |
| Lớp và lịch | 2 | Thỏa thuận lớp và danh sách buổi theo quyền. |
| Học liệu và quyền | 10 | Danh sách; upload; hoàn tất; trạng thái; segment; ready; grant; revoke; original. |
| Bối cảnh và AI | 5 | Availability; context; tạo, đọc và hủy job. |
| Bài soạn và công bố | 6 | Tạo, đọc, sửa, duyệt version; công bố và đọc publication. |
| Đối soát command | 2 | Theo operation ID hoặc cặp commandType và key trong scope actor. |

### 8.1 Quy ước phiên bản và kết quả

Bearer actor do server xác thực. Mọi lệnh mutation cần Idempotency Key; lệnh sửa hoặc quyết định trên phiên bản cần If Match. Thiếu điều kiện trả 428; bản cũ trả 412. ETag của bản nháp là revision của version; planRevision dùng cho công bố theo aggregate. Client giữ phần nhập hợp lệ khi có lỗi.

Receipt được giữ tối thiểu 7 ngày theo actor, command và key; payload khác trả 409. Replay phải tái kiểm quyền rồi xử lý receipt trước điều kiện phiên bản hiện tại. Mất phản hồi mạng phải hỏi trạng thái hoặc gửi lại cùng key, không tạo key mới và đoán thành công. Business unique vẫn tồn tại sau khi receipt được dọn.

API publication dùng projection không có đáp án; Cache Control private no store. Original/segment chỉ đọc khi có quyền tương ứng. Chi tiết mã lỗi và cách client xử lý nằm trong api/errors.md.

## 9 Ma trận quyền và vòng đời nguồn

Bảng 11 Ma trận quyền theo chủ thể

| Chủ thể | Đề nghị và lớp | Nháp và đáp án | Công bố và nguồn |
|---|---|---|---|
| Phụ huynh | Đúng con mình; gửi/hủy đề nghị; đọc lớp của con. | Không đọc nháp hoặc đáp án dự kiến của gia sư. | Đọc công bố còn quyền dẫn xuất; không tự có quyền gốc. |
| Gia sư | Nhận đề nghị gửi đến; đúng lớp được giao. | Soạn, sửa, duyệt và công bố trong trạng thái hợp lệ. | Quản lý nguồn mình tải; cấp/thu hồi theo căn cứ và phạm vi. |
| Học sinh P1 | Liên kết chủ động còn hiệu lực; chỉ lớp mình. | Không đọc nháp hoặc đáp án chưa mở. | Đọc công bố theo quyền hiện hành; quyền gốc tách riêng. |
| Người ngoài lớp | Không có quyền từ việc biết ID. | Không có quyền. | Không có quyền. |
| Quản trị | Metadata phục vụ quản trị đúng vai trò. | Không mặc định đọc nội dung riêng. | Nội dung hỗ trợ cần vụ việc và grant đúng phạm vi, tối đa 24 giờ. |
| Worker | Danh tính dịch vụ và scope job. | Chỉ context đã tối thiểu hóa, không tự công bố. | Kiểm epoch và quyền ở mọi mốc; chỉ ghi kết quả với fencing hợp lệ. |

### 9.1 Tác động thu hồi

Công bố và thu hồi khóa cùng phiên bản nguồn. Nếu công bố commit trước, thu hồi sau đó khiến lần đọc tiếp theo bị chặn; nếu thu hồi commit trước, công bố bị từ chối. Middleware đọc kiểm quan hệ lớp, trạng thái nguồn, grant, thời hạn và epoch trước khi trả nội dung hoặc cache.

Phụ huynh có quyền đọc dẫn xuất vẫn xem công bố hợp lệ khi không được xem tệp gốc. Ngược lại, quyền đọc dẫn xuất không cho phép đọc đoạn nguồn thô. Khi bỏ nguồn, phải loại nội dung chịu ảnh hưởng rồi duyệt bản mới; đổi nhãn sang thủ công không tự xóa nghĩa vụ quyền.

Thu hồi không thu lại được byte đã truyền hoặc bản người dùng đã tải. API gốc dùng proxy xác thực cho từng request, kể cả range, không phát URL GET công khai hoặc dài hạn. Cleanup bất đồng bộ phải được theo dõi mà không trì hoãn chặn đọc.

## 10 Kiểm soát AI và vận hành

Bảng 12 Giới hạn AI và chi phí

| Giới hạn | Giá trị và cách áp dụng |
|---|---|
| Lượt mỗi lớp | 5 lần thử mỗi ngày; ngày theo giờ Việt Nam. |
| Lượt mỗi gia sư | 20 lần thử mỗi ngày; khóa counter khi reserve. |
| Đồng thời | Tối đa 2 job toàn cục và 1 job mỗi gia sư; slot và fencing trong DB. |
| Hàng đợi | Tối đa 20 job chờ; kiểm trong cùng transaction nhận job. |
| Deadline | 120 giây từ lúc nhận, gồm thời gian queue; không dùng phần trăm tiến độ giả. |
| Tiền | AI thật tắt khi chưa có provider, model, chi phí ước tính và trần tiền. Chi phí chưa rõ giữ reservation để đối soát. |
| Gửi lại | Cùng receipt không tính thêm lượt; lần thử mới có attempt và reservation mới. Không resend provider mù khi kết quả chưa rõ. |
| Dữ liệu gửi AI | Topic, mục tiêu và dữ liệu chuyên môn tối thiểu; mã giả, không tên trẻ/liên hệ không cần thiết. |

Worker claim slot và job bằng khóa DB; lease hữu hạn, fencing tăng khi claim lại. Kết quả cũ phải bị từ chối. Provider có thể còn chạy khi lease hết; không lập tức tái gửi. Giữ slot cách ly và chi phí UNKNOWN cho đến khi đối soát đủ để bảo vệ giới hạn chạy và tiền.

Dữ liệu nguồn là đầu vào không tin cậy. Không thực thi chỉ dẫn trong học liệu để thay prompt, quyền hoặc công cụ. Kết quả AI cần kiểm schema, nguồn, tổng phút và nội dung Toán; luôn là nháp để gia sư duyệt. Chưa lựa chọn nhà cung cấp hoặc đo chất lượng AI thật.

### 10.1 Lưu giữ và phục hồi

Theo SRS, audit mục tiêu 180 ngày; học liệu và dẫn xuất 90 ngày sau kết thúc; nháp chưa dùng 30 ngày và nhắc trước 7 ngày; xóa dữ liệu hoạt động tối đa 7 ngày. Backup 7 ngày, RPO không quá 1 giờ và RTO không quá 4 giờ là mục tiêu cần đo trên DB cùng tệp. Restore phải áp lại ledger xóa và thu hồi trước mở traffic. Các mốc này là chính sách dự án, chưa phải chứng nhận tuân thủ pháp luật.

## 11 Hướng dẫn rà soát prototype

Địa chỉ prototype riêng: https://tutorbridge-g3-f02-f04.jenny-180820.chatgpt.site. Tài khoản truy cập giữ theo quyền hiện tại. Giao diện có khung ứng dụng, danh mục 17 màn hình, bộ chọn kịch bản và dấu vết giao dịch mô phỏng. Bộ điều khiển vai trò không phải cơ chế xác thực của sản phẩm.

Bảng 13 Kịch bản rà soát prototype

| Bài rà soát | Các bước và dấu hiệu đạt |
|---|---|
| F02 luồng chính | Làm lại; chọn con; nhập và xác nhận nhu cầu; xem gia sư; xem giá và đủ lịch; gửi; chuyển góc nhìn gia sư; chấp nhận. Kết quả là một lớp và đủ buổi mẫu. |
| F04 luồng chính | Từ lớp, mở học liệu; kiểm nguồn; xác nhận READY; tạo job mẫu; kiểm tra trạng thái đến nháp; chỉnh, duyệt rồi công bố. Phụ huynh chỉ thấy bản công bố. |
| Rỗng hoặc AI lỗi | Chọn kịch bản tương ứng. Không xuất hồ sơ giả khi rỗng; phương án B chỉ xuất hiện sau lựa chọn rõ và có nhãn. |
| Xung đột hoặc hết hạn | Chấp nhận đề nghị trong kịch bản. Không có lớp khi một buổi lỗi hoặc hạn đã qua. |
| Chưa rõ kết quả gửi | Dùng nút kiểm tra trạng thái thao tác; tìm đúng đề nghị đã gửi, không tạo bản thứ hai. |
| AI tắt hoặc hết lượt | Không có job mới; có đường soạn thủ công. Sai tổng phút phải báo lỗi và giữ phần nhập. |
| Thu hồi hoặc ngoài quyền | Bản công bố bị tạm ẩn sau thu hồi. Vai trò ngoài lớp không xem được nháp hoặc đáp án. |

Chọn trực tiếp một màn hình trong thanh rà soát có thể nạp sẵn dữ liệu điều kiện và đổi vai trò mẫu để phục vụ họp. Muốn kiểm tra đủ hành trình, bấm Làm lại luồng chính và đi qua các nút trong khung ứng dụng. Dữ liệu mất khi tải lại; tệp chỉ kiểm tên và kích thước tại trình duyệt, không upload hay extraction thật.

Prototype đã được kiểm tra trên trình duyệt với khung màn hình ứng dụng. Chưa thực hiện kiểm tra trên thiết bị Android thật, kiểm thử người dùng hoặc kiểm tra toàn diện trợ năng. Nguồn prototype được lưu theo phiên bản riêng; ZIP kỹ thuật chỉ chứa các tài liệu thiết kế và kiểm tra liên quan.

## 12 Kết quả kiểm tra và giới hạn bằng chứng

Bảng 14 Kết quả kiểm tra bàn giao

| Nội dung kiểm tra | Kết quả thực tế | Phạm vi kết luận |
|---|---|---|
| SQL parser | Đạt; 69 câu lệnh ở migration 001, 33 ở migration 002. | Cú pháp ngoài thân hàm; không xác nhận compile hoặc chạy trigger. |
| Khóa ngoại tĩnh | Đạt; 88 FK có đích PK hoặc UNIQUE phù hợp. | Kiểm cấu trúc tĩnh; không thay kiểm dữ liệu và giao dịch. |
| OpenAPI | Đạt; 40 operation ID duy nhất, 503 tham chiếu nội bộ phân giải. | Đặc tả hợp lệ 3.1.1; endpoint chưa được triển khai. |
| DTO công bố | Đạt kiểm cấu trúc; không có privateDraftAnswer. | Cần kiểm response/log/cache trên backend thật. |
| Mô hình prototype | 20 kiểm tra Node đạt. | Giá, S/B, lịch, gửi lặp, version, xung đột, quota, thu hồi, đáp án và kết quả muộn. |
| UI trình duyệt | Đã đi hai luồng chính và 8 nhánh ngoại lệ; kiểm giữ giá trị nhập và chặn vai trò. | Mô phỏng dữ liệu; không chứng minh isolation DB, auth thật hoặc AI thật. |
| SQL thực thi | Chưa chạy; đã có candidate test script. | Cần PostgreSQL 17 thực tế; gồm booking, sát biên, rollback và bất biến nội dung. |
| Đồng thời và tải | Chưa chạy; có 16 tình huống triển khai. | Hai replica, DB độc lập, worker crash, restore, quota và epoch. |
| Người dùng và Android | Chưa chạy. | Cần người dùng mục tiêu, thiết bị thật, trợ năng và NFR tổng thể. |

Bằng chứng nằm tại tests/static_validation.json, tests/prototype_model_results.txt và tests/VERIFICATION.md. tests/postgresql_integrity.sql là script ứng viên chưa thực thi. Không ghi đạt cho SQL runtime, kiểm thử bảo mật, hiệu năng hoặc khả năng sử dụng trước khi có kết quả đo.

### 12.1 Các lỗi cần chặn trước triển khai

Migration không compile; DML vượt quyền hoặc trộn lớp; tạo lớp/buổi một phần; trùng booking khi đồng thời; lộ nháp/đáp án; đọc được dẫn xuất sau thu hồi; vượt quota hoặc trần tiền; worker cũ ghi đè nháp. Bất kỳ lỗi nào trong nhóm này đều chặn bàn giao backend để thí điểm.

## 13 Trạng thái rà soát và công việc tiếp theo

Bộ thiết kế F02/F04 đủ nội dung để Đính và Huy rà soát và chuẩn bị coding. Chưa đủ căn cứ nghiệm thu toàn bộ G3. Đính đã đồng ý DP01–DP04; không suy ra Đức Huy đã phê duyệt thay cho xác nhận riêng.

Bảng 15 Công việc và điều kiện còn mở

| Hạng mục còn mở | Chủ trì theo phương án G3 | Đầu ra cần có |
|---|---|---|
| Rà soát nghiệp vụ và dữ liệu | Đính chủ trì; Huy rà soát chéo. | Biên bản chấp nhận hoặc danh sách sửa ERD, TX05, ACL và con trỏ công bố. |
| Rà soát matching AI và học liệu | Huy theo phương án; Đính rà soát chéo. | Công thức và bối cảnh thống nhất; provider/model/cost cap được chọn có căn cứ; cấu hình AI off đến khi đủ điều kiện. |
| Prototype với người dùng | Đính và Huy thống nhất người tham gia và lịch. | Ghi nhận quan sát thực tế, lỗi luồng và ưu tiên sửa; không tạo kết quả thử nghiệm thay người dùng. |
| Kiểm chứng kỹ thuật đầu tiên | Đính phần Spring Boot và DB; Huy phần worker/mobile theo phương án. | DB thử nghiệm; migration compile; test integration và hai replica; một lát cắt F02 chạy thật trước mở rộng. |
| Đóng thiết kế tổng thể | Hai thành viên xác nhận phạm vi và nguồn lực. | F01/F03/F05/F06, kiến trúc và NFR; giờ/tuần, ngân sách, mốc; quyết định Gate G3 theo checklist chung. |

Phân công trên kế thừa TB G3 01, chưa xác lập ngày hoàn thành hoặc giờ làm của từng người. Xác nhận của Đức Huy và nguồn lực định lượng được ghi bổ sung khi có thông tin thực tế; không coi ô chờ là một phê duyệt.

### 13.1 Tài liệu kỹ thuật tham khảo

[1] OpenAPI Initiative. OpenAPI Specification 3.1.1. https://spec.openapis.org/oas/v3.1.1.html. Truy cập ngày 29/10/2026.

[2] PostgreSQL Global Development Group. PostgreSQL 17 Range Types và btree_gist. https://www.postgresql.org/docs/17/rangetypes.html và https://www.postgresql.org/docs/17/btree-gist.html. Truy cập ngày 29/10/2026.

[3] PostgreSQL Global Development Group. PostgreSQL 17 CREATE TRIGGER. https://www.postgresql.org/docs/17/sql-createtrigger.html. Truy cập ngày 29/10/2026.
