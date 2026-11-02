# TutorBridge_G3_05_Thiet_ke_F03_F05_v0_1

> Tài liệu lịch sử, độ ưu tiên thấp nhất. Bản Word kèm wireframe ở `docs/source-docx/g3/TutorBridge_G3_05_Thiet_ke_F03_F05_v0_1.docx`. Hình ảnh không có trong bản này.

NHÓM DỰ ÁN TUTORBRIDGE

HỒ SƠ THIẾT KẾ F03 VÀ F05

LỊCH LỚP VÀ SAU BUỔI HỌC

Tài liệu điều hành và triển khai giai đoạn thiết kế giải pháp

| Thông tin kiểm soát | Nội dung |
|---|---|
| Mã tài liệu | TB G3 05 |
| Phiên bản | 0.1 |
| Ngày phát hành | 29/10/2026 |
| Nhóm thực hiện | Dương Đính và Đức Huy |
| Mục đích sử dụng | Rà soát giải pháp và chuẩn bị triển khai backend cùng ứng dụng. |
| Trạng thái | Bản trình rà soát; chưa nghiệm thu G3 và chưa xác nhận bởi Đức Huy. |

Phạm vi yêu cầu tham chiếu: TB G2 02 v1.1. Trạng thái xác nhận và điều kiện bàn giao được quản lý tại TB G2 03 v1.2.

## 1 Kết luận và phạm vi bàn giao

Hồ sơ này bổ sung thiết kế F03 về đổi lịch, hủy, học bù và kết thúc lớp; F05 về nhật ký chuyên môn, xác nhận tham gia, bài tập P1 và tiến trình. Hai luồng được nối với lớp đã chốt ở F02 và học liệu cùng bài soạn đã duyệt ở F04. Mục đích là thống nhất hành vi, quyền và ranh giới giao dịch trước khi lập trình.

Đã chuẩn bị 17 trạng thái màn hình thuộc 11 mã màn hình, 14 giao dịch TX17 đến TX30, 14 bảng dữ liệu bổ sung và hợp đồng API gồm 32 thao tác. Bản mẫu chỉ mô phỏng trong trình duyệt; chưa thay thế ứng dụng hoặc backend vận hành.

| Căn cứ kiểm soát | Áp dụng |
|---|---|
| TB G2 02 v1.1 | PRD/SRS; UC08, UC09, UC12, UC13, UC15, UC16; BR08 và tiêu chí kiểm thử liên quan. |
| TB G2 03 v1.2 | G2 Đạt có điều kiện. Dương Đính đồng ý; Đức Huy và nguồn lực định lượng còn chờ. |
| TB G3 01 v0.1 | Danh mục 52 màn hình và các tiêu chí nghiệm thu G3. |
| TB G3 02 đến TB G3 04 v0.1 | Thiết kế F02/F04; DDL001/002, hiệu chỉnh003 và hợp đồng giao dịch trước đó. |

R0 đầy đủ vẫn bao gồm bài tập và đăng nhập học sinh ở P1. Hồ sơ không bổ sung thanh toán, chat, push, OCR hoặc chấm bài AI. Các quyết định chi tiết D35 cần hai thành viên rà soát; việc giao hồ sơ không được hiểu là đã phê duyệt các lựa chọn này.

### 1.1 Cách đọc và thứ tự đối chiếu

Các trang wireframe thể hiện bố cục và thông điệp chính. Hợp đồng giao dịch, DDL ứng viên và OpenAPI trong gói kỹ thuật quy định chi tiết trường, khóa, lỗi và thao tác. Nếu phát hiện sai khác với PRD/SRS, ghi quyết định thay đổi trước khi khóa thiết kế; không tự diễn giải thành yêu cầu mới.

## 2 Danh mục màn hình và khả năng truy vết

