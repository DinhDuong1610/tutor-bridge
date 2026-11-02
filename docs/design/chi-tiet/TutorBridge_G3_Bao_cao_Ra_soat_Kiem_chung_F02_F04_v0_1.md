# TutorBridge_G3_Bao_cao_Ra_soat_Kiem_chung_F02_F04_v0_1

> Tài liệu lịch sử, độ ưu tiên thấp nhất. Bản Word kèm wireframe ở `docs/source-docx/g3/TutorBridge_G3_Bao_cao_Ra_soat_Kiem_chung_F02_F04_v0_1.docx`. Hình ảnh không có trong bản này.

NHÓM DỰ ÁN TUTORBRIDGE

BÁO CÁO RÀ SOÁT

VÀ KIỂM CHỨNG THIẾT KẾ

LUỒNG F02 VÀ F04

Tài liệu điều hành và triển khai giai đoạn thiết kế giải pháp

| Thông tin kiểm soát | Nội dung |
|---|---|
| Mã tài liệu | TB G3 04 |
| Phiên bản | 0.1 |
| Ngày phát hành | 29/10/2026 |
| Nhóm thực hiện | Dương Đính và Đức Huy |
| Mục đích sử dụng | Ghi nhận phát hiện, kết quả chạy SQL, nội dung rà soát chéo và điều kiện kiểm chứng tiếp theo trước khi khóa thiết kế. |
| Trạng thái | Đã kiểm chứng SQL nhúng; phát hành để rà soát chéo; chưa kết luận nghiệm thu toàn bộ G3. |

Phạm vi yêu cầu tham chiếu: TB G2 02 v1.1. Trạng thái xác nhận và điều kiện bàn giao được quản lý tại TB G2 03 v1.2.

Kết luận chính: đã tái hiện và sửa ba điểm hở của ràng buộc dữ liệu. Sau sửa, 28 trên 28 ca SQL đạt trên PostgreSQL 17.5 nhúng. Máy chủ PostgreSQL 17, các giao dịch qua API và thử với người dùng thật còn cần thực hiện theo kế hoạch trong hồ sơ này.

## 1 Kết luận và phạm vi bằng chứng

Đợt này hoàn tất phần kiểm chứng kỹ thuật có thể thực hiện trên môi trường lập hồ sơ và chuẩn bị bảng rà soát cho Đính, Huy. Hai migration nền và suite SQL gốc đã chạy được. Kiểm tra mở rộng phát hiện ba trường hợp ràng buộc còn hở; migration 003 đã sửa và 28 ca sau sửa đều đạt trên engine nhúng.

Bảng 1 Tổng hợp kết quả đợt kiểm chứng

| Hạng mục | Kết quả thực tế |
|---|---|
| Nguồn thiết kế | TB G3 03 v0.1 và gói kỹ thuật v0.1; giữ nguyên byte của SQL 001, SQL 002 và OpenAPI trong thư mục baseline để đối chiếu. |
| Môi trường đã chạy | PGlite 0.3.14, engine PostgreSQL 17.5, WebAssembly, một phiên. Version runtime được ghi trực tiếp trong evidence/embedded_results.json. |
| Phát hiện | R01, R02, R03 đều tái hiện trên bản nền. Đã sửa bằng sql/003_review_corrections.sql; không đổi phạm vi yêu cầu G2. |
| Kết quả sau sửa | 28 PASS, 0 FAIL. Từng ca có SQLSTATE kỳ vọng, kết quả thực tế và mã test; mỗi ca hoàn tác bằng savepoint. |
| Chưa thực thi | PostgreSQL native, nhiều session, hai API replica, quyền HTTP, người dùng thật, Android, AI provider, tải, bảo mật và khôi phục. |
| Xác nhận nhóm | Đính đồng ý thực hiện đợt rà soát. Kết luận từng dòng và ý kiến của Đức Huy chờ ghi nhận thực tế. G3 chưa được đóng. |

### 1.1 Cách sử dụng hồ sơ

Đọc mục 2 để xem lỗi và sửa; mục 3 để rà soát chéo; mục 4 để đối chiếu 28 ca; mục 5 để chạy native; mục 6 để chuẩn bị thử prototype; mục 7 và 8 để quản lý điều kiện đóng G3. Gói kiểm chứng đi kèm chứa mã SQL, runner, cấu hình môi trường, sổ rà soát và bằng chứng.

