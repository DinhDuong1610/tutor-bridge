# TutorBridge_Khoi_dong_G3_va_Danh_muc_Man_hinh_v0_1

> Tài liệu lịch sử, độ ưu tiên thấp nhất. Bản Word kèm wireframe ở `docs/source-docx/g3/TutorBridge_Khoi_dong_G3_va_Danh_muc_Man_hinh_v0_1.docx`. Hình ảnh không có trong bản này.

NHÓM DỰ ÁN TUTORBRIDGE

HỒ SƠ KHỞI ĐỘNG G3

VÀ DANH MỤC MÀN HÌNH

DỰ ÁN TUTORBRIDGE

Tài liệu điều hành và triển khai giai đoạn thiết kế giải pháp

| Thông tin kiểm soát | Nội dung |
|---|---|
| Mã tài liệu | TB G3 01 |
| Phiên bản | 0.1 |
| Ngày phát hành | 29/10/2026 |
| Nhóm thực hiện | Dương Đính và Đức Huy |
| Mục đích sử dụng | Phân công thiết kế, xác định các màn hình cốt lõi và chuẩn bị hồ sơ bàn giao G4. |
| Trạng thái | Khởi động thiết kế; Dương Đính đồng ý phương án, Đức Huy chờ xác nhận; chưa nghiệm thu G3. |

Phạm vi yêu cầu tham chiếu: TB G2 02 v1.1. Trạng thái xác nhận và điều kiện bàn giao được quản lý tại TB G2 03 v1.2.

## Mục lục

 1 Mục tiêu và phạm vi triển khai 3

 2 Phân công thực hiện G3 4

 3 Trình tự và kế hoạch nguồn lực 5

 4 Quy ước danh mục và điều hướng 6

 5 Màn hình tài khoản và chức năng chung 7

 6 Màn hình phụ huynh 8

 7 Màn hình gia sư 9

 8 Màn hình lớp và học tập 10

 9 Màn hình học sinh và web công khai 12

 10 Màn hình quản trị 13

 11 Luồng cốt lõi cần dựng prototype 14

 12 Truy vết và điều kiện bàn giao 16

 13 Tiêu chí hoàn thành G3 và việc trước mắt 17

Tra cứu theo mã màn hình tại mục 5 đến mục 10; theo luồng tại mục 11; theo mã UC tại mục 12. Mức P0/P1 và trạng thái xác nhận giữ thống nhất với hồ sơ G2 hiện hành.

## 1 Mục tiêu và phạm vi triển khai

G3 chuyển yêu cầu TutorBridge thành thiết kế đủ cụ thể để lập trình, kiểm thử và vận hành nhất quán. Hồ sơ này xác định phân công, danh mục màn hình và các luồng cần dựng prototype; là đầu vào cho thiết kế kiến trúc, dữ liệu và API tiếp theo.

## 1.1 Trạng thái quyết định

Dương Đính đã đồng ý phạm vi cơ sở và phương án khởi động ngày 29/10/2026. G2 vẫn Đạt có điều kiện đến khi Đức Huy xác nhận và hai thành viên thống nhất nguồn lực/mốc. Trong thời gian này, triển khai thiết kế bằng dữ liệu mô phỏng hoặc được phép; theo dõi điều kiện tại TB G2 03 v1.2.

## 1.2 Phạm vi thiết kế

Thiết kế cho R0: học kèm trực tuyến 1–1 Toán lớp 8 đại số bằng tiếng Việt; Android phụ huynh/gia sư, học sinh theo lời mời ở P1; web quản trị và giới thiệu. Bao phủ tài khoản, nhu cầu/gợi ý, thỏa thuận/lịch, nguồn/soạn/duyệt, nhật ký, bài tập, tiến trình và hỗ trợ/quyền.

P0 được thiết kế và triển khai trước. P1 vẫn bắt buộc cho R0 đầy đủ, gồm đăng nhập học sinh và bài tập; phần P0/P1 phải tách được trong thiết kế. R0a chỉ là lát cắt kiểm chứng nội bộ. Thanh toán, chat, push/email nghiệp vụ, iOS, web gia sư, OCR và lớp nhóm nằm ngoài phạm vi R0 hiện hành.

## 1.3 Kết quả của đợt khởi động

Bàn giao bảng xác nhận G2 đã cập nhật, 9 nhóm trách nhiệm G3, 52 màn hình chức năng và 6 luồng prototype. Chưa có kết quả thử người dùng, quyết định kiến trúc cuối cùng, ERD vật lý, OpenAPI hoặc nghiệm thu G3 trong đợt này. Các đầu ra đó được phân công tại mục 2 và theo dõi tại mục 12.

## 1.4 Căn cứ quản lý

[S1] TB G2 02 v1.1, ngày 29/10/2026, là nguồn yêu cầu chính. [S2] TB G2 03 v1.2 cùng ngày ghi nhận xác nhận và điều kiện bàn giao. [S3] TB PLAN 01 v2.0, ngày 23/10/2026, quy định phân kỳ G3 thiết kế và G4 dựng nền móng. [S4] Xác nhận tiếp tục của Dương Đính ngày 29/10/2026. Khi khác biệt về phạm vi sản phẩm, áp dụng [S1] và kiểm soát thay đổi.

## 2 Phân công thực hiện G3

Dương Đính chủ trì nghiệp vụ, backend, dữ liệu và vận hành. Đức Huy chủ trì UX, web/mobile, matching, học liệu và AI. Phương án đã được Dương Đính đồng ý; Đức Huy xác nhận hoặc ghi điều chỉnh trước khi khóa kế hoạch chung. Người chủ trì chuẩn bị đầu ra, người còn lại rà soát trước khi dùng làm cơ sở triển khai.

