# Quy tắc nghiệp vụ BR01–BR08

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).

## 3 Quy tắc nghiệp vụ

## 3.1 Điều kiện lọc học phí và xếp hạng

BR01 Điều kiện cứng. Gia sư phải có hồ sơ công khai đã duyệt, tài khoản hoạt động, đúng môn/khối/hình thức; giá đáp ứng ngân sách; có lịch đủ thời lượng và số buổi mỗi tuần cho chu kỳ 28 ngày. Thiếu dữ liệu để xác nhận bất kỳ điều kiện cứng nào đều không coi là đạt. Thí điểm chỉ trực tuyến, không dùng vị trí để loại; khi hỗ trợ trực tiếp phải thêm điều kiện khu vực và khoảng đệm bằng thay đổi phạm vi.

BR02 Đơn vị tiền. Giá P là số nguyên dương VND/60 phút. Giá một buổi D phút bằng làm tròn lên của P × D / 60 đến một đồng; ngân sách nhu cầu là trần VND/buổi cùng D. So sánh giá buổi ≤ ngân sách. Màn hình hiển thị cả P, D và giá buổi; không cộng phí nền tảng trong R0. Đề nghị/lớp giữ ảnh chụp điều kiện giá đã chấp thuận.

BR03 Quy tắc xếp hạng v1. Sau lọc, S = 0,60 × s + 0,25 × e + 0,15 × a. s = max(0, min(1, (cos + 1) / 2)), với cos là độ tương đồng giữa mục tiêu tự do và mô tả chuyên môn cùng cấu hình embedding. e = min(m, 60)/60, m là số tháng kinh nghiệm liên quan tự khai hợp lệ. a là tỷ lệ các khung giờ ưu tiên có thể chứa đủ một buổi trong lịch chung, chia tổng khung ưu tiên; không khai ưu tiên thì a = 0,5 cho mọi ứng viên. Điểm không phải xác suất phù hợp hay tiến bộ.

S được làm tròn 6 chữ số thập phân để xếp. Hòa S thì lần lượt s giảm dần, e giảm dần, mã gia sư tăng dần. Luôn lưu đầu vào, điểm thành phần và phiên bản. Giá trị kinh nghiệm 0 hợp lệ; thiếu mô tả ngữ nghĩa cần bổ sung trước tham gia. Top N trả min(5, N), không lặp hồ sơ. Trọng số và chuẩn hóa là giả thuyết v1, phải kiểm chứng trên tập giữ riêng trước tuyên bố hiệu quả.

Phương án đối chứng B không dùng AI: lọc cùng điều kiện rồi B = 0,625 × e + 0,375 × a; hòa điểm theo e rồi mã gia sư. Khi embedding lỗi, chỉ dùng B nếu phụ huynh chọn xem danh sách lọc nghiệp vụ; hiển thị rõ không dùng xếp hạng AI và ghi sự kiện riêng. Không thay điều kiện cứng hoặc tính điểm thiếu như đã có embedding.

## 3.2 Lịch cam kết và thao tác lặp

BR04 Thời gian. Lưu thời điểm chuẩn và hiển thị theo Asia/Ho_Chi_Minh. Khoảng chiếm lịch là [bắt đầu, kết thúc). Phiên 60/90/120 phút, 1–3 buổi/tuần. Phụ huynh chọn ngày bắt đầu từ hôm nay đến 28 ngày tới; chu kỳ gồm 4 khối liên tiếp, mỗi khối 7 ngày kể từ ngày đó, đủ số buổi đã yêu cầu trong từng khối, tối đa 12 buổi. Chỉ đặt buổi tương lai; trừ ngoại lệ và lịch đặt, hợp nhất khung ưu tiên trùng trước tính điểm. Không giữ chỗ từ gợi ý hay đề nghị chờ.

BR05 Chốt và thay đổi. Phụ huynh gửi đủ điều kiện là một sự đồng ý; gia sư chỉ chấp nhận nguyên bản hoặc từ chối. Chốt nguyên tử cả lớp và chuỗi buổi sau kiểm tra lại học sinh/gia sư và phiên bản. Một nhu cầu chỉ tạo một lớp. Muốn đổi điều kiện phải gửi bản mới. Đề nghị hết hạn tại min(thời điểm gửi + 48 giờ, buổi đầu bắt đầu). Gia hạn chu kỳ cần đề nghị và đồng ý mới.

