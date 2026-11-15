# Chống "AI slop"

"AI slop" là giao diện trông như do máy sinh ra theo mặc định: giống mọi app khác, trang trí không mang nghĩa, câu chữ chung chung. Với TutorBridge, rủi ro lớn hơn vẻ ngoài: thẻ đẹp nhưng không nói rõ đã chốt hay chưa, nút "Xác nhận" không nói xác nhận cái gì.

## Nguồn tham khảo
Tra cứu ngày 07/11/2026. Ngày 08/11/2026 đã cài vào máy của Đính ở `~/.claude/skills` (không cài vào repo, không vào CI), danh sách phiên bản trong `~/.claude/skills/SOURCES.md`. Người hoặc agent khác không cần các skill này: tài liệu này đã ghi lại luật cần theo. Lời khuyên của các nguồn này chỉ áp dụng khi không trái với tài liệu này: ví dụ frontend-design khuyên "dám mạo hiểm về thẩm mỹ", còn TutorBridge đã chọn tiết chế và chỉ có một điểm nhấn.

| Nguồn | Dùng để |
|---|---|
| [anthropics/skills, frontend-design](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md) | Lập kế hoạch token trước khi vẽ; danh sách dấu hiệu AI; quy tắc câu chữ |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | Skill và CLI phát hiện mẫu AI (`npx impeccable detect`), lệnh `critique` và `audit` để rà. Apache 2.0 |
| [vercel-labs/web-interface-guidelines](https://github.com/vercel-labs/web-interface-guidelines) | Rà web quản trị: bàn phím, focus, form, chuyển động |
| [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | Tra cứu mẫu UX, cặp font, bảng màu khi cần so sánh phương án. Không dùng để chọn phong cách thay Đính |
| [emilkowalski/skills](https://github.com/emilkowalski/skills): `break-ui`, `animate-expo` | Thử giao diện với dữ liệu xấu nhất ([11](11-du-lieu-xau-nhat.md)); chuyển động trên Expo khi thật cần |
| [figma/mcp-server-guide](https://github.com/figma/mcp-server-guide), thư mục `skills/` | `figma-use`, `figma-generate-library`, `figma-generate-design`, `figma-design-to-code`: cách agent đọc và vẽ Figma đúng |
| [Figma MCP: hướng dẫn và giới hạn](https://developers.figma.com/docs/figma-mcp-server/) | Component, variable, auto layout, ghi chú để agent đọc đúng |

## Không làm
**Màu và nền**
- Gradient, nhất là tím sang xanh; nền kem với điểm nhấn cam đất; nền gần đen với một màu nhấn chói.
- Chữ xám trên nền màu. Màu trạng thái dùng để trang trí.
- Bóng xám giống nhau cho mọi thẻ.

**Thông báo và nhãn**
- Viền màu dày một bên (trái hoặc phải, quá 1 px) ở thông báo, thẻ, dòng danh sách. Nền hiện tại đang có ở `Notice` ([10](10-ra-soat-nen-08-11.md)).
- Nhãn nhỏ đứng riêng phía trên tiêu đề hay nội dung. Chữ trạng thái cần thiết thì đứng đầu câu hoặc đi cùng icon.

**Bố cục**
- Thẻ lồng trong thẻ. Mọi thứ đều là thẻ bo góc giống hệt nhau.
- Ô icon bo vuông phía trên mỗi tiêu đề. Icon trang trí không mang nghĩa.
- Căn giữa mọi thứ. Nội dung đọc được căn trái.
- Dải số liệu lớn kiểu trang giới thiệu SaaS trên trang chủ phụ huynh hay gia sư.
- Đánh số `01`, `02` cho thứ không phải trình tự. Trình tự thật (bước nhu cầu, `Tuần 1`–`Tuần 4`) thì đánh số.

**Chữ**
- Inter, Roboto, Arial mặc định. Dự án dùng Be Vietnam Pro.
- Nhãn viết hoa toàn bộ, giãn chữ. Nhãn nhỏ phía trên tiêu đề ("eyebrow").
- Tô đậm, nghiêng hay đổi màu một từ trong tiêu đề để "nhấn".
- Chuỗi ngăn bằng chấm giữa (`A · B · C`), mũi tên `→` cuối mọi nút và liên kết.

**Chuyển động**
- Hiện dần và trượt lên cho từng khối khi mở trang. Hiệu ứng khi rê chuột trên mọi thẻ. Hiệu ứng nảy.
- Thanh phần trăm giả cho tác vụ không biết thời lượng.

**Câu chữ**
- "Thành công!", "Oops!", "Có lỗi xảy ra", "Vui lòng thử lại sau" không nói lỗi gì.
- Nút "Gửi", "Xác nhận", "OK" khi có động từ cụ thể hơn.
- Màn hình rỗng không có việc làm tiếp.
- Câu quảng cáo, hứa hẹn kết quả học tập.

## Android
Theo luật Android của Impeccable: Material 3 quyết định cấu trúc và tương tác, thương hiệu thể hiện qua màu, chữ, hình khối.
- Thanh điều hướng dưới 3–5 mục (ta có 5). Nút Back và cử chỉ Back của hệ thống luôn hoạt động, không chặn.
- Nội dung không nằm dưới thanh trạng thái, thanh điều hướng hệ thống hay bàn phím.
- Bảng từ dưới lên cho xác nhận, thông báo tạm (snackbar) cho phản hồi ngắn, hộp thoại chỉ cho quyết định phải ngắt.
- Không mượn điều khiển kiểu iOS (công tắc, hộp thoại, nút quay lại riêng).
- Cỡ chữ theo cài đặt hệ thống (không chặn phóng chữ).
- Ảnh chụp để duyệt lấy từ máy Android hoặc giả lập, không từ trình duyệt; chụp thêm ở cỡ chữ lớn.

## Không theo
- Vercel khuyên viết hoa chữ đầu mỗi từ (Title Case) cho tiêu đề và nút. Tiếng Việt chỉ viết hoa chữ đầu câu ([04](04-trang-thai-cau-chu.md#quy-tắc-viết)).
- Lời khuyên "mạo hiểm thẩm mỹ", `bolder`, `overdrive`, `delight` của các skill: TutorBridge chọn tiết chế, một điểm nhấn.

## Phải có
- Mỗi màn hình trả lời được ba câu trong 3 giây: đây là của ai (con nào, lớp nào), trạng thái là gì, việc tiếp theo là gì.
- Một nút chính mỗi màn hình. Điểm nhấn hình khối chỉ ở thẻ lịch buổi học.
- Mỗi phân biệt trạng thái có chữ hoặc icon, không chỉ màu.
- Mọi đường kẻ, viền, nhãn, số thứ tự đều mang thông tin. Bỏ được mà không mất thông tin thì bỏ.
- Trước khi duyệt một màn hình, thử bỏ một phần tử. Không thiếu gì thì bỏ hẳn.

## Checklist rà (Figma và PR)
- [ ] Không có mục nào trong "Không làm"
- [ ] Mọi màu, khoảng cách, bo góc, chữ là token
- [ ] Đủ trạng thái ở [04](04-trang-thai-cau-chu.md) và trạng thái riêng ở [06](06-man-hinh-lat-1.md)
- [ ] Câu chữ trùng nguyên văn bảng câu chữ, không tự viết lại
- [ ] Con đang chọn, giá đủ đơn vị, giờ 24 giờ hiện đúng chỗ
- [ ] Không có gì hiện "thành công" trước khi máy chủ xác nhận
- [ ] Chữ 200% không cắt, nút chính bấm được
- [ ] Tương phản đạt bảng ở [02](02-nen-thiet-ke.md)
- [ ] So với ảnh chụp đã duyệt: khác chỗ nào, vì sao

## Chạy công cụ rà
Đính chọn chưa đưa vào CI ở Lát 1 (OP-19, 13/11/2026); xem lại ở M6. Chạy tay khi rà, trên máy đã cài:

```bash
# Engine đã kiểm SHA-256 theo engine-checksums.txt của skill; telemetry đã tắt
B=~/.impeccable/bin/0.1.19/impeccable.exe
$B detect web/src mobile/src                         # quét tĩnh, thiếu các luật cần trang render
npm run dev --workspace @tutorbridge/web -- --port 3100
$B detect http://localhost:3100/admin/sign-in        # quét trang thật, thêm --viewport 390x844
```

| Khi | Skill | Việc |
|---|---|---|
| Duyệt một màn hình Figma | `impeccable` (`critique`) | Chấm UX trên ảnh chụp các trạng thái |
| Trước khi duyệt | `break-ui` | Kiểm frame dữ liệu xấu nhất theo [11](11-du-lieu-xau-nhat.md) |
| Rà PR web | `web-interface-guidelines`, `impeccable detect` | Bàn phím, focus, form; mẫu AI |
| Rà PR mobile | `impeccable` (`audit`, bản native) | Trợ năng, Material 3, inset, chữ lớn |

Impeccable tìm `PRODUCT.md` và `DESIGN.md` trong `mobile/` và `web/`. Bốn file này chỉ trỏ tới `docs/design/ui/01`, `02`, không chép nội dung (OP-21, 13/11/2026); sửa nội dung ở `docs/design/ui/`.