| Mã và nhóm việc | Chủ trì | Rà soát | Đầu ra |
|---|---|---|---|
| G3-01 Phạm vi, quyết định và nguồn lực | Dương Đính | Đức Huy | Bảng xác nhận G2; mục tiêu/mốc và giờ làm thực tế. |
| G3-02 Luồng UX, màn hình và prototype | Đức Huy | Dương Đính | Danh mục này → wireframe → prototype theo luồng và trạng thái. |
| G3-03 Kiến trúc và quyết định kỹ thuật | Dương Đính | Đức Huy | Ranh giới module, API/worker/kho tệp, ADR và phương án triển khai. |
| G3-04 Dữ liệu, lịch và quyền | Dương Đính | Đức Huy | ERD/từ điển vật lý, bất biến và chuyển trạng thái; rà quyền nguồn với G3-05. |
| G3-05 Matching, học liệu và AI | Đức Huy | Dương Đính | Thiết kế S/B, nguồn có quyền, tác vụ/nháp/duyệt; ghi lựa chọn và kết quả thử rủi ro. |
| G3-06 Hợp đồng API và lỗi | Dương Đính | Đức Huy | OpenAPI theo FR/UC, lỗi, phiên bản và khóa thao tác lặp; Huy đối chiếu mobile/web. |
| G3-07 An toàn dữ liệu và vận hành | Dương Đính | Đức Huy | Threat model, phiên/MFA, bí mật, CI, môi trường, backup/xóa/restore và rollback. |
| G3-08 Thử prototype và đánh giá | Đức Huy | Dương Đính | Kịch bản, người tham gia, quan sát và vấn đề; cả hai cùng thực hiện và rà bằng chứng. |
| G3-09 Ước lượng và bàn giao G4 | Dương Đính | Đức Huy | Backlog theo phụ thuộc/giờ, người làm/rà soát, kế hoạch kiểm thử và kết luận G3. |

Cả hai cùng kiểm chứng người dùng và quyết định qua cổng. Phần quyền, lịch và nguồn phải có rà soát chéo; người thiết kế không tự coi đầu ra của mình là đã được nghiệm thu. Với lớp Toán, nhóm cần bố trí người thẩm định chuyên môn trước đánh giá nội dung thực tế.

## 3 Trình tự và kế hoạch nguồn lực

G3 có khung tham chiếu 2 tuần theo kế hoạch tổng thể. Ngày bắt đầu công việc khởi động là 29/10/2026; ngày hoàn thành chưa được cam kết. Hai thành viên ghi giờ làm thực tế, ước lượng công việc và đặt mốc sau khi rà phụ thuộc. Các mốc dưới đây là trình tự kết quả, không phải lịch đã hoàn thành.

| Mốc | Công việc và phụ thuộc | Điều kiện hoàn thành |
|---|---|---|
| M1 | G3-01 và danh mục màn hình; rà phạm vi/quyền/ngoại lệ. | Ghi xác nhận còn thiếu; mỗi màn hình có vai trò, FR/UC và mức ưu tiên. |
| M2 | G3-02 dựng wireframe/prototype; G3-03 đến G3-05 thiết kế sơ bộ song song. | Đi được 6 luồng; thể hiện nhánh lỗi/thu hồi/xung đột và các điểm cần người xác nhận. |
| M3 | G3-04 đến G3-07 hoàn thiện dữ liệu, API, rủi ro và vận hành; đối chiếu lại prototype. | Có ERD, trạng thái, OpenAPI, ADR và kết quả thử các rủi ro ảnh hưởng thiết kế. |
| M4 | G3-08 thử người dùng; xử lý vấn đề và cập nhật các thiết kế bị ảnh hưởng. | Có ghi chép tác vụ, trợ giúp/lỗi và kết quả từng người; không thay kết quả bằng nhận xét chung. |
| M5 | G3-09 ước lượng lại, rà toàn bộ FR/NFR và nghiệm thu. | Bộ hồ sơ đủ điều kiện giao G4; ghi phần đạt/chưa đạt, người rà soát và quyết định chung. |

## 3.1 Nguồn lực cần ghi trước khi khóa lịch

Dương Đính: giờ/tuần và thời gian bận. Đức Huy: giờ/tuần và thời gian bận. Cả hai: trần ngân sách G3, ngày rà soát giữa kỳ, ngày thử prototype và ngày bàn giao dự kiến. Theo dõi tập trung tại bảng 7 của TB G2 03 v1.2 để tránh nhiều giá trị song song. Chưa đặt trần tiền thì AI trả phí giữ tắt; prototype có thể mô phỏng và gắn nhãn rõ.

Ước lượng theo giờ cho từng đầu ra sau M2, cộng thời gian rà soát và sửa. Mỗi người tối đa một việc chính đang thực hiện. Cuối mỗi buổi cập nhật kết quả, vướng mắc và tác động tới mốc; thay đổi phạm vi đi qua yêu cầu thay đổi có liên kết mã liên quan.

## 4 Quy ước danh mục và điều hướng

Mã màn hình ổn định dùng để liên kết prototype, thiết kế, API và ca kiểm thử. Một mã là một màn hình chức năng; danh sách/chi tiết hoặc bước xác nhận có thể là các trạng thái trong cùng mã. Số 52 không phải số tệp mã nguồn hoặc số URL bắt buộc. Màn hình dùng chung giữ một mã khi phục vụ nhiều vai trò.

