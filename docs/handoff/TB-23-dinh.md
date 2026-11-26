# TB-23 Nghiệm thu M7, phần Đính

- **Chủ task:** Đính (phần Đính). Phần Huy ở `feature/TB-23-authorization-huy` (#242), handoff `docs/handoff/TB-23.md` trên nhánh đó.
- **Nhánh:** `feature/TB-23-authorization-dinh`, tách từ `dev` `9848259`. #249 đã merge; đã gộp `dev` vào nhánh (`214a2a4`, không force-push)
- **Agent đang làm:** Claude (agent của Đính)
- **Cập nhật lần cuối:** 26/11/2026, sau commit `8b7667d` (bảng quyền Thông báo)
- **Mã liên quan:** AT01, AT02; AC-UC02-02; AC-UC05-01, AC-UC05-04; UC03, UC05, UC06; INV-01, INV-06, INV-22, INV-63

## Mục tiêu
Theo Notion (dòng "TB-23 phần Đính", hạn 27/11):
- ca quyền trên các API quản trị xác minh, đề nghị, thông báo (15 thao tác của `r0a-scope.md`);
- AT01 và AT02;
- sửa #203.

Huy nhận các nhóm còn lại (ghi trong #242).

## Kế hoạch
- [x] #203: tên con không vào log ở DEBUG. PR nháp #247, riêng, không phụ thuộc gì.
- [x] Bảng quyền nhóm Đề nghị: `classes/OfferAccessMatrixHttpTest`, 5 trên 6 thao tác. `acceptOffer` nằm trong `PENDING` cho tới khi #238 merge.
- [x] Bảng quyền nhóm Quản trị xác minh: `identity/AdminAccessMatrixHttpTest`, đủ 7 thao tác. Bảng tìm ra một chỗ lệch, sửa ở #249 (xem Quyết định).
- [x] AT02: `classes/At02EmptyMatchingHttpTest`.
- [x] Bảng quyền nhóm Thông báo: `platform/NotificationAccessMatrixHttpTest`, đủ 2 thao tác. Hợp đồng ghi "Recipient only" và vai trò nào cũng có hộp thư, nên không có dòng 403: mỗi vai trò được kiểm như chủ sở hữu, đối chiếu với người khác cùng vai trò. Bộ đọc bảng `r0a-scope.md` chuyển thành `com.tutorbridge.R0aScope`, ba bảng dùng chung.
- [ ] Dòng `acceptOffer` trong bảng Đề nghị: sau khi #238 (TB-19) merge.
- [ ] AT01 qua HTTP, sau khi #238 merge. Luồng: xác nhận nhu cầu → `createMatchingRun` → `sendOffer` → `acceptOffer` → lớp có đúng mục tiêu (bản 1 `EFFECTIVE`, theo Q1 của #19) và đúng các buổi. Hai gia đình chạy song song, không ai thấy dữ liệu của nhau (AC-UC02-02).
- [ ] Xóa file này trước khi Ready.

Đặt hai bảng quyền trong package của module, không đặt trong `m7` như phần Huy, để dùng lại `OfferHttpFixture` (package-private) và dựng đề nghị bằng API thật thay vì SQL. Đề nghị có trigger kiểm lịch buổi, dựng bằng SQL rất dễ sai.

## Khuôn của mỗi bảng (cùng khuôn #242)
- Không có phiên: 401.
- Vai trò ngoài `x-authorization`: 403, và dữ liệu của chủ sở hữu không đổi (so "dấu vân tay" trước và sau).
- Người khác cùng vai trò dùng id của chủ sở hữu: cùng 404 như id lạ, so toàn bộ body trừ `traceId`, dữ liệu không đổi.
- Danh sách của người khác không chứa id nào của chủ sở hữu.
- Chính chủ sở hữu được trả lời, để chứng minh các lần từ chối ở trên là do người gọi, không phải do request hỏng.
- Một test đối chiếu: mọi `operationId` của nhóm trong `r0a-scope.md` đều có dòng trong bảng (hoặc nằm trong `PENDING`).

## Lệnh kiểm tra
```bash
cd backend && ./gradlew test --tests 'com.tutorbridge.classes.OfferAccessMatrixHttpTest' \
  --tests 'com.tutorbridge.identity.AdminAccessMatrixHttpTest' --tests 'com.tutorbridge.classes.At02EmptyMatchingHttpTest'
# mong đợi: 33 + 32 + 4 ca, 0 hỏng (cần Docker)
cd backend && ./gradlew test
```

## Kết quả chạy gần nhất
26/11/2026, máy Đính, Windows, Docker Desktop 29.0.1:
- `OfferAccessMatrixHttpTest` 33/33, `AdminAccessMatrixHttpTest` 32/32, `At02EmptyMatchingHttpTest` 4/4.
- Làm hỏng code mỗi lần một chỗ, rồi trả lại bằng `git checkout` (test đã commit trước):

  | Phép làm hỏng | Ca đỏ |
  |---|---|
  | `getOffer`: gia sư nào cũng đọc được | 1 (gia sư khác) |
  | `sendOffer`: bỏ kiểm vai trò PARENT | 3 (TUTOR, STUDENT, ADMIN) |
  | 3 API đọc của người xác minh bỏ kiểm VERIFY | 3 (ADMIN không VERIFY) |
  | `getAdminSession` bỏ kiểm vai trò | 0, xem dưới |
  | Ngân sách nới 1% | 1 (AT02 ngân sách) |
  | Buổi luôn dài 60 phút, bất kể D | 1 (AT02 lịch) |

  `getAdminSession` bỏ kiểm vai trò mà vẫn xanh vì có hai lớp kiểm: lần đọc phiên còn đòi dòng `auth_sessions` có `admin_mfa_verified_at`, mà vai trò khác không bao giờ có dòng đó (`loginAccount` từ chối ADMIN, MFA chỉ dành cho ADMIN). Đây là phòng thủ hai lớp, không phải test yếu.
- #203 (nhánh riêng): trước khi sửa, test mới đỏ; sau khi sửa 19/19. Toàn bộ backend 661 ca, 0 hỏng.
- #249 (nhánh riêng): `VerificationDecisionsHttpTest` 15/15. Trả code về bản `dev` thì ca mới đỏ. Toàn bộ backend 661 ca, 0 hỏng.

## Sửa theo Codex rà `b0238d9` (26/11/2026)
- **[Nên sửa] Ca danh sách chỉ kiểm "không chứa id":** body lỗi 403 hay 500 cũng không chứa id, nên ca vẫn xanh dù API hỏng. Sửa ở `8454bca`:
  - ca của người khác: trước khi so id, khẳng định nhận 200 và `items` rỗng;
  - ca của chính chủ sở hữu: danh sách phải chứa id của chủ sở hữu. Nhờ vậy API trả rỗng cho mọi người cũng bị bắt.
- **Kiểm ngược:**

  | Phép làm hỏng | Ca đỏ |
  |---|---|
  | `listOffers` trả 403 cho mọi PARENT (đúng tình huống Codex nêu; trước khi sửa vẫn xanh) | 2 |
  | `listOffers` trả trang rỗng cho mọi người | 2 |
  | Hàng chờ người xác minh trả trang rỗng cho mọi người | 1 |

  Sau khi sửa: bảng Đề nghị 33/33, bảng Quản trị 32/32, toàn bộ backend 730 ca, 0 hỏng.
- **[Chặn Ready] Còn thiếu nhóm Thông báo, `acceptOffer`, AT01:** đúng với kế hoạch ở trên. Làm sau khi #237, #238 merge (cả hai còn OPEN ngày 26/11). PR giữ Draft.

## Bảng quyền Thông báo (26/11/2026, `8b7667d`)
- `NotificationAccessMatrixHttpTest` 19/19: 401 cho cả hai thao tác; 4 vai trò × (danh sách của người khác rỗng và không có id; `markNotificationRead` của người khác cùng 404 như id lạ, thông báo vẫn chưa đọc); 4 vai trò × chính chủ được trả lời; ca đối chiếu `r0a-scope.md`.
- Kiểm ngược (test commit trước, trả code bằng `git checkout`):

  | Phép làm hỏng `NotificationService` | Ca đỏ |
  |---|---|
  | Tìm thông báo không lọc người nhận | 4 |
  | Danh sách không lọc người nhận | 8 |
  | Danh sách luôn rỗng | 4 |
- Sau khi gộp `dev`: bảng Đề nghị 33/33, Quản trị 32/32, AT02 4/4, toàn bộ backend 781 ca, 0 hỏng.

## Quyết định đã chọn
- **Đính, 26/11/2026, cho #249:** `decideVerification` kiểm VERIFY (không khóa) trước khi đọc hồ sơ, giống ba API đọc. Trước đây ADMIN không VERIFY nhận 404 trên hồ sơ của người khác. Lần kiểm có khóa ở cuối giao dịch giữ nguyên, thứ tự khóa không đổi.
- **Đính, 26/11/2026, câu của Huy trên #23:** thu quyền `UPDATE`/`DELETE` của `tutorbridge_app` trên `audit_events`, làm sau M7 ở #248. Không chặn TB-23.
- **AT02 không cần trường nguyên nhân trong hợp đồng.** UC05 ghi "trả rỗng và nhóm nguyên nhân đã kiểm tra". Đặc tả PH06 đã duyệt hiện câu cố định kèm "4 nhóm điều kiện đã kiểm", không tính nguyên nhân cho từng lần chạy. Backend chỉ cần trả lượt chạy rỗng, COMPLETED, không có `failureCode`.
- **Test log dùng dấu hiệu ASCII** (#203): console Windows in dấu tiếng Việt thành `?`, nên dấu hiệu có dấu không bao giờ khớp và test sẽ đạt dù tên vẫn lọt.

## Câu hỏi còn mở
Không có.

## Bước kế tiếp
1. Khi #237 merge: viết bảng Thông báo.
2. Khi #238 merge: thêm dòng `acceptOffer`, viết AT01.
3. Trước mỗi bước: `git fetch && git rebase origin/dev`. Khi #249 đã squash vào `dev`, commit `4644d28` sẽ tự rơi khỏi nhánh lúc rebase.
