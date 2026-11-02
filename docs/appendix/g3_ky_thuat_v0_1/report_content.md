# Báo cáo hợp nhất kỹ thuật TutorBridge

TB G3 07 v0.1 | 29/10/2026 | Chưa nghiệm thu G3

## 1 Kết luận và phạm vi hợp nhất

Đã hợp nhất hợp đồng API và chuỗi schema dữ liệu ứng viên cho sáu luồng F01–F06. Gói kỹ thuật cung cấp OpenAPI 3.1.1, migration 001–005, từ điển vật lý, quan hệ khóa ngoại, bảng truy vết 43 FR và bằng chứng kiểm tra. Hồ sơ đủ để tổ chức phiên rà soát kỹ thuật tổng thể; chưa phải kết luận nghiệm thu G3.

OpenAPI hiện có 123 thao tác trên 108 đường dẫn, 138 schema. Chuỗi DDL tạo 77 bảng trên môi trường PostgreSQL 17.5 nhúng. Sau hợp nhất, 64/64 ca SQL đạt, gồm 28 ca hồi quy F02/F04 và 36 ca bổ sung F01/F06. Kiểm tra cấu trúc OpenAPI, tham chiếu nội bộ và tám ca biên payload đã đạt.

Kết quả nhúng không chứng minh nhiều kết nối, API máy chủ, quyền qua HTTP, xử lý kho tệp, AI thật, tải hoặc khôi phục. Bản mẫu F01/F06 vẫn là wireframe tĩnh. Chưa có thử người dùng hoặc xác nhận riêng của Đức Huy trong đợt này.

| Đầu ra | Nơi tra cứu trong gói | Trạng thái |
| --- | --- | --- |
| API hợp nhất | api/openapi.json và openapi.yaml | Ứng viên đã kiểm cấu trúc |
| Schema vật lý | sql/001–005; data/physical_dictionary.json | Đã thực thi nhúng |
| Truy vết và quyết định | review/fr_traceability.csv; reconciliation.csv | 43 FR; 8 mục tích hợp |
| Kiểm chứng máy chủ | native/run.py; evidence/native_status.json | Chuẩn bị; chưa thực thi |

## 2 Cơ sở phiên bản và kiểm soát thay đổi

Yêu cầu gốc tiếp tục theo PRD/SRS TB G2 02 v1.1, cùng danh mục màn hình TB G3 01 v0.1. Thiết kế F02/F04, sửa lỗi 003, F03/F05 và F01/F06 là đầu vào. Các bản gốc không bị ghi đè trong lần hợp nhất; migration 001–004 được sao nguyên byte, có SHA256 và kiểm tra đối chiếu.

005 là migration mở rộng ứng viên cho tài khoản, lời mời, xác minh, hỗ trợ và xóa. Tệp này thêm cột hoặc bảng trên nền hiện có; không tạo lại accounts, students hoặc auth_sessions. Không tạo credential, tài khoản quản trị, mã MFA hoặc quyền runtime có thể sử dụng ngay.

Tài liệu này chuẩn hóa hợp đồng giữa các gói chưa phát hành. Khi đã có client hoặc dữ liệu thật, đổi tên schema, đường dẫn, trạng thái hoặc chính sách cần đánh giá tương thích và lập thay đổi có phiên bản. Không coi bản hợp nhất này là đã được khách hàng phê duyệt.

| Nguồn | Vai trò trong hợp nhất | Cách giữ căn cứ |
| --- | --- | --- |
| TB G2 02 v1.1 | 43 FR, 16 UC, NFR và chính sách | Giữ mã yêu cầu; không giảm P1 |
| F02/F04 và sửa 003 | API cơ sở; lịch, nguồn, AI, công bố | 001–003 giữ nguyên byte |
| TB G3 05 v0.1 | F03/F05, nhật ký, bài tập và tiến trình | 004 giữ nguyên; OpenAPI ghép có đổi ref |
| TB G3 06 v0.1 | F01/F06, kiến trúc và vận hành | Chuyển catalog sang schema định kiểu; bổ sung 005 |

## 3 Các khác biệt đã xử lý trong ứng viên

Hai gói dùng cùng tên ReviewInput cho dữ liệu khác nhau. Bản hợp nhất giữ tên đó cho duyệt bài soạn và đổi nhận xét bài nộp thành SubmissionReviewInput; các tham chiếu được viết lại theo đúng ngữ nghĩa. Không ghép hai tập trường vào cùng DTO.

Problem thống nhất theo cấu trúc cơ sở F02/F04, dùng traceId làm mã tương quan trong payload. Tên correlationId trong phần diễn giải được hiểu là cùng khái niệm, không thêm hai trường bắt buộc khác nhau. Máy chủ ví dụ dùng /api/v1 và miền .invalid; không phải URL dịch vụ đã triển khai.

