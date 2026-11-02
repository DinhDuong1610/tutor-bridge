# TutorBridge_G3_06_F01_F06_Kien_truc_v0_1

> Tài liệu lịch sử, độ ưu tiên thấp nhất. Bản Word kèm wireframe ở `docs/source-docx/g3/TutorBridge_G3_06_F01_F06_Kien_truc_v0_1.docx`. Hình ảnh không có trong bản này.

NHÓM DỰ ÁN TUTORBRIDGE

THIẾT KẾ F01 VÀ F06

KIẾN TRÚC TỔNG THỂ TUTORBRIDGE

Tài liệu điều hành và triển khai giai đoạn thiết kế giải pháp

| Thông tin kiểm soát | Nội dung |
|---|---|
| Mã tài liệu | TB G3 06 |
| Phiên bản | 0.1 |
| Ngày phát hành | 29/10/2026 |
| Nhóm thực hiện | Dương Đính và Đức Huy |
| Mục đích sử dụng | Rà soát thiết kế tài khoản, hồ sơ, hỗ trợ, quyền dữ liệu và phương án triển khai. |
| Trạng thái | Trình rà soát; chưa nghiệm thu G3. |

Phạm vi yêu cầu tham chiếu: TB G2 02 v1.1. Trạng thái xác nhận và điều kiện bàn giao được quản lý tại TB G2 03 v1.2.

## Mục lục và tài liệu tham chiếu

1 Kết luận và phạm vi bàn giao    Trang 3

2 Luồng F01 và trạng thái giao diện    Trang 4

3 Hồ sơ học sinh gia sư và xác minh    Trang 5

4 Luồng F06 và quyền hỗ trợ    Trang 6

5 Thu hồi xóa và phục hồi dữ liệu    Trang 7

6 Kiến trúc tổng thể và ranh giới mô đun    Trang 8

7 Ánh xạ dữ liệu và điều kiện tích hợp    Trang 9

8 Giao dịch và hợp đồng API bổ sung    Trang 10

9 Bảo mật và mô hình đe dọa    Trang 11

10 AI chi phí và vận hành tác vụ    Trang 12

11 Migration sao lưu và quy trình phát hành    Trang 13

12 Kế hoạch kiểm chứng và nguồn lực    Trang 14

13 Backlog G4 dự kiến và phụ thuộc    Trang 15

14 Quyết định và điều kiện khóa G3    Trang 16

Tài liệu nguồn đã đối chiếu: PRD/SRS TB G2 02 v1.1; khởi động và danh mục màn hình TB G3 01 v0.1; thiết kế F03/F05 TB G3 05 v0.1. Gói F02/F04 và sửa 003 được tham chiếu cho schema và giao dịch lịch. Các quyết định mới được quản lý tại mục 14.

## 1 Kết luận và phạm vi bàn giao

Hồ sơ bổ sung thiết kế F01 về tài khoản, hồ sơ và liên kết học sinh; F06 về hỗ trợ, quyền tạm thời, thu hồi và xóa dữ liệu. Kiến trúc đề xuất hợp nhất sáu luồng F01–F06 trên cùng ranh giới mô đun, giao dịch và vận hành. Đây là đầu vào trình rà soát G3, chưa phải biên bản nghiệm thu hoặc sản phẩm đã triển khai.

Cơ sở đối chiếu là PRD/SRS TB G2 02 v1.1, danh mục màn hình TB G3 01 v0.1 và thiết kế F03/F05 TB G3 05 v0.1. F02/F04 cùng các sửa lỗi SQL đã có tiếp tục là đường cơ sở; tài liệu này không thay thế hoặc tự sửa migration trước đó.

Dương Đính đã yêu cầu tiếp tục bước thiết kế. Việc này không thay xác nhận riêng của Đức Huy, kết quả thử người dùng hoặc xác nhận nguồn lực. Các giá trị kỹ thuật mới trong hồ sơ là đề xuất cần rà soát.

Gói kèm gồm đặc tả màn hình, hợp đồng API bổ sung, ánh xạ dữ liệu cần mở rộng, mô hình đe dọa, ADR, kế hoạch phục hồi và backlog. Không phát hành migration 005 vì cần đối chiếu cấu trúc vật lý, dữ liệu cũ và kiểm chứng giao dịch trên PostgreSQL máy chủ trước khi khóa schema.

