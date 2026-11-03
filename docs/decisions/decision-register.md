# Sổ quyết định TutorBridge

Bản Markdown của sổ TB HS 02 (42 mục), dùng làm nguồn tra cứu khi làm việc. Bản Word gốc giữ tại `docs/source-docx/`.

- **Cơ sở xác nhận:** ngày 02/11/2026, Đính cho biết cả Đính và Huy đã xác nhận nội dung các tài liệu. Sổ này ghi lại lời báo đó. Huy chưa tự ghi ý kiến vào sổ.
- **Huy rà Nhóm 1–5:** ngày 03/11/2026, Đính cho biết Huy đã xác nhận Nhóm 1–5, gồm các CR và ADR. Các mục ghi "chờ Huy" trong sổ này được coi là đã xác nhận từ ngày đó.
- **Giới hạn:** xác nhận tài liệu không tạo ra giá trị mà tài liệu còn để trống và không tự giải quyết chỗ hai tài liệu mâu thuẫn. Các trường hợp này ở mục 6.

| Nhãn | Ý nghĩa |
|---|---|
| Đã chốt | Dùng được ngay |
| Có CR | Đã chốt, phải đọc kèm CR trong [change-requests.md](change-requests.md) |
| Thiếu giá trị | Đồng ý nguyên tắc, còn thiếu tên, số hoặc lựa chọn |
| Mâu thuẫn | Hai mục đã chốt nói khác nhau, chưa dùng được |

## 1. Phạm vi và yêu cầu (G2)

| Mã | Chủ đề | Nội dung | Trạng thái |
|---|---|---|---|
| G2-D01 | Phân khúc R0 | 1–1 Toán 8 đại số, trực tuyến, tiếng Việt; chu kỳ lịch 28 ngày | Đã chốt |
| G2-D02 | Kênh sản phẩm | Android phụ huynh/gia sư/học sinh P1; web quản trị/giới thiệu; iOS/web gia sư xem xét ở R1 | Đã chốt |
| G2-D03 | Tài khoản | Email/mật khẩu; một vai trò người dùng; phụ huynh quản lý con; học sinh được mời riêng | Đã chốt |
| G2-D04 | Thu phí | R0 miễn phí nền tảng, không thanh toán tiền học; mô hình doanh thu vẫn là giả thuyết | Đã chốt |
| G2-D05 | Thời lượng và lịch | Giá VND/60 phút; buổi 60/90/120 phút; 1–3 buổi/tuần; đổi/hủy và xác nhận buổi P0 | Đã chốt |
| G2-D06 | AI và matching | Soạn có nguồn P0; có soạn thủ công; S/B để kiểm chứng; PDF có văn bản và DOCX | Đã chốt |
| G2-D07 | Quyền và vòng đời | Một phụ huynh/hồ sơ; quyền theo quan hệ/trạng thái; BR08; quản trị không mặc định đọc học liệu | Đã chốt |
| G2-D08 | Phân công và chất lượng | NFR là mục tiêu. Phần phân công đã thay bằng CR-001; giờ, tiền, mốc xem OP-01 | Có CR |

## 2. Nguyên tắc thiết kế (G3-DP)

| Mã | Chủ đề | Nội dung | Trạng thái |
|---|---|---|---|
| G3-DP01 | Toàn vẹn lịch và gửi lặp | Ràng buộc DB, khóa theo thứ tự ổn định; receipt 7 ngày; outbox cùng commit | Đã chốt |
| G3-DP02 | Quyền nguồn và dẫn xuất | Tách xem gốc/tải/dùng AI/dẫn xuất; dependency và access_epoch | Đã chốt |
| G3-DP03 | Tác vụ tệp và AI | Pipeline nhiều bước; lease; quota/budget; kết quả đến muộn không đè nháp | Đã chốt |
| G3-DP04 | Phiên bản và công bố | PlanVersion, pointer công bố, DTO nháp/đáp án/gốc riêng; kiểm quyền hiện hành | Đã chốt |

## 3. Lịch, học tập (G3-D35)