Các điểm có tác động chính sách được giữ là đề xuất cần nhóm kết luận, đặc biệt người cấp quyền hỗ trợ và giới hạn minh chứng chuyên môn. Việc người dùng yêu cầu tiếp tục không được ghi thay xác nhận riêng của thành viên.

| Mã | Nội dung tích hợp | Điều kiện còn lại |
| --- | --- | --- |
| D07-01/02 | Tên schema duyệt; lỗi và prefix thống nhất | Rà soát client và hợp đồng trước phát triển |
| D07-03 | Chủ sở hữu nguồn cấp VIEW_ORIGINAL cho hỗ trợ | Đề xuất bảo thủ; không tự cho tải/AI/đáp án |
| D07-04 | Minh chứng PDF/JPEG/PNG, ≤5MiB, tối đa 10 tệp | Giới hạn mới cần Đính/Huy kết luận |
| D07-05/06 | NEEDS_INFO → PENDING kèm lý do; ETag lần tạo đầu | Cài service và thử lỗi phiên bản |
| D07-07/08 | Đăng ký học sinh qua lời mời; đóng yêu cầu xóa có bằng chứng | Chưa có email, storage hoặc export thật |

## 4 Hợp đồng API và truy vết yêu cầu

Hợp đồng hợp nhất có schema đầu vào và đầu ra theo từng thao tác. Bổ sung đường cho xác thực quản trị, tái xác thực, tài khoản học sinh qua lời mời, minh chứng, lịch rảnh và mục tiêu lớp nhằm nối các điểm còn thiếu giữa nhóm chức năng. Danh mục thao tác ghi nguồn và mã TX hoặc UC; bảng FR ghi rõ bằng chứng cùng giới hạn.

Schema cấm thuộc tính ngoài danh sách ở các DTO mới. Đăng ký thông thường chỉ nhận PARENT hoặc TUTOR; owner hồ sơ con lấy từ phiên. STUDENT chỉ tạo qua lời mời hợp lệ rồi xác minh email và chấp thuận riêng. Không có đường tự chọn ADMIN.

Phiên mobile dùng Bearer opaque; web quản trị có phương án cookie HttpOnly Secure. Lệnh dùng cookie phải kiểm CSRF và Origin; mô tả chính sách trong OpenAPI không thay triển khai thực tế. Đọc nội dung riêng trả private, no-store; DTO bài tập và bài nộp không mang đáp án bí mật.

| Nhóm nguồn | Số thao tác | Phạm vi |
| --- | --- | --- |
| F02/F04 | 40 | Nhu cầu, matching, đề nghị, lớp, nguồn, AI và công bố |
| F03/F05 | 32 | Đổi lịch, nhật ký, xác nhận, bài tập và tiến trình |
| Bổ sung F01/F06 và điểm nối | 51 | Tài khoản, hồ sơ, hỗ trợ, privacy; lịch và mục tiêu |
| Tổng | 123 | 108 đường dẫn; 138 schema; 1.594 tham chiếu đã phân giải |

## 5 Schema vật lý và phạm vi ràng buộc

Chuỗi 001–005 tạo 77 bảng. Từ điển cột, kiểu, NULL, mặc định, CHECK và FK được trích từ schema đã thực thi, không chỉ từ phân tích văn bản. Sơ đồ all_tables.mmd biểu diễn hướng phụ thuộc FK, không gán lực lượng quan hệ khi chưa xét nullable và unique.

005 bổ sung credential hash và phiên theo epoch, challenge một lần theo mục đích, lời mời học sinh, scope quản trị, hồ sơ MFA dưới dạng handle, minh chứng chuyên môn, vụ việc, quyền tạm, nhiệm vụ xóa theo kho và bằng chứng xuất ledger. Dữ liệu thử chỉ có định danh mô phỏng, không có mật khẩu hoặc email thật.

DB hỗ trợ kiểm tính đúng thành phần. Service vẫn phải kiểm actor, quan hệ và trạng thái; không cấp quyền toàn bộ bảng/hàm cho ứng dụng. Tác vụ xóa tệp, chỉ mục và backup không được thực hiện bằng một CHECK trong DB.

| Cụm ràng buộc | Điều đã thử | Điều service phải làm |
| --- | --- | --- |
| Phiên và challenge | Epoch, TTL, mục đích và consume lặp | Hash mật khẩu, email, MFA và revoke toàn giao dịch |
| Lời mời và xác minh | Đúng người, đúng owner, cấm tự duyệt | Tạo account/link, receipt/audit/outbox nguyên tử |
| Grant hỗ trợ | Hạn ≤24 giờ; owner cấp; nguồn thu hồi | ACL HTTP, proxy đọc và kiểm trước stream |
| Yêu cầu xóa | Không báo đủ kho hoặc đóng sớm | Xóa thật từng kho; backup và ledger độc lập |

