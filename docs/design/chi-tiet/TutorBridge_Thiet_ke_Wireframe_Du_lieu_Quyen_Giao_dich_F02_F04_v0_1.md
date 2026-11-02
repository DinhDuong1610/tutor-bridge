# TutorBridge_Thiet_ke_Wireframe_Du_lieu_Quyen_Giao_dich_F02_F04_v0_1

> Tài liệu lịch sử, độ ưu tiên thấp nhất. Bản Word kèm wireframe ở `docs/source-docx/g3/TutorBridge_Thiet_ke_Wireframe_Du_lieu_Quyen_Giao_dich_F02_F04_v0_1.docx`. Hình ảnh không có trong bản này.

NHÓM DỰ ÁN TUTORBRIDGE

THIẾT KẾ WIREFRAME

DỮ LIỆU QUYỀN VÀ GIAO DỊCH

HAI LUỒNG F02 VÀ F04

Dự án TutorBridge  |  Hồ sơ thiết kế giải pháp giai đoạn G3

| Thông tin kiểm soát | Nội dung |
|---|---|
| Mã tài liệu | TB G3 02 |
| Phiên bản | 0.1 |
| Ngày phát hành | 29/10/2026 |
| Nhóm thực hiện | Dương Đính và Đức Huy |
| Mục đích sử dụng | Rà soát trải nghiệm, dữ liệu, quyền và tính nhất quán trước khi hoàn thiện ERD và OpenAPI. |
| Trạng thái | Thiết kế đề xuất để Dương Đính và Đức Huy rà soát; chưa nghiệm thu G3. |

Căn cứ: PRD và SRS TB G2 02 v1.1; danh mục màn hình TB G3 01 v0.1. Mã màn hình và phạm vi R0 được giữ thống nhất với hồ sơ yêu cầu.

## Mục lục

 1 Mục tiêu phạm vi và tình huống minh họa 3

 2 Chỉ mục màn hình và truy vết 4

 3 Wireframe cho luồng F02 và F04 5

 4 Thực thể dữ liệu dự kiến 18

 5 Quan hệ dữ liệu và bất biến 22

 6 Ma trận quyền theo đối tượng 23

 7 Quyền nguồn dẫn xuất và tác vụ nền 24

 8 Giao dịch của luồng tìm và chốt lớp 25

 9 Giao dịch của luồng học liệu và soạn bài 27

 10 Giao dịch duyệt công bố và truy xuất 29

 11 Kịch bản rà soát thiết kế 30

 12 Quyết định cần rà soát và công việc tiếp theo 32

Mục 3 chứa 26 khung nhìn của 17 mã màn hình, bao gồm 9 biến thể trạng thái. Mục 4 đến mục 10 mô tả dữ liệu, quyền và giao dịch; mục 11 và mục 12 dùng để ghi nhận kết quả rà soát tiếp theo.

## 1 Mục tiêu phạm vi và tình huống minh họa

Hồ sơ chuyển hai luồng ưu tiên của TutorBridge sang wireframe tĩnh và thiết kế nghiệp vụ có thể đối chiếu. F02 đi từ chọn học sinh, xác nhận nhu cầu, tìm gia sư đến chốt lớp/lịch. F04 đi từ tiếp nhận học liệu, kiểm tra nguồn đến soạn nháp, duyệt và công bố đúng quyền.

Đầu ra gồm 17 mã màn hình, 26 khung nhìn, 22 nhóm thực thể logic, ma trận quyền, 16 hợp đồng giao dịch và 16 kịch bản rà soát. Thiết kế dữ liệu là cơ sở để xây ERD vật lý và hợp đồng API; các nhóm thực thể có thể tách thành nhiều bảng khi triển khai.

### 1.1 Trạng thái và cách sử dụng

Dương Đính chuẩn bị wireframe và phần dữ liệu/quyền/giao dịch để cùng Đức Huy rà soát. Trạng thái xác nhận G2 tiếp tục theo hồ sơ hiện hành; G3 chưa nghiệm thu. DP01–DP04 ở mục 12 là các đề xuất triển khai cần rà soát.

Wireframe là bản tĩnh dùng dữ liệu minh họa, chưa phải giao diện hoàn thiện hoặc kết quả thử người dùng. Đăng nhập học sinh/bài tập P1 vẫn thuộc R0 đầy đủ; hồ sơ này chỉ thể hiện ranh giới quyền liên quan. Bản hình không thực hiện giao dịch hoặc gọi AI.

### 1.2 Kịch bản dữ liệu dùng xuyên suốt

| Thuộc tính | Giá trị minh họa |
|---|---|
| Người học và phạm vi | Học sinh mẫu A; 1–1 Toán 8 đại số trực tuyến, tiếng Việt. |
| Nhu cầu | 90 phút; 2 buổi/tuần; trần 200.000 VND/buổi; chu kỳ 02/11–29/11/2026. |
| Gia sư mẫu A | 120.000 VND/60 phút; 180.000 VND/90 phút; kinh nghiệm 24 tháng tự khai. |
| 8 buổi cụ thể | 02, 05, 09, 12, 16, 19, 23 và 26/11/2026; 18:00–19:30, giờ Việt Nam. |
| Đề nghị mẫu | Gửi 29/10/2026 lúc 10:00; hết hạn 31/10/2026 lúc 10:00, trước buổi đầu. |
| Học liệu và AI | TL01 bản 1; trích đoạn mẫu trang 2. Hình AI sẵn sàng giả định đã có quyền/cấu hình/trần tiền hợp lệ; thực tế chưa chốt trần thì AI tắt. |

### 1.3 Tài liệu căn cứ

Các nguồn cùng ngày 29/10/2026: [S1] TB G2 02 v1.1, mục 2–9/12; [S2] TB G3 01 v0.1, mục 5–8/11–13; [S3] TB G2 03 v1.2; [S4] Chỉ đạo của Dương Đính về chuẩn bị F02/F04. Thay đổi yêu cầu đi qua kiểm soát phạm vi cơ sở.

## 2 Chỉ mục màn hình và truy vết

Mã hình giữ theo [S2]. Những khung có hậu tố trạng thái trong mục 3 là biến thể của cùng màn hình, không tăng số mã màn hình. FR-37 áp dụng trạng thái/lỗi/thử lại toàn bộ; sự kiện đánh giá FR-38 được ghi theo quyền và cấu hình UC13.

