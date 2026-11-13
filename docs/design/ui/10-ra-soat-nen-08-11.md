# Rà nền giao diện hiện tại (08/11/2026)

Rà phần nền mobile và web đã merge (#49–#57) trước khi thiết kế, bằng các skill ở [08](08-chong-ai-slop.md#nguồn-tham-khảo). Mục đích: biết nền đang lệch định hướng ở đâu để Foundations sửa một lần. Báo cáo này **chưa sửa code**; việc sửa đi theo Issue riêng sau khi Đính duyệt Foundations.

## Đã chạy
| Công cụ | Lệnh | Kết quả |
|---|---|---|
| Impeccable detect 0.1.19, quét tĩnh | `impeccable detect web/src mobile/src` | 67 file, 1 phát hiện (`side-tab` ở `web/src/shared/ui/notice.tsx:17`). Quét tĩnh không chạy được luật cần trang đã render, nên con số là thiếu |
| Impeccable detect, quét trang chạy thật | `impeccable detect http://localhost:3100/`, `/huong-dan`, `/admin/sign-in` ở 1280 × 800; `/admin/sign-in` ở 390 × 844 | 0 phát hiện. Các trang hiện chỉ là trang chờ, nên kết quả nói ít |
| Vercel web-interface-guidelines | Đọc tay `web/src/shared/ui/*` theo luật | Xem bảng dưới |
| Impeccable audit (native), luật Android | Đọc tay `mobile/src/shared/ui/*`, `mobile/app.json` | Xem bảng dưới |
| break-ui, bước 1 | Tra giới hạn trường trong `contracts/openapi.yaml` | Bộ dữ liệu ở [11](11-du-lieu-xau-nhat.md) |

Telemetry của Impeccable đã tắt (`impeccable telemetry off`) trước khi quét. Các phát hiện về chữ 200% và bàn phím là **suy ra từ code**, chưa chụp trên máy Android.

## Phát hiện
Mức theo Impeccable: P1 phải sửa trước khi phát hành, P2 sửa ở lần kế, P3 trau chuốt.

| Mức | Chỗ | Vấn đề | Đề xuất |
|---|---|---|---|
| P1 | `web/src/shared/ui/button.tsx:8`, `input.tsx` (shadcn) | Vòng focus `ring-ring/50` là màu primary 50% trên nền trắng: tương phản đo được **2,33:1**, dưới mức 3:1 cho chỉ báo focus (WCAG 1.4.11) | Viền focus 2 px màu `primary` đặc, cách 2 px, như [02](02-nen-thiet-ke.md#trợ-năng-nfr12) |
| P1 | `mobile/src/shared/ui/Button.tsx` | Chữ nút nằm trong hàng ngang (`flexDirection: row`) không có `flexShrink`. Với chữ 200% và nhãn dài ("Chấp nhận đề nghị"), chữ có thể tràn khỏi nút thay vì xuống dòng (suy ra, chưa chụp) | Cho chữ co và xuống dòng; kiểm bằng frame chữ 200% trong Figma và trên máy |
| P1 | `mobile/src` | Không có xử lý bàn phím (IME inset). Form dài (PH04, AUTH01) có nút chính bị bàn phím che | `StickyActionBar` tránh bàn phím ([03](03-thanh-phan.md)); thêm vào nền khi làm Issue form đầu tiên |
| P2 | `web/src/shared/ui/notice.tsx:17`, `mobile/src/shared/ui/Notice.tsx:36` | Viền màu dày 4 px một bên: dấu hiệu AI dễ nhận nhất (Impeccable `side-tab`, craft floor cấm viền màu một bên quá 1 px ở thông báo) | Bỏ viền một bên. Thông báo dùng nền nhạt, icon trạng thái và chữ trạng thái đứng đầu câu, xem [03](03-thanh-phan.md#đã-có-trong-code) |
| P2 | `Notice` hai bên | Nhãn tone ("Thông tin", "Lỗi") nằm riêng một dòng phía trên nội dung: dạng "eyebrow" mà craft floor cấm. Nhãn này có lý do (không chỉ dùng màu), nên cần đổi cách thể hiện chứ không bỏ | Icon trạng thái kèm nhãn trợ năng, hoặc chữ trạng thái đứng đầu câu cùng dòng: "Lỗi: Không tải được dữ liệu." Đính chọn khi duyệt `Notice` |
| P2 | `web/src/shared/ui/button.tsx:8` (shadcn) | `transition-all`: Vercel liệt vào mẫu cấm, gây chuyển động cả những thuộc tính không cần | Liệt kê thuộc tính: màu nền, màu viền |
| P2 | `web/src/shared/ui/input.tsx` (shadcn) | Ô nhập đổi sang 14 px từ màn hình vừa (`md:text-sm`), khác [02](02-nen-thiet-ke.md#chữ) (ô nhập dùng `body` 16) | Giữ 16 ở mọi cỡ màn hình |
| P2 | `web/src/app/globals.css` | Chưa tạo kiểu cho bề mặt của trình duyệt: vùng chọn chữ, con trỏ, thanh cuộn, `theme-color` | Thêm theo token khi làm Foundations |
| P2 | `mobile/src/shared/ui/Button.tsx` | Nút bị vô hiệu dùng độ mờ 0,6; nhấn dùng độ mờ 0,85 thay cho màu nhấn. Chữ trên nút mờ khó đọc | Dùng `primaryPressed` khi nhấn; trạng thái vô hiệu có màu riêng và lý do vô hiệu bằng chữ cạnh nút |
| P3 | `mobile/src/shared/ui/Button.tsx:51` | `fontSize: 16` viết thẳng trong component, trái quy ước "không viết số trong màn hình" | Dùng text style `label` sau khi chốt cỡ |
| P3 | `ComingSoon` hai bên | Căn giữa toàn bộ | Chỉ là trang tạm; không vẽ lại trong Figma |

## Phát hiện thêm khi thử frame chữ 200% trong Figma (08/11/2026)
| Mức | Chỗ | Vấn đề | Đề xuất |
|---|---|---|---|
| P1 | `NavigationBar`, mọi màn hình có tab | Ở 200%, nhãn 5 tab trên khổ 360 gãy giữa chữ ("Thôn / g báo", "Tài khoả / n") | Đính chốt 12/11/2026 (OP-22): giới hạn phóng riêng nhãn tab khoảng 1,3. Các phương án đã xét: giới hạn phóng nhãn tab (ví dụ `maxFontSizeMultiplier` khoảng 1,3 chỉ cho nhãn tab, icon vẫn có nhãn trợ năng), hoặc chỉ hiện nhãn của tab đang mở khi chữ lớn |
| P1 | `Button` | Xác nhận phát hiện ở trên: nhãn dài ("Đăng xuất khỏi thiết bị này") tràn khỏi nút ở 200%. Thiết kế đã rút nhãn thành "Đăng xuất tài khoản"; code vẫn phải cho chữ trên nút xuống dòng | Như dòng `Button.tsx` ở trên |
| P2 | `SessionCard` ở chữ 200% | Giờ "18:00–19:30" không có chỗ ngắt nên bị cắt mép phải khi khối ngày giữ cạnh trái | Khi cỡ chữ hệ thống lớn (khoảng 1,5 lần trở lên), xếp khối ngày lên trên nội dung; hoặc cho giờ xuống dòng sau dấu gạch nối. Đính chọn khi duyệt `SessionCard` |
| P2 | `StatusLabel`, `VerificationLabel` ở chữ 200% | Nhãn dài ("Đang chờ gia sư trả lời", "Đã xác minh: Toán 8, đại số") không xuống dòng, bị cắt | Code cho chữ trong nhãn xuống dòng và nhãn rộng tối đa bằng khung chứa |
| P2 | Nhóm lựa chọn ngang (PH04: 60/90/120 phút, 1/2/3 buổi) ở chữ 200% | Ba ô cạnh nhau cắt chữ ("60 phú") | Khi chữ lớn, xếp các lựa chọn theo cột dọc |
| P2 | `CycleStrip` ở chữ 200% | Nhãn "Tuần 1"–"Tuần 4" bị cắt trong 4 cột | Khi chữ lớn, xếp 4 tuần thành 2 × 2 hoặc thành danh sách dọc |
| P2 | `TimeChip` Booked ở chữ 200% | Chữ "đã có buổi" bị cắt | Như `StatusLabel`: chữ trong chip được xuống dòng |
| Đã sửa trong thiết kế | CL02, thanh chọn tuần | Nhãn tuần giữa hai nút bị ép thành cột chữ ở 200% | Nhãn ở trên, hai nút ở hàng dưới |
| Đã sửa trong thiết kế | GS04, phần chú thích | Khung giờ và lời giải thích cùng hàng làm chữ bị ép thành 1 ký tự mỗi dòng ở 200% | Xếp lời giải thích dưới khung giờ ở mọi cỡ chữ |
| Đã sửa trong thiết kế | AUTH06, dòng thiết bị | Nút "Đăng xuất" và nhãn "Thiết bị này" đặt cùng hàng làm cột tên bị ép, gãy từng âm tiết ở 200% | Đặt hành động dưới tên thiết bị ở mọi cỡ chữ |

## Điều đang làm tốt
- Tách chữ trạng thái khỏi màu ở mọi thông báo và lỗi trường ("Lỗi: ..."), có vùng đọc tự động.
- Vùng chạm nút và ô nhập mobile ≥ 48.
- Câu chữ trạng thái giống nhau giữa mobile và web, dùng ký tự `…`.
- Không có gradient, bóng đổ hàng loạt, icon trang trí hay thẻ lồng thẻ.

## Ảnh hưởng tới thiết kế
- `Notice` phải được thiết kế lại trong Figma trước các màn hình khác, vì mọi trạng thái dùng nó.
- Mỗi màn hình có frame "Dữ liệu xấu nhất" và frame chữ 200% ([07](07-figma.md#cách-vẽ)).
- Android theo cấu trúc Material 3 cho điều hướng, nút Back, bảng từ dưới lên, thông báo tạm ([08](08-chong-ai-slop.md#android)).