| Nhóm | Số màn hình | Người sử dụng và điều hướng chính |
|---|---|---|
| AUTH | 7 | Tài khoản thường; AUTH07 chỉ học sinh P1. Quản trị dùng AD01. |
| COM | 4 | Thông báo, hỗ trợ và quyền dữ liệu theo vai trò/quan hệ. |
| PH | 10 | Phụ huynh: Tổng quan, Tìm gia sư, Lớp học/Lịch, Thông báo, Tài khoản. |
| GS | 10 | Gia sư: Tổng quan, Đề nghị, Lớp học/Lịch, Học liệu, Tài khoản. |
| CL | 8 | Màn hình lớp dùng chung; tác vụ gia sư và phụ huynh tách quyền; CL07–08 thuộc P1. |
| HS | 1 | Học sinh P1: Tổng quan, Lịch, Nội dung/Bài tập; dùng lại CL02, CL06, CL07. |
| AD | 10 | Web quản trị: Xác minh, Hỗ trợ, Tài khoản, AI, Dữ liệu, Kiểm toán theo quyền. |
| WEB | 2 | Web công khai: Giới thiệu và Hướng dẫn/Chính sách; không có học liệu riêng. |

## 4.1 Trạng thái chung bắt buộc

Mỗi màn hình có tải/chờ, rỗng, lỗi trường, lỗi hệ thống, hết phiên và không còn quyền khi áp dụng. Tác vụ ghi có đang gửi, đã xác nhận, chưa rõ kết quả và tra cứu/thử lại an toàn; không chỉ khóa nút để chống lặp. Không hiển thị thành công trước xác nhận máy chủ. FR-37 áp dụng cho toàn bộ danh mục; FR-38 đo sự kiện liên quan theo UC13.

Hiển thị con/lớp đang chọn, ngày giờ Việt Nam dạng 24 giờ và đơn vị tiền rõ. Không chỉ dùng màu để phân biệt trạng thái; nhãn trợ năng, focus/bàn phím trên web và phóng chữ 200% phải giữ tác vụ chính. Không đặt dữ liệu ngoài quyền vào payload, cache hoặc giao diện rồi chỉ ẩn bằng CSS.

Truy vết chất lượng: NFR01–03 cho quyền, nguồn và thu hồi; NFR04 cho ghi đồng thời/thử lại; NFR05 cho lỗi AI/nháp; NFR06–08 cho dữ liệu, nội dung không đáng tin và bí mật; NFR09 cho audit; NFR10 cho độ trễ nghiệp vụ/thông báo; NFR11 cho xóa/khôi phục; NFR12 cho trợ năng; NFR13–14 cho tác vụ/hạn mức AI; NFR15–16 cho tương thích, theo dõi và phát hành. Các thiết kế API, dữ liệu và vận hành tiếp theo phải cụ thể hóa cách thực hiện và kiểm chứng các yêu cầu này.

## 5 Màn hình tài khoản và chức năng chung

AUTH phục vụ phụ huynh/gia sư và nhánh lời mời học sinh. COM dùng chung theo quyền; học sinh chỉ tiếp cận phần được cho phép và yêu cầu quyền/xóa qua phụ huynh theo UC16. Quản trị có điểm đăng nhập riêng AD01.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| AUTH01 Đăng ký và chọn vai trò | P0 | UC01 FR-01 | Chỉ phụ huynh/gia sư tự đăng ký; email, tên, mật khẩu và điều khoản. Báo lỗi cạnh trường; không cấp vai trò quản trị/học sinh từ lựa chọn tự do. |
| AUTH02 Xác minh email | P0 | UC01 FR-01, FR-39 | Mở liên kết đúng mục đích, hiển thị xác minh thành công, hết hạn hoặc đã dùng; có gửi lại được kiểm soát. Không báo đã xác minh khi gửi email lỗi. |
| AUTH03 Đăng nhập | P0 | UC01 FR-02, FR-39 | Đúng vai trò; trạng thái sai thông tin, trì hoãn đăng nhập, tài khoản khóa và phiên hết hạn. Giữ bản nháp đã lưu để tiếp tục sau đăng nhập lại. |
| AUTH04 Yêu cầu khôi phục mật khẩu | P0 | UC01 FR-39 | Nhập email và nhận thông báo chung; không xác nhận email có tài khoản hay không. Có trạng thái đang gửi, lỗi và giới hạn gửi lại. |
| AUTH05 Đặt lại mật khẩu | P0 | UC01 FR-39 | Mật khẩu 15–128 ký tự; liên kết một lần hạn 15 phút. Hiển thị hết hạn/đã dùng; thành công thu hồi mọi phiên cũ và dẫn về đăng nhập. |
| AUTH06 Bảo mật và quản lý phiên | P0 | UC01 FR-02, FR-39 | Đổi mật khẩu, xem/thu hồi phiên và đăng xuất. Phân biệt phiên hiện tại; xác nhận thao tác nhạy cảm và chỉ báo hoàn tất sau máy chủ xác nhận. |
| AUTH07 Nhận lời mời học sinh | P1 | UC02 FR-36 | Lời mời email riêng hạn 24 giờ, kiểm tra đúng người rồi liên kết. Hiển thị sai người/hết hạn/đã dùng; không cho tự đăng ký hoặc nhập mã lớp để nhận quyền. |
| COM01 Hộp thông báo | P0 | UC15 FR-41 | Đã đọc/chưa đọc và liên kết đến đối tượng trong quyền. Liên kết cũ kiểm tra lại quyền; không lộ dữ liệu riêng trong tiêu đề. P1 bổ sung sự kiện bài tập/học sinh. |
| COM02 Danh sách yêu cầu hỗ trợ | P0 | UC14 FR-40 | Chỉ vụ việc của chính người dùng; lọc trạng thái và mở chi tiết. Rỗng có đường tạo yêu cầu; không cam kết thời gian hỗ trợ chưa được xác lập. |
| COM03 Tạo và theo dõi vụ việc | P0 | UC14 FR-40 | Loại, đối tượng, mô tả tối đa 2.000 ký tự; phản hồi và lịch sử. Nếu cần đọc học liệu, hiển thị phạm vi, lý do, thời hạn tối đa 24 giờ để chủ thể có quyền quyết định và thu hồi. |
| COM04 Quyền dữ liệu và yêu cầu xóa | P0 | UC02, UC16 FR-36, FR-42 | Xem liên kết/quyền; yêu cầu thu hồi hoặc xóa, xác nhận phạm vi và theo dõi mã xử lý. Nêu tác động tới lớp hoạt động, dẫn xuất và giới hạn với bản đã tải. Thu hồi tài khoản học sinh bổ sung ở P1. |