| Mã | Vai trò và nhiệm vụ | Yêu cầu | Giao dịch |
|---|---|---|---|
| PH02 | Phụ huynh chọn/quản lý con | UC02; FR-03/36 | TX01 |
| PH03 | Danh sách và phiên bản nhu cầu | UC04; FR-04 | TX01 |
| PH04 | Nhập điều kiện và lịch tìm | UC04/07; FR-04/19 | TX01/02 |
| PH05 | Xem lại/xác nhận nhu cầu | UC04; FR-04 | TX02 |
| PH06 | Gợi ý, rỗng và AI lỗi | UC05; FR-09–14 | TX03 |
| PH07 | Xem hồ sơ công khai | UC03/05; FR-05–07/12 | TX03 |
| PH08 | Xem lại và gửi đề nghị | UC06; FR-15/17 | TX04 |
| PH09 | Theo dõi/hủy/tra cứu gửi | UC06; FR-15–17 | TX06/16 |
| GS05 | Gia sư nhận hoặc từ chối | UC06; FR-16/17 | TX05/06 |
| CL01 | Lớp và thỏa thuận đã chốt | UC04/07; FR-17–19 | TX05 |
| CL02 | Lịch/buổi đã cam kết | UC07; FR-19 | TX05/15 |
| GS06 | Gia sư tiếp nhận học liệu | UC10; FR-21/22 | TX07 |
| GS07 | Kiểm tra và quản lý nguồn | UC10/16; FR-22/23/42 | TX08/09 |
| GS08 | Bối cảnh và tác vụ soạn | UC11; FR-24–26/28/30 | TX10/11 |
| GS09 | Biên tập, nguồn và nháp | UC11; FR-26–30 | TX11/12 |
| GS10 | Duyệt rồi công bố bản | UC11; FR-27–29 | TX13/14 |
| CL06 | Đọc bản công bố trong quyền | UC11/16; FR-29/42 | TX09/15 |

NFR01–09 áp dụng quyền, nguồn, đồng thời, nháp và audit; NFR10–16 áp dụng hiệu năng, khôi phục, trợ năng, quota, mở rộng và theo dõi. Wireframe chỉ thể hiện hành vi nhìn thấy; việc đáp ứng NFR phải được kiểm chứng trên triển khai sau này.

## 3 Wireframe cho luồng F02 và F04

### 3.1 Chọn học sinh và quản lý nhu cầu

Hình 1 PH02 và PH03 trong tình huống minh họa

Truy vết: PH02, PH03; UC02/UC04; FR-03, FR-04, FR-36.

Phụ huynh chọn rõ hồ sơ con rồi tạo hoặc tiếp tục nhu cầu. Mọi truy vấn kiểm tra quan hệ quản lý; không nhận owner_id từ biểu mẫu. E02 và E05; lệnh lưu nháp TX01.

## 3 Wireframe tiếp theo

### 3.2 Nhập và xác nhận điều kiện tìm

Hình 2 PH04 và PH05 trong tình huống minh họa

Truy vết: PH04, PH05; UC04/UC07; FR-04, FR-19.

Tách Lưu nháp khỏi Xác nhận. Nhu cầu đủ ngân sách, thời lượng, tần suất và lịch mới được tìm. Xác nhận đúng revision và lịch 28 ngày; sửa tạo bản mới. E04/E05; TX01–TX02.

## 3 Wireframe tiếp theo

### 3.3 Xếp hạng và xem hồ sơ công khai

Hình 3 PH06 và PH07 trong tình huống minh họa

Truy vết: PH06, PH07; UC03/UC05; FR-05–FR-07, FR-09–FR-14.

Minh họa chỉ có hai người đạt điều kiện, không bổ sung đủ năm. Giá 120.000 VND/60 phút thành 180.000 VND/90 phút, nằm trong trần 200.000 VND/buổi. Không lộ liên hệ hoặc minh chứng gốc. E03/E06; TX03.

## 3 Wireframe tiếp theo

### 3.4 Gửi và theo dõi đề nghị

Hình 4 PH08 và PH09 trong tình huống minh họa

Truy vết: PH08, PH09; UC06; FR-15–FR-17.

Hiển thị ảnh chụp điều kiện và toàn bộ ngày học trước gửi. Hạn mẫu 31/10 lúc 10:00 là 48 giờ từ lúc gửi, sớm hơn buổi đầu. Đề nghị chờ không giữ chỗ. E07; TX04/TX06/TX16.

## 3 Wireframe tiếp theo

### 3.5 Gia sư chấp nhận và tạo lớp

Hình 5 GS05 và CL01 trong tình huống minh họa

Truy vết: GS05, CL01; UC04/UC06/UC07; FR-16–FR-19.

Nút Chấp nhận yêu cầu đồng ý nguyên bản. TX05 kiểm tra lại mọi buổi cho cả gia sư và học sinh, tạo lớp/lịch, chốt đề nghị và hủy đề nghị cạnh tranh trong cùng giao dịch. UI chỉ hiển thị lớp sau khi giao dịch thành công.

## 3 Wireframe tiếp theo

### 3.6 Lịch đã cam kết và tiếp nhận học liệu

Hình 6 CL02 và GS06 trong tình huống minh họa

Truy vết: CL02, GS06; UC07/UC10; FR-19, FR-21–FR-22.

CL02 kết thúc F02 và cung cấp buổi cho F04. GS06 bắt đầu F04 trong lớp chờ bắt đầu hoặc hoạt động. Tải tệp, xử lý, kiểm tra và sẵn sàng là các bước khác nhau; không dùng một cờ thành công. E10/E12; TX07–TX08.

## 3 Wireframe tiếp theo

### 3.7 Kiểm tra nguồn và xác nhận bối cảnh

Hình 7 GS07 và GS08 trong tình huống minh họa

Truy vết: GS07, GS08; UC10/UC11; FR-22–FR-26, FR-28, FR-30.

Gia sư xác nhận trích xuất/công thức và quyền khai thác nguồn. Quyền xem, tải gốc và dùng AI tách riêng; phạm vi dẫn xuất cần rà theo DP02. Bối cảnh lưu phiên bản mục tiêu, nguồn và quyền; trường hợp chưa có nhật ký đủ xác nhận được ghi rõ. TX08–TX10.

## 3 Wireframe tiếp theo

### 3.8 Biên tập duyệt và công bố phiên bản

Hình 8 GS09 và GS10 trong tình huống minh họa

Truy vết: GS09, GS10; UC11; FR-26–FR-30.

Mở nguồn đúng phiên bản cạnh nội dung khi biên tập. Duyệt và Công bố là hai lệnh riêng; GS10 minh họa sau khi duyệt. Sửa bản đã duyệt/công bố tạo nháp mới. Nguồn và cảnh báo được kiểm tra lại ở cả hai lệnh. TX11–TX14.

## 3 Wireframe tiếp theo

### 3.9 Bản công bố hợp lệ và nội dung bị tạm ẩn

Hình 9 CL06 và CL06 trong tình huống minh họa

Truy vết: CL06; UC11/UC16; FR-23, FR-29, FR-42.

Phụ huynh chỉ nhận bản công bố; học sinh nhận ở P1 khi liên kết còn hiệu lực. Đáp án và tệp gốc không được tự gửi kèm. Thu hồi có ảnh hưởng phải chặn cả nội dung dẫn xuất, không chỉ liên kết nguồn. E14/E19/E20; TX09/TX15.

## 3 Wireframe tiếp theo

### 3.10 Kết quả rỗng và lỗi xếp hạng AI

Hình 10 PH06 và PH06 trong tình huống minh họa

Truy vết: PH06; UC05; FR-09–FR-14, FR-37.

