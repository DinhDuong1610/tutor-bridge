# Đặc tả chức năng 26 màn hình Lát 1

Đây là đầu vào để vẽ: mỗi màn hình **phải hiện gì, làm được gì, có trạng thái nào, quy tắc nào phải thấy được**. Bố cục do Figma quyết định. Nguồn: `03-man-hinh.md`, UC01–UC07, UC15, BR01–BR07, F01, F02, `contracts/openapi.yaml`. Trạng thái chung ở [04](04-trang-thai-cau-chu.md) không nhắc lại; ở đây chỉ ghi trạng thái riêng.

Khi vẽ xong và duyệt, nội dung của màn hình chuyển sang `screens/<mã>.md` kèm node Figma ([09](09-quy-trinh.md)).

## Tài khoản (F01, UC01)

### AUTH01 Đăng ký và chọn vai trò
- **API:** `registerAccount`.
- **Hiển thị:** chọn vai trò "Phụ huynh" hoặc "Gia sư" (`ChoiceGroup`), email, tên hiển thị, mật khẩu, đồng ý điều khoản (liên kết tới bản điều khoản có phiên bản).
- **Tác vụ:** "Tạo tài khoản". Liên kết "Đã có tài khoản? Đăng nhập".
- **Trạng thái riêng:** email đã dùng; mật khẩu ngoài 15–128 ký tự; chưa đồng ý điều khoản; đã tạo, chờ xác minh email.
- **Phải thấy:** gợi ý "15–128 ký tự" dưới ô mật khẩu, có nút hiện/ẩn mật khẩu. Chỉ hai vai trò; không có lựa chọn quản trị hay học sinh.
- **Không được:** báo "đã xác minh" khi email chưa gửi được.

### AUTH02 Xác minh email
- **API:** `verifyEmail`, `resendVerification`.
- **Trạng thái riêng:** đang xác minh; đã xác minh (nút "Đăng nhập"); liên kết hết hạn (30 phút); liên kết đã dùng; liên kết sai mục đích; gửi lại bị giới hạn.
- **Phải thấy:** mỗi trạng thái nói rõ việc làm tiếp: hết hạn thì "Gửi lại liên kết".

### AUTH03 Đăng nhập
- **API:** `loginAccount`.
- **Hiển thị:** email, mật khẩu, "Đăng nhập", "Quên mật khẩu", "Tạo tài khoản".
- **Trạng thái riêng:** sai thông tin (câu chung, không nói email có tồn tại hay không); bị trì hoãn sau 5 lần sai trong 15 phút ("Thử lại sau 15 phút"); tài khoản bị khóa; email chưa xác minh; vừa hết phiên (`endedBy: expired`); vừa đăng xuất.
- **Phải thấy:** sau khi đăng nhập lại, bản nháp đã lưu ở máy chủ còn nguyên (AC-UC01-04).

### AUTH04 Yêu cầu khôi phục mật khẩu
- **API:** `requestRecovery`.
- **Trạng thái riêng:** đã gửi, câu chung "Nếu email này có tài khoản, chúng tôi đã gửi liên kết đặt lại mật khẩu. Liên kết có hạn 15 phút."; giới hạn gửi lại.
- **Không được:** xác nhận hay phủ nhận email có tài khoản.

### AUTH05 Đặt lại mật khẩu
- **API:** `resetPassword`.
- **Hiển thị:** mật khẩu mới, nhập lại.
- **Trạng thái riêng:** liên kết hết hạn hoặc đã dùng; thành công: "Đã đổi mật khẩu. Mọi phiên đăng nhập cũ đã bị đăng xuất." và nút "Đăng nhập".

### AUTH06 Bảo mật và quản lý phiên
- **API:** `listOwnSessions`, `revokeOwnSession`, `reauthenticate`, `changePassword`, `logoutAccount`.
- **Hiển thị:** danh sách phiên (thiết bị, lần hoạt động cuối), phiên hiện tại có nhãn "Thiết bị này"; "Đổi mật khẩu"; "Đăng xuất".
- **Tác vụ:** thu hồi một phiên (`ConfirmSheet`); đổi mật khẩu sau khi nhập lại mật khẩu hiện tại.
- **Trạng thái riêng:** phiên đã bị thu hồi; cần xác thực lại; đổi mật khẩu xong thì các phiên khác bị đăng xuất.
- **Phải thấy:** chỉ báo đã thu hồi sau khi máy chủ xác nhận.