## 6 Màn hình phụ huynh

Mọi thao tác nhu cầu, lớp và tiến trình phải giữ rõ học sinh đang chọn. Kết quả tìm không giữ chỗ. Phụ huynh quyết định lựa chọn gia sư và xác nhận tham gia từng buổi bằng chính tài khoản của mình.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| PH01 Trang chủ phụ huynh | P0 | UC02, UC07, UC09, UC15 FR-03, FR-19, FR-32, FR-41 | Luôn hiện con đang chọn; buổi sắp tới, việc chờ xác nhận, nhu cầu/đề nghị và thông báo. Đổi con tải lại dữ liệu đúng phạm vi, không trộn lịch và tiến trình. |
| PH02 Hồ sơ các con | P0 | UC02 FR-03, FR-36 | Danh sách, tạo và sửa tên hiển thị/khối/mục tiêu ngắn; không bắt buộc ảnh, trường hoặc địa chỉ. P1 có mời học sinh, trạng thái liên kết và thu hồi. |
| PH03 Danh sách và phiên bản nhu cầu | P0 | UC04 FR-04 | Nháp, bản đã xác nhận và lịch sử; bản cũ chỉ đối chiếu. Tạo/sửa dẫn tới biểu mẫu; sửa tạo bản nháp mới, không thay mục tiêu của lớp đã chốt. |
| PH04 Nhập nhu cầu học kèm | P0 | UC04, UC07 FR-04, FR-19 | Con, mục tiêu 20–2.000 ký tự, ngân sách VND/buổi, thời lượng, tần suất, ngày bắt đầu và khung rảnh/ưu tiên. Thiếu điều kiện chỉ lưu nháp; kiểm tra chu kỳ 28 ngày. |
| PH05 Xem lại và xác nhận nhu cầu | P0 | UC04 FR-04 | Hiện toàn bộ điều kiện, lịch, ngân sách và phiên bản trước xác nhận. Chỉ tìm/gửi từ bản hiệu lực; dữ liệu cũ yêu cầu tải lại, giữ phần nhập hợp lệ. |
| PH06 Kết quả và so sánh gia sư | P0 | UC05 FR-09–14 | Tối đa 5 hồ sơ thật; giá/buổi, lịch chung, căn cứ và nhãn tự khai/xác minh. Tách rỗng, thiếu dữ liệu, lỗi; phụ huynh chủ động chọn danh sách B có nhãn khi AI lỗi. Không giữ chỗ. |
| PH07 Chi tiết gia sư công khai | P0 | UC03, UC05 FR-05–07, FR-12 | Chuyên môn, giá VND/60 phút và giá theo buổi, lịch chung, phạm vi xác minh. Không lộ minh chứng gốc/liên hệ riêng. Điểm xếp hạng không biểu diễn thành xác suất tiến bộ. |
| PH08 Xem lại và gửi đề nghị | P0 | UC06 FR-15, FR-17 | Hiện mục tiêu, ảnh chụp giá và đủ buổi cụ thể; hạn phản hồi; xác nhận gửi đúng bản. Cảnh báo không giữ chỗ; lịch/giá thay đổi phải xem lại, không gửi lặp khi chưa rõ kết quả. |
| PH09 Danh sách và chi tiết đề nghị | P0 | UC06 FR-15–17 | Chờ/chấp nhận/từ chối/hủy/hết hạn và lý do; phụ huynh hủy đề nghị đang chờ. Chỉ dẫn sang lớp khi lớp và mọi buổi đã được xác nhận nguyên tử; đề nghị cạnh tranh có lý do kết thúc. |
| PH10 Tiến trình học của con | P0/P1 | UC13 FR-35, FR-38 | Chọn con/lớp/kỳ; tách hoàn thành, hủy, vắng, chờ và phản hồi; có mẫu số và mở bản ghi gốc. N=0 hiện không áp dụng; phần bài tập bổ sung P1, không suy ra học lực từ điểm danh. |

## 7 Màn hình gia sư