| Luồng | Yêu cầu tiếp nhận | Ranh giới |
|---|---|---|
| F01 | UC01–03; FR-01,02,03,05,06,07,36,39 | AUTH01–07, PH02, GS02–03, AD01,03–05 |
| F06 | UC14,16; FR-40,42 | COM02–04, AD06–07,09–10 |
| Chung | FR-37; UC15; NFR01–16 | Thông báo, quyền, outbox, vận hành và kiểm chứng |

## 2 Luồng F01 và trạng thái giao diện

AUTH01 chỉ cho phụ huynh hoặc gia sư tự đăng ký. Người dùng nhập email, mật khẩu 15–128 ký tự, tên hiển thị và chấp thuận điều khoản có phiên bản. Máy chủ gán vai trò theo danh sách cho phép; không tiếp nhận quyền quản trị do ứng dụng gửi. Tài khoản chờ xác minh chưa được tạo lớp hoặc tải học liệu.

AUTH02 đăng nhập; AUTH03 xác minh liên hệ. Liên kết xác minh hạn 30 phút, khôi phục hạn 15 phút và lời mời học sinh hạn 24 giờ. Mã chỉ được dùng một lần, đúng mục đích và đúng người. Thông báo khôi phục dùng nội dung chung để không xác nhận sự tồn tại tài khoản.

AUTH06 cho xem phiên, thu hồi phiên và đổi mật khẩu sau tái xác thực. Đổi hoặc khôi phục mật khẩu thu hồi toàn bộ phiên cũ trong cùng giao dịch. Dữ liệu nháp đã lưu tại máy chủ vẫn tồn tại sau đăng nhập lại.

Đăng nhập sai năm lần trong 15 phút kích hoạt trì hoãn 15 phút; kiểm soát theo tài khoản và nguồn gửi. Không ghi mật khẩu, mã liên kết hoặc token vào log. Hạn phiên người dùng là 24 giờ không hoạt động, tối đa 7 ngày; quản trị là 30 phút và 12 giờ, kèm yếu tố xác thực bổ sung trước thí điểm.

| Màn hình | Tác vụ | Trạng thái bắt buộc |
|---|---|---|
| AUTH01–03 | Đăng ký, đăng nhập, xác minh | Đang gửi; lỗi mạng; hết hạn; đã dùng; bị khóa; giới hạn gửi |
| AUTH04–06 | Khôi phục, đổi mật khẩu, phiên | Thông báo chung; mã sai mục đích; phiên đã thu hồi; cần đăng nhập lại |
| AUTH07 P1 | Nhận lời mời học sinh | Sai email; hết hạn; đã nhận; bị thu hồi; tranh chấp liên kết |
| AD01 | Đăng nhập quản trị | Không có tự đăng ký; bước xác thực bổ sung; phiên hết hạn |

## 3 Hồ sơ học sinh gia sư và xác minh

PH02 tạo hồ sơ con bằng tên hiển thị, khối lớp và mục tiêu ngắn; không bắt buộc ngày sinh, trường, ảnh hoặc địa chỉ. owner được lấy từ phiên phụ huynh đã xác minh, không lấy từ payload. Chọn rõ hồ sơ con trước thao tác và không gộp dữ liệu theo tên hoặc email.

Một phụ huynh quản lý mỗi hồ sơ trong R0. P1 dùng lời mời riêng tới email học sinh. Người nhận phải chứng minh quyền với email đích và xác nhận trước liên kết; không tìm ghép học sinh theo mã lớp. Thu hồi liên kết tăng revision quyền, vô hiệu mọi yêu cầu đọc/nộp tiếp theo nhưng giữ dấu vết bài đã nộp.

GS02/GS03 tách bản tự khai, bản chuyên môn đang duyệt và phiên bản đã xác minh. Kinh nghiệm 0–600 tháng; giá nguyên dương VND/60 phút. Giá và lịch mới chỉ áp dụng đề nghị mới, không sửa thỏa thuận lớp cũ. Sửa chuyên môn chuyển phần đó về chờ duyệt; không dùng nhãn duyệt cũ cho nội dung mới.