| Mã | Chủ đề | Nội dung | Trạng thái |
|---|---|---|---|
| G3-D35-01 | TTL học bù | min(thời điểm tạo + 48 giờ, giờ buổi mới); một đề nghị/một buổi bù | Đã chốt |
| G3-D35-02 | Kết thúc lớp | Chặn AI/tạo mới; đóng bài đang mở; không tự mở đáp án; đọc công bố hợp lệ tối đa 90 ngày | Đã chốt |
| G3-D35-03 | Nhật ký phiên bản mới | Bản công bố mới cần phụ huynh xác nhận lại; không kế thừa đồng ý cũ | Đã chốt |
| G3-D35-04 | Nộp và đáp án | Nộp mới vào bản đề hiện hành; đáp án mở theo assignmentVersion; ticket ảnh tối đa 60 giây | Đã chốt |
| G3-D35-05 | Chỉ số tiến trình | Tỷ lệ có N/kỳ/asOf/definitionVersion; N=0 là không áp dụng; không suy học lực | Đã chốt |

## 4. Kiến trúc và hợp đồng (G3-D16, G3-D07)

`G3-D07-xx` khác `G2-D07`.

| Mã | Chủ đề | Nội dung | Trạng thái |
|---|---|---|---|
| G3-D16-01 | Kiến trúc | Nguyên khối chia mô đun; API/worker riêng; PostgreSQL chung; Next.js/React Native. AI theo CR-002 | Có CR |
| G3-D16-02 | Phiên | Opaque có tra trạng thái/epoch để thu hồi; cookie và CSRF cho web; MFA quản trị | Đã chốt |
| G3-D16-03 | Quyền hỗ trợ | Tối đa 24 giờ, theo vụ việc, không tự cấp. Người cấp theo G3-D07-03 (CR-006) | Có CR |
| G3-D16-04 | Phục hồi quyền/xóa | Ledger bền vững ngoài snapshot cũ; áp thu hồi trước khi mở dịch vụ sau restore | Đã chốt |
| G3-D16-05 | Hạ tầng tác vụ | DB outbox, quota reservation cho R0; AI tắt đến khi GATE03 đủ điều kiện | Đã chốt |
| G3-D07-01 | Tên schema API | ReviewInput cho duyệt soạn; SubmissionReviewInput cho nhận xét bài nộp | Đã chốt |
| G3-D07-02 | Lỗi và đường dẫn | Problem thống nhất; traceId; prefix `/api/v1` | Đã chốt |
| G3-D07-03 | Phạm vi grant hỗ trợ | Chỉ chủ sở hữu nguồn cấp VIEW_ORIGINAL; không tải/AI/đáp án | Đã chốt (Đính, 03/11/2026) |
| G3-D07-04 | Minh chứng gia sư | PDF/JPEG/PNG; tối đa 10 tệp, mỗi tệp 5 MiB | Đã chốt |
| G3-D07-05 | Yêu cầu bổ sung hồ sơ | NEEDS_INFO ánh xạ PENDING kèm lý do; phải rõ trên giao diện | Đã chốt |
| G3-D07-06 | Tạo và sửa lần đầu | Tạo dùng If-None-Match:*; sửa dùng If-Match; ETag không đoán phía client | Đã chốt |
| G3-D07-07 | Đăng ký học sinh | Chỉ qua lời mời; xác minh email; tạo account chưa tự liên kết | Đã chốt |
| G3-D07-08 | Hoàn tất xóa | Tách xóa 6 kho hoạt động với đóng quy trình sau backup/ledger; có bằng chứng ngoài DB | Đã chốt |

## 5. Mục tổng hợp (XC)

| Mã | Chủ đề | Nội dung | Trạng thái |
|---|---|---|---|
| XC01 | Bản gốc và quyền phê duyệt | Dùng đề cương và kế hoạch v2.0 trong `docs/source-docx/`. Đính duyệt sản phẩm; người chủ trì phần nào duyệt kiến trúc phần đó; bất đồng thì giữ mở và lập CR | Đã chốt (Đính, 03/11/2026; chờ Huy) |
| XC02 | Tầm nhìn và phạm vi | OCR, AI tiến độ, trực tiếp/đa môn, thanh toán là lộ trình, không vào R0; AI soạn đã thành P0 | Đã chốt |
| XC03 | Bằng chứng thị trường | G0/G1 là tổng hợp nội bộ, chưa có phỏng vấn; không giả dữ liệu | Thiếu giá trị (OP-05) |
| XC04 | Tiêu chí G3 về tài liệu | Duyệt thiết kế, truy vết, review và kế hoạch kiểm chứng; đọc kèm CR-004 | Có CR |
| XC05 | Xác nhận UX | F01/F06 tĩnh không coi là tương tác; review walkthrough trước; chưa chọn giữ hay đổi tiêu chí 5 người | Thiếu giá trị (OP-06) |
| XC06 | Vai trò và nguồn lực | Vai trò theo CR-001; 20 giờ/tuần mỗi người (Đính, 03/11/2026); thiếu dự phòng, ngân sách trần, mốc | Thiếu giá trị (OP-01) |
| XC07 | AI và nội dung | Thiếu người duyệt Toán độc lập, dataset/ngưỡng, trần tiền; nhà cung cấp TBD | Thiếu giá trị (OP-07) |
| XC08 | Quyền trẻ em và bản quyền | Thời hạn BR08 chưa thẩm định, không gọi là tuân thủ pháp luật; thiếu người rà chuyên môn | Thiếu giá trị (OP-07) |
| XC09 | Năng lực và cam kết | NFR là mục tiêu; không công bố chịu tải hoặc SLA khi chưa đo | Đã chốt |
| XC10 | Nghiệp vụ hỗ trợ | Thiếu người xác minh/hỗ trợ, giờ phục vụ, cách giải quyết bất đồng | Thiếu giá trị (OP-07) |
| XC11 | Bên nhận hồ sơ | Thiếu tên hội đồng, khách hàng, đơn vị kiểm duyệt, mức NDA | Thiếu giá trị (OP-08) |
| XC12 | Context và skills | Xây từ bộ tài liệu đã xác nhận, có truy vết nguồn; đọc kèm CR-004 | Có CR |

