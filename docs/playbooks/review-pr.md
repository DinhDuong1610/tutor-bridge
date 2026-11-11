# Playbook: review-pr

Dùng khi rà một PR, kể cả tự rà trước khi gửi và khi agent này rà code của agent kia. Agent rà **không thay** người rà.

## Thứ tự rà
1. **Đúng việc:** PR khớp Issue, không có phần ngoài phạm vi.
2. **Bất biến:** đối chiếu [invariants](../context/invariants.md) với phần đổi, ưu tiên quyền, lịch, nguồn, đáp án.
3. **Nghiệm thu:** mỗi AC có test, test có khẳng định thật, có ca âm.
4. **Thất bại:** mất mạng, gửi lại, đồng thời, bản cũ, hết hạn.
5. **Rò dữ liệu:** DTO, lỗi, log, cache.
6. **Mở rộng:** truy vấn có giới hạn, có chỉ mục, không N+1, không trạng thái trong RAM.
7. **Ranh giới:** không đọc bảng mô-đun khác, không gọi ngược chiều.

## Dùng đúng nền
Phần nền đã có sẵn ([conventions](../context/conventions.md)). PR tự làm lại phần nền, hoặc đi vòng qua nó, là lỗi cần sửa.

| Phần | Phải thấy |
|---|---|
| Backend | Controller khai báo `IdempotencyKey`, `WritePrecondition`, lấy `@AuthenticationPrincipal Actor`; thao tác công khai khai báo bằng `PublicEndpoints`; test HTTP kế thừa `HttpTest` và có `Contract.matches()`; thời gian và ID từ `Clock`, `IdGenerator` |
| Mobile | Đọc bằng `call` trong `useQuery`, ghi bằng `useCommand`; màn hình dùng `QueryScreen`, `CommandStatus`; không xử lý 401 tại chỗ; màu từ `theme.ts`; test bằng `mockApi`, `renderScreen` |
| Web | Trang quản trị gọi `verifyAdminSession()` rồi `load(...)`; ghi qua Server Action trả `runCommand(...)` và `useServerCommand`; không gọi API từ trình duyệt; màu từ biến CSS |
| Giao diện | PR ghi mã màn hình và version Figma; ảnh app cạnh ảnh đã duyệt cho từng trạng thái, chỗ khác có lý do; câu chữ đúng nguyên văn `screens/<mã>.md`; qua checklist ở `docs/design/ui/08-chong-ai-slop.md` |
| Chung | Không thêm thư viện ngoài ADR-0009 khi chưa có Đính; `npm ls react @types/react` một phiên bản; không sinh `Idempotency-Key` tại màn hình |

## Mức rà theo rủi ro
| Vùng | Cách rà |
|---|---|
| Quyền, lịch, giao dịch, nguồn, đáp án, migration | Đọc từng dòng, chạy thử nếu nghi ngờ |
| Nghiệp vụ thường | Đọc logic và test |
| CRUD, giao diện đơn giản | Đọc nhanh, xem trạng thái lỗi |

## Dấu hiệu test giả
- Test đạt cả khi xóa code cần kiểm
- Ca đồng thời chạy lần lượt
- Mock đúng phần cần kiểm
- Bảng rỗng nên ca đạt mà không chứng minh gì

Khi nghi ngờ, tạm làm hỏng code để xem test có đỏ không. Đợt kiểm chứng V6 từng có ca đạt trên bảng rỗng, và phải thêm ca đối chứng trước/sau mới có giá trị.

## Cách báo kết quả
Mỗi phát hiện ghi: mức (chặn, nên sửa, góp ý), file và dòng, tình huống cụ thể gây sai, đề xuất. Chỉ báo lỗi có tình huống thật. Không có phát hiện thì nói rõ đã kiểm những gì.

## Người rà phải tự trả lời
- Tôi có giải thích được đoạn code này làm gì và tại sao không?
- Tình huống xấu nhất là gì, và có test không?

Chưa trả lời được thì chưa duyệt. Không hiểu thì không merge.