AD03/AD04 chỉ hiển thị hồ sơ được giao. Người xác minh không tự duyệt hồ sơ của mình; ghi phiên bản, phạm vi môn/khối, kết quả, lý do và thời điểm. Minh chứng gốc không xuất hiện ở DTO tìm gia sư. Nhãn xác minh chỉ mô tả phạm vi đã kiểm tra.

| Đối tượng | Chuyển trạng thái | Điều kiện |
|---|---|---|
| Lời mời | PENDING → ACCEPTED / REVOKED / EXPIRED | Một lần; đúng email và hồ sơ; kiểm lại tại commit |
| Chuyên môn | DRAFT → SUBMITTED → VERIFIED / NEEDS_INFO / REJECTED | Quyết định đúng revision; người được giao; cấm tự duyệt |
| Hồ sơ công khai | Bản đã duyệt → HIDDEN khi bị khóa | Tìm kiếm lọc trạng thái tài khoản và bản chuyên môn hiện hành |

## 4 Luồng F06 và quyền hỗ trợ

COM02 tạo vụ việc gồm loại, mô tả tối đa 2.000 ký tự và đối tượng trong quyền. COM03 hiển thị trạng thái, phản hồi và kết quả; không hiển thị ghi chú nội bộ hoặc vụ việc của gia đình khác. Không gửi mật khẩu hoặc học liệu ngoài quyền vào mô tả.

AD06 tiếp nhận và phân công. AD07 xử lý trong phạm vi vụ việc. Nếu cần học liệu, người xử lý xin cấp quyền theo đối tượng và hành động cụ thể, nêu mục đích, người chấp thuận và thời hạn tối đa 24 giờ. Đề xuất người đang có quyền cấp tương ứng mới được chấp thuận; người hỗ trợ không tự cấp quyền cho mình.

Quyền tạm không mặc định cho tải, AI hoặc đáp án. Quyền nguồn còn hiệu lực, quan hệ lớp và trạng thái tài khoản vẫn được kiểm tra. Thu hồi nguồn hoặc hết hạn quyền tạm chặn lần đọc tiếp theo, kể cả đường dẫn cũ. Việc gia hạn tạo quyết định mới có audit.

Khóa tài khoản thu hồi phiên và loại khỏi matching, không xóa lịch hoặc tự xác nhận buổi học. Gửi cảnh báo tối thiểu cho lớp liên quan và mở đầu mối hỗ trợ. Hiệu chỉnh chuyên môn hoặc xác nhận buổi phải dùng phiên bản mới trong F05, không sửa thẳng lịch sử bằng công cụ quản trị.

| Trạng thái vụ việc | Chuyển hợp lệ | Dữ liệu cần có |
|---|---|---|
| NEW | IN_PROGRESS | Người xử lý và phân loại |
| IN_PROGRESS | WAITING_INFO / RESOLVED | Yêu cầu bổ sung hoặc kết quả có lý do |
| WAITING_INFO | IN_PROGRESS | Phản hồi mới giữ lịch sử |
| RESOLVED | CLOSED / IN_PROGRESS | Đóng hoặc mở lại với lý do; không xóa kết quả cũ |

## 5 Thu hồi xóa và phục hồi dữ liệu

COM04 hiển thị quyền đang có và phạm vi yêu cầu. Yêu cầu thu hồi hợp lệ chặn quyền ngay tại commit; không chờ worker xóa tệp. Tạo tombstone và sự kiện outbox cùng giao dịch. Mọi đọc, tải, truy xuất, duyệt, công bố và trả kết quả AI kiểm lại quyền hiện hành.

Tác vụ xóa theo dõi từng kho: DB nội dung, object storage và phiên bản tệp, đoạn trích, chỉ mục truy xuất, cache, nháp và dẫn xuất. Hoàn tất kho đang hoạt động trong 7 ngày; không báo hoàn tất chỉ vì đã ẩn giao diện. Bản dẫn xuất bị ẩn cho người đã mất quyền cho đến khi loại nội dung nguồn và duyệt lại phiên bản mới.