| Mã | Số dạng | Nội dung | Liên kết |
|---|---|---|---|
| CL01 | 2 | Lớp và xác nhận kết thúc | UC07/08/16; TX20 |
| CL02 | 1 | Lịch và trạng thái buổi | UC07/08; TX17–20 |
| CL03 | 4 | Đổi, phản hồi, hủy, học bù | UC08; TX17–19 |
| CL04 | 1 | Nhật ký chuyên môn | UC09; TX21/22 |
| CL05 | 1 | Xác nhận hoặc báo khác biệt | UC09; TX23 |
| CL07 | 1 | Bài tập và các lần nộp | UC12; TX25 |
| CL08 | 3 | Giao, nhận xét, đóng và đáp án | UC12; TX24/26–28 |
| PH10 | 1 | Tiến trình trong kỳ | UC13; TX29 |
| COM01 | 1 | Thông báo trong hệ thống | UC15; TX30 |
| HS01 | 1 | Trang học tập trong liên kết | UC02/12; TX25 |
| AD02 | 1 | Số liệu tổng hợp được phép | UC13/14; TX29/30 |

17 dạng dùng chung cấu trúc màn hình; không làm tăng tùy tiện danh mục 52 màn hình cơ sở. Các hậu tố như CL03-review chỉ định trạng thái trong bản mẫu. HS01 không có quyền xác nhận phụ huynh; AD02 không được mặc định đọc đề, nhật ký hoặc hồ sơ trẻ.

### 2.1 Quy ước dùng chung

Mọi thời gian hiển thị theo giờ Việt Nam, lưu thời điểm có múi giờ và đối chiếu bằng giờ máy chủ. Lỗi giữ nội dung đã nhập; lỗi phiên bản yêu cầu tải bản mới trước quyết định. Mất phản hồi phải tra kết quả operation hoặc gửi lại đúng khóa, không tự suy đoán thao tác thất bại.

## 3 Lịch hiện hành và đề nghị đổi

Hình 1. Bố cục rà soát, không phải màn hình ứng dụng đã phát hành.

Lịch gốc chỉ được thay sau khi bên còn lại chấp thuận nguyên bản và giao dịch đặt lịch mới thành công. Không giữ chỗ lịch mới trong thời gian chờ.

## 4 Phản hồi đề nghị và hủy buổi

Hình 2. Bố cục rà soát, không phải màn hình ứng dụng đã phát hành.

Trùng lịch hoặc phiên bản cũ phải kết thúc giao dịch mà vẫn giữ booking cũ. Hủy là quyết định riêng của một bên với lý do, không phải chấp thuận đề nghị đổi.

## 5 Học bù và kết thúc lớp

Hình 3. Bố cục rà soát, không phải màn hình ứng dụng đã phát hành.

Học bù tạo buổi mới liên kết buổi hủy hoặc vắng. Kết thúc lớp chặn ghi mới và AI nhưng vẫn áp dụng quyền đọc sau kết thúc theo BR08.

## 6 Nhật ký và xác nhận tham gia

Hình 4. Bố cục rà soát, không phải màn hình ứng dụng đã phát hành.

Công bố chuyên môn và xác nhận tham gia là hai bước độc lập. Quá 72 giờ chỉ tạo nhắc xác nhận; không tự đồng ý và không tự tính hoàn thành.

## 7 Giao bài và nộp bài P1

Hình 5. Bố cục rà soát, không phải màn hình ứng dụng đã phát hành.

Đề giao, hạn nộp và đáp án gắn phiên bản. Phụ huynh nộp thay ghi riêng tác giả thao tác và học sinh; gửi lại tạo lần nộp mới bất biến.

## 8 Nhận xét đáp án và tiến trình

Hình 6. Bố cục rà soát, không phải màn hình ứng dụng đã phát hành.

Đóng bài và mở đáp án là hai lệnh riêng. Tiến trình hiển thị mẫu số, kỳ và thời điểm chốt; trạng thái N bằng 0 phải được giải thích rõ.

## 9 Trạng thái và bất biến của lịch lớp

