# Nền thiết kế (token)

Token là nguồn chung của Figma, mobile và web. Figma lưu bằng **Variables**, mobile trong `mobile/src/shared/ui/theme.ts`, web trong `web/src/app/globals.css`. Ba nơi cùng tên vai trò, cùng giá trị. Đổi một token: sửa Figma, tạo version, sửa bảng này và hai file code trong **cùng một PR** ([09](09-quy-trinh.md#đổi-sau-khi-duyệt)).

Đính duyệt trang `01 Foundations` v1 ([07](07-figma.md)); mọi giá trị ở đây đã có trong code từ #95.

## Màu
Giữ bảng màu hiện tại (Đính chọn giữ xanh dương đậm, 07/11/2026). Chỉ nền sáng.

| Figma `color/…` | Mobile `colors.` | Web biến CSS | Giá trị | Dùng cho |
|---|---|---|---|---|
| `bg/default` | `background` | `--background` | `#FFFFFF` | Nền màn hình |
| `bg/subtle` | `surface` | `--muted`, `--secondary` | `#F4F6F8` | Nền khối, ô nhập, thẻ chưa chốt |
| `text/default` | `text` | `--foreground` | `#14181F` | Chữ chính |
| `text/muted` | `textMuted` | `--muted-foreground` | `#4A5361` | Chữ phụ, chú thích |
| `border/default` | `border` | `--border`, `--input` | `#7A8494` | Viền ô nhập, viền thẻ chưa chốt |
| `border/subtle` | `divider` | `--divider` | `#D5DAE1` | Đường phân cách trang trí, không dùng cho viền ô nhập |
| `brand/primary` | `primary` | `--primary`, `--ring` | `#0B5CAD` | Nút chính, liên kết, focus, khối ngày của thẻ lịch |
| `brand/primary-pressed` | `primaryPressed` | `--primary-pressed` | `#08467F` | Trạng thái nhấn của nút chính |
| `brand/on-primary` | `onPrimary` | `--primary-foreground` | `#FFFFFF` | Chữ trên nền primary |
| `status/info` | `info` | `--primary` | `#0B5CAD` | Thông tin, đang gửi |
| `status/info-bg` | `infoSurface` | `--accent` | `#E8F1FB` | Nền thông báo thông tin, nền nút phụ khi nhấn (Đính chốt 21/11/2026, #188) |
| `status/success` | `success` | `--success` | `#1E6B34` | Đã xác nhận |
| `status/success-bg` | `successSurface` | `--success-surface` | `#E7F5EC` | |
| `status/warning` | `warning` | `--warning` | `#8A5A00` | Chưa rõ kết quả, bản cũ, sắp hết hạn |
| `status/warning-bg` | `warningSurface` | `--warning-surface` | `#FFF4DB` | |
| `status/danger` | `danger` | `--destructive` | `#B42318` | Lỗi, mất quyền, thao tác không hoàn tác |
| `status/danger-bg` | `dangerSurface` | `--destructive-surface` | `#FDECEA` | |

**Độ tương phản**, đo ngày 07/11/2026 theo công thức WCAG 2.1. Đạt AA nếu chữ ≥ 4,5:1, viền ô nhập và icon mang nghĩa ≥ 3:1.

| Cặp | Tỉ lệ | Cặp | Tỉ lệ |
|---|---|---|---|
| text / background | 17,79 | primary / background | 6,67 |
| text / surface | 16,42 | primary / surface | 6,16 |
| textMuted / background | 7,77 | onPrimary / primary | 6,67 |
| textMuted / surface | 7,18 | onPrimary / primaryPressed | 9,58 |
| textMuted / infoSurface | 6,81 | primary / infoSurface | 5,85 |
| danger / dangerSurface | 5,75 | danger / background | 6,57 |
| warning / warningSurface | 5,42 | success / successSurface | 5,82 |
| border / background | 3,78 | border / surface | 3,49 |
| divider / background | 1,41 | Chỉ trang trí, không mang nghĩa | |

Quy tắc màu:
- Màn hình không viết mã màu. Figma không dùng màu rời, chỉ dùng variable.
- Primary chỉ cho **một** nút chính mỗi màn hình, liên kết, focus và khối ngày của thẻ lịch. Không tô nền lớn bằng primary.
- Màu trạng thái chỉ cho trạng thái. Không dùng `success` làm màu trang trí.
- Không gradient, không bóng xám đổ đều mọi thẻ ([08](08-chong-ai-slop.md)).

## Chữ
Font **Be Vietnam Pro** (Đính chọn, 07/11/2026). Mã nguồn mở, có đủ dấu tiếng Việt. Dùng 3 độ đậm: 400, 500, 600.

| Figma text style | Mobile `typography.` | Cỡ / dòng | Đậm | Dùng cho |
|---|---|---|---|---|
| `title` | `title` | 24 / 32 | 600 | Tiêu đề màn hình |
| `heading` | `heading` | 18 / 26 | 600 | Tiêu đề khối, giờ trên thẻ lịch |
| `body` | `body` | 16 / 24 | 400 | Nội dung, ô nhập |
| `body-strong` | `bodyStrong` | 16 / 24 | 500 | Giá, tên người trong thẻ |
| `label` | `label` | 14 / 20 | 500 | Nhãn ô nhập, nhãn trạng thái, nút |
| `caption` | `caption` | 13 / 18 | 400 | Chú thích, thời điểm |
| `date-numeral` | `dateNumeral` | 28 / 32 | 600 | Chỉ số ngày trong khối ngày của thẻ lịch |
| `badge` (chữ số badge, Figma 8:232) | `badge` | 11 / 16 | 400 | Chỉ số đếm trong badge của `NavigationBar` (Đính, 22/11/2026, #95); phóng tối đa 1,3 lần (OP-22) |

- **Chiều cao dòng bắt buộc.** Dấu tiếng Việt chồng (ệ, ẫ, ặ) bị cắt khi dòng sát cỡ chữ. Mọi style đều có line height, ít nhất 1,4 lần cỡ chữ với chữ thường. Ngoại lệ theo bảng trên: `title` 24/32 (1,33) và `caption` 13/18 (1,38), Đính chốt giữ bảng ngày 21/11/2026 (#188); `date-numeral` chỉ chứa số, không có dấu.
- Không viết hoa toàn bộ. Viết hoa chữ đầu câu cho tiêu đề, nút và nhãn.
- Số tiền, giờ và ngày dùng chữ số cùng độ rộng (`tabular-nums` trên web, `fontVariant: ["tabular-nums"]` trên mobile) để cột thẳng hàng.
- Mobile tải font bằng `@expo-google-fonts/be-vietnam-pro` 0.4.1 với `expo-font`, chỉ 3 độ đậm, nạp lúc chạy bằng `useFonts` (vẫn chạy trên Expo Go); Đính duyệt gói ngày 14/11/2026 theo ADR-0009 (#95). Mỗi độ đậm là một `fontFamily` riêng trong `theme.ts`, không đặt `fontWeight`. Web tải qua `next/font/google` với tập con `vietnamese`.
- Web quản trị dùng cùng thang chữ. Bảng dữ liệu dùng `label` cho tiêu đề cột và `body` cho ô, không thu nhỏ dưới 14.

## Khoảng cách
Lưới 4. Giữ thang hiện có, thêm `xxl`.

| Figma `space/…` | Mobile `spacing.` | Giá trị | Dùng cho |
|---|---|---|---|
| `xs` | `xs` | 4 | Giữa icon và chữ |
| `sm` | `sm` | 8 | Trong một nhóm (nhãn với ô) |
| `md` | `md` | 16 | Lề trái phải màn hình mobile, giữa các ô |
| `lg` | `lg` | 24 | Giữa các khối |
| `xl` | `xl` | 32 | Trước nút chính, trên dưới trạng thái rỗng |
| `xxl` | `xxl` | 48 | Lề trang web quản trị |

"Thoáng" nghĩa là giữa các khối dùng `lg`, không dồn nhiều khối vào một màn hình, chứ không phải tăng cỡ mọi thứ.

## Bo góc
Đính chọn "bo tròn vừa".

| Figma `radius/…` | Mobile `radius.` | Giá trị | Dùng cho |
|---|---|---|---|
| `sm` | `sm` | 8 | Ô nhập, nút, nhãn trạng thái |
| `md` | `md` | 12 | Thẻ, khung thông báo, thẻ lịch |
| `full` | `full` | 999 | Nhãn dạng viên thuốc (đếm chưa đọc) |

Web: `--radius` đổi từ `0.625rem` sang `0.75rem` để shadcn/ui ra 8 và 12.

## Độ nổi
Không dùng bóng làm mặc định. Phân lớp bằng nền `surface` và viền `divider`. Chỉ thanh tác vụ cố định ở cuối màn hình mobile có viền trên `divider`. Hộp thoại và bảng chọn dùng nền tối mờ phía sau, không thêm bóng.

## Icon
- Một bộ icon dạng nét cho mỗi kênh, không trộn bộ. Đề xuất: Android dùng Material Symbols (Outlined), vì luật Android của Impeccable coi icon ngoài bộ của nền tảng là dấu hiệu app bê từ web sang; web quản trị dùng Lucide (đã có qua shadcn/ui). Gói mobile đã duyệt: `react-native-svg` bản Expo SDK 58 (15.16.0) cùng đường vẽ SVG Material Symbols Outlined trong `mobile/src/shared/ui/Icon.tsx` (Đính, 21/11/2026, comment rà #188; #95). Web chưa cài `lucide-react` (`TBD` Đính, handoff TB-95). Trong Figma dùng bản SVG của đúng bộ.
- Icon mang nghĩa luôn đi kèm chữ, hoặc có nhãn trợ năng nếu đứng một mình.
- Không đặt icon trang trí trên mỗi tiêu đề.

## Kích thước và lưới
| Kênh | Khung Figma | Ghi chú |
|---|---|---|
| Android | 360 × 800, kiểm thêm 412 × 915 | Lề `md`. Vùng chạm tối thiểu 48 × 48 (`minTouchSize`) |
| Web quản trị | 1280 × 800, kiểm thêm 1440 | Cột nội dung tối đa 1200, lề `xxl` |

## Chuyển động
- Chỉ dùng khi cho thấy điều vừa đổi: mở bảng chọn, thêm một dòng, chuyển trạng thái lệnh. 150–200 ms, giảm dần đều, không nảy.
- Không hiệu ứng khi tải trang, không trượt hiện từng khối.
- Tôn trọng cài đặt giảm chuyển động của hệ điều hành và trình duyệt.
- Tác vụ lâu (chạy gợi ý) dùng chỉ báo không xác định, không thanh phần trăm giả (baseline mục 9).

## Trợ năng (NFR12)
- Tương phản theo bảng trên. Đổi màu thì đo lại và ghi số ở đây.
- Chữ phóng 200%: mỗi màn hình có một khung Figma ở cỡ chữ 200% cho trạng thái nhiều chữ nhất, nút chính vẫn thấy và bấm được.
- Web: thứ tự Tab theo thứ tự đọc; focus thấy rõ, viền 2 màu `primary` đặc cách 2 (tương phản 6,67:1). Không dùng vòng focus mờ: `ring-ring/50` của shadcn chỉ đạt 2,33:1 ([10](10-ra-soat-nen-08-11.md)). Mọi tác vụ dùng được bằng bàn phím.
- Web: tạo kiểu cho vùng chọn chữ, thanh cuộn, `theme-color` theo token, không để mặc định của trình duyệt.
- Mobile: mỗi nút, icon, ô nhập có nhãn trợ năng; thông báo trạng thái dùng vùng đọc tự động (`accessibilityLiveRegion`), như `QueryScreen` đang làm.