BR08: nháp không hoạt động 30 ngày được nhắc rồi xóa sau 7 ngày nếu không giữ lại. Nội dung lớp xóa hoặc ẩn danh sau 90 ngày kết thúc; audit tối thiểu 180 ngày; backup mã hóa giữ 7 ngày. Tranh chấp chỉ giữ phần tối thiểu có lý do, thông báo và ngày rà lại tối đa 30 ngày. Đây là chính sách nội bộ cần rà trước dữ liệu thật.

Yêu cầu xóa tài khoản có lớp hoạt động phải điều phối kết thúc lớp, giải phóng booking và thông báo; không xóa cascade trước khi xử lý quan hệ. Nếu lỗi giữa chừng, tài khoản vẫn bị chặn truy cập và tác vụ giữ trạng thái cần xử lý. Bản đã tải ra thiết bị ngoài hệ thống không thể bị máy chủ xóa cưỡng chế.

| Trạng thái xóa | Ý nghĩa | Điều kiện tiến tiếp |
|---|---|---|
| BLOCKED_PENDING_PURGE | Quyền đã chặn; kho chưa xóa đủ | Có danh sách kho và tác vụ idempotent |
| PURGING / PARTIAL_FAILURE | Đang xử lý hoặc còn kho lỗi | Thử lại có giới hạn; đối soát object/index |
| ACTIVE_STORES_PURGED | Đã xóa đủ kho hoạt động | Ghi thời hạn backup và phần giữ tối thiểu |
| CLOSED | Kết thúc quy trình | Có bằng chứng kho, backup hết hạn hoặc phần giữ được giải trình |

## 6 Kiến trúc tổng thể và ranh giới mô đun

Đề xuất Spring Boot theo kiến trúc nguyên khối chia mô đun, PostgreSQL làm nguồn dữ liệu giao dịch; React Native cho Android, Next.js cho web quản trị và giới thiệu. API và worker chạy tiến trình riêng cùng phiên bản hợp đồng. Hai API có thể dùng chung DB và kho tệp; không lưu phiên hoặc khóa nghiệp vụ chỉ trong bộ nhớ tiến trình.

Ranh giới tin cậy gồm thiết bị người dùng, cổng API, DB và kho tệp riêng, worker xử lý tệp/AI, nhà cung cấp AI và dịch vụ email xác thực. API không chuyển nguyên token sang worker hoặc nhà cung cấp. Job chỉ mang mã đối tượng và phiên bản; worker truy xuất lại theo quyền đã kiểm tra.

Thông báo dùng transactional outbox và consumer khử lặp theo event/người nhận. Hàng đợi DB là phương án đầu tiên để giảm vận hành; việc chuyển sang broker riêng cần ADR theo bằng chứng tải. Matching đọc phiên bản hồ sơ/lịch hợp lệ; lớp và lịch cam kết luôn do giao dịch PostgreSQL quyết định.

| Mô đun | Sở hữu dữ liệu | Giao tiếp |
|---|---|---|
| Identity và Profiles | Tài khoản, phiên, hồ sơ, liên kết, xác minh | F01; cung cấp principal và quan hệ |
| Matching và Classes | Nhu cầu, gợi ý, đề nghị, lớp, booking | F02/F03; khóa lịch và receipt |
| Materials và Planning | Nguồn, quyền, đoạn trích, AI, phiên bản duyệt | F04; truy xuất theo giao quyền |
| Learning | Nhật ký, xác nhận, bài tập, tiến trình | F05; bản bất biến và đáp án riêng |
| Support và Privacy | Vụ việc, quyền tạm, xóa, tombstone | F06; lệnh thu hồi và audit |
| Platform | Outbox, cấu hình, quan sát, quota | Hạ tầng dùng chung; không bỏ qua quyền mô đun |

## 7 Ánh xạ dữ liệu và điều kiện tích hợp

Các tên dưới đây là mô hình đích ứng viên. Gói trước đã có accounts, auth_sessions và các thực thể hồ sơ/quyền; cần mở rộng đúng bảng hiện hữu thay vì tạo bảng trùng. Tệp data_mapping.json ghi rõ đối tượng hiện có phải đối chiếu và phần bổ sung logic. Chưa tuyên bố số bảng vật lý sau hợp nhất.