Gia sư kiểm soát hồ sơ, lịch rảnh, đề nghị và nội dung chuyên môn. Nguồn chỉ dùng khi sẵn sàng và còn quyền; AI tạo nháp, gia sư chịu trách nhiệm rà soát và duyệt phiên bản trước công bố.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| GS01 Trang chủ gia sư | P0 | UC03, UC06, UC07, UC09, UC15 FR-05, FR-16, FR-19, FR-31, FR-41 | Việc cần làm: hoàn thiện hồ sơ, đề nghị, buổi sắp tới, nhật ký và bản soạn. Hồ sơ chưa duyệt/khóa có trạng thái và đường xử lý; P1 bổ sung bài cần nhận xét. |
| GS02 Hồ sơ chuyên môn và học phí | P0 | UC03 FR-05, FR-06 | Môn/khối, mô tả 50–3.000 ký tự, kinh nghiệm 0–600 tháng và giá nguyên dương VND/60 phút. Xem bản công khai đã duyệt khác bản đang sửa; đổi giá không sửa thỏa thuận cũ. |
| GS03 Minh chứng và kết quả xác minh | P0 | UC03 FR-07 | Tải minh chứng của mình, gửi duyệt, xem yêu cầu bổ sung/lý do và phiên bản. Nhãn xác minh nêu đúng phạm vi; không công khai tài liệu gốc cho người tìm gia sư. |
| GS04 Lịch rảnh và ngoại lệ | P0 | UC07 FR-08 | Nhập lịch tuần, ngoại lệ và xem lại trước xác nhận. Tách rảnh nháp, rảnh hiệu lực và buổi đã đặt; sửa lịch rảnh không hủy buổi cam kết; dùng giờ Việt Nam. |
| GS05 Đề nghị nhận lớp | P0 | UC06 FR-16, FR-17 | Danh sách/chi tiết điều kiện và hạn phản hồi; chấp nhận nguyên bản hoặc từ chối. Khi một buổi xung đột, không báo nhận lớp thành công; muốn đổi điều kiện yêu cầu đề nghị mới. |
| GS06 Kho học liệu của lớp | P0 | UC10 FR-21, FR-22 | Đúng lớp và quyền tải; PDF có văn bản/DOCX tối đa 10 MiB, 100 trang PDF hoặc 200.000 ký tự; tối đa 20 phiên bản tài liệu hoạt động/lớp. Hiện trạng thái tiếp nhận/xử lý/kiểm tra/sẵn sàng/lỗi. |
| GS07 Nguồn và quyền học liệu | P0 | UC10, UC16 FR-22, FR-23, FR-42 | Kiểm tra văn bản trích xuất và vị trí nguồn, xác nhận sẵn sàng; cấp/thu hồi theo quyền. Nguồn thu hồi dẫn tới tạm ẩn dẫn xuất; không chỉ làm hỏng liên kết. Nêu giới hạn xóa bản đã tải. |
| GS08 Bối cảnh và tác vụ soạn bài | P0 | UC11 FR-24–26, FR-28, FR-30 | Chọn lớp/buổi, mục tiêu hiệu lực, nhận xét đủ xác nhận và tối đa 5 nguồn sẵn sàng. Hiện AI tắt/quota/chờ/đang xử lý/lỗi/hủy; không tạo phần trăm giả; có đường soạn thủ công. |
| GS09 Biên tập bản soạn và mở nguồn | P0 | UC11 FR-26–30 | Mục tiêu, ôn tập, hoạt động theo phút, bài tập/đáp án dự kiến và nguồn; mở nguồn cạnh nội dung. Phân biệt tự biên soạn; cảnh báo còn tồn tại; tổng phút đúng thời lượng; lỗi AI không đè nháp. |
| GS10 Duyệt và công bố bài soạn | P0 | UC11 FR-27–29 | Xem lại đúng phiên bản, nguồn và buổi; gia sư xử lý cảnh báo rồi duyệt/công bố. Đáp án tách quyền; sửa bản đã công bố tạo nháp mới; nguồn mất quyền chặn công bố và ẩn dẫn xuất liên quan. |

## 8 Màn hình lớp và học tập

CL là các màn hình được tái sử dụng theo quan hệ lớp và trạng thái. Bản công bố, nhật ký chuyên môn, xác nhận tham gia, đề bài và đáp án có quyền riêng; không suy quyền từ việc cùng là thành viên lớp.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| CL01 Lớp học và thỏa thuận | P0 | UC04, UC07, UC08 FR-17–19, FR-43 | Phụ huynh/gia sư xem lớp, bản giá/mục tiêu, buổi và lịch sử. Đề xuất/đồng ý mục tiêu mới; chủ động chia sẻ liên hệ của mình; kết thúc lớp có lý do, hủy lịch tương lai và chuyển quyền sau kết thúc. |
| CL02 Lịch lớp và chi tiết buổi | P0 | UC07, UC08 FR-19, FR-20, FR-43 | Phụ huynh/gia sư xem theo ngày/tuần; mở buổi, nội dung và đường đổi/hủy. Học sinh chỉ đọc ở P1. Tách lịch đã xác nhận với đề nghị đổi; hiển thị giờ 24 giờ và đúng lớp/con. |
| CL03 Đổi hủy và học bù | P0 | UC08 FR-20, FR-43 | Đề xuất/duyệt đổi cùng phiên bản; lịch cũ còn hiệu lực đến khi áp dụng thành công. Mỗi bên hủy buổi tương lai có lý do; học bù tạo buổi liên kết riêng. Xung đột/hết hạn có đường chọn lại. |
| CL04 Nhật ký chuyên môn của gia sư | P0 | UC09 FR-31 | Gia sư ghi thời gian thực tế, có mặt/vắng, nội dung/nhận xét/kế hoạch tiếp; lưu nháp rồi công bố. Sửa có phiên bản và yêu cầu xác nhận lại khi ảnh hưởng; không hoàn thành buổi tương lai. |
| CL05 Xác nhận tham gia của phụ huynh | P0 | UC09 FR-32 | Đọc nhật ký, xác nhận tham gia hoặc báo khác biệt; tách ý kiến hai bên. Quá 72 giờ vẫn chờ, có nhắc; vắng/tranh chấp không thành hoàn thành và không đưa vào bối cảnh AI đủ xác nhận. |
| CL06 Bài soạn đã công bố | P0 | UC11 FR-29 | Phụ huynh và gia sư xem bản công bố đúng lớp; học sinh xem ở P1. Không lộ nháp/đáp án chưa mở hoặc nguồn gốc chưa được cấp riêng; khi mất quyền hiện trạng thái không còn khả dụng. |
| CL07 Bài tập và bài nộp | P1 | UC12 FR-33, FR-34 | Phụ huynh nộp thay hoặc học sinh liên kết nộp: văn bản tối đa 10.000 ký tự, tối đa 5 ảnh JPEG/PNG × 5 MiB. Có hạn/đóng, nộp lại, muộn, tác giả thao tác và lịch sử nhận xét; đáp án chỉ khi đã mở. |
| CL08 Giao bài và nhận xét của gia sư | P1 | UC12 FR-33, FR-34 | Giao bản đã duyệt, đặt hạn, xem từng bản nộp, nhận xét và đóng bài. Chỉ chủ động mở đáp án sau đóng; đến hạn không tự mở; sửa đề tạo phiên bản và giữ quan hệ bài nộp cũ. |