| Đối tượng | Chuyển trạng thái | Điều kiện |
|---|---|---|
| Đề nghị | PENDING → APPLIED | Bên còn lại chấp thuận; đúng phiên bản; còn hạn; cả hai lịch hợp lệ. |
| Đề nghị | PENDING → REJECTED hoặc CANCELLED | Từ chối hoặc người đề nghị rút; không đổi lịch gốc. |
| Đề nghị | PENDING → EXPIRED | Đã đến hạn theo giờ máy chủ; không tự áp dụng. |
| Đề nghị | PENDING → NOT_APPLICABLE | Buổi bị hủy, lớp kết thúc, phiên bản cũ hoặc lịch mới không còn khả dụng. |
| Buổi | SCHEDULED → CANCELLED | Một bên hủy tương lai có lý do; giải phóng cả hai booking. |
| Lớp | WAITING hoặc ACTIVE → ENDED | Một bên kết thúc có lý do; giữ lịch sử; không mở lại tự động. |

Đổi lịch chỉ áp dụng buổi chưa bắt đầu; thời lượng vẫn theo thỏa thuận 60, 90 hoặc 120 phút. Khoảng lịch là [bắt đầu, kết thúc), đệm học trực tuyến bằng 0. Xét xung đột theo cả học sinh và gia sư, bao gồm các lớp khác trong cùng tài nguyên.

Hạn đổi lịch bằng thời điểm sớm hơn giữa lúc tạo cộng 48 giờ và giờ bắt đầu buổi gốc. Chi tiết đề xuất cho học bù là thời điểm sớm hơn giữa lúc tạo cộng 48 giờ và giờ bắt đầu buổi mới; cần xác nhận D35–01. Không đặt giới hạn số lần học bù ngoài PRD/SRS.

Buổi đã bắt đầu hoặc đã qua không được viết lại lịch kế hoạch. Mọi hiệu chỉnh đi qua lịch sử kiểm toán theo F06. Học bù không sửa buổi gốc thành hoàn thành. Thay đổi lịch tăng revision và buộc công việc soạn bài đang chạy kiểm lại ngữ cảnh.

## 10 Nhật ký và kết quả tham gia

Gia sư tạo nháp sau khi buổi bắt đầu. Ba trường nội dung đã dạy, nhận xét chuyên môn và kế hoạch tiếp theo có giới hạn riêng 5.000 ký tự. Có mặt yêu cầu bắt đầu thực tế nhỏ hơn kết thúc thực tế và kết thúc không vượt giờ máy chủ. Vắng không có giờ dạy thực tế giả.

| Tình huống | Kết quả buổi | Đủ dùng nhận xét cho AI |
|---|---|---|
| Chưa công bố nhật ký | AWAITING_ TUTOR | Không |
| Đã công bố, chưa có ý kiến phụ huynh | AWAITING_ PARENT | Không |
| Phụ huynh đồng ý có mặt đúng bản | COMPLETED | Có, nếu nguồn và lớp còn cho phép |
| Phụ huynh đồng ý vắng đúng bản | ABSENT | Không |
| Phụ huynh báo khác biệt có ghi chú | DISPUTED | Không |
| Quá 72 giờ chưa phản hồi | Giữ trạng thái đang chờ | Không; chỉ nhắc trong hệ thống |

Nháp không thay bản đang công bố. Công bố bản sửa tạo phiên bản mới; bản cũ và mọi ý kiến vẫn được lưu. D35–03 đề xuất mọi bản công bố mới đều cần phụ huynh xác nhận lại. Lệnh xác nhận phải chỉ rõ logVersionId và If-Match; không chuyển ý kiến từ bản cũ sang bản mới.

COMPLETED chỉ hình thành khi nhật ký hiện hành do gia sư công bố ghi có mặt, phụ huynh đồng ý đúng phiên bản và không còn khác biệt trên bản đó. Học sinh và quản trị không được xác nhận thay. Ngữ cảnh AI giữ phiên bản đã sử dụng; không viết lại nội dung bài soạn cũ khi nhật ký thay đổi.

## 11 Bài tập phiên bản và vòng đời đáp án

Chỉ gia sư của lớp ACTIVE được giao phiên bản đã duyệt từ nguồn hoặc bài soạn hợp lệ. Câu hỏi, nguồn, hạn nộp và đáp án được chốt theo assignmentVersion. Phát hành lại tạo bản mới; các lần nộp cũ tiếp tục gắn đề cũ. Thiết bị đang giữ phiên bản cũ nhận lỗi và giữ dữ liệu nhập để người dùng đối chiếu trước khi nộp lại.

