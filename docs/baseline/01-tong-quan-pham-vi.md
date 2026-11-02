# Tổng quan và phạm vi

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).
> **Lưu ý:** phân công ở mục 1.3 và D08 đã thay bằng CR-001; AI theo CR-002.

## 1 Tổng quan sản phẩm

## 1.1 Vấn đề và giá trị cần kiểm chứng

TutorBridge phục vụ phụ huynh cần tìm và theo dõi gia sư, gia sư cần tiếp nhận lớp và chuẩn bị bài theo từng học sinh. Hai mô đun cần kiểm chứng là xếp hạng gia sư sau lọc điều kiện bắt buộc và soạn nháp có nguồn từ học liệu riêng của lớp. Con người lựa chọn gia sư, xác nhận thỏa thuận và duyệt nội dung chuyên môn.

| Mã | Giả thuyết giá trị | Bằng chứng cần có |
|---|---|---|
| G-01 | Chuẩn hóa và gợi ý giảm công chọn gia sư | Trải nghiệm tìm gần nhất; tác vụ đối chứng; mức phù hợp danh sách. |
| G-02 | Soạn nháp có nguồn giảm tổng thời gian chuẩn bị | Đo cả đọc nguồn, tạo, sửa và duyệt; chấm chất lượng chuyên môn. |
| G-03 | Mục tiêu và nhận xét xuyên suốt giúp dùng lại dữ liệu lớp | Đo nhập lại, buổi có nhật ký đủ xác nhận, tác vụ quay lại. |
| G-04 | Quyền và phiên bản giúp kiểm tra được quá trình | Kiểm thử truy cập, nguồn, duyệt và dấu vết thay đổi. |
| G-05 | Thu bằng chứng để quyết định tiếp tục hoặc thu hẹp | Phỏng vấn, đối chứng, chi phí và phản hồi sử dụng lại. |

## 1.2 Giới hạn bằng chứng và mục tiêu thí điểm

Hai nguồn G2 và báo cáo G0 G1 chưa kèm kết quả phỏng vấn hoặc số đo AI. Kế hoạch khám phá tối thiểu 10 phụ huynh và 10 gia sư còn là việc phải thực hiện. Phân khúc Toán 8 và kênh Android là phạm vi được chọn để thiết kế thí điểm, cần kiểm tra bằng người dùng thực trước khi mở rộng.

R0 ưu tiên chứng minh chu trình hoạt động, dữ liệu đúng quyền và phương pháp đo công bằng. Chưa cam kết tăng điểm học tập, tỷ lệ ghép lớp, doanh thu hoặc phục vụ hàng nghìn người dùng đồng thời. NFR quy định cách kiểm thử để thiết kế mở rộng có căn cứ.

## 1.3 Các bên và trách nhiệm sản phẩm

Dương Đính phụ trách cơ sở nghiệp vụ, lịch, quyền và vận hành; Đức Huy phụ trách matching, học liệu, AI và trải nghiệm mobile. Cả hai rà soát chéo phần còn lại. Gia sư được mời chịu trách nhiệm duyệt chuyên môn; nhóm cần chỉ định người thẩm định Toán độc lập trước khi đánh giá. Phân công chi tiết và năng lực thời gian cần được xác nhận trong GATE01.

## 2 Phạm vi và quyết định hợp nhất

## 2.1 Phạm vi các lần sử dụng

| Mức | Nội dung | Điều kiện |
|---|---|---|
| R0a | Bản kiểm chứng nội bộ: hồ sơ, nhu cầu, matching, lớp/lịch, nguồn, soạn và duyệt; dữ liệu được phép hoặc mô phỏng có nhãn. | P0 theo lát cắt; không gọi là thí điểm đầy đủ hoặc kết quả thị trường. |
| R0 | Thí điểm kín 1–1 Toán 8 đại số, trực tuyến, tiếng Việt; toàn bộ P0 và P1; miễn phí nền tảng. | Có nghiệm thu sản phẩm, nguồn hợp lệ, phụ huynh giám sát và các GATE đã đóng trước dùng thật. |
| R1 | Phát hành giới hạn sau R0; cân nhắc iOS/web gia sư và mô hình phí. | Có thay đổi phạm vi, kiểm chứng giá trị, vận hành và ngân sách riêng. |
| R2 | Thêm môn/khối, trực tiếp, OCR, rotation, chat, push/email nghiệp vụ, đánh giá công khai, AI báo cáo và offline. | Mỗi nhánh có yêu cầu/kiểm thử/quyền riêng; chưa tự động đưa vào R1. |

## 2.2 Chức năng theo kênh trong R0

| Kênh | Phạm vi bắt buộc |
|---|---|
| Android phụ huynh | Hồ sơ con; nhu cầu; gợi ý; đề nghị; lịch; xác nhận buổi; kế hoạch công bố; bài nộp thay; tiến trình; hỗ trợ/quyền. |
| Android gia sư | Hồ sơ/minh chứng/giá; lịch; đề nghị; lớp; học liệu; soạn và mở nguồn; duyệt; nhật ký; bài tập/nhận xét. |
| Android học sinh P1 | Nhận lời mời; xem lịch/nội dung/bài tập của mình; nộp và đọc nhận xét. Không tự đăng ký tự liên kết. |
| Web quản trị | Xác minh được giao; khóa hồ sơ/tài khoản; vụ việc; quyền tạm; cấu hình hạn mức; số đo và nhật ký trong quyền. |
| Web công khai | Giới thiệu và hướng dẫn tham gia; không công khai hồ sơ học sinh, lịch lớp hoặc học liệu. |

