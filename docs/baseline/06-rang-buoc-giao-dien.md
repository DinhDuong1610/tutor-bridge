# Ràng buộc kỹ thuật và giao diện

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).
> **Lưu ý:** AI theo CR-002; OP-03 còn mở về cách Spring gọi worker.

## 8 Ràng buộc và bàn giao kỹ thuật

G2 chốt hành vi và điều kiện đạt; G3 quyết định kiến trúc, schema, API và cấu hình triển khai. Ưu tiên cấu trúc nghiệp vụ rõ, kiểm soát giao dịch/quyền, kiểm thử phần rủi ro và đo trên cấu hình cụ thể trước tăng độ phức tạp vận hành.

Bảng 9 Định hướng thiết kế G3

| Thành phần | Định hướng và đầu ra G3 |
|---|---|
| Backend | Java/Spring Boot cho tài khoản, quyền, matching nghiệp vụ, lớp/lịch và công bố; ranh giới module rõ. G3 chọn cơ chế transaction, khóa/ràng buộc lịch, idempotency và event thông báo. |
| Mobile và web | React Native cho Android R0; Next.js cho web giới thiệu/quản trị. Hợp đồng API độc lập kênh, lỗi/phiên bản thống nhất; không đưa khóa AI vào ứng dụng. |
| AI | Python/FastAPI theo định hướng PRD [S2]; chỉ backend được gọi tác vụ hợp lệ. G3 thử model embedding, nhà cung cấp LLM, schema đầu ra, truy xuất và bộ đánh giá trước cố định. |
| Dữ liệu và tệp | PostgreSQL, kho tệp riêng; pgvector là phương án cần xác minh bằng thử nghiệm. Phân quyền nguồn và lớp, migration/version, backup và quy trình xóa là bắt buộc. |
| Triển khai | Docker Compose phù hợp thử nghiệm ban đầu; môi trường phát triển/kiểm thử/thí điểm tách dữ liệu và bí mật. Có CI, health check, metrics, logs tối thiểu và runbook rollback/restore. |
| Mở rộng | Dùng NFR15 để thiết kế 2 API cùng dữ liệu chung và tác vụ tách tiến trình; chưa yêu cầu Kubernetes hoặc tách mọi module thành dịch vụ riêng. |

Các giá trị embedding cụ thể, model, chiều vector, số token, chunk/overlap và top-k trong PRD [S2] là điểm bắt đầu cho thử nghiệm G3. Không coi là ràng buộc ổn định chỉ vì xuất hiện trong đề cương. Thay model/prompt/cấu hình phải giữ phiên bản và đánh giá lại kết quả tương ứng.

## Hồ sơ phải có khi hoàn thành G3

Luồng UX và màn hình; sơ đồ kiến trúc và quyết định thiết kế; ERD/từ điển vật lý; hợp đồng API và lỗi; threat model theo quyền/AI; kế hoạch migration/backup; chiến lược kiểm thử; ước lượng backlog theo giờ thực tế và cấu hình chi phí. Mỗi phần liên kết mã FR/NFR/UC để không mất yêu cầu khi chuyển sang lập trình.

## 9 Yêu cầu giao diện

Bảng 10 Quy tắc giao diện dùng chung

| Nhóm màn hình | Kết quả và trạng thái bắt buộc |
|---|---|
| Tài khoản/hồ sơ | Xác minh/đăng nhập/khôi phục; chọn con luôn rõ; danh sách nháp và hồ sơ chưa đủ dữ liệu; không trộn phần công khai với dữ liệu riêng. |
| Tìm và chọn | Nhập, xem lại/xác nhận, kết quả và so sánh; phân biệt thiếu dữ liệu/rỗng/lỗi; gắn nhãn AI hoặc danh sách lọc; giá/đơn vị/lịch chung và phiên bản rõ. |
| Đề nghị/lịch | Bản điều kiện, hạn phản hồi, đã gửi và kết quả chốt; lịch đã đặt so với rảnh; xung đột có đường chọn lại; đổi chờ không hiển thị như đã đổi. |
| Học liệu/soạn | Đúng lớp/nguồn đang chọn; trạng thái tệp/AI; mở nguồn cạnh nội dung; cảnh báo thiếu căn cứ; lưu nháp, soạn thủ công, duyệt và công bố là tác vụ rõ. |
| Sau buổi/bài tập | Nhật ký chuyên môn khác xác nhận tham gia; chờ/tranh chấp/vắng rõ; bài nộp đúng người, hạn và phiên bản; đáp án mở riêng. |
| Tiến trình/hỗ trợ | Kỳ/mẫu số/dữ liệu thiếu; mở được nguồn; trạng thái vụ việc/xóa; giải thích quyền hết hiệu lực không lộ dữ liệu ngoài quyền. |

Giao diện tiếng Việt; ngày giờ 24 giờ và đơn vị tiền rõ. Mỗi form có lỗi cạnh trường, giữ dữ liệu hợp lệ đã nhập, trạng thái đang gửi và cách thử lại. Tác vụ lâu không hiển thị phần trăm giả. Chỉ báo đã lưu khi máy chủ xác nhận; khi chưa rõ kết quả hiển thị đang kiểm tra trạng thái.

Không chỉ dùng màu để phân biệt tự khai/xác minh, AI/thủ công, nháp/duyệt và thành công/lỗi. Trợ năng theo NFR12. G3 chọn phiên bản Android và trình duyệt được hỗ trợ bằng kiểm tra thiết bị thí điểm; trước phát hành phải ghi danh sách và chạy toàn bộ luồng trên mỗi nhóm được công bố hỗ trợ.

## Mã lỗi nghiệp vụ tối thiểu

Cần có nhóm lỗi: chưa xác thực, không có quyền/không còn khả dụng, dữ liệu không hợp lệ, phiên bản cũ, lịch xung đột, đề nghị hết hạn, thao tác đang xử lý/đã xử lý, tệp không hỗ trợ, nguồn chưa sẵn sàng, nguồn bị thu hồi, AI lỗi và vượt hạn mức. API cụ thể thuộc G3; thông báo người dùng không tiết lộ đối tượng ngoài quyền.
