# ADR-0009 Nền giao diện cho mobile và web

**Trạng thái:** Đã chọn (Đính, 06/11/2026). Mục 1–4 Đính chọn trực tiếp, mục 5–9 Đính đồng ý theo đề xuất của agent. **Chủ trì:** Đính (CR-012).

## Vấn đề
Mobile và web mới có khung chạy CI (TB-5). Nếu mỗi Issue giao diện tự chọn điều hướng, cách gọi API, style và cấu trúc thư mục, code của hai người và hai agent sẽ không thống nhất. Cần chốt trước Issue giao diện đầu tiên (TB-10).

## Quyết định
| # | Chủ đề | Chọn | Ai chọn |
|---|---|---|---|
| 1 | Điều hướng mobile | Expo Router, route theo file trong `mobile/src/app/` | Đính |
| 2 | Lấy dữ liệu và cache | TanStack Query cho mobile gọi API qua `@tutorbridge/api-client`. Web chỉ gọi API phía máy chủ Next; TanStack Query trên web chỉ dùng cho phần tương tác phía trình duyệt và gọi route handler hoặc server action của Next, không gọi thẳng API | Đính |
| 3 | Style mobile | `StyleSheet` của React Native với token màu, cỡ chữ, khoảng cách trong `mobile/src/shared/ui/theme`. Không thêm thư viện style | Đính |
| 4 | Style web | Tailwind CSS, shadcn/ui cho bảng và form quản trị | Đính |
| 5 | Form | react-hook-form với zod. Schema zod đặt trong thư mục tính năng | Đính, theo đề xuất |
| 6 | Lưu phiên mobile | `expo-secure-store`, đúng "kho bảo mật của hệ điều hành" của ADR-0004 | Đính, theo đề xuất |
| 7 | Test web | Vitest với Testing Library. Mobile giữ Jest với `jest-expo` | Đính, theo đề xuất |
| 8 | Ngôn ngữ | Chuỗi tiếng Việt viết thẳng trong code, không dùng thư viện đa ngôn ngữ ở Lát 1 | Đính, theo đề xuất |
| 9 | Code dùng chung | Gói `packages/shared` (`@tutorbridge/shared`): kiểu Problem, sinh `Idempotency-Key`, định dạng ngày giờ Asia/Ho_Chi_Minh 24 giờ và tiền VND. Không chứa React | Đính, theo đề xuất |

Cấu trúc thư mục và cách thêm một chức năng ở [conventions](../../context/conventions.md).

## Vì sao không chọn phương án khác
| Phương án | Lý do bỏ |
|---|---|
| React Navigation khai báo bằng code | Nhiều code khung; Expo Router chạy trên nền này và là hướng Expo khuyên dùng |
| Tự viết hook lấy dữ liệu | Mỗi agent dễ viết cache, hủy và thử lại theo một kiểu |
| NativeWind cho mobile | Thêm lớp biên dịch cho React Native; Lát 1 chưa cần dùng chung class với web |
| Thư viện component mobile có sẵn | Phụ thuộc thiết kế của thư viện; trạng thái bắt buộc vẫn phải tự làm |

## Hệ quả
- Thêm phụ thuộc: `expo-router` và các gói đi kèm, `@tanstack/react-query`, `tailwindcss`, shadcn/ui. Mobile cài bằng `npx expo install`. Phiên bản chốt khi làm module tương ứng.
- Token màu của mobile và web định nghĩa riêng ở Lát 1. Muốn dùng chung thì làm sau, khi có thiết kế giao diện.
- Thử lại tự động của TanStack Query phải tắt cho lệnh ghi. Lệnh ghi chưa rõ kết quả đi qua tra trạng thái lệnh, không gửi lại mù (INV-20).