## 9 Màn hình học sinh và web công khai

Học sinh chỉ dùng sau lời mời hợp lệ ở P1 và dùng lại màn hình lớp trong quyền. Web công khai phục vụ giới thiệu/hướng dẫn, không thay thế ứng dụng nghiệp vụ phụ huynh hoặc gia sư.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| HS01 Trang học tập của học sinh | P1 | UC02, UC07, UC11, UC12 FR-19, FR-29, FR-33, FR-34, FR-36 | Chỉ lớp và dữ liệu của mình sau nhận lời mời; đến CL02, CL06, CL07. Không thấy nhu cầu/đề nghị, nháp hoặc quyền quản lý phụ huynh; thu hồi liên kết chặn quyền ở yêu cầu kế tiếp. |
| WEB01 Giới thiệu TutorBridge | P0 | PRD mục 2.2 | Giá trị, phân khúc R0 và cách tham gia; nói đúng trạng thái thí điểm. Dẫn tới hướng dẫn/đăng ký phù hợp kênh; không công khai học sinh, lịch lớp, học liệu hay tuyên bố hiệu quả chưa đo. |
| WEB02 Hướng dẫn và chính sách | P0 | UC01, UC14, UC16 FR-01, FR-40, FR-42 | Hướng dẫn theo vai trò, nguồn hỗ trợ, điều kiện tham gia, quyền dữ liệu và lưu/xóa. Nội dung dịch vụ cần rà trước thí điểm; không nhận thanh toán hoặc hứa SLA ngoài phạm vi. |

## 9.1 Điều kiện truy cập của học sinh

AUTH07 liên kết đúng người được phụ huynh mời. HS01 chỉ đưa tới lịch, nội dung công bố và bài tập của mình; học sinh không tự tìm/ghép lớp, đổi thỏa thuận, xác nhận thay phụ huynh hoặc duyệt bài soạn. Trước P1, phụ huynh thao tác thay bằng chính phiên phụ huynh và hệ thống lưu người thao tác.

## 9.2 Nội dung công khai và riêng tư

Thông tin công khai chỉ theo phạm vi [S1]. Không có danh sách học sinh, lịch riêng, học liệu hoặc minh chứng gốc trên web giới thiệu. Khi đường dẫn yêu cầu đăng nhập hay quyền đã mất, hiển thị thông báo phù hợp mà không tiết lộ đối tượng ngoài quyền. Hồ sơ gia sư trong PH07 theo phạm vi công khai được duyệt, không đồng nghĩa mọi dữ liệu hồ sơ là công khai.

## 10 Màn hình quản trị