Tách Không có ứng viên khỏi Lỗi dịch vụ. Chỉ phụ huynh chủ động chọn mới trả danh sách B có nhãn không dùng AI; không nới điều kiện cứng. E06 lưu loại thuật toán và phiên bản thực sự dùng. TX03.

## 3 Wireframe tiếp theo

### 3.11 Xung đột khi chốt và chưa rõ kết quả gửi

Hình 11 GS05 và PH09 trong tình huống minh họa

Truy vết: GS05, PH09; UC06; FR-15–FR-17, FR-37; NFR04.

Xung đột bất kỳ buổi nào làm TX05 không tạo lớp/buổi dở. Khi mất mạng sau gửi, tra cứu mã thao tác trong phạm vi actor/loại lệnh trước thử lại. Cùng mã cùng nội dung trả kết quả cũ; khác nội dung bị từ chối. TX05/TX16.

## 3 Wireframe tiếp theo

### 3.12 AI chưa bật và bản nháp có cảnh báo

Hình 12 GS08 và GS09 trong tình huống minh họa

Truy vết: GS08, GS09; UC11; FR-27, FR-30, FR-37; NFR03/NFR05/NFR14.

Chưa có trần tiền thực tế thì AI tắt. Quá quota/hàng chờ đầy trả lý do, không thử vô hạn. Bản nháp có tổng phút sai, nguồn hết quyền hoặc cảnh báo chưa xử lý không được duyệt/công bố. Soạn thủ công giữ cùng quy trình kiểm tra. TX09–TX14.

## 3 Wireframe tiếp theo

### 3.13 Tác vụ đang xử lý và đối chiếu nguồn

Hình 13 GS08 và GS09 trong tình huống minh họa

Truy vết: GS08, GS09; UC10/UC11; FR-22, FR-25, FR-27, FR-28, FR-37.

Sau khi nhận tác vụ, GS08 hiển thị chờ/đang xử lý và kết quả cuối, không dùng phần trăm giả. GS09 giữ đoạn đang biên tập và mở đúng nguồn cùng bối cảnh; trên mobile có thể dùng khung nguồn xếp dọc hoặc bảng mở chồng, có đường quay lại không mất nháp. TX10–TX12/TX15.

## 4 Thực thể dữ liệu dự kiến

Các tên tiếng Anh là tên logic dự kiến. PK là khóa chính, FK là khóa ngoại; ID do máy chủ gán. Mọi thời điểm lưu theo chuẩn thống nhất, hiển thị Asia/Ho_Chi_Minh; tiền dùng số nguyên VND, revision phục vụ chống ghi đè.

| Nhóm | Thuộc tính chính | Bất biến và liên kết |
|---|---|---|
| E01 Account và phiên | account_id; role; status; verified_at; session_id/revoked_at; revision. | Một vai trò người dùng ở R0. Actor do phiên máy chủ xác định; khóa/thu hồi chặn lệnh tiếp theo. Tài khoản 1–N hồ sơ/đề nghị/tác vụ. AUTH; mọi TX. |
| E02 Student Profile và Student Link | student_id; parent_id; display_name; grade; student_account_id; link_status; expiry; revision. | Một phụ huynh quản lý ở R0. Liên kết học sinh chỉ hiệu lực sau lời mời hợp lệ P1; không suy quyền từ email/ID. PH02; CL01/CL06. UC02; FR-03/36. |
| E03 Tutor Profile Version và Verification | tutor_id; profile_version; subject/grade; experience_months; hourly_rate_vnd; public_status; verifier/scope. | Kinh nghiệm 0–600 hợp lệ; giá nguyên dương. Tách tự khai và xác minh; chỉ bản công khai được duyệt tham gia matching. PH07, GS05. UC03/UC05; FR-05–07. |
| E04 Availability Version Window Exception | availability_id; owner_type/id; version; confirmed_at; weekday/date; start/end; timezone; preferred. | Nháp không tham gia lọc. Trừ ngoại lệ và buổi bận trước tính; sửa lịch rảnh không sửa lịch cam kết. PH04/PH05; nền GS04. UC07; FR-08/19. |
| E05 Learning Need Version | need_id; student_id; version/revision; goal; budget_per_session; duration; frequency; cycle_start; availability_version; status. | Chỉ bản xác nhận hiện hành được tìm/gửi. D 60/90/120, tần suất 1–3 mỗi khối 7 ngày; chu kỳ 4 khối. Sửa tạo version mới. PH03–PH05. UC04; FR-04/18. |
| E06 Matching Run và Candidate Score | run_id; need_version; input/config_version; mode S/B; tutor/profile/calendar versions; component scores; rank/reasons. | Cùng điều kiện cứng cho S/B; số kết quả min(5, N), không trùng. Lưu bằng chứng đúng dữ liệu; không xem điểm là xác suất học lực. PH06/PH07. UC05; FR-09–14. |

## 4 Thực thể dữ liệu tiếp theo

Tiếp tục danh mục nhóm thực thể. Mỗi nhóm giữ liên kết tới màn hình và mã yêu cầu; việc tách bảng, chỉ mục và cấu trúc vật lý được hoàn thiện ở bước ERD.

| Nhóm | Thuộc tính chính | Bất biến và liên kết |
|---|---|---|
| E07 Class Offer và Offer Session | offer_id; need_version; tutor_id; immutable terms; goal/rate/session-price snapshot; sessions; expires_at; status; revision. | Đề nghị chờ không tạo booking. Một đề nghị chờ tương đương không được nhân đôi bởi khóa khác; hết hạn lấy thời điểm sớm hơn 48 giờ và buổi đầu. PH08/PH09/GS05. UC06; FR-15–17. |
| E08 Tutoring Class và Agreement | class_id; originating_need_id; accepted_offer_id; student_id; tutor_id; accepted_terms_version; status; revision. | Một nhu cầu tạo tối đa một lớp. Bản giá/thỏa thuận không đổi khi hồ sơ gia sư thay đổi. Chờ bắt đầu/hoạt động cho phép soạn; kết thúc chặn tạo mới. CL01. UC06–UC08; FR-17–19/43. |
| E09 Class Goal Version | goal_id; class_id; version; text; source_need_version; proposed_by; approvals; effective_at. | Chỉ bản hai bên chấp thuận là mục tiêu hiệu lực. Sửa nhu cầu không sửa mục tiêu lớp. Context soạn tham chiếu chính xác version. CL01/GS08. UC04/UC11; FR-18/24. |
| E10 Lesson Session và Resource Booking | session_id; class_id; start/end; status; revision; resource_type/id; booked_interval; booking_status. | Mỗi buổi cam kết tạo hai booking: gia sư và học sinh. Khoảng [start, end) không chồng với booking hiệu lực cùng tài nguyên; chuỗi tối đa 12 buổi. CL02/GS08. UC06/UC07; FR-17/19. |
| E11 Lesson Log Version và Attendance | log_id/version; session_id; teacher_confirmation; parent_attendance; dispute; source_version. | Chỉ nhận xét gia sư xác nhận, thuộc buổi được phụ huynh xác nhận có tham gia và không tranh chấp mới vào bối cảnh AI. Lớp chưa có nhật ký vẫn soạn từ nguồn/mục tiêu. Đầu vào GS08 từ UC09; FR-31/32. |
| E12 Material và Material Version | material_id; version_id; owner/uploader; class_scope; blob_key; hash/type/size; extract/index/review states; active. | Không ghi đè tệp cũ khi thay tệp. Kiểm tra 10 MiB, 100 trang PDF/200.000 ký tự; tối đa 20 phiên bản hoạt động/lớp, kể cả phiên bản đang tiếp nhận. GS06/GS07. UC10; FR-21/22. |