Mọi đối tượng thuộc gia đình hoặc lớp có khóa ngoại tới chủ thể; thời gian là timestamptz theo UTC, hiển thị theo múi giờ người dùng. revision chống ghi đè tách khỏi phiên bản chuyên môn bất biến. Token chỉ lưu digest; email cần bản bảo vệ để gửi thư và digest chuẩn hóa có khóa để kiểm trùng. Không ghi email nguyên văn vào audit.

Ràng buộc duy nhất phải bảo vệ email chuẩn hóa, token digest, liên kết học sinh đang hiệu lực và receipt theo actor/lệnh/key. Kiểm token một lần cần khóa hàng hoặc UPDATE có điều kiện consumed_at IS NULL AND expires_at > now(), sau đó kiểm số hàng tác động trong cùng giao dịch.

| Nhóm | Trường trọng yếu | Ràng buộc cần triển khai |
|---|---|---|
| Tài khoản và phiên | credential hash, role, status, authEpoch, lastSeenAt, absoluteExpiresAt | Khóa/đổi mật khẩu tăng epoch; mọi API kiểm phiên và trạng thái |
| Challenge và invite | purpose, tokenDigest, target, expiresAt, consumedAt, revision | Một lần; đúng mục đích; đúng email; không tự cấp ADMIN |
| Hồ sơ và xác minh | owner, studentLink, submittedVersion, reviewer, scope | Một phụ huynh; cấm tự duyệt; con trỏ bản công khai có kiểm trạng thái |
| Vụ việc và grant | caseId, assignee, objectScope, actions, approver, expiresAt | Grant không rộng hơn quyền người cấp; ≤24 giờ |
| Xóa và giữ tối thiểu | requestId, tombstone, storeTask, holdReason, reviewAt | Receipt một lần; theo dõi từng kho; hold ≤30 ngày trước rà lại |

## 8 Giao dịch và hợp đồng API bổ sung

Giao dịch ghi nhận receipt, audit tối thiểu và outbox cùng commit khi phù hợp. Lệnh công khai như đăng nhập/khôi phục có rate limit và thông báo chung; lệnh nghiệp vụ riêng dùng Idempotency-Key, payload hash và If-Match khi sửa. Không lưu mật khẩu hoặc token thô trong receipt.

API trả 401 khi không có phiên hợp lệ; 403 khi biết đối tượng nhưng không được phép; 404 khi cần che sự tồn tại; 409 khi trạng thái hoặc idempotency xung đột; 412 khi revision cũ; 422 khi dữ liệu sai; 429 khi bị giới hạn. Nội dung riêng trả private, no-store. Lỗi có code, correlationId và hướng xử lý tối thiểu.

Tệp api_supplement.json là danh mục hợp đồng ứng viên có phương thức, đường dẫn, actor, đầu vào, đầu ra và điều kiện giao dịch; chưa phải OpenAPI hợp nhất có schema đầy đủ. Không thể dùng tệp này để sinh client hoặc kết luận API đã chạy.

| Nhóm giao dịch | Commit bắt buộc | Kiểm cạnh tranh |
|---|---|---|
| Tài khoản và challenge | Tạo đúng role; tiêu thụ token; đổi credential và epoch | Hai lần reset/verify chỉ một lần tiêu thụ |
| Invite và revoke | Khóa hồ sơ/link; kiểm đúng đích; đổi quyền và audit | Nhận lời mời cạnh tranh thu hồi: chỉ trạng thái hiện hành có hiệu lực |
| Duyệt và khóa | Khóa account/profile; kiểm reviewer; loại matching | Sửa chuyên môn trong lúc duyệt trả stale, không công khai nhầm |
| Grant hỗ trợ | Khóa vụ việc và grant; xác nhận quyền cấp; hạn ≤24 giờ | Thu hồi nguồn thắng mọi lần đọc tiếp theo |
| Xóa và hold | Chặn quyền, tombstone, task, receipt cùng commit | Worker thử lại không phục hồi nội dung hoặc nhân đôi yêu cầu |

## 9 Bảo mật và mô hình đe dọa