| Nội dung | Quy tắc kiểm soát |
|---|---|
| Nộp bài | Phụ huynh nộp thay hoặc học sinh có liên kết hiện hành; lưu actor và student riêng. |
| Giới hạn | Chữ ≤10.000 ký tự; tối đa 5 ảnh JPEG/PNG; mỗi ảnh ≤5 MiB. Kiểm tra byte, loại tệp, hash và ticket trước commit. |
| Hạn nộp | receivedAt > dueAt mới là muộn; bằng hạn không muộn. Không đặt hạn thì không muộn. Bài OPEN qua hạn vẫn nhận. |
| Lần nộp | Bất biến, đánh số tăng; gửi lại cùng khóa trả kết quả cũ. Khác nội dung cùng khóa báo xung đột. |
| Nhận xét | Gia sư chỉ định đúng submissionId; thêm phiên bản nhận xét, không sửa lời giải, tác giả hoặc cờ muộn. |
| Đáp án | Tách bảng và API; chỉ mở chủ động sau đóng, đúng bản và đúng quyền. DTO đề hoặc lần nộp luôn không chứa đáp án. |

Đến hạn không tự đóng bài hoặc mở đáp án. Lớp kết thúc đề xuất đóng nhận bài với lý do CLASS_ENDED và giữ đáp án chưa mở ở trạng thái ẩn. Đáp án đã mở vẫn phải kiểm quyền hiện hành, nguồn và thời hạn đọc; không cấp quyền vĩnh viễn từ một lần truy cập trước.

## 12 Mô hình dữ liệu bổ sung

DDL004 là ứng viên áp dụng sau DDL001, DDL002 và hiệu chỉnh003 của TB G3 04. Bổ sung 14 bảng, nâng tổng cấu trúc ứng viên lên 61 bảng. Đây là thiết kế vật lý chuẩn bị triển khai, chưa phải migration sản xuất đã thử với dữ liệu thực.

| Nhóm bảng mới | Mục đích và khóa chính |
|---|---|
| schedule_change_requests | Đề nghị, thời hạn, bên đề nghị, revision buổi kỳ vọng và kết quả. |
| session_schedule_history | Lịch trước và sau; liên kết request, actor, receipt; duy nhất buổi và revision mới. |
| class_end_records | Một lần kết thúc lớp; lý do, actor, thời điểm và receipt. |
| lesson_log_heads | Con trỏ bản công bố; FK gồm version, session và class. |
| attendance_decisions | Ý kiến tăng phiên bản theo logVersion, actor và receipt. |
| confirmation_reminders | Duy nhất logVersion, người nhận và loại nhắc 72 giờ. |
| assignments và assignment_versions | Trạng thái bài và bản đề; FK bản hiện hành đúng bài và lớp. |
| assignment_private_answers và assignment_answer_releases | Đáp án riêng cùng lệnh mở theo assignmentVersion. |
| submission_attempts và submission_attachments | Bản nộp bất biến, actor khác student; tối đa năm vị trí ảnh. |
| submission_review_versions | Lịch sử nhận xét đúng bản nộp. |
| evaluation_events | Sự kiện mã giả, kiểu, thời gian, phiên bản và kết quả; không có nội dung tự do. |

Các thay đổi trên bảng cũ gồm FK học bù cùng lớp và không tự tham chiếu; mở trường nhận xét từ 4.000 lên 5.000 ký tự; bổ sung nhật ký có cấu trúc và khóa kết hợp lớp với học sinh. answer_releases theo plan của thiết kế trước không được dùng thay đáp án bài tập P1.

## 13 Phân quyền và nối vòng đời học liệu

| Thao tác | Phụ huynh | Gia sư | Học sinh | Quản trị |
|---|---|---|---|---|
| Đổi, hủy, kết thúc | Lớp của con | Lớp phụ trách | Không | Không làm thay |
| Học bù | Đề nghị hoặc duyệt | Đề nghị hoặc duyệt | Không | Không làm thay |
| Công bố nhật ký | Đọc | Công bố | Theo quyền đã cấp | Không mặc định |
| Xác nhận tham gia | Đúng bản | Không làm thay | Không | Không làm thay |
| Giao và nhận xét bài | Đọc | Thực hiện | Đọc trong liên kết | Không mặc định |
| Nộp bài | Nộp thay con | Không | Liên kết còn hiệu lực | Không |
| Đóng và mở đáp án | Không | Đúng bản | Không | Không |
| Tiến trình PH10 | Con của mình | Lớp phụ trách | Không mặc định | Chỉ tổng hợp AD02 |