## 4 Thực thể dữ liệu tiếp theo

Tiếp tục danh mục nhóm thực thể. Mỗi nhóm giữ liên kết tới màn hình và mã yêu cầu; việc tách bảng, chỉ mục và cấu trúc vật lý được hoàn thiện ở bước ERD.

| Nhóm | Thuộc tính chính | Bất biến và liên kết |
|---|---|---|
| E13 Source Segment | segment_id; material_version_id; ordinal; locator; text_hash; extract_version; quality_flag; index_status. | Vị trí trang PDF hoặc mục/đoạn DOCX ổn định theo phiên bản. Đoạn sai công thức không đủ điều kiện dùng; không tạo mã nguồn giả. GS07/GS09. UC10/UC11; FR-22/25/27. |
| E14 Material Grant và Access Epoch | grant_id; version/scope; principal; capability; issuer; expiry; revoked_at; access_epoch. | Xem gốc, tải gốc và dùng AI là quyền riêng. Thu hồi cập nhật epoch hiệu lực ngay; dẫn xuất theo phạm vi cho phép tại DP02, không suy quyền từ URL. GS07/CL06. UC10/UC16; FR-23/42. |
| E15 Preparation Context | context_id; class/session; topic; duration; goal_version; eligible_log_versions; selected_source_versions; epochs; config_version. | Snapshot đầu vào tối đa 5 nguồn sẵn sàng trong quyền. Không gửi tên/liên hệ không cần thiết; thay bối cảnh tạo context mới, không ghi đè bản cũ. GS08. UC11; FR-24/25/28. |
| E16 AIJob và Attempt | job_id; context_id; actor; status; attempt_id; claimed_by; lease/fencing_token; deadline; result_ref; error_code. | Mỗi kết quả thuộc context/attempt. Không giữ transaction DB khi gọi LLM; worker mất quyền/lease không được ghi. Không để phản hồi cũ ghi đè nháp người dùng sửa. GS08/GS09. UC11; NFR03–05/13–15. |
| E17 Quota và Budget Reservation | reservation_id; job/attempt; class/tutor/day; estimated_cost; cap/config_version; reserved/settled/released; currency. | Đặt chỗ quota/chi phí nguyên tử; cùng mã thao tác không tính lại. Một lần thử dịch vụ mới tính theo chính sách đã xác nhận; không tự lặp vô hạn. GS08; TX10/TX11. NFR13/14. |

## 4 Thực thể dữ liệu tiếp theo

Tiếp tục danh mục nhóm thực thể. Mỗi nhóm giữ liên kết tới màn hình và mã yêu cầu; việc tách bảng, chỉ mục và cấu trúc vật lý được hoàn thiện ở bước ERD.

| Nhóm | Thuộc tính chính | Bất biến và liên kết |
|---|---|---|
| E18 Lesson Plan và Plan Version | plan_id; version_id; class/session; context; author; origin AI/manual; content; revision; status; reviewer/reviewed_at. | Mỗi bản duyệt gắn chính xác nội dung, context, cấu hình và nguồn. Sửa sau duyệt/công bố sinh nháp mới; bản công bố hợp lệ cũ vẫn giữ đến khi thay thế. GS09/GS10. UC11; FR-26/28–30. |
| E19 Activity Citation Dependency | activity_id; plan_version; minutes; content_origin; citation_id; segment/version/locator; dependency_scope/epoch. | Tổng phút đúng D. Trích dẫn thuộc nguồn thực dùng và còn quyền. Dependency bao phủ nội dung dẫn xuất để thu hồi, không chỉ danh sách liên kết trích dẫn. GS09/CL06. UC11; FR-25–27. |
| E20 Publication và Answer Access | class/session; current_plan_version; audience; published_by/at; revision; visibility; answer_access_state. | Chỉ pointer tới bản đã duyệt hợp lệ. DTO phụ huynh/học sinh không chứa nháp/đáp án chưa mở hoặc blob gốc. Mở đáp án là quyền riêng ở UC12/P1. GS10/CL06. UC11/UC12; FR-29/33. |
| E21 Command Receipt | actor_id; command_type; idempotency_key; payload_hash; status; result_object/version; created/expires_at. | Khóa duy nhất theo actor/loại lệnh/key; giữ 7 ngày. Cùng key khác nội dung từ chối; ngoài hạn ràng buộc nghiệp vụ vẫn ngăn hiệu lực trùng. PH08/GS05/GS10; TX16. BR06; FR-37. |
| E22 Outbox Event và Audit Event | event_id; aggregate/version; recipient; event_type; dispatch status; actor; timestamp; result; trace_id. | Outbox cùng transaction nghiệp vụ; phân phối lại không nhân thông báo. Audit tối thiểu không chứa khóa bí mật/học liệu nguyên văn; analytics tách quyền và dữ liệu giả danh. UC13/UC15; FR-38/41; NFR09/16. |

## 5 Quan hệ dữ liệu và bất biến

| Quan hệ | Lực lượng và cấu trúc | Ràng buộc cần thiết kế |
|---|---|---|
| Account → StudentProfile | 1–N; mỗi hồ sơ có 1 parent_id ở R0. | Tra quyền từ quan hệ máy chủ; link học sinh là quan hệ riêng có trạng thái. |
| LearningNeed → NeedVersion → MatchingRun | 1–N → 1–N. | Offer lưu đúng version; run/profile/lịch cũ không đủ bảo đảm lúc chốt. |
| LearningNeed → Offer → Class | 1–N đề nghị; tối đa 1 lớp cho một nhu cầu. | Ràng buộc duy nhất originating_need_id của lớp và accepted_offer_id. |
| Class → Session → Booking | 1–N; mỗi session có booking gia sư và học sinh. | Ràng buộc không chồng khoảng cho cùng resource; ghi toàn chuỗi trong TX05. |
| Class → MaterialVersion → Segment | Lớp có tối đa 20 bản hoạt động; bản có N đoạn. | Một tệp có nhiều bản; chỉ index/bản sẵn sàng còn quyền dùng cho truy xuất. |
| Context → Job → PlanVersion | Context bất biến; job/attempt tạo kết quả có phiên bản. | Không ghi đè nháp đang sửa; không trả nội dung từ epoch quyền cũ. |
| PlanVersion ↔ Segment | N–N qua citation và dependency. | Dependency phải bao phủ dẫn xuất cả khi người biên tập đã xóa liên kết trích dẫn. |
| Session → Publication → PlanVersion | Một pointer công bố hiện hành cho buổi. | Thay pointer nguyên tử; lưu lịch sử metadata theo chính sách và không tạo đường vòng đọc bản bị thu hồi. |

