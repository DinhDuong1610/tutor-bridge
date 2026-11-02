# Truy vết yêu cầu

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).

## 12 Truy vết yêu cầu

H là PRD [S2]; K là hồ sơ G2 v0.1; G01 là báo cáo G0 G1 v1.1. FR chính thức giữ thứ tự H FR1–38; AC nằm trong UC tương ứng. Mọi hàng chịu FR-37 và các NFR theo quyền/lịch/nguồn liên quan.

Bảng 15 Truy vết FR 1 đến 15

| Mã | Yêu cầu | Mức | Đặc tả và AC | Nguồn |
|---|---|---|---|---|
| FR-01 | Đăng ký đúng vai trò | P0 | UC01 | H FR1 |
| FR-02 | Đăng nhập đăng xuất và phiên | P0 | UC01 | H FR2 |
| FR-03 | Hồ sơ riêng cho từng học sinh | P0 | UC02 | H FR3 |
| FR-04 | Nhu cầu được xác nhận theo phiên bản | P0 | UC04 | H FR4 |
| FR-05 | Hồ sơ chuyên môn gia sư | P0 | UC03 | H FR5 |
| FR-06 | Học phí và đơn vị so sánh | P0 | UC03 | H FR6 |
| FR-07 | Xác minh chuyên môn có lịch sử | P0 | UC03 | H FR7 |
| FR-08 | Lịch rảnh và ngoại lệ đã xác nhận | P0 | UC07 | H FR8 |
| FR-09 | Lọc toàn bộ điều kiện cứng | P0 | UC05 | H FR9 |
| FR-10 | Giao lịch đủ thời lượng và tần suất | P0 | UC05 | H FR10 |
| FR-11 | Xếp hạng có điểm thành phần | P0 | UC05 | H FR11 |
| FR-12 | Tối đa năm kết quả có căn cứ | P0 | UC05 | H FR12 |
| FR-13 | Lưu phiên bản và điểm xếp hạng | P0 | UC05 | H FR13 |
| FR-14 | Phân biệt rỗng thiếu dữ liệu và lỗi | P0 | UC05 | H FR14 |
| FR-15 | Gửi đề nghị chống trùng | P0 | UC06 | H FR15 |

Bảng 15 Truy vết FR 16 đến 30

| Mã | Yêu cầu | Mức | Đặc tả và AC | Nguồn |
|---|---|---|---|---|
| FR-16 | Phản hồi đúng bên và thỏa thuận | P0 | UC06 | H FR16 |
| FR-17 | Chốt lớp và lịch nguyên tử | P0 | UC06, UC07 | H FR17 |
| FR-18 | Chuyển và thay đổi mục tiêu lớp | P0 | UC04 | H FR18 |
| FR-19 | Xem lịch và nội dung đúng quyền | P0 | UC07 | H FR19 |
| FR-20 | Đổi lịch và học bù có xác nhận | P0 | UC08 | H FR20 |
| FR-21 | Tải học liệu có quyền sử dụng | P0 | UC10 | H FR21 |
| FR-22 | Xử lý và mở đúng nguồn | P0 | UC10 | H FR22 |
| FR-23 | Cấp và thu hồi quyền học liệu | P0 | UC10 | H FR23 |
| FR-24 | Xác nhận bối cảnh soạn bài | P0 | UC11 | H FR24 |
| FR-25 | Truy xuất trong phạm vi nguồn được phép | P0 | UC11 | H FR25 |
| FR-26 | Tạo kế hoạch và bài tập cấu trúc | P0 | UC11 | H FR26 |
| FR-27 | Kiểm tra nguồn cấu trúc và cảnh báo | P0 | UC11 | H FR27 |
| FR-28 | Dấu vết phiên bản tạo bài | P0 | UC11 | H FR28 |
| FR-29 | Sửa duyệt và công bố phiên bản | P0 | UC11 | H FR29 |
| FR-30 | Soạn thủ công khi AI lỗi | P0 | UC11 | H FR30 |

Bảng 15 Truy vết FR 31 đến 43