Kết quả nhúng xác nhận SQL và trigger đã thực thi trên engine nêu trên. Kết quả này không xác nhận khóa tranh chấp nhiều kết nối, transaction service, bảo mật API hoặc năng lực chịu tải. PGlite ở đây là công cụ kiểm chứng, không phải lựa chọn máy chủ sản phẩm.

## 2 Phát hiện và thay đổi đã thực hiện

### R01 Nguồn sẵn sàng thiếu số đo văn bản

READY vẫn nhận extracted_characters = NULL. CHECK cho phép kết quả NULL; điều kiện > 0 chưa bắt buộc NOT NULL. Nguồn thiếu số đo văn bản có thể được coi là sẵn sàng nếu lớp service bỏ sót.

Khắc phục: 003 thêm ready_requires_text_count. Kiểm tra hồi quy SQL08 nhận SQLSTATE 23514 sau sửa; trên bản nền thao tác sai vẫn được chấp nhận.

### R02 Thay đổi quan hệ buổi học và lớp

Có thể đổi class_id của buổi chưa được bảng khác tham chiếu; booking giữ thông tin cũ. Trigger đồng bộ chỉ theo during/state; chưa khóa định danh và lớp của buổi. Quan hệ buổi và lớp có thể sai, dẫn đến lịch đặt không khớp đối tượng của lớp mới.

Khắc phục: 003 thêm protect_session_identity và trigger trước UPDATE. Kiểm tra hồi quy SQL09 nhận SQLSTATE 23514 sau sửa; trên bản nền thao tác sai vẫn được chấp nhận.

### R03 Vượt số nguồn khi cập nhật bối cảnh

Có thể chuyển nguồn thứ sáu vào context bằng UPDATE. Giới hạn tối đa năm nguồn chỉ được kiểm khi INSERT. Context vượt phạm vi nguồn đã thiết kế và giả định giới hạn xử lý.

Khắc phục: 003 mở guard cho INSERT và UPDATE OF context_id. Kiểm tra hồi quy SQL11 nhận SQLSTATE 23514 sau sửa; trên bản nền thao tác sai vẫn được chấp nhận.

Bảng 2 Truy vết sửa ràng buộc

| Mã | Phạm vi | Bằng chứng và trạng thái |
|---|---|---|
| R01 | E12 / UC10 / TX08 | SQL08; đã sửa và kiểm chứng trên engine nhúng; chờ rà soát chéo. |
| R02 | E10 / UC06 UC07 / TX05 | SQL09; đã sửa và kiểm chứng trên engine nhúng; chờ rà soát chéo. |
| R03 | E15 / UC11 / TX10 | SQL11; đã sửa và kiểm chứng trên engine nhúng; chờ rà soát chéo. |

Đây là các điểm hở ở tầng ràng buộc của bản thiết kế, chưa phải kết luận về lỗ hổng API đang chạy. Lớp service theo thiết kế vẫn phải kiểm quyền, quan hệ, trạng thái và khóa trong cùng transaction. Các sửa được bổ sung ở migration 003; bản nền được giữ nguyên để tái hiện và truy vết.

Đính chủ trì rà soát mã SQL và tích hợp; Huy rà soát chéo tác động tới lịch, học liệu và bối cảnh. Kết quả tự động không được dùng thay xác nhận của thành viên. Khi đưa vào nhánh backend, ghi mã commit, môi trường native và kết quả chạy lại.

## 3 Bảng rà soát chéo F02 F04

Mỗi dòng cần kết luận Đồng ý, Yêu cầu sửa hoặc Chưa đủ bằng chứng, kèm người ghi và ngày. Chủ trì và người rà soát kế thừa phương án G3; các phần giờ làm, ngân sách và mốc vẫn chờ hai thành viên thống nhất.

Bảng 3 Nội dung rà soát RV01 đến RV08

