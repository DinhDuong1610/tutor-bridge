# Danh mục màn hình theo vai trò

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 01 mục 4–10; 52 mã màn hình

### Quy ước danh mục và điều hướng

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

### Trạng thái chung bắt buộc

Mỗi màn hình có tải/chờ, rỗng, lỗi trường, lỗi hệ thống, hết phiên và không còn quyền khi áp dụng. Tác vụ ghi có đang gửi, đã xác nhận, chưa rõ kết quả và tra cứu/thử lại an toàn; không chỉ khóa nút để chống lặp. Không hiển thị thành công trước xác nhận máy chủ. FR-37 áp dụng cho toàn bộ danh mục; FR-38 đo sự kiện liên quan theo UC13.

Hiển thị con/lớp đang chọn, ngày giờ Việt Nam dạng 24 giờ và đơn vị tiền rõ. Không chỉ dùng màu để phân biệt trạng thái; nhãn trợ năng, focus/bàn phím trên web và phóng chữ 200% phải giữ tác vụ chính. Không đặt dữ liệu ngoài quyền vào payload, cache hoặc giao diện rồi chỉ ẩn bằng CSS.

Truy vết chất lượng: NFR01–03 cho quyền, nguồn và thu hồi; NFR04 cho ghi đồng thời/thử lại; NFR05 cho lỗi AI/nháp; NFR06–08 cho dữ liệu, nội dung không đáng tin và bí mật; NFR09 cho audit; NFR10 cho độ trễ nghiệp vụ/thông báo; NFR11 cho xóa/khôi phục; NFR12 cho trợ năng; NFR13–14 cho tác vụ/hạn mức AI; NFR15–16 cho tương thích, theo dõi và phát hành. Các thiết kế API, dữ liệu và vận hành tiếp theo phải cụ thể hóa cách thực hiện và kiểm chứng các yêu cầu này.

### Màn hình tài khoản và chức năng chung

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

### Màn hình phụ huynh

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

### Màn hình gia sư

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

### Màn hình lớp và học tập

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

### Màn hình học sinh và web công khai

Học sinh chỉ dùng sau lời mời hợp lệ ở P1 và dùng lại màn hình lớp trong quyền. Web công khai phục vụ giới thiệu/hướng dẫn, không thay thế ứng dụng nghiệp vụ phụ huynh hoặc gia sư.

| Mã và màn hình | Mức | Truy vết | Nội dung và trạng thái chính |
|---|---|---|---|
| HS01 Trang học tập của học sinh | P1 | UC02, UC07, UC11, UC12 FR-19, FR-29, FR-33, FR-34, FR-36 | Chỉ lớp và dữ liệu của mình sau nhận lời mời; đến CL02, CL06, CL07. Không thấy nhu cầu/đề nghị, nháp hoặc quyền quản lý phụ huynh; thu hồi liên kết chặn quyền ở yêu cầu kế tiếp. |
| WEB01 Giới thiệu TutorBridge | P0 | PRD mục 2.2 | Giá trị, phân khúc R0 và cách tham gia; nói đúng trạng thái thí điểm. Dẫn tới hướng dẫn/đăng ký phù hợp kênh; không công khai học sinh, lịch lớp, học liệu hay tuyên bố hiệu quả chưa đo. |
| WEB02 Hướng dẫn và chính sách | P0 | UC01, UC14, UC16 FR-01, FR-40, FR-42 | Hướng dẫn theo vai trò, nguồn hỗ trợ, điều kiện tham gia, quyền dữ liệu và lưu/xóa. Nội dung dịch vụ cần rà trước thí điểm; không nhận thanh toán hoặc hứa SLA ngoài phạm vi. |

### Điều kiện truy cập của học sinh

AUTH07 liên kết đúng người được phụ huynh mời. HS01 chỉ đưa tới lịch, nội dung công bố và bài tập của mình; học sinh không tự tìm/ghép lớp, đổi thỏa thuận, xác nhận thay phụ huynh hoặc duyệt bài soạn. Trước P1, phụ huynh thao tác thay bằng chính phiên phụ huynh và hệ thống lưu người thao tác.

### Nội dung công khai và riêng tư

Thông tin công khai chỉ theo phạm vi [S1]. Không có danh sách học sinh, lịch riêng, học liệu hoặc minh chứng gốc trên web giới thiệu. Khi đường dẫn yêu cầu đăng nhập hay quyền đã mất, hiển thị thông báo phù hợp mà không tiết lộ đối tượng ngoài quyền. Hồ sơ gia sư trong PH07 theo phạm vi công khai được duyệt, không đồng nghĩa mọi dữ liệu hồ sơ là công khai.

### Màn hình quản trị

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