## 6 Kết quả kiểm chứng và giới hạn

Năm migration chạy thành công trên PostgreSQL 17.5 nhúng PGlite 0.3.14. Sáu mươi bốn ca SQL đạt: 28 ca hồi quy lịch, học liệu và công bố; 36 ca bổ sung F01/F06. Bộ bổ sung kiểm phiên, mã một lần, lời mời, xác minh, grant, trạng thái hỗ trợ và điều kiện hoàn tất xóa.

OpenAPI qua validator 3.1.1, kiểm tên operation duy nhất, tham số đường dẫn và 1.594 tham chiếu nội bộ. Tám payload mẫu kiểm các biên như vai trò ADMIN trong đăng ký, mật khẩu ngắn, ownerId ngoài hợp đồng, PATCH rỗng và capability AI trong grant hỗ trợ. Đây là kiểm schema, không phải kiểm bảo mật máy chủ.

Bộ nhúng đã được chạy lại từ runner đóng gói bằng dependency hiện có. Không có kết quả sinh client, tải ứng dụng Android hoặc chạy Spring Boot. Không dùng số ca đạt để kết luận bao phủ 43 FR hay hoàn thành NFR.

| Hạng mục | Kết quả | Giới hạn |
| --- | --- | --- |
| Migration 001–005 | 5/5 đạt trên nhúng | DB mới; chưa diễn tập dữ liệu cũ |
| SQL hồi quy và bổ sung | 64/64 đạt | Một phiên nhúng; chưa HTTP |
| OpenAPI và payload | Hợp lệ; 8 ca biên đạt | Chưa sinh và chạy client |
| Native PostgreSQL | Chưa thực thi | Không có postgres/initdb/docker; configure thiếu bison |
| Người dùng và NFR | Chưa thực hiện | Không có kết quả tải, restore, AI hoặc thử người dùng |

## 7 Kế hoạch chạy PostgreSQL máy chủ

Gói native được cập nhật để áp dụng đầy đủ 001–005, chạy lại 64 ca SQL và thêm năm kịch bản máy chủ. Runner chỉ kết nối loopback vào DB tạm có tên cố định, yêu cầu PostgreSQL major 17 và từ chối DB đã có schema tutorbridge. Không có thao tác xóa schema hiện hữu.

Hai kịch bản trùng lịch kiểm tài nguyên gia sư và học sinh qua các kết nối độc lập. Kịch bản thu hồi nguồn khi công bố và mã reset một lần yêu cầu quan sát chờ khóa. Kịch bản thời gian liền kề chỉ dùng hai phiên commit tuần tự để kiểm biên, không được mô tả là tranh chấp đồng thời.

Runner đã qua kiểm cú pháp Python; chưa có kết quả native tại môi trường soạn. Hướng dẫn trong README dùng VS Code và Command Prompt trên Windows. Mật khẩu test được sinh ngẫu nhiên tại máy người chạy, không đóng gói hoặc in giá trị. Container dùng dữ liệu tạm và chỉ nên dùng cho kiểm chứng.

| Mã | Kịch bản | Bằng chứng phải ghi |
| --- | --- | --- |
| N01/N02 | Hai giao dịch giữ cùng khoảng giờ tài nguyên | PID độc lập; thấy chờ khóa; một bên bị loại |
| N03 | Hai khoảng liền kề | Hai phiên commit tuần tự; cả hai hợp lệ |
| N04 | Thu hồi nguồn cạnh tranh công bố | Chờ khóa; công bố bị chặn sau revoke |
| N05 | Hai lần tiêu thụ cùng challenge | Chờ khóa; chỉ lần đầu nhận account |
| Bàn giao | Ghi phiên bản và kết quả | evidence/native_results.json của lần chạy thật |

## 8 Điều kiện migration và vận hành

Không chạy 005 thẳng trên dữ liệu thật. Cần đối soát tài khoản ACTIVE cũ, email_verified_at, credential, phiên và xác minh chuyên môn cũ. Migration thêm cột không tự chứng minh các tài khoản đã thỏa chính sách xác minh mới. Các cột backfill cần được kiểm kê, sửa có nguồn và xác nhận trước khi enforce rộng hơn.

Thu hồi quyền phải hiệu lực tại commit trước khi worker xóa. Request xóa theo dõi sáu kho hoạt động; chỉ đánh ACTIVE_STORES_PURGED khi đủ kết quả có bằng chứng. CLOSED còn yêu cầu mốc hết hạn backup và bằng chứng ledger xuất bền vững. Bản ghi CONFIRMED trong DB do service ghi cần được đối soát với kho ngoài thật.