| Mã | Phạm vi rà soát | Chủ trì và rà chéo | Trạng thái và bằng chứng |
|---|---|---|---|
| RV01 | Phạm vi R0 và P1 | Đính; rà chéo: Huy | Đủ căn cứ thiết kế; chờ rà soát nhóm. Đối chiếu G2 02 và G3 01; P1 vẫn bắt buộc cho R0 đầy đủ. |
| RV02 | Giá lịch và xếp hạng S B | Huy; rà chéo: Đính | Hợp đồng đã có; chưa có matching backend thật. Giá/buổi, hard filter cả chuỗi, tie-break và chọn B rõ ràng. |
| RV03 | TX05 chốt toàn bộ lịch | Đính; rà chéo: Huy | Kiểm DB thành phần nhúng đạt; kiểm hai API chưa chạy. 28 ca có kiểm booking/rollback thành phần; native và service cần bổ sung. |
| RV04 | Quyền quan hệ và phạm vi lớp | Đính; rà chéo: Huy | Kiểm FK đạt; quyền API chưa kiểm chứng. FK ghép được kiểm; actor/ACL trên HTTP và support grant còn triển khai. |
| RV05 | Nguồn và trạng thái READY | Huy; rà chéo: Đính | Guard đã sửa; pipeline thật còn mở. R01 và SQL08/26; byte thật, MIME, extraction chưa có worker. |
| RV06 | Context tối đa năm nguồn | Huy; rà chéo: Đính | Guard đã sửa; logic service và worker còn mở. R03 và SQL10/11; context/log/epoch theo hợp đồng TX10. |
| RV07 | Version duyệt và công bố | Đính; rà chéo: Huy | Kiểm DB nhúng đạt; API đọc/cache chưa kiểm chứng. SQL14–20; tổng phút, bất biến, nguồn, grant và lớp kết thúc. |
| RV08 | Gửi lặp và mất phản hồi | Đính; rà chéo: Huy | Unique/TTL đạt; hành vi HTTP chưa chạy. SQL21/22 bảo vệ receipt; payload hash/replay/ETag cần service. |

## 3 Bảng rà soát chéo F02 F04 tiếp theo

Mỗi dòng cần kết luận Đồng ý, Yêu cầu sửa hoặc Chưa đủ bằng chứng, kèm người ghi và ngày. Chủ trì và người rà soát kế thừa phương án G3; các phần giờ làm, ngân sách và mốc vẫn chờ hai thành viên thống nhất.

Bảng 4 Nội dung rà soát RV09 đến RV15

| Mã | Phạm vi rà soát | Chủ trì và rà chéo | Trạng thái và bằng chứng |
|---|---|---|---|
| RV09 | Quota ngân sách và worker | Huy; rà chéo: Đính | Còn mở; chưa chốt provider model cost cap. SQL23 chỉ xác nhận mặc định AI tắt; reservation/lease/fencing chưa triển khai. |
| RV10 | Hợp đồng API 40 thao tác | Đính; rà chéo: Huy | Đủ cấu trúc; chờ rà soát chéo và implementation. Bản OpenAPI nguồn giữ nguyên; kiểm schema chưa tương đương endpoint. |
| RV11 | Khả năng sử dụng prototype | Huy; rà chéo: Đính | Chưa có thử người dùng thật. Chuẩn bị tác vụ và phiếu người thử theo vai trò. |
| RV12 | Native DB và đồng thời | Đính; rà chéo: Huy | Đã chuẩn bị; chưa thực thi native. Compose PostgreSQL 17; 28 ca và bốn bài chạy nhiều session/boundary. |
| RV13 | F01 F03 F05 F06 | Theo G3 01; rà chéo: Rà soát chéo | Chưa đủ căn cứ đóng G3. Hoàn thiện prototype các luồng bắt buộc và P1. |
| RV14 | Threat model backup CI NFR | Đính; rà chéo: Huy | Còn mở. G3 AC05; cần thiết kế và bằng chứng thử rủi ro. |
| RV15 | Phân công nguồn lực và xác nhận | Hai thành viên; rà chéo: Hai thành viên | Đính đồng ý đợt rà soát; Huy và nguồn lực chờ điền. Giờ/tuần ngân sách mốc; ý kiến từng người về hồ sơ. |

Sổ chi tiết review/review_register.json có trường kết luận riêng cho Đính, Huy, ngày và ghi chú quyết định. Những ô Chờ rà soát hoặc Chờ xác nhận chưa được tính là đã phê duyệt. Nội dung giữ mở phải ghi người xử lý và bằng chứng còn thiếu.