Ưu tiên kiểm quyền theo đối tượng tại dịch vụ nghiệp vụ, bao gồm cả đường tải, operation lookup, worker và cache. Giao diện ẩn nút không được coi là kiểm soát quyền. Quản trị hỗ trợ và người xác minh có tập quyền riêng; thao tác nhạy cảm yêu cầu tái xác thực và có audit.

Đề xuất phiên opaque có tra trạng thái phía máy chủ cho R0 để thực hiện thu hồi ngay. Web quản trị dùng cookie Secure, HttpOnly và bảo vệ CSRF; mobile lưu bí mật phiên bằng kho bảo mật của hệ điều hành. Mật khẩu dùng hàm băm chuyên dụng có salt; thuật toán và tham số khóa ở ADR sau benchmark runtime, không tự triển khai mật mã.

| Đe dọa | Biện pháp thiết kế | Ca kiểm chứng cần chạy |
|---|---|---|
| Đổi ID để đọc con/lớp khác | Kiểm role, quan hệ, trạng thái và quyền nguồn mỗi lần | Hai gia đình, hai lớp; mọi API/tệp/job và DTO |
| Chiếm hoặc dùng lại mã reset | Digest, TTL, purpose, consume nguyên tử; chặn log | Đồng thời hai kết nối; sai purpose; token đã dùng |
| Hỗ trợ tự đọc học liệu | Grant theo vụ việc, đối tượng, hành động; audit | Không grant; hết hạn; thu hồi; tự cấp; xin đáp án |
| Prompt injection và rò lớp | Nguồn là dữ liệu; không cấp công cụ tùy ý; lọc ACL | Nguồn chứa lệnh bỏ quyền; marker riêng từng lớp |
| Tệp nguy hiểm và URL cũ | Kho cách ly; kiểm byte/loại; proxy đọc tái kiểm quyền | Sai MIME; macro; file lỗi; đọc sau revoke |
| Phục hồi làm sống lại dữ liệu | Tombstone ngoài bản backup phục hồi; cổng mở dịch vụ | Xóa rồi restore bản cũ; đối soát object và chỉ mục |

## 10 AI chi phí và vận hành tác vụ

Matching giữ thuật toán S và đối chứng B theo BR03, không tuyên bố trọng số tối ưu. Tách lọc điều kiện cứng khỏi xếp hạng; lưu phiên bản đầu vào và cấu hình để phát lại. Thiếu ứng viên khác với lỗi dịch vụ. Chưa thay model hoặc nhà cung cấp bằng suy đoán.

AI soạn bài chỉ nhận tập nguồn có quyền và phiên bản cụ thể. Trước đọc nguồn, trước gọi dịch vụ, trước lưu/trả và trước duyệt đều kiểm lại quyền, lớp và revision. Nội dung đầu vào bỏ tên, email, địa chỉ và dữ liệu không cần thiết. Đáp án có DTO và endpoint riêng.

Giới hạn cơ sở: tối đa hai tác vụ AI toàn hệ thống và một tác vụ mỗi gia sư; nhận tác vụ p95 ≤2 giây, có kết quả hoặc lỗi rõ trong 120 giây kể cả chờ. Reservation quota và ngân sách phải được ghi nguyên tử giữa các worker; timeout không có nghĩa nhà cung cấp chưa tính phí.

Đề xuất sổ chi phí gồm reserve, settle, release và UNKNOWN. Chỉ giải phóng phần dự toán khi biết kết quả; lỗi không rõ chi phí giữ khoản dự phòng tới đối soát. Trần ngày/tháng cần xác nhận bằng tiền và phiên bản cấu hình trước bật AI. AI mặc định tắt nếu chưa có ngân sách, nhà cung cấp và quyết định xử lý dữ liệu; soạn thủ công tiếp tục hoạt động.

| Tình huống | Hành vi | Bằng chứng cần có |
|---|---|---|
| Hết quota hoặc trần tiền | Chặn trước gọi; hiện lý do; giữ nháp | Hai worker tranh quota và lệnh thử lại |
| Dịch vụ timeout hoặc lỗi | Kết thúc có trạng thái; không thử lại vô hạn | Log mã giả, deadline, chi phí và số lần gọi |
| Nguồn bị thu hồi | Không trả nội dung; đánh dấu dẫn xuất | Thu hồi ở từng điểm của pipeline |
| Sai cấu trúc hoặc nguồn | Giữ lỗi thật; không tự công bố | Gia sư sửa và duyệt; thẩm định Toán độc lập |