Quyền phải kiểm tra phía máy chủ ở mỗi lần đọc, ghi, lấy tệp và mở liên kết thông báo. Tài khoản, vai trò, quan hệ lớp, studentLinkRevision, trạng thái nguồn và thời hạn đều phải còn hợp lệ tại commit; ẩn nút chỉ hỗ trợ giao diện.

Khi lớp kết thúc, chặn USE_AI bằng chính sách lớp và classRevision tại bước nhận việc, truy cập nguồn, ghi kết quả và công bố. Không tự thu hồi toàn bộ sourceEpoch chỉ vì kết thúc lớp: cách đó sẽ chặn sai nội dung công bố còn quyền đọc tối đa 90 ngày theo BR08. Thu hồi nguồn là sự kiện khác và vẫn chặn nội dung dẫn xuất.

Gia sư không tiếp tục xem hồ sơ trẻ hoặc lịch trống tương lai ngoài quan hệ lớp. Bản gốc riêng thuộc chủ sở hữu hoặc quyền cấp độc lập. Xóa, xuất dữ liệu, xử lý khiếu nại và hiệu chỉnh sau kết thúc tiếp tục cần thiết kế F06.

## 14 Giao dịch lịch và kết thúc lớp

| Mã | Giao dịch | Đọc khóa và kết quả nguyên tử |
|---|---|---|
| TX17 | Tạo đề nghị đổi hoặc bù | Kiểm bên tham gia, trạng thái, revision và hạn; ghi request, receipt và outbox. Chưa đặt booking mới. |
| TX18 | Phản hồi đề nghị | Khóa lớp, buổi và request; khóa tài nguyên theo thứ tự cố định; kiểm thời hạn và revision; đổi cả hai booking, session, lịch sử, request, receipt và outbox trong một commit. |
| TX19 | Hủy buổi tương lai | Khóa lớp và buổi; kiểm start > now; ghi lý do; vô hiệu cả hai booking và request liên quan; receipt cùng outbox. |
| TX20 | Kết thúc lớp | Khóa lớp; ghi endedAt và revision; hủy buổi start > now, hết hiệu lực request, đóng bài mở, chặn công việc AI theo lớp; receipt và outbox cùng commit. |

TX18 tạo buổi mới khi học bù, có makeupForId cùng lớp; không thay đổi thời gian hoặc kết quả buổi gốc. Đổi lịch cập nhật cùng session và tăng revision. Mọi xung đột, lỗi ghi booking hoặc kiểm tra cuối giao dịch phải rollback toàn bộ thay đổi lịch; booking cũ còn hiệu lực.

### 14.1 Thứ tự khóa và gửi lại

Thứ tự chung là lớp, thực thể nghiệp vụ, tài nguyên theo khóa ổn định rồi dữ liệu phụ. Worker dùng cùng quy ước; thử lại deadlock hoặc serialization có giới hạn và đúng khóa idempotency. Không coi khóa mô phỏng trong JavaScript là bằng chứng cho khóa nhiều phiên PostgreSQL.

Receipt định danh theo actor, loại lệnh và Idempotency-Key, có hash payload chuẩn hóa. Gửi lại cùng nội dung trả kết quả đã commit; khác nội dung báo xung đột. Khi mất phản hồi, operation lookup kiểm quyền hiện hành và trả biên nhận tối thiểu, không làm lộ nội dung đã bị thu hồi.

## 15 Giao dịch nhật ký và xác nhận