## 4 Danh mục và kết quả kiểm tra SQL

Môi trường thực thi PostgreSQL 17.5 qua PGlite 0.3.14; toàn bộ các dòng dưới đây đã chạy sau khi áp 001, 002 và 003. PASS nghĩa là thao tác hợp lệ được chấp nhận hoặc thao tác sai bị từ chối đúng mã kỳ vọng; không có ca nào được bỏ qua.

Bảng 5 Ca SQL 1 đến 14

| Mã | Điều kiện kiểm tra | SQLSTATE kỳ vọng | Kết quả |
|---|---|---|---|
| SQL01 | Mỗi buổi sinh hai booking | Thành công | PASS |
| SQL02 | Từ chối khoảng lịch chồng lấn | 23P01 | PASS |
| SQL03 | Cho phép hai khoảng sát biên | Thành công | PASS |
| SQL04 | Hủy buổi giải phóng cả hai booking | Thành công | PASS |
| SQL05 | Lỗi cuối làm hoàn tác cả lô thử | Thành công | PASS |
| SQL06 | Một nhu cầu không tạo hai lớp | 23505 | PASS |
| SQL07 | Nguồn của lớp khác bị từ chối | 23503 | PASS |
| SQL08 | READY cần số ký tự trích xuất khác NULL | 23514 | PASS |
| SQL09 | Buổi không được chuyển sang lớp khác | 23514 | PASS |
| SQL10 | INSERT nguồn thứ sáu bị từ chối | 23514 | PASS |
| SQL11 | UPDATE nguồn thứ sáu bị từ chối | 23514 | PASS |
| SQL12 | Chấp nhận đúng 20 phiên bản hoạt động | Thành công | PASS |
| SQL13 | Từ chối phiên bản hoạt động thứ 21 | 23514 | PASS |
| SQL14 | Bài 89 phút không duyệt cho buổi 90 phút | 23514 | PASS |

SQL08, SQL09 và SQL11 là ba ca hồi quy gắn với R01–R03. Runner chạy chúng trên bản nền để lưu hiện tượng chấp nhận sai, sau đó áp 003 và chạy toàn bộ 28 ca. File kết quả giữ rõ version engine, hash và SQLSTATE để đối chiếu.

## 4 Danh mục và kết quả kiểm tra SQL tiếp theo

Môi trường thực thi PostgreSQL 17.5 qua PGlite 0.3.14; toàn bộ các dòng dưới đây đã chạy sau khi áp 001, 002 và 003. PASS nghĩa là thao tác hợp lệ được chấp nhận hoặc thao tác sai bị từ chối đúng mã kỳ vọng; không có ca nào được bỏ qua.

Bảng 6 Ca SQL 15 đến 28

| Mã | Điều kiện kiểm tra | SQLSTATE kỳ vọng | Kết quả |
|---|---|---|---|
| SQL15 | Không sửa hoạt động đã duyệt | 23514 | PASS |
| SQL16 | Không công bố bản còn nháp | 23514 | PASS |
| SQL17 | Công bố được bản thủ công đã duyệt | Thành công | PASS |
| SQL18 | Epoch nguồn thu hồi chặn công bố | 23514 | PASS |
| SQL19 | Thiếu quyền dẫn xuất chặn công bố | 23514 | PASS |
| SQL20 | Lớp kết thúc chặn công bố mới | 23514 | PASS |
| SQL21 | Receipt duy nhất theo actor command key | 23505 | PASS |
| SQL22 | Receipt phải giữ tối thiểu 7 ngày | 23514 | PASS |
| SQL23 | AI thật mặc định tắt | Thành công | PASS |
| SQL24 | Không thay điều khoản đề nghị đã gửi | 23514 | PASS |
| SQL25 | Thời lượng buổi phải đúng lớp | 23514 | PASS |
| SQL26 | READY cần gia sư kiểm tra | 23514 | PASS |
| SQL27 | Buổi của lớp khác không gắn vào plan | 23503 | PASS |
| SQL28 | Không mở đáp án trước khi đóng bài tập | 23514 | PASS |