### COM01 Hộp thông báo
- **API:** `listOwnNotifications`, `markNotificationRead`. **UC15.**
- **Hiển thị:** `NotificationItem` theo thời gian mới nhất trước; chưa đọc có chấm và chữ "Chưa đọc"; thời điểm 24 giờ.
- **Tác vụ:** mở thông báo đi tới đối tượng (PH09, GS05, CL01) và đánh dấu đã đọc; "Xem thêm" theo `cursor`.
- **Trạng thái riêng:** rỗng "Chưa có thông báo."; đối tượng không còn quyền: mở ra trạng thái mất quyền chung.
- **Phải thấy:** Lát 1 chỉ có thông báo về đề nghị và chốt lớp (FR-41 trong phạm vi). Tiêu đề không chứa dữ liệu riêng: "Có phản hồi cho đề nghị của bạn", không ghi tên con hay giá.

## Phụ huynh (F01, F02)

### PH01 Trang chủ phụ huynh
- **API:** `listStudents`, `listNeeds`, `listOffers`, `listSessions`, `listOwnNotifications`.
- **Hiển thị:** `StudentSwitcher`; buổi sắp tới của con đang chọn (`SessionCard`, tối đa 3, "Xem lịch"); việc đang chờ: nhu cầu nháp, đề nghị đang chờ kèm `Deadline`; thông báo chưa đọc mới nhất.
- **Trạng thái riêng:** chưa có con (dẫn tới PH02); con chưa có nhu cầu ("Tạo nhu cầu học kèm"); chưa có lớp.
- **Phải thấy:** đổi con thì toàn bộ khối tải lại đúng con đó, không trộn (AC-UC02-01).

### PH02 Hồ sơ các con
- **API:** `listStudents`, `createStudent`, `getStudentProfile`, `patchStudent`. **UC02.**
- **Hiển thị:** danh sách con (tên hiển thị, lớp, mục tiêu ngắn); biểu mẫu tạo và sửa với đúng ba trường đó.
- **Trạng thái riêng:** rỗng "Chưa có hồ sơ con nào." và "Thêm hồ sơ con"; bản cũ khi sửa.
- **Không được:** hỏi ảnh, ngày sinh, trường, địa chỉ. Không có mời học sinh (P1).

### PH03 Danh sách và phiên bản nhu cầu
- **API:** `listNeeds`, `getNeed`. **UC04.**
- **Hiển thị:** nhu cầu của con đang chọn với `StatusLabel` (Nháp, Đã xác nhận, Đã đóng), ngày tạo; bản đã xác nhận hiện tóm tắt điều kiện.
- **Tác vụ:** "Tạo nhu cầu"; mở nháp để sửa (PH04); mở bản đã xác nhận để tìm gia sư (PH06) hoặc xem đề nghị (PH09).
- **Phải thấy:** bản cũ chỉ để đối chiếu, không có nút tìm hay gửi.

### PH04 Nhập nhu cầu học kèm
- **API:** `createNeedDraft`, `updateNeedDraft`, `getStudentCalendar`, `replaceStudentCalendar`. **UC04, UC07; BR02, BR04.**
- **Hiển thị:** con đang chọn (không đổi được trong biểu mẫu); phạm vi cố định ghi bằng chữ: "Toán 8, đại số, học trực tuyến"; mục tiêu (20–2.000 ký tự, bộ đếm); ngân sách tối đa mỗi buổi (VND); thời lượng 60/90/120 phút; số buổi mỗi tuần 1–3; ngày bắt đầu (từ hôm nay tới 28 ngày sau); khung giờ rảnh của con và khung ưu tiên (`WeekAvailability`).
- **Tác vụ:** "Lưu nháp" (lưu được khi thiếu điều kiện); "Xem lại" sang PH05 (chỉ khi đủ điều kiện).
- **Trạng thái riêng:** nháp chưa đủ điều kiện, liệt kê thiếu gì; khung rảnh không đủ cho số buổi đã chọn; bản cũ.
- **Phải thấy:** ngân sách là trần cho một buổi đúng thời lượng đã chọn: đổi thời lượng thì ghi rõ "ngân sách cho buổi 90 phút". Giờ theo giờ Việt Nam.
- **Không được:** cho chọn môn, khối, hình thức khác (ngoài phân khúc R0).