Phân tách quyền xác minh, hỗ trợ, cấu hình và kiểm toán dù một người có thể được giao nhiều nhiệm vụ. Giao diện chỉ thể hiện quyền hiện có; API vẫn phải kiểm tra. Quyền học liệu tạm thời tối đa 24 giờ phải được cấp đúng vụ việc.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| AD01 Đăng nhập và xác thực quản trị | P0 | UC01, UC14 FR-02, FR-39, FR-40 | Tài khoản được cấp riêng, thêm yếu tố xác thực trước thí điểm; không có tự đăng ký. Hiện phiên hết hạn/khóa; không để người dùng thường chọn vai trò quản trị. |
| AD02 Tổng quan vận hành và đánh giá | P0 | UC13, UC14 FR-38, FR-40 | Số liệu tổng hợp trong quyền, kỳ và mẫu số, độ đầy đủ sự kiện; tình trạng tác vụ/hỗ trợ. Không đưa nội dung học hoặc thông tin trẻ lên bảng tổng quan; không biến dữ liệu thiếu thành tỷ lệ thành công. |
| AD03 Hàng chờ xác minh gia sư | P0 | UC03, UC14 FR-07, FR-40 | Chỉ hồ sơ được phân công; lọc trạng thái, xem phiên bản và người xử lý. Nhóm hỗ trợ không mặc định được đọc minh chứng của người xác minh. |
| AD04 Thẩm định hồ sơ và minh chứng | P0 | UC03, UC14 FR-07, FR-40 | Người xác minh mở đúng minh chứng, ghi phạm vi/lý do/kết quả và yêu cầu bổ sung. Không tự duyệt hồ sơ mình; lưu lịch sử, không dùng nhãn bảo đảm toàn diện. |
| AD05 Tài khoản và hồ sơ bị hạn chế | P0 | UC01, UC14 FR-39, FR-40 | Khóa tài khoản/ẩn hồ sơ theo quyền và lý do; thu hồi phiên, loại khỏi matching. Không tự xóa lớp, xác nhận thay các bên hoặc ghi đè lịch sử; cảnh báo lớp bị ảnh hưởng. |
| AD06 Danh sách và phân công vụ việc | P0 | UC14 FR-40 | Phân loại, người xử lý, trạng thái và mức độ; chỉ dữ liệu cần cho tiếp nhận. Có mới/đang xử lý/chờ bổ sung/giải quyết/đóng; mở lại giữ lịch sử. |
| AD07 Xử lý vụ việc và quyền tạm | P0 | UC14 FR-40 | Xem dữ liệu theo vụ việc, xin quyền học liệu tối đa 24 giờ với lý do và người cho phép. Chưa cấp/hết hạn/thu hồi thì chặn đọc. Ghi kết quả, phản hồi và audit; không tự chốt lịch thay hai bên. |
| AD08 Cấu hình AI và hạn mức | P0 | UC11, UC14 FR-26, FR-40 | Người được phân quyền xem model/prompt/cấu hình phiên bản, trần chi, quota và hàng chờ; bật/tắt có audit. Chưa có trần tiền thì AI tắt. Không hiển thị hay lưu khóa bí mật trong biểu mẫu/log. |
| AD09 Xử lý thu hồi và xóa dữ liệu | P0 | UC16 FR-42 | Xác minh phạm vi, chặn quyền, theo dõi kho/chỉ mục/cache/dẫn xuất và kết quả xóa. Nếu giữ tối thiểu vì tranh chấp phải có lý do/ngày rà lại. Theo dõi áp nhật ký xóa khi khôi phục; không báo xong khi còn truy cập. |
| AD10 Nhật ký kiểm toán | P0 | UC14, UC16 FR-40, FR-42 | Chỉ người có quyền kiểm toán; lọc người/thao tác/đối tượng/phiên bản/kết quả/mã truy vết. Không có mật khẩu, khóa hoặc học liệu nguyên văn; ghi dấu vết duyệt/quyền/công bố/xóa. |

## 11 Luồng cốt lõi cần dựng prototype

Dựng trước wireframe đủ để thử tác vụ. Mỗi luồng có điểm vào, kết quả cuối, nhánh ngoại lệ và đường quay lại; dữ liệu mô phỏng phải có nhãn, không ghi thành kết quả AI hoặc kết quả nghiên cứu thực tế.

### F01 Đăng ký và hoàn thiện hồ sơ

AUTH01 → AUTH02 → AUTH03; phụ huynh đến PH02, gia sư đến GS02 → GS03 → GS04; quản trị AD03 → AD04.

Điểm cần kiểm tra: Gia sư chỉ xuất hiện khi bản công khai được duyệt và đủ điều kiện; hồ sơ chờ/bị khóa có trạng thái riêng. Lời mời học sinh đi AUTH07, không qua đăng ký tự do.

### F02 Từ nhu cầu đến lớp đã chốt

PH02 → PH03 → PH04 → PH05 → PH06 → PH07 → PH08 → PH09; gia sư GS05; hai bên đến CL01 → CL02.

Điểm cần kiểm tra: Thử thiếu dữ liệu, rỗng, AI lỗi/chọn B, phiên bản cũ, đề nghị hết hạn và một buổi xung đột. Chỉ báo chốt sau khi cả lớp và chuỗi buổi được ghi thành công; không coi đề nghị là giữ chỗ.

### F03 Đổi lịch hủy học bù và kết thúc

CL02 → CL03 → bên kia phản hồi → CL02; kết thúc tại CL01. COM01 dẫn tới đúng bản cần phản hồi.

Điểm cần kiểm tra: Đổi chờ giữ lịch cũ; xung đột không mất lịch. Hủy có lý do; buổi bù liên kết riêng. Kết thúc giải phóng lịch tương lai và chặn soạn mới; không tự đánh dấu buổi vắng là hoàn thành.

## 11 Luồng nội dung và hỗ trợ tiếp theo

### F04 Học liệu đến bài soạn công bố

GS06 → GS07 → GS08 → GS09 → GS10 → CL06.

Điểm cần kiểm tra: Thử nguồn xử lý lỗi/chưa kiểm tra/thu hồi, AI tắt hoặc quá quota, bản nháp đã sửa và cảnh báo. Chỉ gia sư có quyền duyệt; phụ huynh/học sinh không nhận nháp hoặc đáp án chưa mở. Soạn thủ công vẫn đi qua duyệt.

### F05 Sau buổi học và tiến trình

CL04 → COM01 → CL05 → PH10; P1: CL08 → CL07 → CL08 → PH10; học sinh từ HS01.

Điểm cần kiểm tra: Tách xác nhận chuyên môn với tham gia. Thử chờ quá 72 giờ, bất đồng, vắng, nộp muộn/nộp lại/đóng bài và đáp án chưa mở. Số liệu có kỳ, mẫu số và nguồn; không suy luận học lực.

### F06 Hỗ trợ thu hồi và xóa dữ liệu

COM02 → COM03 → AD06 → AD07; COM04 → AD09; kiểm toán AD10.

Điểm cần kiểm tra: Người quản lý dữ liệu quyết định quyền tạm theo vụ việc; hết hạn/thu hồi phải chặn đọc. Xóa nguồn xử lý cả dẫn xuất; yêu cầu có mã và trạng thái thực. Mở đường dẫn cũ không làm sống lại quyền.

## 11.1 Đợt thử ưu tiên