SQL05 kiểm rollback của lô ghi trong một transaction thử, chưa kiểm toàn bộ accept_offer của Spring Boot. SQL21 và SQL22 kiểm unique và thời hạn receipt, chưa kiểm hash payload, replay hoặc phục hồi response mạng ở API. SQL23 chỉ xác nhận mặc định AI tắt, chưa kiểm quota và ngân sách của provider.

## 5 Chuẩn bị kiểm chứng PostgreSQL máy chủ

Môi trường lập hồ sơ không hỗ trợ chạy PostgreSQL native theo cấu hình hệ điều hành chuẩn. Vì vậy bộ native được bàn giao ở trạng thái Đã chuẩn bị, chưa thực thi. Cấu hình dùng official image postgres: 17-bookworm, database thử riêng, cổng chỉ mở loopback và password ngẫu nhiên sinh tại máy chạy.

Bảng 7 Thành phần môi trường native

| Thành phần | Thiết lập và điều kiện |
|---|---|
| Cơ sở dữ liệu | tb_g3_review; kiểm major PostgreSQL 17 khi kết nối; từ chối schema có sẵn, không tự xóa để chạy lại. |
| Vai trò và secret | Role migration dùng riêng cho kiểm chứng; password file sinh mới và không in giá trị; không đưa vào Git hoặc gói chia sẻ. |
| Vòng đời dữ liệu | DB container ở tmpfs, mất khi xóa hoặc tạo lại container. Lưu evidence ra thư mục kit trước khi kết thúc. |
| Khóa phiên bản | Khóa major và nền bookworm; patch/digest image chưa khóa. Khi chạy phải ghi version và digest thực tế. |
| Runner | 28 ca dùng cùng fixture và case JSON; thêm bốn bài dưới đây. Kết quả native_results.json chỉ tạo sau khi chạy thật. |

Bảng 8 Bài kiểm chứng nhiều kết nối và biên thời gian

| Mã | Thao tác chuẩn bị | Bằng chứng phải có |
|---|---|---|
| N01 | Hai session tranh khoảng lịch của gia sư. | Thấy session chờ khóa thật; bên sau nhận 23P01. |
| N02 | Hai session tranh khoảng lịch của học sinh. | Thấy session chờ khóa thật; bên sau nhận 23P01. |
| N03 | Hai khoảng sát biên qua hai kết nối, commit tuần tự. | Cả hai thành công; chỉ là kiểm biên, không chứng minh tranh chấp. |
| N04 | Một session thu hồi nguồn, session kia công bố. | Thấy lock wait; sau commit thu hồi, công bố nhận 23514. |

Hướng dẫn chạy từng bước nằm tại native/README.md. Compose mới được parse YAML; Python mới được compile cú pháp, chưa kết nối DB thật. Các bài DB thành phần chưa thay kiểm chốt lớp toàn bộ, quyền HTTP và gửi lặp qua hai Spring Boot replica. Không kết luận đạt native từ trạng thái đã chuẩn bị.

## 6 Chuẩn bị thử prototype với người dùng

Chưa có người thử hoặc kết quả quan sát thực tế trong đợt này. Địa chỉ prototype: https://tutorbridge-g3-f02-f04.jenny-180820.chatgpt.site. Người thử dùng dữ liệu mẫu và các nút trong khung ứng dụng, không dùng thanh rà soát để bỏ qua điều kiện tác vụ.

Bảng 9 Tác vụ thử theo vai trò

| Mã và vai trò | Tác vụ giao cho người thử | Dấu hiệu hoàn thành |
|---|---|---|
| UX01 Phụ huynh | Chọn con, xác nhận nhu cầu, chọn gia sư, xem giá và lịch rồi gửi đề nghị. | Đúng con; hiểu giá mỗi buổi, trạng thái chờ và chưa giữ chỗ. |
| UX02 Gia sư | Xem và nhận đề nghị; mở lớp và lịch. Làm thêm kịch bản có một buổi xung đột. | Hiểu nhận nguyên bản; tìm được lớp đủ buổi; hiểu lý do chưa tạo lớp khi xung đột. |
| UX03 Gia sư | Kiểm nguồn, tạo nháp mẫu hoặc soạn thủ công; sửa thời lượng, duyệt và công bố. | Phân biệt nguồn READY, nháp, duyệt và công bố; biết tác động quyền nguồn. |

### 6.1 Mẫu và cách tính kết quả