Các FK lịch sử có thể yêu cầu giữ hàng tối thiểu. Thiết kế purge phải xóa hoặc ẩn danh nội dung và định danh theo phạm vi, không xóa cascade làm mất dấu giao dịch; ledger/audit không trở thành đường vòng đọc nội dung đã thu hồi. Diễn tập restore phải áp quyền và tombstone mới nhất trước mở API hoặc worker.

| Việc cần kiểm trước dùng thật | Đầu ra | Chủ trì đề xuất |
| --- | --- | --- |
| Upgrade snapshot có dữ liệu cũ | Danh sách backfill, số hàng và thời gian khóa | Đính |
| Vai trò runtime và MFA quản trị | Quyền tối thiểu; bootstrap có audit | Đính; Huy rà |
| Tệp, chỉ mục và ledger độc lập | Bằng chứng delete/revoke tại từng kho | Huy; Đính rà |
| Backup và phục hồi nhất quán | RPO/RTO đo được; ca xóa không sống lại | Cả hai |

## 9 Tình trạng tiêu chí nghiệm thu G3

G3 chưa được đóng. Hồ sơ đã tiến thêm từ các gói rời sang hợp đồng kỹ thuật chung, nhưng vẫn cần kết luận nhóm, bằng chứng máy chủ, thử prototype và kế hoạch nguồn lực. Các trường xác nhận tiếp tục để trống hoặc chờ ghi nhận, không điền thay Đính/Huy.

D35-01 đến D35-05 và D16-01 đến D16-05 tiếp tục quản lý theo hồ sơ trước. D07 bổ sung các quyết định hợp nhất trong đợt này. Các giới hạn hoặc chính sách mới cần được rà tác động và chấp nhận trước khóa baseline.

Kiểm thử toàn hệ thống trước R0 vẫn theo tiến độ phát triển. G3 không cần giả lập rằng mọi API hoặc NFR sản phẩm đã chạy; cần đủ thiết kế, kế hoạch và kiểm chứng rủi ro lớn có căn cứ.

| Điều kiện | Tình trạng | Căn cứ còn thiếu |
| --- | --- | --- |
| API/schema chung | Có ứng viên đã kiểm cấu trúc | Rà nghiệp vụ và hợp đồng client |
| Rủi ro PostgreSQL nhiều kết nối | Bộ chạy đã chuẩn bị | Kết quả native thực tế |
| Prototype và người dùng | Có các gói thiết kế | Tối thiểu 5 người; mục tiêu 4 hoàn thành độc lập |
| Migration dữ liệu cũ | Có kế hoạch | Snapshot, backfill và diễn tập |
| Nhóm và nguồn lực | Chờ ghi nhận | Kết luận Đính/Huy; giờ, ngân sách, mốc |

## 10 Thứ tự công việc tiếp theo

Phiên làm việc tiếp theo nên bắt đầu bằng rà tám mục D07 và các quyết định D35/D16 còn mở, sau đó chạy bộ native trên máy có Docker hoặc PostgreSQL 17. Ghi kết quả theo đúng tệp bằng chứng thay vì chỉ ghi thông báo đã chạy. Lỗi mới phải được sửa và chạy lại đúng phần chịu tác động.

Song song, tổ chức thử prototype theo vai trò và hoàn thiện phần tương tác còn tĩnh nếu tác vụ cần thao tác. Phiếu user_testing.csv dùng mã giả P01–P05, không có kết quả sẵn. Người quan sát ghi mức trợ giúp và điểm vướng; không hướng dẫn từng bước rồi đánh hoàn thành độc lập.

Sau khi đủ kết quả, cập nhật bảng nghiệm thu G3 theo từng tiêu chí: đạt, đạt có điều kiện hoặc chưa đạt; ghi phần bàn giao G4 và người chịu trách nhiệm. Mốc nền móng G4 giữ đăng nhập và lưu hồ sơ xuyên suốt, có kiểm quyền âm và người còn lại dựng được dự án từ README.

| Thứ tự | Công việc và đầu ra | Người đề xuất |
| --- | --- | --- |
| 1 | Rà quyết định; ghi đồng ý hoặc yêu cầu sửa theo từng mã | Đính và Huy |
| 2 | Chạy native; lưu JSON, phiên bản và lỗi nếu có | Đính; Huy rà |
| 3 | Thử người dùng; sửa UX; bổ sung bằng chứng | Huy; Đính quan sát |
| 4 | Chốt giờ mỗi tuần, ngân sách và mốc theo backlog | Cả hai |
| 5 | Lập biên bản nghiệm thu G3 và bàn giao G4 theo điều kiện thực tế | Đính tổng hợp; Huy rà |