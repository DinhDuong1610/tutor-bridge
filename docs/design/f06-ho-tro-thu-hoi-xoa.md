# F06 — Hỗ trợ, thu hồi và xóa dữ liệu

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 06 mục 4, 5, 8; TB G3 07 về schema có kiểu

### Luồng F06 và quyền hỗ trợ

COM03 tạo vụ việc gồm loại, mô tả tối đa 2.000 ký tự và đối tượng trong quyền. COM03 hiển thị trạng thái, phản hồi và kết quả; không hiển thị ghi chú nội bộ hoặc vụ việc của gia đình khác. Không gửi mật khẩu hoặc học liệu ngoài quyền vào mô tả.

AD06 tiếp nhận và phân công. AD07 xử lý trong phạm vi vụ việc. Nếu cần học liệu, người xử lý xin cấp quyền theo đối tượng và hành động cụ thể, nêu mục đích, người chấp thuận và thời hạn tối đa 24 giờ. Đề xuất người đang có quyền cấp tương ứng mới được chấp thuận; người hỗ trợ không tự cấp quyền cho mình.

Quyền tạm không mặc định cho tải, AI hoặc đáp án. Quyền nguồn còn hiệu lực, quan hệ lớp và trạng thái tài khoản vẫn được kiểm tra. Thu hồi nguồn hoặc hết hạn quyền tạm chặn lần đọc tiếp theo, kể cả đường dẫn cũ. Việc gia hạn tạo quyết định mới có audit.

Khóa tài khoản thu hồi phiên và loại khỏi matching, không xóa lịch hoặc tự xác nhận buổi học. Gửi cảnh báo tối thiểu cho lớp liên quan và mở đầu mối hỗ trợ. Hiệu chỉnh chuyên môn hoặc xác nhận buổi phải dùng phiên bản mới trong F05, không sửa thẳng lịch sử bằng công cụ quản trị.

| Trạng thái vụ việc | Chuyển hợp lệ | Dữ liệu cần có |
|---|---|---|
| NEW | IN_PROGRESS | Người xử lý và phân loại |
| IN_PROGRESS | WAITING_INFO / RESOLVED | Yêu cầu bổ sung hoặc kết quả có lý do |
| WAITING_INFO | IN_PROGRESS | Phản hồi mới giữ lịch sử |
| RESOLVED | CLOSED / IN_PROGRESS | Đóng hoặc mở lại với lý do; không xóa kết quả cũ |

### Thu hồi xóa và phục hồi dữ liệu

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

### Giao dịch và hợp đồng API bổ sung

Giao dịch ghi nhận receipt, audit tối thiểu và outbox cùng commit khi phù hợp. Lệnh công khai như đăng nhập/khôi phục có rate limit và thông báo chung; lệnh nghiệp vụ riêng dùng Idempotency-Key, payload hash và If-Match khi sửa. Không lưu mật khẩu hoặc token thô trong receipt.

API trả 401 khi không có phiên hợp lệ; 403 khi biết đối tượng nhưng không được phép; 404 khi cần che sự tồn tại; 409 khi trạng thái hoặc idempotency xung đột; 412 khi revision cũ; 422 khi dữ liệu sai; 429 khi bị giới hạn. Nội dung riêng trả private, no-store. Lỗi có code, traceId và hướng xử lý tối thiểu.

| Nhóm giao dịch | Commit bắt buộc | Kiểm cạnh tranh |
|---|---|---|
| Tài khoản và challenge | Tạo đúng role; tiêu thụ token; đổi credential và epoch | Hai lần reset/verify chỉ một lần tiêu thụ |
| Invite và revoke | Khóa hồ sơ/link; kiểm đúng đích; đổi quyền và audit | Nhận lời mời cạnh tranh thu hồi: chỉ trạng thái hiện hành có hiệu lực |
| Duyệt và khóa | Khóa account/profile; kiểm reviewer; loại matching | Sửa chuyên môn trong lúc duyệt trả stale, không công khai nhầm |
| Grant hỗ trợ | Khóa vụ việc và grant; xác nhận quyền cấp; hạn ≤24 giờ | Thu hồi nguồn thắng mọi lần đọc tiếp theo |
| Xóa và hold | Chặn quyền, tombstone, task, receipt cùng commit | Worker thử lại không phục hồi nội dung hoặc nhân đôi yêu cầu |

Danh mục API bổ sung cũ đã được hợp nhất thành OpenAPI có schema đầu vào/đầu ra. Đặc tả hiện hành trong gói đọc là api_openapi_hop_nhat.yaml; không dùng danh mục sơ bộ cũ để sinh thêm phạm vi.