### 5.1 Ràng buộc trọng yếu tại kho dữ liệu

Duy nhất lớp/nhu cầu, không chồng booking và receipt duy nhất phải giữ đúng qua hai API. Ràng buộc DB và khóa cần thử bằng cạnh tranh thực tế, không dựa vào giao diện hoặc khóa RAM.

Truy vấn cần đo để chọn chỉ mục: lớp theo con/gia sư; đề nghị theo nhu cầu/trạng thái/hạn; booking theo tài nguyên/thời gian; nguồn theo lớp/version/quyền; job theo trạng thái/deadline.

### 5.2 Phiên bản và vòng đời

Version nghiệp vụ bất biến khác revision chống ghi đè. Đề nghị/lớp giữ giá và mục tiêu đã chấp thuận. PlanVersion giữ dấu vết context/nguồn; không giữ nội dung nhạy cảm như đường vòng sau thu hồi. Áp dụng BR08 cho nháp, kết thúc lớp, audit và backup.

## 6 Ma trận quyền theo đối tượng

Phụ huynh là người quản lý đúng hồ sơ; gia sư là thành viên đúng lớp; học sinh chỉ có tài khoản liên kết P1. Mỗi ô là quyền tối đa, còn phụ thuộc trạng thái tài khoản/lớp/nguồn. Quản trị được chia quyền xác minh, hỗ trợ và kiểm toán theo nhiệm vụ.

| Đối tượng | Phụ huynh | Gia sư | Học sinh P1 | Quản trị |
|---|---|---|---|---|
| Hồ sơ con/nhu cầu | Quản lý con mình | Tối thiểu từ đề nghị/lớp | Chỉ hồ sơ tối thiểu | Theo vụ việc |
| Hồ sơ gia sư | Xem bản công khai | Sửa của mình | Xem công khai | Xác minh được giao |
| Tìm/đề nghị | Tìm, gửi, hủy của con | Phản hồi gửi mình | Không | Metadata vụ việc |
| Chốt lớp/lịch | Đồng ý khi gửi | Chấp nhận nguyên bản | Chỉ xem lịch mình | Không chốt thay |
| Tải học liệu R0 | Cung cấp ngoài ứng dụng | Gia sư lớp có quyền | Không | Không mặc định |
| Xem/tải tệp gốc | Cấp riêng | Chủ nguồn/cấp riêng | Cấp riêng | Quyền tạm vụ việc |
| Dùng nguồn AI | Không | USE_FOR_AI + đúng lớp | Không | Không mặc định |
| Nháp/đáp án dự kiến | Không | Gia sư lớp có quyền | Không | Không mặc định |
| Duyệt/công bố | Không | Gia sư được giao | Không | Không duyệt thay |
| Bản công bố | Trong lớp của con | Trong lớp mình | Link hiệu lực | Không mặc định |
| Đáp án/gốc kèm bài | Chỉ khi mở quyền | Theo UC12/nguồn | Chỉ khi mở quyền | Không mặc định |
| Thu hồi nguồn | Theo ủy quyền | Chủ nguồn/ủy quyền | Qua phụ huynh | Xử lý được giao |

## 7 Quyền nguồn dẫn xuất và tác vụ nền

### 7.1 Quyền nguồn và quyền đọc dẫn xuất

VIEW_ORIGINAL, DOWNLOAD_ORIGINAL và USE_FOR_AI là ba capability độc lập. Quyền khai thác nguồn phải đủ cho việc dùng nội dung trong bản soạn công bố tới thành viên đúng lớp; người soạn xác nhận phạm vi này tại GS07. Đề xuất DP02 yêu cầu ghi riêng căn cứ/phạm vi dẫn xuất để tránh đồng nhất quyền đọc bài soạn với quyền tải tệp gốc.

Phụ huynh có thể xem bản công bố hợp lệ của con mà không có quyền mở tệp gốc. Ngược lại, có tệp gốc không tự cho đọc nháp/đáp án. Khi quyền sử dụng nguồn cho phạm vi dẫn xuất bị thu hồi, kiểm tra dependency để tạm ẩn đúng phần và đối tượng bị ảnh hưởng; không chỉ xóa liên kết trích dẫn.

### 7.2 Điểm kiểm tra quyền trên toàn tuyến

| Điểm kiểm tra | Điều kiện và dữ liệu cần đối chiếu |
|---|---|
| Nhận lệnh | Phiên, tài khoản, vai trò, quan hệ con/lớp, trạng thái và revision. |
| Chọn/truy xuất nguồn | Class scope, source version, trạng thái sẵn sàng, capability, access_epoch và mục đích. |
| Lưu và trả kết quả AI | Quyền lớp/nguồn hiện hành, deadline/lease, context và revision nháp; tránh kết quả cũ. |
| Duyệt và công bố | Đúng plan version, người duyệt, cảnh báo, tổng phút và quyền mọi dependency; phối hợp TX09. |
| Đọc payload/cache/tải tệp | Quan hệ hiện hành và audience; không đưa nháp/đáp án/gốc ngoài quyền vào payload; URL cũ không vượt thu hồi. |
| Khôi phục và tác vụ nền | Áp nhật ký xóa/thu hồi trước mở truy cập; worker không tự nâng quyền hay hồi sinh dữ liệu. |

### 7.3 Tài khoản dịch vụ và audit

Worker chỉ nhận tác vụ qua backend với ID phạm vi, cấu hình và quyền cần thiết; không nhận quyền quản trị toàn bộ học liệu. Gia sư là người duyệt chuyên môn; quản trị có quyền cấu hình không đồng nghĩa được đọc nháp. Audit lưu actor/thao tác/version/kết quả, không ghi học liệu nguyên văn hoặc bí mật. Quyền đọc tạm phục vụ vụ việc theo UC14 tối đa 24 giờ, có cấp, hết hạn và thu hồi.

## 8 Giao dịch của luồng tìm và chốt lớp

Mỗi TX mô tả ranh giới nghiệp vụ và điều kiện chấp nhận. Các mã lỗi là đề xuất cho hợp đồng API G3. Receipt/outbox/audit được ghi cùng giao dịch khi có thay đổi nghiệp vụ; lời gọi dịch vụ bên ngoài không nằm trong transaction DB dài.

### TX01 Lưu nhu cầu nháp

Phụ huynh; PH03/PH04; E02/E04/E05.

Kiểm tra quan hệ con, giới hạn trường và revision. Ghi version/revision nháp cùng receipt; không xác nhận thiếu điều kiện. Sửa nhu cầu đã xác nhận tạo bản mới; không sửa mục tiêu lớp.

Kết quả và lỗi: Chỉ báo Đã lưu khi nhận kết quả; bản cũ báo VERSION_CONFLICT và giữ dữ liệu nhập hợp lệ. UC04; FR-04; NFR01/04.