### PH05 Xem lại và xác nhận nhu cầu
- **API:** `getNeed`, `confirmNeed`.
- **Hiển thị:** toàn bộ điều kiện đúng như máy chủ đã lưu; `CycleStrip` chu kỳ 28 ngày; khung rảnh và ưu tiên.
- **Tác vụ:** "Xác nhận nhu cầu" (`ConfirmSheet` nói: sau khi xác nhận, sửa sẽ tạo bản mới); "Sửa" về PH04.
- **Trạng thái riêng:** bản cũ (tải lại, giữ phần nhập hợp lệ); đã xác nhận, dẫn tới "Tìm gia sư".
- **Phải thấy:** điều kiện trên màn hình khớp bản lưu ở máy chủ (AC-UC04-01), không hiện bản đang có ở máy.

### PH06 Kết quả và so sánh gia sư
- **API:** `createMatchingRun`, `getMatchingRun`. **UC05; BR01, BR03.**
- **Hiển thị:** nhãn đầu danh sách "Danh sách lọc theo điều kiện của bạn, không dùng xếp hạng AI" (Lát 1 chỉ có `mode = B`); tối đa 5 `TutorCard` theo thứ tự máy chủ trả; mỗi thẻ có `PriceBlock`, lịch chung tóm tắt, kinh nghiệm, `VerificationLabel`, lý do khớp; nhắc "chưa giữ chỗ".
- **Tác vụ:** mở PH07; "Tìm lại" khi kết quả hết hạn (`expiresAt`).
- **Trạng thái riêng:** đang tìm (chỉ báo không xác định, không phần trăm); không có gia sư (liệt kê nhóm điều kiện đã kiểm); lỗi chạy (`FAILED`, giữ nhu cầu, "Thử lại"); kết quả đã cũ; nhu cầu là bản cũ.
- **Phải thấy:** "không có gia sư" và "lỗi" là hai trạng thái nhìn khác nhau (AC-UC05-04). Lý do khớp ghi rõ tự khai hay đã xác minh.
- **Không được:** hiện điểm hay phần trăm ([03](03-thanh-phan.md#không-có-điểm-số-trên-thẻ-gia-sư-đề-xuất-đính-chốt-khi-duyệt-ph06)); thêm hồ sơ mẫu cho đủ 5.

### PH07 Chi tiết gia sư công khai
- **API:** `getTutorPublic`. **UC03, UC05.**
- **Hiển thị:** tên hiển thị, mô tả chuyên môn, kinh nghiệm, `PriceBlock` theo nhu cầu đang xem, lịch chung, phạm vi xác minh.
- **Tác vụ:** "Gửi đề nghị" sang PH08.
- **Không được:** hiện minh chứng gốc, email, số điện thoại, liên hệ riêng (AC-UC03-03, BR07).

### PH08 Xem lại và gửi đề nghị
- **API:** `sendOffer`. **UC06; BR02, BR04, BR05.**
- **Hiển thị:** gia sư; mục tiêu; giá theo 60 phút, thời lượng, giá một buổi; **đủ từng buổi** của chu kỳ (`SessionCard` variant `proposed`, `CycleStrip`); hạn trả lời do máy chủ tính (nhỏ hơn giữa 48 giờ và giờ bắt đầu buổi đầu); nhắc "chưa giữ chỗ".
- **Tác vụ:** "Gửi đề nghị" qua `ConfirmSheet`: "Gửi đề nghị là bạn đồng ý các điều kiện trên. Gia sư chỉ có thể chấp nhận nguyên bản hoặc từ chối."
- **Trạng thái riêng:** giá hoặc lịch đã đổi, phải xem lại (AC-UC03-04); nhu cầu là bản cũ; chưa rõ kết quả (không cho gửi lần hai khi chưa tra trạng thái).

### PH09 Danh sách và chi tiết đề nghị
- **API:** `listOffers`, `getOffer`, `cancelOffer`. **UC06.**
- **Hiển thị:** đề nghị nhóm theo nhu cầu; `StatusLabel` và lý do kết thúc; `Deadline` khi đang chờ; chi tiết là ảnh chụp điều kiện đã gửi.
- **Tác vụ:** "Hủy đề nghị" khi đang chờ (`ConfirmSheet`); "Xem lớp" chỉ khi `ACCEPTED` và có `classId`.
- **Trạng thái riêng:** đề nghị kết thúc vì đề nghị khác của cùng nhu cầu đã chốt; hết hạn; gia sư từ chối.

## Gia sư (F01, F02)

### GS01 Trang chủ gia sư
- **API:** `getOwnTutorProfile`, `listOffers`, `listSessions`, `listOwnNotifications`.
- **Hiển thị:** "Việc cần làm" theo thứ tự: hoàn thiện hồ sơ, gửi duyệt, bổ sung theo yêu cầu, xác nhận lịch rảnh, đề nghị đang chờ (kèm `Deadline`); buổi sắp tới (`SessionCard`).
- **Phải thấy:** vì sao hồ sơ chưa xuất hiện khi phụ huynh tìm. Không hiện nhật ký, bản soạn (ngoài Lát 1). Đề nghị và buổi học không có tên học sinh trong hợp đồng (OP-25).

### GS02 Hồ sơ chuyên môn và học phí
- **API:** `getOwnTutorProfile`, `saveTutorProfile`. **UC03; BR02.**
- **Hiển thị:** môn và khối (Toán 8, cố định); mô tả cách dạy (50–3.000 ký tự); kinh nghiệm liên quan (0–600 tháng, 0 hợp lệ); giá VND cho 60 phút (số nguyên dương); `StatusLabel`; "Bản đang công khai" và "Bản đang sửa" khi khác nhau.
- **Tác vụ:** "Lưu nháp".
- **Trạng thái riêng:** bản cũ; sửa chuyên môn của bản đã duyệt: báo "Phần chuyên môn sẽ phải duyệt lại".
- **Phải thấy:** đổi giá chỉ áp dụng cho đề nghị mới, lớp đã chốt giữ giá cũ. Kinh nghiệm luôn là tự khai (`TutorPublic.experienceLabel = SELF_DECLARED`).
- **TBD (OP-24):** khi bản sửa đang chờ xác minh, phụ huynh còn thấy bản đang công khai không; khi đang chờ xác minh, gia sư có được sửa tiếp không; sau khi bị từ chối có được sửa và gửi lại không.

### GS03 Minh chứng và kết quả xác minh
- **API:** `createEvidenceUpload`, `completeEvidenceUpload`, `submitTutorProfile`. **UC03.**
- **Hiển thị:** tệp minh chứng đã tải (tên, trạng thái tải); kết quả xác minh: phạm vi, lý do, phiên bản, thời điểm.
- **Tác vụ:** "Tải minh chứng"; "Gửi duyệt" (`ConfirmSheet`).
- **Trạng thái riêng:** đang tải tệp; tải lỗi; tệp không hỗ trợ; đang chờ xác minh; cần bổ sung (lý do); chưa được duyệt (lý do).
- **Phải thấy:** minh chứng chỉ người xác minh xem, không công khai cho phụ huynh. Tệp PDF, JPEG, PNG; mỗi tệp tối đa 5 MiB; 1–10 tệp mỗi lần gửi (G3-D07-04 đã chốt, khớp hợp đồng). Trạng thái tệp theo `TutorEvidenceRecord.state`. Hợp đồng không có thao tác xóa tệp.
- **TBD (OP-23):** G3-D07-05 đã chốt "cần bổ sung" phải hiện lý do, nhưng `VerificationRecord` không có trường lý do và gia sư không có API đọc kết quả xác minh. Trạng thái "Cần bổ sung" và "Chưa được duyệt" dừng ở khung TBD tới khi có contract-change.

### GS04 Lịch rảnh và ngoại lệ
- **API:** `getTutorCalendar`, `replaceTutorCalendar`. **UC07; BR04.**
- **Hiển thị:** `WeekAvailability` với ba lớp tách bạch: rảnh đang sửa (nháp), rảnh đang hiệu lực, buổi đã đặt; ngoại lệ theo ngày (nghỉ, bận).
- **Tác vụ:** thêm, sửa khung giờ; thêm ngoại lệ; "Xem lại" rồi "Xác nhận lịch rảnh".
- **Trạng thái riêng:** giờ kết thúc không sau giờ bắt đầu; bản cũ.
- **Phải thấy:** sửa lịch rảnh không hủy buổi đã đặt; buổi đã đặt không sửa được ở đây. Khung sát nhau 18:00–19:00 và 19:00–20:00 hợp lệ.
- **Cách nhập:** theo tuần cộng ngoại lệ theo ngày (UC07). `EffectiveCalendar.windows` là các khoảng cụ thể, tối đa 200, nên ứng dụng trải mẫu tuần ra các ngày rồi trừ ngoại lệ. Màn hình xem lại cho biết số khoảng sẽ tạo. TBD (OP-26): trải ra bao lâu; bản nháp dùng 28 ngày tới.

### GS05 Đề nghị nhận lớp
- **API:** `listOffers`, `getOffer`, `acceptOffer`, `rejectOffer`. **UC06; BR05.**
- **Hiển thị:** danh sách đề nghị, đang chờ lên đầu, `Deadline`; chi tiết: mục tiêu (ảnh chụp `goalSnapshot`), `PriceBlock`, đủ từng buổi (`SessionCard` `proposed`, `CycleStrip`). Hợp đồng `Offer` không có tên học sinh: không hiện tên (TBD OP-25).
- **Tác vụ:** "Chấp nhận đề nghị" (`ConfirmSheet`: "Chấp nhận là đồng ý nguyên bản các điều kiện và toàn bộ buổi học"); "Từ chối".
- **Trạng thái riêng:** xung đột lịch: không nhận được, không buổi nào được đặt; đã hết hạn; phụ huynh đã hủy; đã chốt với gia sư khác; chưa rõ kết quả.
- **Phải thấy:** chỉ báo "Đã nhận lớp" sau khi máy chủ xác nhận toàn bộ lớp và buổi. Muốn đổi điều kiện thì phải từ chối, không có nút "đề xuất thay đổi".

## Lớp (chỉ xem ở Lát 1)

### CL01 Lớp học và thỏa thuận
- **API:** `getClass`. **UC04, UC07.**
- **Hiển thị:** hai bên (tên hiển thị), con, mục tiêu đã chốt, giá đã chốt (`PriceBlock`), `StatusLabel` của lớp, liên kết tới CL02.
- **Không được:** vẽ đổi mục tiêu, chia sẻ liên hệ, kết thúc lớp (ngoài Lát 1).

### CL02 Lịch lớp và chi tiết buổi
- **API:** `listSessions`. **UC07.**
- **Hiển thị:** xem theo tuần, `SessionCard` mỗi buổi; `CycleStrip` của chu kỳ; chi tiết buổi: ngày, giờ, thời lượng, trạng thái.
- **Phải thấy:** đúng lớp và đúng con đang chọn; giờ 24 giờ. Không có nút đổi hay hủy (UC08 ngoài Lát 1).

## Web quản trị

### AD01 Đăng nhập và xác thực quản trị
- **API:** `loginAccount`, `completeAdminMfa`.
- **Hiển thị:** email, mật khẩu; bước xác thực bổ sung.
- **Trạng thái riêng:** sai thông tin; bị trì hoãn; bị khóa; mã xác thực sai; phiên hết hạn (30 phút không hoạt động, tối đa 12 giờ).
- **Không được:** có liên kết đăng ký.

### AD03 Hàng chờ xác minh gia sư
- **API:** `listAssignedVerifications`. **UC03.**
- **Hiển thị:** `DataTable`: gia sư, phiên bản hồ sơ, ngày gửi, trạng thái, người xử lý; lọc theo trạng thái.
- **Trạng thái riêng:** rỗng "Không có hồ sơ nào được giao cho bạn."
- **Phải thấy:** chỉ hồ sơ được giao.

### AD04 Thẩm định hồ sơ và minh chứng
- **API:** `readAssignedEvidence`, `decideVerification`. **UC03; AC-UC03-02.**
- **Hiển thị:** bên trái nội dung hồ sơ đúng phiên bản và `EvidenceViewer`; bên phải biểu mẫu quyết định: kết quả (Xác minh, Yêu cầu bổ sung, Từ chối), phạm vi môn và khối khi xác minh, lý do.
- **Trạng thái riêng:** hồ sơ đã đổi phiên bản trong lúc xem; không được tự duyệt hồ sơ của mình; đã quyết định.
- **Phải thấy:** nhãn kết quả nói đúng phạm vi đã kiểm, không có từ "đảm bảo".
