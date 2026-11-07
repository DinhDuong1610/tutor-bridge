# Chống "AI slop"

"AI slop" là giao diện trông như do máy sinh ra theo mặc định: giống mọi app khác, trang trí không mang nghĩa, câu chữ chung chung. Với TutorBridge, rủi ro lớn hơn vẻ ngoài: thẻ đẹp nhưng không nói rõ đã chốt hay chưa, nút "Xác nhận" không nói xác nhận cái gì.

## Nguồn tham khảo
Tra cứu ngày 07/11/2026. Dùng làm checklist và để rà, **không** cài vào repo hay thêm vào CI khi Đính chưa quyết. Lời khuyên của các nguồn này chỉ áp dụng khi không trái với tài liệu này: ví dụ frontend-design khuyên "dám mạo hiểm về thẩm mỹ", còn TutorBridge đã chọn tiết chế và chỉ có một điểm nhấn.

| Nguồn | Dùng để |
|---|---|
| [anthropics/skills, frontend-design](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md) | Lập kế hoạch token trước khi vẽ; danh sách dấu hiệu AI; quy tắc câu chữ |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | Skill và CLI phát hiện mẫu AI (`npx impeccable detect`), lệnh `critique` và `audit` để rà. Apache 2.0 |
| [vercel-labs/web-interface-guidelines](https://github.com/vercel-labs/web-interface-guidelines) | Rà web quản trị: bàn phím, focus, form, chuyển động |
| [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | Tra cứu mẫu UX, cặp font, bảng màu khi cần so sánh phương án. Không dùng để chọn phong cách thay Đính |
| [funboy322/avoid-ai-design](https://github.com/funboy322/avoid-ai-design) | Rà code giao diện đã viết, tìm mẫu mặc định của shadcn/ui |
| [Figma MCP: hướng dẫn và giới hạn](https://developers.figma.com/docs/figma-mcp-server/) | Component, variable, auto layout, ghi chú để agent đọc đúng |

## Không làm
**Màu và nền**
- Gradient, nhất là tím sang xanh; nền kem với điểm nhấn cam đất; nền gần đen với một màu nhấn chói.
- Chữ xám trên nền màu. Màu trạng thái dùng để trang trí.
- Bóng xám giống nhau cho mọi thẻ.

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

## Công cụ rà tự động (TBD)
Có thể chạy `npx impeccable detect` cho web và `critique` trên ảnh chụp mobile. Đưa vào CI hay không là quyết định của Đính, theo dõi ở OP-19. Trước đó chỉ chạy tay khi rà.
