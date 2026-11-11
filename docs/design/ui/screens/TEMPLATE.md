# <MÃ> <Tên màn hình>

Sao chép file này thành `screens/<MÃ>.md` khi màn hình được duyệt. Ảnh là một PNG cho cả section (mọi frame trạng thái), tỉ lệ 1:1, đặt ở `screens/img/<MÃ>-v<n>.png`; bảng Trạng thái liệt kê node từng frame để xem chi tiết. Ảnh của bản cũ giữ lại khi có v mới.

- **Trạng thái thiết kế:** Đã duyệt v1 (Đính, ngày)
- **Version Figma:** `<MÃ> v1 YYYY-MM-DD`
- **Node:** link "Copy link to selection" của section
- **Kênh:** Android | Web quản trị
- **Route:** `/parent/...` hoặc `/admin/...`
- **Issue giao diện:** #
- **Căn cứ:** UC, FR, BR; mục trong [06-man-hinh-lat-1.md](../06-man-hinh-lat-1.md)
- **API:** operationId

## Bố cục
Từ trên xuống. Mỗi khối ghi component và token chính.

| Khối | Component | Nội dung | Ghi chú |
|---|---|---|---|
| Thanh trên | `StudentSwitcher` | "Đang xem: ..." | |
| | | | |

## Trạng thái
| Trạng thái | Ảnh | Khác mặc định ở đâu | Câu chữ |
|---|---|---|---|
| Mặc định | `mac-dinh.png` | | |
| Đang tải | | | |
| Rỗng | | | |
| Lỗi trường | | | |
| Lỗi hệ thống | | | |
| Mất quyền | | | |
| Chưa rõ kết quả | | | |
| Riêng: ... | | | |
| Chữ 200% | | | |

## Câu chữ
Mọi chuỗi của màn hình, nguyên văn. Chuỗi chung lấy từ [04](../04-trang-thai-cau-chu.md), ghi "(chung)".

| Vị trí | Chuỗi |
|---|---|
| Tiêu đề | |
| Nút chính | |

## Hành vi
- Bấm gì thì đi đâu, mở gì.
- Thứ tự focus (web), nhãn trợ năng của icon (mobile).
- Điều kiện ẩn hiện.

## Ánh xạ sang code
| Figma | Mobile | Web |
|---|---|---|
| `Button variant=primary` | `<Button variant="primary">` | `<Button>` |

## Khác so với đặc tả và câu hỏi còn mở
`TBD` kèm câu hỏi, ai trả lời.