R0 không có cuộc gọi/video tích hợp, bản đồ/định tuyến, lớp nhóm, thanh toán/hoàn phí, tự chấm bài, sửa học lực bằng AI hoặc hỗ trợ offline hoàn chỉnh. Công cụ học trực tuyến được hai bên sử dụng riêng; đường dẫn buổi nếu lưu chỉ hiển thị thành viên lớp.

## 2.3 Các quyết định phạm vi cơ sở

Bảng 1 Quyết định hợp nhất D01 đến D04

| Mã | Nội dung được chọn | Căn cứ và xử lý khác biệt |
|---|---|---|
| D01 | Phân khúc R0: học kèm 1–1 Toán 8, đại số, tiếng Việt, trực tuyến; chu kỳ lịch 28 ngày. Trực tiếp và môn/khối khác P2. | Đức Huy mục 2.2; thu hẹp trực tuyến để kiểm chứng trước, chưa là kết quả khảo sát. |
| D02 | Kênh Android cho phụ huynh/gia sư bằng React Native; web quản trị và giới thiệu. Web gia sư và phát hành iOS ở R1 sau đánh giá nhu cầu. | Chọn phương án mobile của K, giữ định hướng web/mobile; không triển khai đồng thời mọi giao diện. |
| D03 | Tài khoản Email/mật khẩu và xác minh liên hệ; một tài khoản một vai trò người dùng trong R0; hồ sơ con do phụ huynh quản lý. Học sinh đăng nhập bằng lời mời ở P1. | K D03; [S2] Q01, Q02 được thay bằng UC01, UC02. Tham chiếu quyết định được chuẩn hóa tại UC01 và UC02. |
| D04 | Thu phí R0 miễn phí dịch vụ nền tảng, không xử lý tiền học. Giá gia sư là điều kiện thỏa thuận; mô hình phí hai bên giữ trong backlog sau khảo sát. | Giữ phương án Dương Đính từ G01, hoãn thu tiền theo Đức Huy; không phát sinh tích hợp thanh toán. |

Các quyết định D xác lập phương án cơ sở của bản hợp nhất, chờ hai thành viên xác nhận. Trường hợp điều chỉnh phân khúc, kênh hoặc mô hình thu phí, nhóm rà soát tác động đối với luồng nghiệp vụ, quyền dữ liệu, chi phí và tiêu chí phát hành trước khi cập nhật phạm vi cơ sở.

## 2.4 Quản lý tham chiếu và truy vết quyết định

Nhật ký D01–D08 là danh mục quyết định dùng thống nhất trong hồ sơ. Các tham chiếu Q của tài liệu nguồn được chuyển sang quy tắc và yêu cầu có thể truy vết: phạm vi và kênh ở D01–D02; tài khoản ở D03 và UC01–UC02; hồ sơ ở UC03; giá và lịch ở BR02–BR06; mục tiêu và xác nhận ở UC04, UC09; học liệu và dữ liệu ở UC10, UC16, BR08; AI ở UC11 và NFR14; đánh giá ở mục 10.

## 2.5 Các quyết định nghiệp vụ và chất lượng

Bảng 2 Quyết định hợp nhất D05 đến D08

| Mã | Nội dung được chọn | Căn cứ và xử lý khác biệt |
|---|---|---|
| D05 | Lịch và buổi Giá VND/60 phút; phiên 60/90/120 phút, 1–3 buổi/tuần; xác nhận chuỗi hữu hạn. Đổi/hủy và phụ huynh xác nhận từng buổi là P0. | Khắc phục khác biệt P1 của Đức Huy với K FR08/FR09; chuyên môn và tham gia có xác nhận riêng. |
| D06 | Matching và AI AI soạn bài P0, có soạn thủ công; S và B ở BR03 là quy tắc v1 để kiểm chứng. PDF có văn bản/DOCX, người duyệt là gia sư. | Theo trọng tâm PRD [S2]; thay mức S của K bằng P0, không coi trọng số là tối ưu đã chứng minh. |
| D07 | Quyền và dữ liệu Một phụ huynh/hồ sơ ở R0; quyền vai trò kèm quan hệ/trạng thái. Vòng đời/xóa/dẫn xuất theo BR08; không mặc định quản trị đọc học liệu. | Bổ sung quy tắc còn mở của cả hai nguồn; chính sách với người dùng thật cần rà trước thí điểm. |
| D08 | Nguồn lực và chất lượng NFR01–16 là mục tiêu thiết kế/kiểm thử. Dương Đính chủ trì nghiệp vụ/backend/vận hành; Đức Huy chủ trì AI/học liệu/mobile; rà soát chéo. | Phân công cơ sở để chia backlog; giờ/tuần, ngân sách và ngày giao chưa được xác nhận, theo GATE01. |

## 2.6 Những thay đổi ưu tiên cần xác nhận

AI soạn bài chuyển từ S của K thành P0 theo trọng tâm PRD [S2]. Đổi/hủy và phụ huynh xác nhận buổi chuyển từ P1 của Đức Huy thành P0 để lịch và số buổi có thể vận hành đúng. Bài tập và đăng nhập học sinh giữ P1; thông báo trong hệ thống, hỗ trợ và xóa dữ liệu bổ sung P0 từ K. Phần dài hạn được giữ ở P2, không xóa khỏi tầm nhìn sản phẩm.