| Mã | Yêu cầu | Mức | Đặc tả và AC | Nguồn |
|---|---|---|---|---|
| FR-31 | Nhật ký chuyên môn được xác nhận | P0 | UC09 | H FR31 |
| FR-32 | Phụ huynh xác nhận tham gia | P0 | UC09 | H FR32 |
| FR-33 | Giao bài và kiểm soát đáp án | P1 | UC12 | H FR33 |
| FR-34 | Nộp và nhận xét có lịch sử | P1 | UC12 | H FR34 |
| FR-35 | Tiến trình từ dữ liệu nguồn | P0/P1 | UC13 | H FR35 |
| FR-36 | Liên kết và thu hồi quyền học sinh | P0/P1 | UC02 | H FR36 |
| FR-37 | Trạng thái lỗi và thử lại an toàn | P0 | Tất cả UC | H FR37 |
| FR-38 | Sự kiện phục vụ đánh giá | P0 | UC13 | H FR38 |
| FR-39 | Khôi phục thu hồi phiên và bảo vệ tài khoản | P0 | UC01 | K FR01 |
| FR-40 | Quản trị hỗ trợ và xử lý sự cố | P0 | UC14 | K FR15 |
| FR-41 | Thông báo nghiệp vụ trong hệ thống | P0 | UC15 | K FR14 |
| FR-42 | Yêu cầu thu hồi xóa và vòng đời dữ liệu | P0 | UC16 | K FR16 |
| FR-43 | Hủy vắng và kết thúc lớp | P0 | UC08 | K FR08; G01 mục 5–6 |

## 12.1 Đối chiếu hồ sơ khởi động và yêu cầu chất lượng

Bảng 16 Đối chiếu đầy đủ các nhóm K

| Nguồn K | Nơi tiếp nhận chính | Xử lý |
|---|---|---|
| FR01–FR04 | UC01–UC04; FR01–07,18,36,39 | Giữ; bổ sung liên kết và chính sách tài khoản. |
| FR05–FR08 | UC05–UC08; FR08–20,43 | Giữ lọc/lịch; thêm công thức, hạn và vòng đời cụ thể. |
| FR09 | UC09; FR31–32 | Giữ xác nhận phụ huynh; tách chuyên môn/tham gia. |
| FR10–FR12 | UC10–UC12; FR21–30,33–34 | AI từ S thành P0; bài tập P1 thuộc R0 đầy đủ. |
| FR13–FR16 | UC13–UC16; FR35,38,40–42 | Giữ báo cáo, thông báo, hỗ trợ, quyền/xóa; chat P2. |
| NFR01–NFR05 | NFR01–NFR16 | Mở rộng từ 5 nhóm thành 16 yêu cầu có mục tiêu/kiểm chứng. |
| D01–D08 | Mục 2.3–2.6 và GATE01–04 | Có lựa chọn cơ sở; còn xác nhận/điều kiện vận hành. |
| G01 mục 2–9 | G-01–G-05; D01–D08; UC01–UC16 | Giữ nhu cầu trong R0 hoặc P2, không biến giả thuyết thành chứng cứ. |

[S2] NFR01–NFR12 được giữ ở NFR01–NFR12 cùng tên chủ đề; chỉ tiêu hiệu năng tách thêm NFR13–16. [S2] US001–US028 được tiếp nhận lần lượt ở UC01, UC02, UC04, UC03, UC07, UC05, UC06, UC04, UC07, UC08, UC10, UC11, UC09, UC12, UC13 theo nhóm tương ứng; bảng FR và mô tả truy vết từng UC là căn cứ chi tiết.

Không còn tham chiếu phụ thuộc Q chưa định nghĩa trong yêu cầu thực hiện. FR-37 là yêu cầu trạng thái/lỗi/thử lại chung, không có một UC riêng. FR-38 đo sự kiện xuyên suốt nhưng được định nghĩa và nghiệm thu ở UC13. FR-36 tách P0 quyền quản lý con và P1 quyền đăng nhập học sinh.