Đổi lịch giữ buổi cũ đến khi hai bên đồng ý và lịch mới được đặt thành công. Hủy buổi tương lai bởi một bên có lý do và thông báo sẽ giải phóng lịch, không phạt tiền ở R0. Đề nghị đổi chưa áp dụng không ngăn quyền hủy; hủy làm đề nghị đổi hết hiệu lực. Buổi đã qua chỉ hiệu chỉnh bản ghi, không viết lại thời gian kế hoạch. Vắng/học bù giữ liên kết và số liệu riêng.

BR06 Chống lặp và xung đột. Các lệnh gửi/chốt/đổi/hủy/nộp/duyệt/xóa mang mã thao tác trong phạm vi người dùng và loại lệnh. Trong 7 ngày, cùng mã/cùng nội dung trả kết quả cũ; cùng mã/khác nội dung bị từ chối. Ngoài thời hạn, ràng buộc nghiệp vụ vẫn ngăn lớp/buổi/đề nghị hiệu lực trùng. Kiểm tra này không chỉ dựa vào việc khóa nút gửi hoặc khóa trong bộ nhớ cục bộ.

Thay đổi đồng thời dùng phiên bản đối tượng để tránh ghi đè. Bản cũ nhận lỗi cần tải lại, không tự gộp điều kiện. Sau mất kết nối không xác định đã lưu, khách truy vấn bằng mã thao tác trước thử lại; không hiển thị thành công khi chưa xác nhận.

## 3.3 Liên hệ quyền nội dung và vòng đời dữ liệu

BR07 Phạm vi lớp. Trước chốt chỉ hiển thị tên hiển thị, chuyên môn, giá và lịch chung; không lộ email/liên hệ hoặc địa chỉ trẻ. Sau chốt, phụ huynh và gia sư có thể chủ động chia sẻ liên hệ của chính mình; không tự mở toàn bộ dữ liệu cá nhân. Mục tiêu lớp thay đổi cần hai bên đồng ý. Chuyên môn do gia sư duyệt; tham gia buổi do phụ huynh xác nhận riêng; không đồng nhất im lặng với đồng ý.

BR08 Vòng đời. Trong lớp hoạt động, chỉ truy cập nội dung theo cấp quyền. Khi kết thúc, chặn tạo mới/AI, thu hồi dùng học liệu gốc cho AI và quyền gia sư với hồ sơ con ngoài lớp; giữ quyền đọc nội dung đã công bố đúng phạm vi tối đa 90 ngày. Tài liệu gốc chỉ còn chủ sở hữu hoặc người được cấp quyền riêng; lịch rảnh tương lai không tiếp tục công khai cho lớp đã kết thúc.

Nháp không hoạt động 30 ngày được nhắc trước và xóa sau 7 ngày nữa nếu không giữ lại. Dữ liệu lớp/tệp/nội dung công bố được xóa hoặc ẩn danh sau 90 ngày từ kết thúc. Nhật ký kiểm toán tối thiểu giữ 180 ngày, không chứa học liệu nguyên văn. Backup mã hóa giữ 7 ngày; không dùng backup làm kho truy cập thông thường. Các thời hạn này là chính sách nội bộ cơ sở, cần rà phù hợp mục đích và trách nhiệm trước thí điểm thật.

Yêu cầu xóa hợp lệ chặn quyền ngay, hoàn tất xóa ở các kho đang hoạt động trong 7 ngày; backup hết thời hạn lưu trong tối đa 7 ngày tiếp theo chính sách lưu. Nếu phục hồi sớm phải áp danh sách xóa/thu hồi trước khi mở dịch vụ. Tranh chấp chỉ cho giữ phần tối thiểu có lý do, thời hạn rà lại tối đa 30 ngày và thông báo người liên quan; không tự giữ tất cả dữ liệu vô hạn.

Khi nguồn bị thu hồi, các bản nháp/kế hoạch/bài tập liên quan bị đánh dấu và tạm ẩn phần nội dung dẫn xuất đối với người đã mất quyền, không chỉ ẩn liên kết nguồn. Chủ thể còn quyền phải loại nội dung bị thu hồi hoặc cấp lại hợp lệ rồi duyệt phiên bản mới. Dấu vết phiên bản giữ mã và sự kiện tối thiểu, không giữ nội dung bí mật như một đường vòng truy cập. Bản đã tải ngoài hệ thống không thể được xóa cưỡng chế từ máy chủ.