| Mã | Giao dịch | Ràng buộc commit |
|---|---|---|
| TX21 | Lưu nháp nhật ký | Gia sư đúng lớp; buổi đã bắt đầu; validate từng trường và giờ thực tế; không đổi con trỏ công bố; dùng revision để chống ghi đè. |
| TX22 | Công bố phiên bản | Khóa lớp, session, head và draft; kiểm quyền và chuyên môn; tạo bản bất biến, đổi head, đưa trạng thái về chờ ý kiến; receipt và thông báo cùng commit. |
| TX23 | Ghi ý kiến phụ huynh | Khóa lớp, session, head và bản cụ thể; kiểm If-Match; thêm decision; chỉ COMPLETED nếu cùng bản hiện hành có mặt, đồng ý và không bất đồng. |

Một bản công bố mới không xóa bản cũ hoặc các ý kiến trước. Gia sư không được sửa trực tiếp nhận xét đã dùng trong bài soạn công bố. Nhận xét đủ điều kiện cho AI được trỏ bằng versionId trong ngữ cảnh cụ thể; bản sửa không tự thay nội dung đầu ra lịch sử.

### 15.1 Nhắc xác nhận sau 72 giờ

Worker kiểm tra lại head và ý kiến trước khi tạo nhắc. Khóa duy nhất gồm logVersion, recipient và reminderKind để tránh gửi lặp. Cùng giao dịch ghi reminder và outbox, sau đó bộ chuyển phát tạo thông báo trong hệ thống. Nhắc không tạo attendanceDecision và không đổi kết quả buổi.

Nếu có khác biệt, giữ chuyên môn của gia sư và ý kiến phụ huynh dưới dạng phiên bản riêng. Không tự chuyển thành đồng ý khi quá hạn. Trình tự giải quyết bất đồng, hỗ trợ và hiệu chỉnh được bàn giao cho F06, chưa mô phỏng như đã có người quản trị xử lý.

## 16 Giao dịch bài tập và đáp án P1

| Mã | Giao dịch | Ràng buộc commit |
|---|---|---|
| TX24 | Giao bản bài tập | Lớp ACTIVE, gia sư và nguồn còn quyền; bản được duyệt; tạo version bất biến và đổi issued head. Bản cũ vẫn gắn với lần nộp cũ. |
| TX25 | Nhận lần nộp | Khóa lớp, assignment, version và studentLink; kiểm OPEN, revision, tác giả, byte tệp và ticket; gắn receivedAt máy chủ, due snapshot; receipt/outbox cùng commit. |
| TX26 | Nhận xét bản nộp | Gia sư chỉ định submissionId; thêm reviewVersion; không sửa phần bài làm, actor, receivedAt hoặc late. |
| TX27 | Đóng nhận bài | Kiểm bản hiện hành và quyền; chuyển CLOSED, ghi closedAt cùng lý do. Không ghi answerRelease. |
| TX28 | Mở đáp án đúng bản | Chỉ gia sư, sau CLOSED; kiểm nguồn, version và quyền tại commit; thêm release với receipt. Đọc qua endpoint riêng, kiểm quyền lại mỗi lần. |

Đề xuất ticket tệp riêng có hạn không quá 60 giây; cần D35–04 xác nhận. Ticket ràng buộc actor, học sinh, assignmentVersion và objectKey; dịch vụ không tin tên đuôi hoặc MIME từ trình duyệt. Tệp chưa commit không thành nội dung có thể đọc; cần chính sách dọn tệp mồ côi trong kiến trúc lưu trữ.

CLOSED hoặc lớp ENDED chặn lần nộp mới. Gửi lại đúng lệnh đã nhận trước khi đóng chỉ trả receipt cũ, không phát sinh lần nộp. Thu hồi liên kết học sinh hoặc nguồn chặn truy cập và dữ liệu nhạy cảm dù URL cũ còn tồn tại. Cache đáp án phải private, no-store và không dùng chung với DTO đề bài.

## 17 Tiến trình thông báo và dữ liệu đánh giá

TX29 trả snapshot có from, to, asOf, definitionVersion và dataRevision. Mẫu số N gồm các buổi có thời điểm bắt đầu kế hoạch hiệu lực thuộc [from, to) và kết thúc kế hoạch không vượt asOf; giữ cả buổi hủy, vắng, đang chờ hoặc có khác biệt. Tử số C là số buổi COMPLETED đủ xác nhận trong đúng N. Nếu N bằng 0, trả NULL cùng NOT_APPLICABLE.