### TX02 Xác nhận nhu cầu

Phụ huynh; PH05; E04/E05/E21.

Kiểm tra đủ điều kiện cứng, lịch đã xác nhận và chu kỳ 4 khối 7 ngày. So sánh revision rồi chuyển đúng version sang xác nhận. Tìm kiếm chỉ nhận version hiệu lực.

Kết quả và lỗi: Thiếu trường trả VALIDATION_FAILED; bản cũ không tạo đề nghị hoặc kết quả mới. UC04/UC07; FR-04/19.

### TX03 Chạy gợi ý và lưu bằng chứng

Phụ huynh; PH06/PH07; E03–E06.

Đọc snapshot hợp lệ, tính ngoài transaction dài; lọc toàn bộ điều kiện rồi xếp S theo BR03. Lưu mode/config/đầu vào/điểm/lý do và thứ tự. B chỉ dùng sau người dùng chọn; kiểm tra lại dữ liệu trước lệnh ghi đề nghị.

Kết quả và lỗi: N=0 khác lỗi dịch vụ; min(5, N) không trùng; thay dữ liệu không làm run cũ thành cam kết lịch. UC05; FR-09–14; NFR10.

### TX04 Gửi đề nghị nhận lớp

Phụ huynh; PH08/PH09; E05/E07/E21/E22.

Kiểm tra quyền, nhu cầu hiện hành, hồ sơ công khai và đủ lịch/giá hiện tại. Ghi đề nghị, snapshot mục tiêu/giá và mọi buổi; expiry sớm hơn 48 giờ và buổi đầu; receipt/outbox cùng commit. Chưa tạo ResourceBooking.

Kết quả và lỗi: Chống đề nghị chờ tương đương dù đổi key; trả đúng trạng thái đã gửi. Không cho gửi từ version cũ. UC06; FR-15; BR05/06.

## 8 Giao dịch chốt lớp tiếp theo

Mỗi TX mô tả ranh giới nghiệp vụ và điều kiện chấp nhận. Các mã lỗi là đề xuất cho hợp đồng API G3. Receipt/outbox/audit được ghi cùng giao dịch khi có thay đổi nghiệp vụ; lời gọi dịch vụ bên ngoài không nằm trong transaction DB dài.

### TX05 Chấp nhận và chốt toàn chuỗi

Gia sư đích; GS05 → CL01/CL02; E05/E07–E10/E21/E22.

Trong một transaction, khóa/điều phối các đối tượng chung theo thứ tự ổn định; kiểm tra actor, hạn, version, tài khoản/hồ sơ và tất cả buổi tương lai cho cả gia sư lẫn học sinh. Tạo lớp/thỏa thuận/mục tiêu, mọi session và hai booking/buổi; chấp nhận đề nghị, hủy đề nghị cạnh tranh cùng nhu cầu, ghi receipt/outbox.

Kết quả và lỗi: Bất kỳ xung đột/ràng buộc nào thất bại thì rollback toàn bộ. Duy nhất lớp/nhu cầu và không chồng booking được bảo đảm ở kho dữ liệu dùng chung, không dựa khóa RAM. UC06/UC07; FR-16/17; NFR04/15.

### TX06 Từ chối hủy hoặc hết hạn đề nghị

Gia sư từ chối; phụ huynh hủy; hệ thống hết hạn; E07/E21/E22.

Chuyển từ Chờ trên revision hiện hành, kiểm tra chủ thể và lý do khi áp dụng. Ghi trạng thái, receipt/outbox. Lệnh hết hạn cạnh tranh với chấp nhận dùng cùng kiểm soát trạng thái và hạn phía máy chủ.

Kết quả và lỗi: Chỉ một kết quả hiệu lực; đề nghị từ chối/hủy/hết hạn không tạo lớp. Lệnh trên trạng thái cuối trả kết quả hiện tại, không đảo ngược ngầm. UC06; FR-16/17.

### 8.1 Thứ tự kiểm soát chốt lịch

Thiết kế TX05 theo thứ tự ổn định cho need/offer, tài nguyên học sinh và gia sư. Kiểm lại các buổi, thời lượng, số buổi từng khối, giá và trạng thái; ràng buộc DB là lớp bảo vệ cuối. Nếu bất kỳ thao tác tạo booking, lớp hay session thất bại thì toàn bộ giao dịch không có hiệu lực. Cơ chế cụ thể được ghi ADR và thử với hai API trước nghiệm thu.

Gửi đề nghị ở TX04 không tạo booking và không chặn người khác. Hai đề nghị cạnh tranh hoặc lệnh hết hạn cùng lúc nhận lớp chỉ có một trạng thái cuối hợp lệ. Thông báo lấy từ outbox sau commit; giao dịch thất bại không phát thông báo nhận lớp thành công.

## 9 Giao dịch của luồng học liệu và soạn bài

Mỗi TX mô tả ranh giới nghiệp vụ và điều kiện chấp nhận. Các mã lỗi là đề xuất cho hợp đồng API G3. Receipt/outbox/audit được ghi cùng giao dịch khi có thay đổi nghiệp vụ; lời gọi dịch vụ bên ngoài không nằm trong transaction DB dài.

### TX07 Tiếp nhận tệp riêng tư

Gia sư lớp; GS06; E08/E12/E21/E22.

Kiểm tra lớp cho phép soạn, quyền sử dụng và loại/kích thước thực. Chốt phiên bản chờ xử lý sau khi xác nhận tệp đã lưu bền vững trong kho riêng; kiểm soát giới hạn 20 bản bằng dữ liệu chung. Đưa xử lý vào hàng đợi qua outbox.

Kết quả và lỗi: DB và kho tệp không được giả định có một transaction chung. Theo dõi tải dở, retry finalize và dọn tệp mồ côi không tham chiếu; không báo nguồn sẵn sàng ở bước này. UC10; FR-21; NFR08/11.

### TX08 Xử lý và xác nhận nguồn

Worker xử lý; gia sư kiểm tra; GS07; E12/E13/E14/E22.

Worker trích xuất/lập chỉ mục theo version; gia sư kiểm tra văn bản/công thức và phạm vi sử dụng. Trạng thái Sẵn sàng chỉ hiệu lực khi đủ kiểm tra, chỉ mục dùng được và quyền còn hợp lệ. Công thức bị đánh dấu lỗi không vào tập nguồn hợp lệ.

Kết quả và lỗi: Tệp scan/khóa/hỏng hoặc vượt hạn được báo lý do. Retry cùng nội dung giữ version; thay nội dung tạo version mới. Worker cũ không phục hồi nguồn đã thu hồi. UC10; FR-22; NFR02/03/06.

### TX09 Thu hồi quyền và vô hiệu dẫn xuất

Chủ nguồn/người được ủy quyền; GS07; E14/E16/E19/E20/E22.