Theo G3 AC03 của TB G3 01, đề xuất ít nhất 5 người và mục tiêu ít nhất 4 hoàn thành luồng chính không cần hướng dẫn từng bước. Mẫu số phải tách theo tác vụ và vai trò. Chỉ ghi 4 trên 5 khi có 5 quan sát cùng tác vụ trong điều kiện tương đương; không cộng người thử các tác vụ khác nhau để suy ra đạt.

Nếu chưa đủ mẫu cho tác vụ, ghi Chưa đánh giá tiêu chí và bổ sung người thử hoặc trình điều chỉnh tiêu chí có xác nhận. Mẫu nhỏ phục vụ khám phá lỗi và cải thiện luồng; không dùng để khẳng định đại diện thị trường.

Bảng 10 Phiếu ghi nhận một lượt thử

| Trường | Giá trị cần ghi thực tế |
|---|---|
| Mã người thử và vai trò | Chờ ghi mã ẩn danh, không ghi tên trẻ hoặc liên hệ. |
| Tác vụ và ngày | Chờ ghi UX01, UX02 hoặc UX03 và ngày thực hiện. |
| Hoàn thành và trợ giúp | Chờ ghi có/không; số lần trợ giúp; có hướng dẫn từng bước hay không. |
| Lỗi và ghi chú | Chờ ghi màn hình, thao tác, lỗi quan sát và câu hỏi của người thử. |
| Người quan sát và kết luận | Chờ ghi Đính hoặc Huy; dẫn mã issue và mẫu số đúng tác vụ. |

## 7 Điều kiện hoàn thành G3 còn lại

Phạm vi F02/F04 đã có thêm bằng chứng thực thi và sửa guard. Điều kiện đóng G3 vẫn quản lý theo G3 AC01–AC06, không thay bằng số ca PASS. Bảng sau ghi trạng thái bằng chứng hiện có; chưa phải biên bản nghiệm thu của hai thành viên.

Bảng 11 Đối chiếu tiêu chí G3

| Mã | Điều kiện từ TB G3 01 | Trạng thái sau đợt này |
|---|---|---|
| AC01 | Hai thành viên xác nhận phạm vi, phân công, giờ làm, ngân sách và mốc. | Đính đồng ý đợt rà soát; Huy và thông tin nguồn lực còn chờ. |
| AC02 | Prototype đủ luồng cốt lõi, trạng thái lỗi, quyền, xung đột, P1 và truy vết. | F02/F04 đã có; các luồng còn lại và P1 cần hoàn thiện theo danh mục chung. |
| AC03 | Thử người dùng theo mẫu và tiêu chí đã thống nhất; ghi mẫu số rõ. | Đã có tác vụ và phiếu; chưa có quan sát thực tế. |
| AC04 | Kiến trúc, ADR, ERD, quyền, trạng thái và OpenAPI nhất quán. | F02/F04 có thiết kế và 003; còn rà soát chéo, kiến trúc và phạm vi tổng thể. |
| AC05 | Thử rủi ro lớn; phương án AI, threat model, migration, backup, CI và FR NFR AT. | SQL nhúng đã có; native, API, worker, bảo mật, tải và khôi phục còn mở. |
| AC06 | Backlog ước lượng theo nguồn lực; người làm/rà, phụ thuộc và điều kiện bàn giao G4. | Cần nguồn lực và mốc thực tế để khóa backlog; chưa đủ căn cứ kết luận chung. |

### 7.1 Phạm vi bắt buộc thiết kế tiếp

F03 gồm đổi lịch, hủy, học bù và kết thúc; F05 gồm sau buổi học và tiến trình; F01 là đăng ký và hồ sơ; F06 là hỗ trợ, thu hồi và xóa dữ liệu. P1 về học sinh và bài tập vẫn là điều kiện của R0 đầy đủ. Các phần này tiếp tục kế hoạch sau khi rà soát F02/F04, không tự được coi là đã hoàn tất.

Nguồn lực, ngân sách và lựa chọn provider/model AI phải được nhóm ghi nhận trước khi khóa lịch triển khai. AI thật giữ mặc định tắt đến khi có cấu hình và trần tiền được xác nhận. Chưa có bằng chứng xác nhận hệ thống chịu hàng nghìn người dùng.

