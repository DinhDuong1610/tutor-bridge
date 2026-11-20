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
| XC01 | Bản gốc và quyền phê duyệt | Dùng đề cương và kế hoạch v2.0 trong `docs/source-docx/`. Đính duyệt sản phẩm; người chủ trì phần nào duyệt kiến trúc phần đó (người chủ trì theo CR-012); bất đồng thì giữ mở và lập CR | Có CR |
| XC02 | Tầm nhìn và phạm vi | OCR, AI tiến độ, trực tiếp/đa môn, thanh toán là lộ trình, không vào R0; AI soạn đã thành P0 | Đã chốt |
| XC03 | Bằng chứng thị trường | G0/G1 là tổng hợp nội bộ, chưa có phỏng vấn; không giả dữ liệu. Chấp nhận rủi ro thay cho phỏng vấn trước M2 (OP-05). Vẫn chưa có bằng chứng thị trường | Đã chốt (Đính, Huy, 06/11/2026) |
| XC04 | Tiêu chí G3 về tài liệu | Duyệt thiết kế, truy vết, review và kế hoạch kiểm chứng; đọc kèm CR-004 | Có CR |
| XC05 | Xác nhận UX | F01/F06 tĩnh không coi là tương tác; review walkthrough trước. Chấp nhận rủi ro không thử bản mẫu trước M2 (OP-06); chưa chọn giữ hay đổi tiêu chí 5 người | Đã chốt (Đính, Huy, 06/11/2026) |
| XC06 | Vai trò và nguồn lực | Vai trò theo CR-001, người chủ trì theo CR-012; 20 giờ/tuần mỗi người (Đính, 03/11/2026); thiếu dự phòng, ngân sách trần, mốc | Thiếu giá trị (OP-01) |
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
| OP-13 | Đã đóng: Đính `DinhDuong1610`, Huy `HuyDEV10` đều là collaborator (kiểm qua GitHub API ngày 06/11/2026) | CR-010 | Đính | Không |
| OP-14 | Đã đóng 03/11/2026: Đính quyết định đợt M0 (TB-1, TB-3, TB-4, TB-7, TB-2, TB-5) "vì đây là phần nền tảng nên lần này là ngoại lệ": agent viết phần tác giả tự giải thích theo yêu cầu của Đính, merge vào `dev` không qua duyệt chéo, "không cần rà lại". Từ PR sau áp dụng lại đầy đủ [quy trình Git](../process/git-workflow.md) | CR-010 | Đính | Không |
| OP-15 | Đã đóng 06/11/2026: Đính quyết định phần nền (#42–#57) và các PR cấu hình còn tồn đọng là ngoại lệ, "chỉ ngoại lệ lần này thôi, không cần Huy review": agent tạo PR, tự điền phần tác giả (có ghi rõ do agent viết), tự merge vào `dev` các PR thuộc cấu hình và môi trường sau khi CI đạt. Đã merge theo ngoại lệ này: #41, #40 của Huy và các PR của phần nền. #39 (TB-8) là chức năng, không thuộc ngoại lệ. Đính cho thêm một lần cùng ngày cho PR #77 (cập nhật bàn giao): agent viết phần tác giả và merge không qua Huy review. Từ PR sau áp dụng lại đầy đủ [quy trình Git](../process/git-workflow.md) | CR-010, OP-14 | Đính | Không |
| OP-05 | Đã đóng 06/11/2026: Đính chọn "ghi nhận chấp nhận rủi ro" thay cho phỏng vấn trước M2. Huy xác nhận cùng ngày | XC03 | Đính, Huy | Không |
| OP-06 | Đã đóng 06/11/2026: Đính chọn chấp nhận rủi ro, không thử bản mẫu trước M2. Giữ hay đổi tiêu chí 5 người vẫn chưa chọn. Huy xác nhận cùng ngày | XC05 | Đính, Huy | Không |
| OP-07 | Người duyệt Toán, người rà dữ liệu trẻ em, người xác minh và hỗ trợ, trần tiền AI | XC07, XC08, XC10 | Đính, Huy | Chặn dữ liệu thật và AI thật (GATE02, GATE03) |
| OP-08 | Bên nhận hồ sơ và mức phân phối | XC11 | Đính | Chặn gửi ngoài |
| OP-16 | Logo: Đính gửi bản demo ngày 08/11/2026, "sau này sẽ có nhiều thay đổi"; đã đặt vào trang `01 Foundations` của Figma, chưa đưa vào repo. Màu giao diện không theo logo, `primary` giữ #0B5CAD (Đính, 08/11/2026). Còn chờ bản logo chốt (SVG cho web, PNG cho Android) | ADR-0011 | Đính | Không |
| OP-17 | Đã đóng 08/11/2026: tạo file [TutorBridge UX-UI](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl) trong team Verijob (gói Education) theo lựa chọn của Đính; link và file key ghi ở `docs/design/ui/07-figma.md` | ADR-0011 | Đính | Không |
| OP-18 | Đã đóng 13/11/2026 (#146): Đính chọn ghi số từ trang chính thức của Figma (tra ngày 13/11/2026) vào `07-figma.md`: Figma MCP gói Education bằng ghế Full/Dev của Professional, tối đa 200 lần gọi mỗi ngày và 10 lần mỗi phút; ghế View/Collab 20 lần mỗi tháng. Trang Code Connect liệt kê Education (cần ghế Full hoặc Dev), trái với ghi chú cũ "không có"; Đính mở thử trong Figma để xác nhận. Nội dung cũ: Kiểm giới hạn Figma MCP và Dev Mode của gói Education trên trang chính thức của Figma; ghi số vào `07-figma.md` | ADR-0011 | Đính | Không |
| OP-19 | Đã đóng 13/11/2026 (#146): Đính chọn chưa đưa vào CI ở Lát 1, tiếp tục chạy tay khi rà PR giao diện (`08-chong-ai-slop.md`); xem lại ở M6. Lý do: engine mới ở bản 0.1.x phải tải file chạy riêng, quét tĩnh thiếu luật cần trang render, báo sai sẽ chặn merge của nhóm hai người. Nội dung cũ: Có đưa rà tự động chống AI slop (`impeccable detect` hoặc tương đương) vào CI không | ADR-0011 | Đính | Không |
| OP-20 | Đã đóng 12/11/2026: Đính chọn không đặt trần cho `rateVnd60` và `budgetVndSession`, hợp đồng giữ nguyên `minimum: 1`. App hiện số đã định dạng (ví dụ `250.000 ₫`) để người dùng tự thấy nếu nhập nhầm. Nội dung cũ: Hợp đồng chỉ có `minimum: 1` cho `rateVnd60` và `budgetVndSession`, không có trần. Có cần trần (chống nhập nhầm, giới hạn hiển thị) không. Phát hiện khi rà dữ liệu xấu nhất ngày 08/11/2026 | BR02, `docs/design/ui/11-du-lieu-xau-nhat.md` | Đính | Không |
| OP-21 | Đã đóng 13/11/2026 (#146): Đính chọn tạo `PRODUCT.md`, `DESIGN.md` ngắn trong `mobile/` và `web/`, chỉ trỏ tới `docs/design/ui/01`, `02`, không chép nội dung; nguồn vẫn là `docs/design/ui/`. Nội dung cũ: Có tạo `PRODUCT.md`, `DESIGN.md` trong `mobile/`, `web/` cho Impeccable đọc không, hay giữ một nguồn ở `docs/design/ui/` | ADR-0011 | Đính | Không |
| OP-22 | Đã đóng 12/11/2026: Đính chọn giới hạn phóng chữ riêng cho nhãn tab (`maxFontSizeMultiplier` khoảng 1,3); mọi chữ khác vẫn phóng đủ 200%, icon tab vẫn có `accessibilityLabel` đầy đủ. Nội dung cũ: Nhãn 5 tab của thanh điều hướng gãy chữ ở cỡ chữ 200% trên khổ 360. Chọn: giới hạn phóng riêng nhãn tab, hay chỉ hiện nhãn tab đang mở khi chữ lớn, hay cách khác. Phát hiện khi thử frame 200% ngày 08/11/2026 | NFR12, `docs/design/ui/10-ra-soat-nen-08-11.md` | Đính | Không |
| OP-23 | Đã đóng 12/11/2026 (#96): Đính chọn (1) thao tác riêng `getOwnTutorVerification` (GET `/me/tutor-profile/verification`) với schema `TutorVerificationView`, không sửa `VerificationRecord` của quản trị (để OP-28); (2) máy chủ suy ra bốn trạng thái `WAITING`, `NEEDS_INFO`, `VERIFIED`, `REJECTED` từ `PENDING` kèm lý do, cơ sở dữ liệu giữ ba trạng thái theo G3-D07-05; (3) gia sư chỉ thấy lý do ở `NEEDS_INFO` và `REJECTED`. Không cần migration: V5 đã có `reason`, `reviewed_at`. Nội dung cũ: Mâu thuẫn: G3-D07-05 (đã chốt) yêu cầu "cần bổ sung" hiện lý do trên giao diện, F01 yêu cầu gia sư xem lý do và phạm vi; hợp đồng `VerificationRecord` không có trường lý do và gia sư không có thao tác đọc kết quả xác minh (chỉ nhận lúc gửi). Cần contract-change hoặc quyết định khác. Phát hiện khi vẽ GS03 ngày 08/11/2026 Đính đồng ý đi bằng contract-change ngày 10/11/2026: Issue #96. | G3-D07-05, UC03, FR-07, `contracts/openapi.yaml` | Đính | Không |
| OP-24 | Đã đóng 12/11/2026: Đính chọn (1) khi bản sửa chờ xác minh, phụ huynh vẫn thấy bản đã duyệt hiện hành (`current`) cùng nhãn xác minh của chính bản đó, tới khi bản mới được duyệt; (2) bản đã gửi đang chờ (`WAITING`) thì khóa, không sửa tiếp, `saveTutorProfile` trả 409; (3) `NEEDS_INFO` và `REJECTED` đều được sửa và gửi lại thành phiên bản mới, bản và phiếu cũ giữ nguyên để truy vết (INV-54). Cách đóng phiếu `NEEDS_INFO` cũ khi có bản mới: Huy đề xuất 16/11/2026, Đính duyệt 17/11/2026 (#12): trong giao dịch `submitTutorProfile`, bản cũ chuyển `PENDING` → `RETIRED`, phiếu cũ giữ nguyên, ghi `audit_events` `VERIFICATION_SUPERSEDED` cùng `command_id`; bản cũ còn `WAITING` thì trả 409; `decideVerification` trên phiếu đã bị thay trả 409 `VERIFICATION_SUPERSEDED`; trigger V11 chặn ghi phiếu của bản `RETIRED` (#186). Cài đặt ở TB-12c. Nội dung cũ: GS02: (1) khi bản sửa đang chờ xác minh, phụ huynh còn thấy bản đang công khai không; (2) đang chờ xác minh có được sửa tiếp không; (3) bị từ chối có được sửa và gửi lại không | UC03, F01 | Đính | Không |
| OP-25 | Đã đóng 12/11/2026: Đính chọn gia sư chỉ thấy tên hiển thị của học sinh **sau khi chốt lớp**; đề nghị đang chờ không hiện tên. Thêm trường vào hợp đồng lớp, gộp vào #97. Nội dung cũ: Gia sư có được thấy tên hiển thị của học sinh trong đề nghị đang chờ và trong lớp đã chốt không. Hợp đồng `Offer`, `Session` không có tên học sinh; thiết kế GS01, GS05 tạm không hiện tên | UC06, BR07 | Đính | Không |
| OP-26 | Đã đóng 12/11/2026: Đính chọn trải mẫu tuần ra **56 ngày** (8 tuần), vì phụ huynh chọn ngày bắt đầu trong 28 ngày tới và chu kỳ dài 28 ngày (BR04), nên buổi muộn nhất rơi vào ngày thứ 56; trải 28 ngày thì gia sư bị lọc mất theo BR01. Vẫn trong trần 200 khoảng. Lịch ngắn dần theo thời gian, gia sư lưu lại định kỳ. Cùng ngày: `PREFERRED` tính là rảnh khi lọc cứng và xếp buổi, chỉ cộng thêm khi tính điểm khớp lịch (`m0-ban-giao.md`). Nội dung cũ: Lịch rảnh của gia sư (GS04) trải ra bao lâu khi trải mẫu tuần thành khoảng cụ thể: 28 ngày tới, hay khoảng khác. API nhận tối đa 200 khoảng; tài liệu chỉ nói rõ chu kỳ 28 ngày cho nhu cầu (BR04). Bản nháp dùng 28 ngày | UC07, BR04, `replaceTutorCalendar` | Đính | Không |
| OP-27 | Hợp đồng Lát 1 không có thao tác liệt kê lớp (chỉ `getClass`, `listSessions` theo classId). Tab Lớp học và "buổi sắp tới" ở PH01, GS01 phải lấy classId từ đề nghị đã chốt (`listOffers`), hay thêm `listClasses` bằng contract-change Đính đồng ý đi bằng contract-change ngày 10/11/2026: Issue #97. | r0a-scope (API), UC07, FR-19 | Đính | Chặn duyệt CL01 (màn gốc tab Lớp học) |
| OP-28 | Đã đóng 21/11/2026 (#98): Đính chọn (1) thêm `tutor_verifications.submitted_at` bằng migration V12 (số kế tiếp: #187 V13, #189 V14); (2) `listAssignedVerifications` lọc theo `status` lưu trong DB (`PENDING`, `VERIFIED`, `REJECTED`, bỏ trống là tất cả), bản ghi có `reason` (PENDING kèm lý do là NEEDS_INFO) và cờ `superseded`, phiếu bị thay không nằm trong `PENDING`; (3) hiện `tutorDisplayName`, không có email; (4) phân công trong Lát 1: tự giao khi gia sư gửi, xoay vòng giữa các ADMIN (cài ở TB-13, #13). Hợp đồng: schema `AssignedVerification` cho hàng chờ, thao tác `getAssignedVerification` trả bản hồ sơ đã gửi và danh sách minh chứng (id, loại, dung lượng); `VerificationRecord` của gia sư giữ nguyên. Nội dung cũ: Hợp đồng quản trị thiếu dữ liệu cho AD03, AD04: (1) `VerificationRecord` không có tên gia sư, ngày gửi; (2) không có thao tác cho người xác minh đọc nội dung hồ sơ đã gửi đúng phiên bản; (3) không có danh sách minh chứng nên không có evidenceId cho `readAssignedEvidence`; (4) danh sách không lọc được theo trạng thái. Cần contract-change trước khi làm giao diện quản trị Đính đồng ý đi bằng contract-change ngày 10/11/2026: Issue #98. | UC03, FR-07, AC-UC03-02, `contracts/openapi.yaml` | Đính | Không |
| OP-29 | Đã đóng 11/11/2026: Huy quyết định PR #92 (TB-8, hợp đồng V9 và CR-014) là ngoại lệ, merge vào `dev` không chờ Đính bấm duyệt trên GitHub. Theo Huy, Đính đã duyệt bản sửa ngày 10/11/2026 nhưng chưa duyệt được trực tiếp trên PR; trên GitHub, review cuối của Đính vẫn là `CHANGES_REQUESTED` (commit `c737368`, 09/11/2026). Merge khi CR-013 chưa có trên `dev` (Q15 của TB-8): giữ số CR-014, Đính đưa CR-013 lên sau. Chỉ áp dụng cho #92; các PR khác, kể cả #39 và chuỗi TB-10, theo đầy đủ [quy trình Git](../process/git-workflow.md) | CR-010, OP-15 | Huy | Không |
| OP-30 | Đã đóng 11/11/2026: Huy quyết định các PR còn lại của TB-8 là ngoại lệ, "ngoại lệ bỏ qua phần của Đính, tiếp tục thực hiện các phần tiếp theo theo đúng plan": merge vào `dev` không chờ Đính rà và duyệt. Áp dụng cho #39 (PR 2), #108 (PR 2b, đã gộp vào #39) và PR 3 (đăng nhập, phiên), kể cả phần nền chung mà CR-012 giao Đính chủ trì: `TooManyRequests`, `OutboxHandler` và vai trò worker trong `platform`, `spring-boot-starter-mail`, Mailpit trong `infra/compose.yaml`. Huy vẫn tự viết phần tác giả của từng PR. Đính rà lại sau khi merge nếu muốn; sửa đi bằng PR mới | CR-010, CR-012, OP-29 | Huy | Không |
| OP-31 | Đã đóng 11/11/2026: Huy mở rộng ngoại lệ OP-30 sang TB-9, "tiếp tục ngoại lệ bỏ qua review của Đính ở TB-9": #110 (khôi phục, đặt lại mật khẩu) và #111 (đổi mật khẩu, tái xác thực, đã gộp vào #110) merge vào `dev` không chờ Đính rà và duyệt. Huy tự viết phần tác giả của từng PR. Đính rà lại sau khi merge nếu muốn; sửa đi bằng PR mới | CR-010, OP-30 | Huy | Không |
| OP-32 | Đã đóng 11/11/2026: Huy mở rộng ngoại lệ sang TB-10, "tiếp tục duyệt ngoại lệ cho Đính ở phần này": chuỗi màn hình tài khoản #78, #88, #90, #91, #93, #99 (gộp vào #78) merge vào `dev` không chờ Đính duyệt. Cùng ngày Huy chọn bỏ luôn hai điều kiện trước khi Ready trong handoff TB-10: chưa chạy app trên điện thoại thật, chưa mở liên kết sâu thật, chưa có agent còn lại rà. Mọi lệnh gọi của AUTH01–AUTH06 đã kiểm với API thật. Việc còn lại làm sau trên `dev`, sửa bằng PR mới: chạy trên điện thoại và mở liên kết từ Mailpit; agent còn lại rà; liên kết văn bản điều khoản (Q8 của TB-8); câu chữ theo Figma khi Đính duyệt F01 (#94); thời gian chờ theo `Retry-After` (Q6 của TB-10, nền mobile); bỏ `AccountTextField` khi #81 xong; test mobile quá hạn 5 s lúc khởi động nguội (nền mobile, Đính) | CR-010, OP-30, OP-31 | Huy | Không |

OP-29 đến OP-32 lúc đầu được ghi trên `dev` với số OP-16 đến OP-19, trùng với số #94 đã dùng. Khi gỡ xung đột cho #94 ngày 11/11/2026, chúng được đổi sang số mới. Commit và bình luận trên #92, #39, #108, #109, #110, #111, #78 vẫn ghi số cũ.