Ưu tiên F02 và F04 vì trực tiếp kiểm chứng tìm lớp và chuẩn bị bài; sau đó kiểm tra F03 và F05 để bảo đảm chu trình tiếp tục dùng được. F01 và F06 là luồng hỗ trợ bắt buộc. Chọn người thử theo vai trò, dùng kịch bản tác vụ phù hợp, ghi có/không hoàn thành và mức trợ giúp; không cộng các tác vụ khác nhau thành một tỷ lệ không rõ mẫu số.

## 12 Truy vết và điều kiện bàn giao

Bảng sau là chỉ mục đối chiếu ở cấp UC. Liên kết FR cụ thể nằm tại từng màn hình. FR-37 áp dụng toàn bộ; FR-38 gồm sự kiện xuyên luồng được tổng hợp tại UC13. WEB01–02 còn thực hiện phạm vi web công khai ở PRD mục 2.2.

| UC | FR chính | Màn hình tiếp nhận |
|---|---|---|
| UC01 | FR-01, FR-02, FR-39 | AUTH01, AUTH02, AUTH03, AUTH04, AUTH05, AUTH06, AD01, AD05, WEB02 |
| UC02 | FR-03, FR-36 | AUTH07, COM04, PH01, PH02, HS01 |
| UC03 | FR-05, FR-06, FR-07 | PH07, GS01, GS02, GS03, AD03, AD04 |
| UC04 | FR-04, FR-18 | PH03, PH04, PH05, CL01 |
| UC05 | FR-09, FR-10, FR-11, FR-12, FR-13, FR-14 | PH06, PH07 |
| UC06 | FR-15, FR-16, FR-17 | PH08, PH09, GS01, GS05 |
| UC07 | FR-08, FR-17, FR-19 | PH01, PH04, GS01, GS04, CL01, CL02, HS01 |
| UC08 | FR-20, FR-43 | CL01, CL02, CL03 |
| UC09 | FR-31, FR-32 | PH01, GS01, CL04, CL05 |
| UC10 | FR-21, FR-22, FR-23 | GS06, GS07 |
| UC11 | FR-24, FR-25, FR-26, FR-27, FR-28, FR-29, FR-30 | GS08, GS09, GS10, CL06, HS01, AD08 |
| UC12 | FR-33, FR-34 | CL07, CL08, HS01 |
| UC13 | FR-35, FR-38 | PH10, AD02 |
| UC14 | FR-40 | COM02, COM03, AD01, AD02, AD03, AD04, AD05, AD06, AD07, AD08, AD10, WEB02 |
| UC15 | FR-41 | COM01, PH01, GS01 |
| UC16 | FR-42 | COM04, GS07, AD09, AD10, WEB02 |

## 12.1 Nội dung cần thiết kế tiếp

Từ danh mục này, Đức Huy dựng wireframe/prototype; Dương Đính mô tả kiến trúc, quyền và mô hình dữ liệu sơ bộ. Hai bên rà mỗi giao dịch tạo/đổi/hủy/duyệt/xóa, thống nhất hợp đồng đầu vào/đầu ra và đối chiếu NFR. Đây là bước tiếp theo của G3, chưa phải bắt đầu toàn bộ chức năng G5.

## 13 Tiêu chí hoàn thành G3 và việc trước mắt

| Mã | Tiêu chí và bằng chứng phải có |
|---|---|
| G3-AC01 | Phạm vi G2, phân công, giờ làm, ngân sách và mốc đã được hai thành viên xác nhận; thay đổi có kiểm soát. |
| G3-AC02 | Prototype thể hiện các luồng cốt lõi, trạng thái lỗi/quyền/xung đột và nhánh P1; mỗi màn hình truy được FR/UC. |
| G3-AC03 | Theo kế hoạch [S3], đề xuất thử ít nhất 5 người, ít nhất 4 hoàn thành luồng chính không cần hướng dẫn từng bước. Ghi vai trò, tác vụ và mẫu số; mẫu nhỏ phục vụ khám phá. |
| G3-AC04 | Có kiến trúc/ADR, ERD/từ điển vật lý, ma trận quyền và trạng thái, OpenAPI/lỗi; các bất biến lịch/phiên bản/thu hồi được thiết kế nhất quán. |
| G3-AC05 | Có kết quả thử rủi ro lớn, phương án AI/nguồn/quota/chi phí, threat model, kế hoạch migration/backup/restore/CI và chiến lược kiểm thử FR/NFR/AT. |
| G3-AC06 | Backlog được ước lượng theo nguồn lực thực tế; có người làm/rà soát, phụ thuộc, điều kiện hoàn thành và hồ sơ bàn giao G4. Kết luận chung có bằng chứng. |

## 13.1 Việc thực hiện ngay

Dương Đính rà bảng xác nhận G2 và ghi năng lực thời gian/ngân sách dự kiến. Đức Huy rà D01–D08, phân công và danh mục màn hình, ghi đồng ý hoặc đề nghị sửa cùng năng lực thời gian. Tiếp theo, Đức Huy dựng wireframe F02 và F04; Dương Đính lập danh sách thực thể, quyền và giao dịch của hai luồng để rà cùng nhau.

## 13.2 Quản lý phiên bản

Phiên bản 0.1 ngày 29/10/2026: phát hành hồ sơ khởi động, phân công, 52 màn hình và 6 luồng prototype. Màn hình có thể gộp/tách khi giữ đủ hành vi và cập nhật bảng truy vết; thay đổi yêu cầu phải cập nhật [S1]. Khi hoàn tất từng đầu ra G3, bổ sung phiên bản, người rà soát và bằng chứng thực tế.
