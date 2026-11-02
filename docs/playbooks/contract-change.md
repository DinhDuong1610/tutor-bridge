# Playbook: contract-change

Dùng khi đổi OpenAPI hoặc schema. Vì hai người cùng làm một backend, hợp đồng đổi phải đi trước phần code dùng nó.

## Nơi sống của hợp đồng
| Loại | Bản đang dùng | Bản lịch sử, không sửa |
|---|---|---|
| API công khai | `contracts/openapi.yaml` | `docs/appendix/g3_ky_thuat_v0_1/api/` |
| API nội bộ cho worker | `contracts/internal-openapi.yaml` | Chưa có |
| Schema | `backend/src/main/resources/db/migration/` | `docs/appendix/g3_ky_thuat_v0_1/sql/` |

Bản đang dùng được tạo khi dựng repo, sao chép từ phụ lục và `docs/design/migrations-proposed/`.

## Các bước
1. **Xác định có đổi yêu cầu không.** Đổi hành vi nghiệp vụ thì phải có CR trước.
2. **Tạo PR riêng**, chỉ chứa hợp đồng và phần sinh ra, không kèm logic.
3. **OpenAPI:** chỉ thêm khi có thể. Đổi phá vỡ phải qua `/api/v2` hoặc giữ tương thích, vì NFR15 hỗ trợ cả bản app liền trước.
4. **Migration:**
   - Lấy số migration ghi trong Issue
   - Không sửa file đã merge
   - Theo thứ tự expand → backfill → validate → enforce
   - Không khóa bảng lâu; bảng lớn tạo chỉ mục `CONCURRENTLY`
   - Migration phải tương thích với bản code đang chạy trước đó
5. **Chạy kiểm tra:** migration trên DB trống và DB có dữ liệu; sinh lại API client; toàn bộ test.
6. **Cập nhật:** từ điển dữ liệu nếu đổi schema; `module-map` nếu thêm bảng; `r0a-scope` nếu đổi phạm vi.
7. **Cả hai người duyệt** rồi mới merge. Sau đó mới code theo hợp đồng mới.

## Không được
- Sửa code sinh ra bằng tay.
- Nới ràng buộc để test qua.
- Xóa cột hoặc bảng cùng lần ngừng dùng. Tách thành hai lần phát hành.
- Đổi hợp đồng ngầm trong PR tính năng.