Ghi revoked_at và tăng access_epoch trong giao dịch dùng chung; đánh dấu nội dung dẫn xuất bị ảnh hưởng không còn đọc được theo phạm vi. Lập sự kiện dọn index/cache/job. Mọi đọc và bước lưu/trả/duyệt/công bố so quyền hiện tại, kể cả khi dọn nền chưa xong.

Kết quả và lỗi: Không chỉ xóa liên kết. Worker/publisher cùng kiểm soát epoch và phiên bản để một lệnh không vượt qua thu hồi đồng thời. Khôi phục áp nhật ký thu hồi trước mở. UC10/UC16; FR-23/42; NFR02/03/11.

## 9 Giao dịch tác vụ nháp và duyệt tiếp theo

### TX10 Nhận tác vụ AI và giữ hạn mức

Gia sư; GS08; E14–E17/E21/E22.

Chốt context đúng lớp/buổi/mục tiêu, tối đa 5 nguồn sẵn sàng trong quyền. Kiểm tra AI bật, trần tiền và quota; giữ quota/dự toán chi phí, tạo job/receipt/outbox nguyên tử. Worker nhận việc có lease/token; giới hạn 2 chạy toàn hệ thống, 1/gia sư và tối đa 20 chờ.

Kết quả và lỗi: 5 lần/lớp/ngày, 20/gia sư/ngày; AI tắt khi chưa đặt trần. Cùng key không tạo job/giữ quota lần hai. Gọi LLM ngoài transaction; retry dịch vụ mới phải có attempt/quota hợp lệ. UC11; NFR07/13/14.

### TX11 Nhận kết quả AI thành nháp

Worker được giao; GS08 → GS09; E15–E19.

Kiểm tra deadline/lease, context, epoch nguồn và quyền lớp trước lưu/trả. Kiểm cấu trúc, tổng phút và citation ID. Chỉ tạo nháp gắn đúng version nếu hợp lệ; kết quả đến sau sửa nháp không ghi đè, phải được tách hoặc từ chối theo DP03.

Kết quả và lỗi: Nguồn bị thu hồi: không trả/lưu nội dung bị cấm như nháp mới; chỉ giữ metadata lỗi tối thiểu. Job kết thúc/lỗi rõ trong 120 giây kể cả chờ. UC11; FR-26–28; NFR03–05/13.

### TX12 Lưu biên tập hoặc soạn thủ công

Gia sư; GS09; E18/E19/E21.

So revision nháp, giữ nguồn gốc AI/manual và dependency; lưu bản chỉnh sửa có kiểm soát. Thủ công không cần gọi nhà cung cấp; phần tự biên soạn có nhãn. Sửa bản đã duyệt/công bố tạo nháp mới, không sửa bản đang hiển thị.

Kết quả và lỗi: Không coi xóa một citation là đã loại nội dung dẫn xuất. Lỗi mạng/AI không mất nháp đã lưu. UC11; FR-28–30; NFR04/05.

### TX13 Duyệt đúng phiên bản

Gia sư lớp; GS10; E14/E18/E19/E21/E22.

Kiểm quyền lớp, revision, context, nguồn/epoch hiện hành, tổng phút và cảnh báo đã xử lý; ghi người/giờ và dấu vết nội dung được duyệt. Kiểm máy không thay thế việc gia sư kiểm tra Toán.

Kết quả và lỗi: Bản đã sửa sau khi xem lại, cảnh báo tồn tại hoặc nguồn hết quyền bị chặn. Duyệt không tự công bố. UC11; FR-27/28/29; NFR03/09.

## 10 Giao dịch duyệt công bố và truy xuất

Mỗi TX mô tả ranh giới nghiệp vụ và điều kiện chấp nhận. Các mã lỗi là đề xuất cho hợp đồng API G3. Receipt/outbox/audit được ghi cùng giao dịch khi có thay đổi nghiệp vụ; lời gọi dịch vụ bên ngoài không nằm trong transaction DB dài.

### TX14 Công bố nguyên tử

Gia sư lớp; GS10 → CL06; E14/E18–E22.

Kiểm lại lớp, bản đã duyệt, revision pointer và epoch của mọi dependency trong cùng cơ chế điều phối với TX09. Chuyển pointer công bố sang bản hợp lệ, ghi audit/outbox/receipt cùng commit. Người xem cũ chỉ đổi sang bản mới sau thành công.

Kết quả và lỗi: Nếu thu hồi chốt trước, công bố bị chặn; nếu công bố chốt trước, lần đọc sau thu hồi bị chặn. Không có trường hợp tạm công khai nháp/đáp án. UC11; FR-29; NFR01–04/09.

### TX15 Đọc bản công bố và mở nguồn

Phụ huynh/gia sư/học sinh P1; CL06 hoặc GS09; E02/E08/E13/E14/E19/E20.

Đọc lớp/bản công bố theo quan hệ hiện hành, thời hạn lưu và dependency; trả DTO tối thiểu riêng từng quyền. Mở tệp/đoạn gốc cần VIEW/DOWNLOAD riêng; không suy quyền từ citation. Dữ liệu cache/URL cũ phải qua kiểm tra hiện tại hoặc bị vô hiệu tương ứng.

Kết quả và lỗi: Sau thu hồi không trả nội dung dẫn xuất bị ảnh hưởng. Không dùng URL công khai dài hạn để vượt thu hồi; không đưa đáp án vào payload rồi ẩn. UC10/UC11; FR-22/23/29; NFR01–03.

### TX16 Tra cứu kết quả và thử lại an toàn

Actor ban đầu; PH09/GS05/GS08/GS10; E21.

Tra theo actor + loại lệnh + key, kiểm payload_hash và quyền hiện tại đối với kết quả. Chờ xử lý trả trạng thái chờ; hoàn thành trả cùng định danh/kết quả; khác nội dung từ chối. Kết quả receipt không bỏ qua quyền đã thu hồi.

Kết quả và lỗi: Giữ receipt 7 ngày. Sau hạn, unique nghiệp vụ, version và ràng buộc lịch vẫn ngăn hiệu lực trùng. Áp dụng cả khi qua hai API hoặc restart. BR06; FR-37; NFR04/15.

### 10.1 Thông báo lỗi nhìn từ người dùng

Dùng nhóm lỗi AUTH_REQUIRED, ACCESS_UNAVAILABLE, VALIDATION_FAILED, VERSION_CONFLICT, SCHEDULE_CONFLICT, OFFER_EXPIRED, OPERATION_PENDING, FILE_UNSUPPORTED, SOURCE_NOT_READY, SOURCE_REVOKED, AI_DISABLED, AI_FAILED và QUOTA_EXCEEDED. Mỗi lỗi có thông báo tiếng Việt và hành động tiếp theo; không trả dữ liệu đối tượng ngoài quyền. Mã cụ thể được chuẩn hóa trong OpenAPI sau rà soát.

## 11 Kịch bản rà soát thiết kế

Các ca dưới đây là danh sách cần chạy khi có prototype hoặc triển khai tương ứng. Trạng thái tại ngày phát hành: chưa thực hiện; không dùng bảng này như kết quả kiểm thử đạt.

