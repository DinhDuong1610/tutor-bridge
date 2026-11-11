# Thứ tự việc của Đính và Demo 1 của Lát 1

Issue #112. Viết ngày 11/11/2026, sau khi TB-8 và TB-9 đã merge vào `dev` (#92, #39, #109, #110). Đây là **đề xuất của Đính**. Các dòng ghi `TBD` cần Huy đồng ý. Ngày ở đây là mục tiêu để sắp thứ tự, không phải cam kết (OP-01 chưa chốt).

## Hiện trạng `dev` ngày 11/11/2026
| Phần | Đã có | Chưa có |
|---|---|---|
| Backend | `identity`: đăng ký, xác minh, đăng nhập, phiên, khôi phục, đổi mật khẩu, tái xác thực, worker gửi email. Migration tới V9 | Hồ sơ con (TB-11), hồ sơ gia sư (TB-12), quản trị xác minh (TB-13). `classes`, `materials`, `learning`, `privacy` mới có khung |
| Mobile | Nền NEN-MB. Màn hình tài khoản AUTH01–AUTH06 nằm trong chuỗi PR TB-10 của Huy (#78, #88, #90, #91, #93, #99), chưa merge | Chưa chạy trên điện thoại thật hay emulator (handoff TB-10). Chưa có trang chủ (TB-14) |
| Web | Nền NEN-WEB, trang đăng nhập quản trị và khung console | Endpoint đăng nhập quản trị riêng (TB-8 Q17), MFA, màn hình xác minh |
| Hợp đồng | Đủ thao tác cho TB-11, TB-12, TB-13 theo `r0a-scope.md` | Đổi theo OP-23 (#96), OP-28 (#98), TB-8 Q17 |

## Việc của Đính theo tuần
Mỗi tuần khoảng 16 giờ (20 giờ trừ 20% dự phòng). Mỗi dòng là một nhánh và một PR, trừ dòng review.

### Tuần 1: 11–15/11
| # | Việc | Nhánh hoặc PR | Giờ | Mở khóa cho |
|---|---|---|---|---|
| 1 | Đóng TB-87: Huy gỡ xung đột `docs/decisions/change-requests.md` (CR-013 đứng trước CR-014) và merge, theo Đính giao ngày 11/11/2026. Đính không cần làm gì thêm | #94 | 0 | #95 và mọi Issue màn hình |
| 2 | Duyệt chuỗi TB-10 để Huy rebase lên `dev` và merge | #78, #88, #90, #91, #93, #99 | 2–3 | Demo 1a, #67, #81, #89 |
| 3 | Bắt đầu TB-11 API hồ sơ con. Draft PR sau commit đầu | `feature/TB-11-student-profiles` | 8–10 | #67 |
| 4 | Trả lời #83: lỗi 5xx có Problem là thất bại hay chưa rõ kết quả | Comment trên #83 | 0,5 | #75 về sau |

### Tuần 2: 16–22/11
| # | Việc | Nhánh hoặc PR | Giờ | Mở khóa cho |
|---|---|---|---|---|
| 5 | TB-11 sang Ready, Huy duyệt, merge. Mục tiêu 17/11 | `feature/TB-11-student-profiles` | 2–4 | #67, TB-12 bắt đầu được trong `identity` |
| 6 | Hợp đồng #96 (OP-23): gia sư đọc kết quả xác minh kèm lý do. Theo `contract-change`, cấp V11 nếu cần cột lý do | `feature/TB-96-...` | 3–4 | Phần `VerificationRecord` của TB-12, GS03 |
| 7 | Hợp đồng đăng nhập quản trị riêng, bỏ `AdminMfaChallenge` khỏi `LoginResult` (TB-8 Q17). Cần tạo Issue | `feature/TB-<số>-...` | 3–5 | TB-13, #82, #86 |
| 8 | Rà sau merge các PR ngoại lệ OP-16, OP-17, OP-18: #109 phiên, #110 mật khẩu, phần nền chung của #39. Chỗ sai mở Issue `bugfix/` | Comment trên PR, Issue mới | 4–6 | Demo 1 dùng đúng các luồng này |
| 9 | Chuẩn bị và diễn tập Demo 1 với Huy, xem mục dưới | Không có PR, trừ khi phải sửa tài liệu chạy | 2–3 | Demo 1 |

### Tuần 3: 23–29/11
| # | Việc | Nhánh hoặc PR | Giờ | Mở khóa cho |
|---|---|---|---|---|
| 10 | Hợp đồng #98 (OP-28): dữ liệu cho người xác minh. Làm sau khi thống nhất với Huy mô hình minh chứng của TB-12 | `feature/TB-98-...` | 4–6 | TB-13, #68 |
| 11 | #85 log của profile local đi qua `LogRedaction` | `bugfix/TB-85-...` | 1–2 | |
| 12 | Bắt đầu TB-15 API lịch rảnh, trong mô-đun `classes`. Điều kiện: Huy đã rà ADR-0007, `PREFERRED` đã chốt (`m0-ban-giao.md` đang ghi "Có") | `feature/TB-15-...` | 8–10 | #69 |

### Tuần 4: 30/11–06/12
| # | Việc | Ghi chú |
|---|---|---|
| 13 | TB-13 API quản trị xác minh, MFA | Chỉ bắt đầu khi TB-12 của Huy đã merge, vì cùng mô-đun `identity` |
| 14 | #82 `verifyAdminSession` kiểm ADMIN và MFA, #86 `session-expired` | Sau dòng 7. Đi cùng phần web của TB-13 |
| 15 | Tiếp TB-15 | |

TB-16 và #68 đến sau, theo bảng trong [kế hoạch Lát 1](lat-1-ke-hoach.md).

## Huy làm song song (theo kế hoạch, cần Huy xác nhận)
| Tuần | Huy | Đính | Mô-đun hai người đụng |
|---|---|---|---|
| 1 | Hoàn thiện, rebase và merge TB-10 | TB-11 | Huy ở `mobile/src/features/account`, Đính ở backend `identity`. Không trùng |
| 2 | #95 nền giao diện (TBD), rồi #67 sau khi TB-11 merge | Hợp đồng #96, Q17. Rà sau merge | Hợp đồng do Đính, Huy chỉ đọc. Không trùng |
| 3 | TB-12 hồ sơ gia sư | #98, TB-15 | Huy ở `identity`, Đính ở `classes` |
| 4 | Tiếp TB-12, TB-14 | TB-13 sau khi TB-12 merge | `identity` đổi chủ sau khi TB-12 merge |

`TBD` (Huy): nhận #95 trong tuần 2, khi #67 còn chờ TB-11. #95 hiện ghi "Đính phân công" và chưa có người nhận. Nếu Huy nhận, nên gom luôn #81 (`FormTextField`) và #89 (`renderScreen`), vì cả ba sửa `mobile/src/shared/` và màn hình của Huy dùng các file này. Gói font cho Expo vẫn cần Đính duyệt theo ADR-0009. Nếu Huy không nhận, Đính làm #95 ở tuần 3 thay cho dòng 12, và TB-15 lùi một tuần.

## Quy tắc tránh xung đột
1. **Một mô-đun backend, một người, một tuần.** Đây là quy tắc đã có trong [quy trình Git](../process/git-workflow.md). Thứ tự `identity` là TB-11 (Đính) → TB-12 (Huy) → TB-13 (Đính). TB-15 của Đính nằm trong `classes`, nên chạy song song được với TB-12.
2. **TB-11 chỉ thêm file mới** trong `identity`, ví dụ dịch vụ, repository và `web/StudentController.java`. Tránh sửa file dùng chung như `IdentityConfig.java`, `PartiesRepository.java`, `api/Parties.java`. Phải sửa thì ghi rõ trong PR để Huy rebase TB-12 trước khi bắt đầu.
3. **Hợp đồng đi từng PR một.** Không mở hai PR cùng sửa `contracts/openapi.yaml`. Thứ tự: #96 → Q17 → #98. Mỗi PR merge xong thì người đang làm nhánh khác rebase và chạy `npm run generate:api`. Không gộp tay code sinh ra.
4. **Số migration do Đính cấp, ghi trong Issue.** V10 đã cấp cho TB-11 (`students.created_at`, `student_goal_versions`, Đính chốt 11/11/2026). Số kế tiếp là V11, dự kiến cho #96 nếu cần cột lý do, rồi #98 nếu cần.
5. **Sổ quyết định và CR là chỗ dễ xung đột nhất.** #94 phải merge trước mọi PR khác có thêm dòng vào `decision-register.md` hoặc `change-requests.md`. Trước khi chuyển PR sang Ready: `git fetch && git rebase origin/dev` rồi kiểm lại số OP, CR không trùng. `TBD` (Huy, vì Huy giữ quy trình PR): có nên giữ trước số OP, CR trong Issue như số migration không?
6. **`mobile/src/shared/` và phần dùng chung của web: một người tại một thời điểm.** #95, #81, #89 làm cùng một người, xong trước khi #67 và TB-14 dựng màn hình.
7. **Route mobile của phụ huynh đổi theo thứ tự** TB-10 (`parent/security`, `parent/change-password`) → #67 (hồ sơ con) → TB-14 (`parent/index`, trang chủ PH01).
8. **Không xếp chồng dài.** Chuỗi TB-10 có 6 PR xếp chồng, mỗi lần sửa phải rebase cả chuỗi. Từ TB-11 trở đi, mỗi nhánh tách từ `dev` mới nhất và sống 2–3 ngày. Cần chia thì merge phần trước rồi mới tách phần sau.
9. **Xung đột khi merge do Huy giải quyết** (Đính giao ngày 11/11/2026). Đính vẫn tránh xung đột từ đầu theo các quy tắc trên.
10. **File khóa phụ thuộc bị xung đột thì sinh lại** (`npm install`), không sửa tay. Merge các PR Dependabot đang chờ trước khi rebase nhánh chức năng.

## Demo 1: bản demo đầu của Lát 1
Kết quả của M2 và phần phụ huynh của M3, chạy trên điện thoại Android thật với backend local. Chỉ dùng dữ liệu giả, không dùng thông tin trẻ thật.

### Demo 1a: tài khoản (khi TB-10 merge, mục tiêu tuần 2)
1. Phụ huynh đăng ký (AUTH01), nhận email trong Mailpit, mở liên kết xác minh (AUTH02).
2. Đăng nhập (AUTH03). Sai mật khẩu nhiều lần thì bị trì hoãn `LOGIN_DELAYED`.
3. Quên mật khẩu, đặt lại (AUTH04, AUTH05). Phiên cũ bị đăng xuất.
4. Xem phiên, thu hồi phiên khác, đổi mật khẩu, đăng xuất (AUTH06).
5. Gia sư đăng ký, đăng nhập, vào khu gia sư. Trang chủ vẫn là chỗ giữ.

### Demo 1: thêm hồ sơ con (khi TB-11 và #67 merge, mục tiêu tuần 3)
6. Phụ huynh tạo hồ sơ con, xem danh sách, sửa thông tin (PH02).

Không có trong Demo 1: web quản trị, hồ sơ gia sư và xác minh, lịch, nhu cầu, gợi ý, đề nghị, lớp.

### Điều kiện để chạy
| Điều kiện | Hiện trạng | Ai |
|---|---|---|
| TB-10 merge | Chờ Đính duyệt, Huy rebase | Đính, Huy |
| TB-11 và #67 merge | Chưa bắt đầu | Đính, Huy |
| Lối vào AUTH06 và PH02 từ khu phụ huynh | `TBD` (Huy): TB-10 Q11 để lối vào AUTH06 cho trang chủ TB-14. Cho Demo 1, hoặc #67 thêm lối vào tạm ở `parent/index`, hoặc làm trước phần khung của PH01 | Huy |
| Chạy được trên điện thoại thật | Handoff TB-10 ghi chưa chạy được trên emulator, chưa chạy trên điện thoại thật | Huy, Đính diễn tập |
| Mở liên kết trong email | Backend local gửi liên kết `tutorbridge://`. Expo Go chỉ mở `exp://<máy>:8081/--/...`, `tutorbridge://` cần bản dev build. `TBD` (Đính, Huy): đổi `link-base` khi demo, hay thêm dev build (thêm gói thì cần duyệt theo ADR-0009) | Đính chốt |
| Phiên bản điều khoản | Dùng bản thử `ban-thu-chua-hieu-luc` ở cả backend local và `EXPO_PUBLIC_TERMS_VERSION`. Văn bản thật vẫn là việc của Đính (TB-8 Q8) | Đính |
| Máy chạy backend | `.env` có `TB_EMAIL_DIGEST_KEY`, `TB_EMAIL_ENCRYPTION_KEY` và các khóa ghi trong handoff TB-10. Điện thoại dùng IP LAN của máy trong `EXPO_PUBLIC_API_URL` | Người chạy demo |

### Diễn tập
Hai người cùng chạy kịch bản trên một điện thoại, ghi lại lỗi và giờ thực tế. Lỗi chặn demo mở `bugfix/`. Lỗi không chặn ghi vào Issue của màn hình tương ứng. Lần đầu dùng làm buổi "Demo cuối tuần" đang chưa làm trên Notion.

## Câu hỏi còn mở
| # | Câu hỏi | Ai |
|---|---|---|
| 1 | Huy nhận #95, #81, #89 trong tuần 2? | Huy |
| 2 | Lối vào AUTH06 và PH02 cho Demo 1: lối vào tạm trong #67, hay làm trước khung PH01? | Huy, Đính duyệt |
| 3 | Mở liên kết email khi demo: đổi `link-base` hay dùng dev build? | Đính chốt, Huy góp ý |
| 4 | Giữ trước số OP, CR trong Issue như số migration? | Huy |
| 5 | Rà ADR-0007 trước tuần 3 để TB-15 bắt đầu được | Huy |