## 8 Nội dung họp và thứ tự xử lý tiếp theo

Bảng 12 Đầu việc thực hiện sau bàn giao

| Ưu tiên | Người chủ trì theo phương án | Đầu ra có thể kiểm tra |
|---|---|---|
| B01 Rà R01 đến R03 | Đính; Huy rà chéo. | Đối chiếu SQL và kết quả trước/sau; ghi ý kiến về 003, mã commit khi tích hợp. |
| B02 Đi RV01 đến RV15 | Theo từng dòng; hai người cùng kết luận. | Mỗi dòng có đồng ý, yêu cầu sửa hoặc chưa đủ bằng chứng; không để ô chờ được hiểu là đã duyệt. |
| B03 Chạy PostgreSQL native | Đính; Huy kiểm kết quả. | 28 ca và N01–N04 trên DB thử mới; lưu native_results.json, version và image digest. |
| B04 Tổ chức UX01 đến UX03 | Huy; Đính cùng quan sát. | Người phù hợp, ghi quan sát thật và mẫu số riêng; sửa issue tác vụ bị chặn. |
| B05 Khóa phần thiết kế còn lại | Hai thành viên theo G3 01. | F01/F03/F05/F06/P1, kiến trúc, NFR và nguồn lực đủ để kết luận Gate G3. |

Ưu tiên B01 và B02. Chuẩn bị B03 song song với tuyển người thử B04; lập mốc sau khi thống nhất giờ làm và ngân sách. Phát hiện mới phải có mã test và bằng chứng trước sau.

### 8.1 Ghi nhận kết luận thành viên

Bảng 13 Ý kiến chờ điền theo thực tế

| Thành viên | Kết luận đợt rà soát | Ngày và điều kiện kèm theo |
|---|---|---|
| Dương Đính | Đã đồng ý thực hiện đợt rà soát; kết luận từng dòng và migration 003 chờ rà trực tiếp. | Ngày đồng ý thực hiện 29/10/2026; ngày kết luận chi tiết chờ điền. |
| Đức Huy | Chờ rà soát và xác nhận hoặc đề nghị sửa. | Chờ điền ngày, ý kiến và điều kiện khi có xác nhận thực tế. |

### 8.2 Quản lý phiên bản và bàn giao

TB G3 04 v0.1 bổ sung kiểm chứng cho TB G3 03 v0.1; áp SQL theo thứ tự 001, 002, 003. G2 và Gate G3 giữ theo hồ sơ xác nhận.

## 9 Tài liệu tham chiếu và chỉ mục bàn giao

Bảng 14 Chỉ mục tệp trong gói kiểm chứng

| Tệp hoặc nhóm tệp | Vai trò và trạng thái |
|---|---|
| baseline/001 và 002 | Migration nguồn giữ nguyên; đã thực thi nhúng. |
| sql/003_review_corrections.sql | Sửa R01–R03; đã thực thi nhúng và kiểm hồi quy. |
| tests/cases.json và run_embedded.cjs | 28 ca có kỳ vọng SQLSTATE; runner đã chạy trên PGlite. |
| evidence/embedded_results.json | Kết quả thực tế; version engine, hash và trạng thái từng ca. |
| evidence/source_binding.json | Nguồn thiết kế, phiên bản và hash ZIP gốc. |
| native/ và compose.yaml | Môi trường PostgreSQL 17; đã chuẩn bị, chưa thực thi native. |
| review/review_register.json | 15 dòng cần kết luận riêng của Đính và Huy. |
| review/usability_protocol.md | Tác vụ và mẫu ghi nhận; chưa có người dùng thử thực tế. |

### 9.1 Tài liệu tham khảo

[1] Nội bộ: TB G2 02 v1.1; TB G3 01 v0.1; TB G3 03 v0.1. Nguồn và hash tại source_binding.json.

[2] PostgreSQL 17 CHECK Constraints và btree_gist: https://www.postgresql.org/docs/17/ddl-constraints.html và https://www.postgresql.org/docs/17/btree-gist.html. Truy cập 29/10/2026.

[3] PGlite và extensions: https://pglite.dev/docs/about và https://pglite.dev/extensions/. Psycopg: https://www.psycopg.org/psycopg3/docs/basic/usage.html. Truy cập 29/10/2026.