Buổi bù là bản ghi riêng và chỉ vào mẫu số khi đã đến hạn; buổi gốc vẫn giữ trạng thái. Buổi tương lai thống kê riêng. Chỉ số cập nhật đủ xác nhận mô tả dữ liệu vận hành, không kết luận năng lực hoặc tiến bộ học tập. Số bài giao, số học sinh, số lần nộp và số bản đã nhận xét phải tách biệt.

| Chỉ số hoặc dịch vụ | Định nghĩa và giới hạn |
|---|---|
| Kết nối trong 7 ngày | Giữ các trường hợp thất bại hoặc hủy theo nhóm đủ thời gian; không chỉ tính lớp thành công. |
| Duy trì ngày 28 | Nhóm lớp đã bắt đầu đủ 28 ngày; xét hoạt động theo ngày 22 đến 28 theo PRD/SRS. |
| Quay lại soạn trong 14 ngày | Phê duyệt lần hai trong ngày 2 đến 14 trên nhóm đủ thời gian quan sát. |
| TX30 thông báo | Outbox cùng commit; dedupe theo event và người nhận; mở liên kết luôn kiểm quyền hiện hành. |
| Sự kiện đánh giá | Mã giả, loại, thời điểm, phiên bản và kết quả. Không có tên trẻ, liên hệ, đề bài hoặc văn bản nhật ký. |

Mục tiêu độ trễ thông báo p95 dưới 10 giây chưa được đo. Chỉ có thông báo trong hệ thống ở R0; không thêm push, chat hoặc email nghiệp vụ. AD02 chỉ xem tổng hợp trong quyền và độ đầy đủ dữ liệu; các tỷ lệ chưa đủ nhóm quan sát phải ghi chưa đủ dữ liệu.

## 18 Hợp đồng API và xử lý lỗi

Tệp OpenAPI 3.1.1 bổ sung 32 thao tác trên 29 đường dẫn, 34 schema. Đây là hợp đồng ứng viên cho các luồng F03/F05; máy chủ ví dụ không phải API đã triển khai. Các thao tác ghi yêu cầu Bearer và Idempotency-Key; thao tác sửa hoặc quyết định dùng If-Match theo hợp đồng.

| Nhóm API | Số thao tác | Nội dung |
|---|---|---|
| Lớp và lịch | 8 | Đề nghị và phản hồi; hủy; học bù; kết thúc. Đọc lớp/buổi dùng API cơ sở. |
| Nhật ký và xác nhận | 6 | Tạo, đọc, sửa nháp; công bố và ghi ý kiến đúng bản. |
| Bài tập và đáp án | 14 | Giao bản, đọc, ticket, nộp, nhận xét, đóng và mở/đọc đáp án. |
| Tiến trình và thông báo | 4 | Snapshot, danh sách thông báo, đánh dấu đã đọc và tổng hợp. |

Danh mục đầy đủ và quyền từng thao tác nằm trong operation_catalog.json. Lỗi trả mã ổn định, correlationId và hướng xử lý; không đưa nội dung đề hoặc dữ liệu cá nhân vào log kỹ thuật. Dùng 404 khi cần che đối tượng ngoài quyền, 409 cho trạng thái hoặc idempotency xung đột, 412 cho phiên bản cũ, 422 cho dữ liệu không hợp lệ và 429 cho giới hạn tần suất.

Đầu ra PublicAssignment và SubmissionDTO không có đáp án ngay cả khi đã mở. Endpoint đáp án riêng kiểm release đúng assignmentVersion cùng quyền và nguồn. Các API nội dung dùng Cache-Control private, no-store. API tra operation dùng lại hợp đồng chung F02/F04, trả trạng thái sau mất phản hồi theo quyền hiện hành.

## 19 Quyết định chi tiết chờ thống nhất