## 11 Migration sao lưu và quy trình phát hành

Migration theo thứ tự 001–004 hiện hành, sau đó mới tạo bản mở rộng đã đối chiếu. Dùng expand, backfill, validate rồi enforce; không sửa tệp migration đã áp dụng. Chạy trên DB trống và bản sao có dữ liệu cũ, ghi thời gian khóa và đối soát số bản ghi. Không dùng rollback phá dữ liệu làm phương án phục hồi mặc định.

CI đề xuất kiểm lint, unit nghiệp vụ trọng yếu, hợp đồng API, migration và tích hợp PostgreSQL máy chủ. Kiểm bí mật và dependency là điều kiện xuất bản build. Staging tách khóa, DB và bucket khỏi dữ liệu thật; không chép nội dung học sinh sang môi trường phát triển.

Mục tiêu phục hồi theo NFR11 là RPO ≤1 giờ, RTO ≤4 giờ cho DB và tệp nhất quán. Đề xuất backup nền cộng nhật ký DB, object version và manifest nhất quán; chu kỳ thực tế phải chứng minh được RPO. Backup mã hóa giữ 7 ngày; khóa giải mã tách bản sao lưu và có người chịu trách nhiệm.

| Bước phục hồi | Thao tác | Điều kiện mở dịch vụ |
|---|---|---|
| 1 Cách ly | Khóa ghi; chọn mốc DB/tệp; phục hồi vào môi trường kín | Không phục vụ người dùng trong lúc kiểm |
| 2 Áp quyền hiện hành | Đọc ledger xóa/thu hồi bền vững ngoài snapshot cũ | Áp tombstone, epoch và quyền trước mọi read/job |
| 3 Đối soát | Kiểm booking, object, nguồn, dẫn xuất; dựng lại index | Không phục hồi dữ liệu bị xóa từ cache hoặc index |
| 4 Kiểm tra và mở | Chạy ca âm; đo RPO/RTO; ghi người quyết định | Đạt kiểm quyền/xóa; có biên bản và kế hoạch theo dõi |

## 12 Kế hoạch kiểm chứng và nguồn lực

NFR là mục tiêu thiết kế, chưa là số đo. Bộ tải chuẩn giữ 20 RPS trong 30 phút sau 5 phút làm nóng; dữ liệu 1.000 tài khoản, 200 lớp, 5.000 buổi; phối 70% đọc, 20% cập nhật, 10% matching. API thường p95 <500 ms, matching p95 <3 giây, lỗi ngoài dự kiến <1%, thông báo p95 ≤10 giây. Không suy số người đồng thời từ RPS.

G3 cần kiểm chứng rủi ro kiến trúc và thử prototype với ít nhất 5 người theo kế hoạch, mục tiêu 4 người hoàn thành tác vụ chính không cần hướng dẫn từng bước. Ghi vai trò, tác vụ, mức trợ giúp và vấn đề; không dùng kiểm thử tự động thay cho người dùng thật. Dùng dữ liệu mô phỏng có nhãn khi chưa đủ điều kiện tiếp nhận dữ liệu thật.

Bộ native F02/F04 chưa có bằng chứng thực thi trong hồ sơ đã đọc; cần chạy lại chuỗi migration hiện hành trên PostgreSQL máy chủ và nhiều kết nối. F01/F06 lần này chỉ hoàn thiện đặc tả; chưa có backend, migration mới hoặc kiểm thử bảo mật thực tế.

| Nhóm kiểm chứng | Điều kiện đạt | Giai đoạn |
|---|---|---|
| G3 rủi ro dữ liệu | Không trùng lịch, rollback đúng, idempotency và quyền nguồn qua nhiều kết nối | Trước khóa quyết định thiết kế liên quan |
| F01/F06 tích hợp | Reset/revoke một lần; grant đúng hạn; xóa/restore không sống lại | G4 dựng nền và phát triển lát cắt |
| NFR toàn hệ thống | Đo theo đúng tải/cỡ mẫu; không bỏ lỗi khỏi báo cáo | Khi có sản phẩm đủ luồng trước R0 |
| Rà soát nhóm | Đính/Huy có kết luận, giờ/tuần, ngân sách và mốc | Trước khóa kế hoạch G4 |