| Mã | Tình huống | Kết quả cần quan sát và truy vết |
|---|---|---|
| RV01 | Đổi con trên PH02 rồi mở nhu cầu/lịch cũ. | Không đọc/sửa dữ liệu con khác; trạng thái chọn con rõ. UC02; NFR01; AT01. |
| RV02 | Thiếu ngân sách hoặc dùng bản nhu cầu cũ. | Chỉ lưu nháp; xác nhận/tìm/gửi bị chặn đúng lỗi. UC04; AC-UC04-01/02. |
| RV03 | Có 0, 1, 2 hoặc hơn 5 gia sư đạt. | Đúng min(5, N), không lặp/vi phạm điều kiện; rỗng khác lỗi AI. UC05; AC-UC05-01/02. |
| RV04 | Embedding lỗi rồi chọn danh sách B. | Giữ toàn bộ điều kiện cứng; nhãn không dùng AI và mode lưu đúng. UC05; FR-14. |
| RV05 | Hai gia sư nhận cùng nhu cầu; hai lớp tranh giờ gia sư/học sinh. | Tối đa một giao dịch hợp lệ; không có lớp/session/booking dở. UC06/UC07; AT04/AT13. |
| RV06 | Mất mạng sau gửi/chốt; phát lại cùng key hoặc key khác nội dung. | Cùng kết quả, không nhân đôi; khác nội dung bị từ chối. BR06; AT13. |
| RV07 | Chấp nhận sau hết hạn hoặc tài khoản bị khóa. | Không tạo lớp; trả trạng thái có lý do trong quyền. UC06; NFR01/04. |
| RV08 | Tệp scan, sai loại, mã hóa, quá hạn hoặc tải đồng thời vượt 20 bản. | Lý do rõ, không nguồn giả; giới hạn số bản không bị vượt. UC10; FR-21/22. |

## 11 Kịch bản rà soát tiếp theo

Các ca dưới đây là danh sách cần chạy khi có prototype hoặc triển khai tương ứng. Trạng thái tại ngày phát hành: chưa thực hiện; không dùng bảng này như kết quả kiểm thử đạt.

| Mã | Tình huống | Kết quả cần quan sát và truy vết |
|---|---|---|
| RV09 | Tệp trích xuất xong nhưng chưa được gia sư kiểm tra. | Không chọn dùng AI; sai công thức được đánh dấu không dùng. UC10; AC-UC10-02. |
| RV10 | Thu hồi nguồn khi truy xuất, gọi AI, lưu kết quả, duyệt hoặc công bố. | Không trả/công bố nội dung hết quyền; tạm ẩn dẫn xuất và chặn cache/URL cũ. AT07/AT12/AT19; NFR02/03. |
| RV11 | Không có trần tiền, quá quota hoặc đầy hàng chờ. | Từ chối trước gọi dịch vụ; không tính lặp do cùng key; có soạn thủ công. AT08/AT17; NFR13/14. |
| RV12 | Kết quả AI đến sau khi gia sư đã sửa nháp hoặc worker hết lease. | Không ghi đè bản người dùng; trạng thái và phiên bản rõ. UC11; NFR04/05. |
| RV13 | Tổng phút sai, citation ID lạ hoặc cảnh báo chưa xử lý. | Không duyệt/công bố; gia sư mở được nguồn đúng version để sửa. UC11; AC-UC11-01/03. |
| RV14 | Sửa một bản đã công bố rồi lưu nháp. | Bản công bố hợp lệ cũ vẫn hiển thị; bản sửa cần duyệt/công bố lại. UC11; AT18. |
| RV15 | Phụ huynh/học sinh/admin gọi trực tiếp API nháp, đáp án hoặc tệp gốc. | Bị chặn nếu thiếu quyền; payload/cache không chứa nội dung chưa được mở. AT01/AT07/AT15; NFR01. |
| RV16 | Hai API, restart worker, phân phối lại outbox và phục hồi sau xóa. | Không lặp nghiệp vụ/thông báo; không sống lại quyền, nguồn hay dẫn xuất bị thu hồi. AT13/AT19; NFR04/11/15. |

## 12 Quyết định cần rà soát và công việc tiếp theo

Các quyết định dưới đây cụ thể hóa phương án triển khai và cần Dương Đính/Đức Huy rà soát. Chúng không thay thế D01–D08 của hồ sơ G2. Nếu phương án làm thay đổi hành vi hoặc phạm vi đã chốt, lập yêu cầu thay đổi và cập nhật [S1] trước triển khai phần bị ảnh hưởng.

| Mã | Đề xuất cần quyết định | Bằng chứng hoặc đầu ra tiếp theo |
|---|---|---|
| DP01 | Ràng buộc lịch và duy nhất tại DB, khóa thứ tự ổn định, receipt 7 ngày, outbox cùng commit. | ADR và thử hai API nhận lớp đồng thời, gửi lại sau mất mạng/restart; chọn cấu trúc booking vật lý. |
| DP02 | Tách quyền gốc, dùng AI và căn cứ/phạm vi chia sẻ dẫn xuất; dependency và access_epoch chặn thu hồi trên mọi đường. | Rà nội dung GS07/CL06; ma trận đọc bản công bố so với mở gốc; thử thu hồi đồng thời với duyệt/công bố/cache. |
| DP03 | Pipeline tệp nhiều bước; worker có lease; giữ quota/chi phí; xử lý kết quả đến sau sửa nháp. | Chọn cơ chế hàng đợi, quota/budget ledger, định nghĩa ngày hạn mức và chi phí retry sau khi chọn nhà cung cấp; GATE03 trước bật AI thật. |
| DP04 | PlanVersion có kiểm soát; pointer công bố và DTO tách nháp/đáp án/gốc; kiểm quyền hiện hành. | ERD/OpenAPI và ca âm qua API/URL/cache; tiêu chí thay thế bản công bố và xử lý dữ liệu sau kết thúc/xóa. |

### 12.1 Trình tự thực hiện sau rà soát

Bước 1: Dương Đính và Đức Huy đi qua 13 cặp khung nhìn, ghi vấn đề theo mã màn hình và quyết định DP tương ứng. Bước 2: chỉnh wireframe rồi dựng prototype có thao tác cho F02/F04. Bước 3: chốt ERD vật lý, ADR giao dịch và OpenAPI từ E01–E22/TX01–TX16. Bước 4: chạy các thử nghiệm rủi ro và thử người dùng theo tiêu chí G3; cập nhật bằng chứng trước nghiệm thu.

### 12.2 Mẫu ghi nhận ý kiến và phiên bản

Mỗi ý kiến gồm mã màn hình/TX/DP, tình huống, vấn đề, đề xuất, người xử lý, người rà soát và kết quả. Tại phát hành 0.1 ngày 29/10/2026, hồ sơ được chuẩn bị để rà soát; kết quả xác nhận được ghi theo thực tế. Giữ nguồn và lịch sử khi sửa; không ghi đạt thử nghiệm hoặc đóng G3 chỉ từ việc hoàn thành tài liệu.
