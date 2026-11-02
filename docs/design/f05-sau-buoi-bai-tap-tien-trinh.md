# F05 — Sau buổi học, bài tập và tiến trình

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 05 mục 10, 11, 13, 15–17

### Nhật ký và kết quả tham gia

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

### Bài tập phiên bản và vòng đời đáp án

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

### Phân quyền và nối vòng đời học liệu

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

### Giao dịch nhật ký và xác nhận

| Mã | Giao dịch | Ràng buộc commit |
|---|---|---|
| TX21 | Lưu nháp nhật ký | Gia sư đúng lớp; buổi đã bắt đầu; validate từng trường và giờ thực tế; không đổi con trỏ công bố; dùng revision để chống ghi đè. |
| TX22 | Công bố phiên bản | Khóa lớp, session, head và draft; kiểm quyền và chuyên môn; tạo bản bất biến, đổi head, đưa trạng thái về chờ ý kiến; receipt và thông báo cùng commit. |
| TX23 | Ghi ý kiến phụ huynh | Khóa lớp, session, head và bản cụ thể; kiểm If-Match; thêm decision; chỉ COMPLETED nếu cùng bản hiện hành có mặt, đồng ý và không bất đồng. |

Một bản công bố mới không xóa bản cũ hoặc các ý kiến trước. Gia sư không được sửa trực tiếp nhận xét đã dùng trong bài soạn công bố. Nhận xét đủ điều kiện cho AI được trỏ bằng versionId trong ngữ cảnh cụ thể; bản sửa không tự thay nội dung đầu ra lịch sử.

### Nhắc xác nhận sau 72 giờ

Worker kiểm tra lại head và ý kiến trước khi tạo nhắc. Khóa duy nhất gồm logVersion, recipient và reminderKind để tránh gửi lặp. Cùng giao dịch ghi reminder và outbox, sau đó bộ chuyển phát tạo thông báo trong hệ thống. Nhắc không tạo attendanceDecision và không đổi kết quả buổi.

Nếu có khác biệt, giữ chuyên môn của gia sư và ý kiến phụ huynh dưới dạng phiên bản riêng. Không tự chuyển thành đồng ý khi quá hạn. Trình tự giải quyết bất đồng, hỗ trợ và hiệu chỉnh được bàn giao cho F06, chưa mô phỏng như đã có người quản trị xử lý.

### Giao dịch bài tập và đáp án P1

| Mã | Giao dịch | Ràng buộc commit |
|---|---|---|
| TX24 | Giao bản bài tập | Lớp ACTIVE, gia sư và nguồn còn quyền; bản được duyệt; tạo version bất biến và đổi issued head. Bản cũ vẫn gắn với lần nộp cũ. |
| TX25 | Nhận lần nộp | Khóa lớp, assignment, version và studentLink; kiểm OPEN, revision, tác giả, byte tệp và ticket; gắn receivedAt máy chủ, due snapshot; receipt/outbox cùng commit. |
| TX26 | Nhận xét bản nộp | Gia sư chỉ định submissionId; thêm reviewVersion; không sửa phần bài làm, actor, receivedAt hoặc late. |
| TX27 | Đóng nhận bài | Kiểm bản hiện hành và quyền; chuyển CLOSED, ghi closedAt cùng lý do. Không ghi answerRelease. |
| TX28 | Mở đáp án đúng bản | Chỉ gia sư, sau CLOSED; kiểm nguồn, version và quyền tại commit; thêm release với receipt. Đọc qua endpoint riêng, kiểm quyền lại mỗi lần. |

Đề xuất ticket tệp riêng có hạn không quá 60 giây; cần D35–04 xác nhận. Ticket ràng buộc actor, học sinh, assignmentVersion và objectKey; dịch vụ không tin tên đuôi hoặc MIME từ trình duyệt. Tệp chưa commit không thành nội dung có thể đọc; cần chính sách dọn tệp mồ côi trong kiến trúc lưu trữ.

CLOSED hoặc lớp ENDED chặn lần nộp mới. Gửi lại đúng lệnh đã nhận trước khi đóng chỉ trả receipt cũ, không phát sinh lần nộp. Thu hồi liên kết học sinh hoặc nguồn chặn truy cập và dữ liệu nhạy cảm dù URL cũ còn tồn tại. Cache đáp án phải private, no-store và không dùng chung với DTO đề bài.

### Tiến trình thông báo và dữ liệu đánh giá

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