| Mã | Nội dung đề xuất | Điểm cần xác nhận |
|---|---|---|
| D35–01 | TTL học bù = min tạo +48 giờ, giờ buổi mới; một request một buổi bù. | PRD đã nêu hạn đổi; chi tiết học bù cần chốt. |
| D35–02 | Kết thúc lớp chặn AI theo classRevision; đóng bài mở, không tự mở đáp án. | Giữ quyền đọc công bố hợp lệ tối đa 90 ngày; không thu hồi nguồn hàng loạt. |
| D35–03 | Mọi bản nhật ký công bố mới cần xác nhận phụ huynh lại. | Đổi lại tính minh bạch; có thể tăng thao tác xác nhận. |
| D35–04 | Nộp mới chỉ vào đề hiện hành; đáp án mở theo assignmentVersion. Ticket ảnh đề xuất tối đa 60 giây. | Giữ bản cũ để đối chiếu; kiểm lại liên kết, quyền và byte trước commit. |
| D35–05 | Mọi tỷ lệ tiến trình kèm N, kỳ, asOf và definitionVersion. | Không tính N=0 thành 0%; tách trạng thái dữ liệu và kết quả học tập. |

Dương Đính và Đức Huy chưa có xác nhận riêng cho D35–01 đến D35–05 trong hồ sơ này. Các mục trên là đề xuất thiết kế chi tiết trong phạm vi đã thống nhất; không ghi thay ý kiến thành viên hoặc tự nâng trạng thái nghiệm thu.

### 19.1 Trách nhiệm rà soát

Theo phương án G3, Đính chủ trì nghiệp vụ, backend và vận hành; Huy chủ trì matching, AI, học liệu và mobile. Đề xuất Đính kiểm TX17–30, dữ liệu và quyền; Huy rà soát màn hình, nguồn/AI và vòng đời P1; hai người rà soát chéo kết thúc lớp và version. Việc giao chủ trì không thay xác nhận nguồn lực định lượng còn thiếu.

## 20 Kết quả kiểm tra và điều kiện tiếp theo

| Hạng mục | Kết quả thực hiện | Giới hạn |
|---|---|---|
| Mô hình hành vi | 33 kiểm tra tự động đạt trên mô hình JavaScript. | Không chứng minh phân quyền máy chủ hoặc cạnh tranh nhiều phiên. |
| DDL004 | 34 câu lệnh phân tích cú pháp; biên dịch chuỗi001–004 thành công, 61 bảng. | PostgreSQL17.5 nhúng WebAssembly một phiên; chưa thử PostgreSQL native hoặc migration dữ liệu. |
| OpenAPI | 32 thao tác, 29 đường dẫn, 34 schema; kiểm cấu trúc OpenAPI3.1.1 đạt. | Chưa có backend để thử API và quyền thực tế. |
| Bản mẫu giao diện | Rà soát tương tác các nhánh chính và ngoại lệ; biên bản trong gói kỹ thuật. | Dữ liệu mẫu; chưa có người dùng thật, AI thật hoặc tải tệp lên máy chủ. |
| Tải và bảo mật | Chưa thực hiện trong phạm vi bàn giao này. | Không có kết luận chịu tải hàng nghìn người dùng hoặc đáp ứng NFR. |

F03/F05 đủ làm đầu vào cho phiên rà soát thiết kế. G3 chưa được nghiệm thu tổng thể: còn F01 và F06, kiến trúc toàn hệ thống, mô hình đe dọa, NFR và kế hoạch đo tải/khôi phục, backlog triển khai theo phụ thuộc, kết quả rà soát thành viên, nguồn lực và các kiểm chứng người dùng theo tiêu chí G3.

Bước tiếp theo là thiết kế F01 về tài khoản, hồ sơ và liên kết học sinh, cùng F06 về hỗ trợ, quyền dữ liệu, thu hồi và hiệu chỉnh. Sau đó hợp nhất kiến trúc, hợp đồng và ma trận truy vết để tổ chức nghiệm thu G3. Chỉ chuyển các điều kiện thành đã đạt khi có bằng chứng cụ thể.

### 20.1 Ghi nhận kết quả phiên rà soát

| Người rà soát | Kết luận | Ngày và điều kiện |
|---|---|---|
| Dương Đính | Chờ ghi nhận | Chưa có kết luận riêng cho hồ sơ này. |
| Đức Huy | Chờ ghi nhận | Chưa có kết luận riêng cho hồ sơ này. |