## 13 Backlog G4 dự kiến và phụ thuộc

Các khoảng dưới đây là ước lượng ngày công tập trung, chưa phải cam kết lịch. Một ngày công quy ước 6 giờ; lịch thực tế phụ thuộc số giờ mỗi tuần của từng người. Công việc phát triển sáu luồng đầy đủ nằm sau nền móng G4 và phải được ước lượng riêng.

Lát cắt kết thúc G4 là đăng nhập và lưu hồ sơ chạy từ Android đến API và DB, kèm kiểm quyền âm; thành viên còn lại dựng được dự án từ README trên máy riêng. F02 xuyên suốt là lát cắt triển khai tiếp theo sau nền này.

| Mã và đầu ra | Chủ trì đề xuất | Phụ thuộc | Ngày công |
|---|---|---|---|
| G4-01 Repo, CI, cấu hình và secrets | Đính; Huy rà | Xác nhận stack và môi trường | 1–2 |
| G4-02 PostgreSQL, migration và seed | Đính | 01; schema hợp nhất đã rà | 2–3 |
| G4-03 Phiên, đăng nhập, reset, revoke | Đính | 02; hợp đồng F01 | 3–5 |
| G4-04 Android shell và auth flow | Huy | 01; API auth thống nhất | 2–4 |
| G4-05 Hồ sơ con và gia sư tối thiểu | Đính/Huy | 03,04; owner phía server | 2–4 |
| G4-06 Outbox, audit, health và worker | Đính; Huy rà | 02; event envelope | 2–3 |
| G4-07 Kho riêng, đọc có quyền, kiểm tệp | Huy; Đính rà | 03,06; threat model | 2–4 |
| G4-08 Staging, restore và bàn giao | Cả hai | 01–07; runbook | 2–3 |

## 14 Quyết định và điều kiện khóa G3

D35-01 đến D35-05 của F03/F05 tiếp tục chờ kết luận riêng theo hồ sơ nguồn. Đợt này không tự chuyển các quyết định đó thành đã xác nhận. Các quyết định mới bên dưới là đề xuất để hai thành viên ghi đồng ý, yêu cầu sửa hoặc chưa đủ thông tin.

Sau đợt này, sáu luồng đã có hồ sơ thiết kế theo từng nhóm, nhưng chưa đủ căn cứ đóng G3 chính thức. Cần hợp nhất OpenAPI có schema đầy đủ, ERD/migration và truy vết trên cùng phiên bản; chạy kiểm chứng PostgreSQL máy chủ; hoàn tất thử người dùng; ghi kết luận rà soát và nguồn lực. Bằng chứng thực thi và quyết định phải được gắn tệp, ngày, phiên bản và người chịu trách nhiệm.

Không chuyển điều kiện của sản phẩm trước R0 thành điều kiện đã đạt ở G3. Nghiệm thu thiết kế ghi rõ phần được chấp nhận, phần có điều kiện và phần chuyển sang phát triển; các lỗi quyền, lịch, đáp án, mất dữ liệu và nguồn giả vẫn là điều kiện chặn phát hành.

| Mã | Đề xuất cần kết luận | Trạng thái |
|---|---|---|
| D16-01 | Nguyên khối chia mô đun; API và worker riêng; PostgreSQL chung | Đính/Huy chờ rà soát |
| D16-02 | Phiên opaque tra trạng thái máy chủ; epoch phục vụ revoke | Đính/Huy chờ rà soát |
| D16-03 | Grant hỗ trợ do người đang có quyền tương ứng cấp; ≤24 giờ | Đính/Huy chờ rà soát |
| D16-04 | Ledger xóa/thu hồi bền vững độc lập snapshot; chặn mở sau restore | Đính/Huy chờ rà soát |
| D16-05 | DB outbox và quota reservation làm nền R0; AI tắt tới GATE03 | Đính/Huy chờ rà soát |