## 6. Việc còn mở

**Chặn tạo repo** là phải có trước khi tạo repo. Các mục khác có mốc riêng.

| Mã | Việc cần chốt | Mã liên quan | Người chốt | Chặn |
|---|---|---|---|---|
| OP-01 | Đã có 20 giờ/tuần mỗi người. Còn thiếu ngân sách trần và mốc mong muốn. AI trả phí vẫn tắt khi chưa có trần tiền | XC06, G2-D08 | Đính, Huy | Không chặn tạo repo; chặn cam kết ngày và AI trả phí |
| OP-02 | Đã đóng 03/11/2026: dùng bản hẹp G3-D07-03 (CR-006) | G3-D16-03, G3-D07-03 | Đính | Không |
| OP-03 | Đã đóng 03/11/2026: [ADR-0005](adr/0005-ai-worker-keo-viec.md) (CR-007). Đính ủy quyền chốt; Huy chủ trì AI nên cần rà lại | CR-002 | Đính, Huy rà | Không |
| OP-04 | Đã đóng 03/11/2026, xem XC01 | XC01 | Đính | Không |
| OP-09 | Đã đóng 03/11/2026: repo private, Đính là admin, monorepo, squash merge, GitHub Issue/Project | CR-005b | Đính | Không |
| OP-10 | Đã đóng 03/11/2026: thêm V6 theo ADR-0006, đã kiểm chứng. Đính ủy quyền chốt; Huy cần rà | CR-009, ADR-0006 | Đính, Huy rà | Không |
| OP-11 | Chọn embedding model và số chiều vector cho điểm s | ADR-0006, XC07 | Huy | Chặn dùng S; Lát 1 chạy với B |
| OP-12 | Đã đóng 03/11/2026: dùng GitHub Free (CR-010) | CR-005b, CR-010 | Đính | Không |
| OP-13 | Đã có username Đính `DinhDuong1610`. Còn thiếu username Huy; thêm Huy làm collaborator trước PR code đầu tiên | CR-010 | Đính | Chặn review chéo |
| OP-14 | Đã đóng 03/11/2026: Đính quyết định đợt M0 (TB-1, TB-3, TB-4, TB-7, TB-2, TB-5) "vì đây là phần nền tảng nên lần này là ngoại lệ": agent viết phần tác giả tự giải thích theo yêu cầu của Đính, merge vào `dev` không qua duyệt chéo, "không cần rà lại". Từ PR sau áp dụng lại đầy đủ [quy trình Git](../process/git-workflow.md) | CR-010 | Đính | Không |
| OP-05 | Lịch phỏng vấn và người phụ trách, hoặc ghi nhận chấp nhận rủi ro | XC03 | Đính, Huy | Chặn G5 |
| OP-06 | Giữ hay đổi tiêu chí thử 5 người | XC05 | Đính, Huy | Chặn G5 |
| OP-07 | Người duyệt Toán, người rà dữ liệu trẻ em, người xác minh và hỗ trợ, trần tiền AI | XC07, XC08, XC10 | Đính, Huy | Chặn dữ liệu thật và AI thật (GATE02, GATE03) |
| OP-08 | Bên nhận hồ sơ và mức phân phối | XC11 | Đính | Chặn gửi ngoài |
