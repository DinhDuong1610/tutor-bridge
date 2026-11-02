# Playbook: handoff

Dùng để giữ task tiếp tục được khi đổi giữa Claude và Codex, hoặc đổi giữa Đính và Huy.

Agent có thể hết hạn mức mà không kịp báo trước. Vì vậy **cập nhật handoff sau mỗi commit**, không đợi cuối phiên.

## Quy tắc
- Mỗi task có một file `docs/handoff/<mã-issue>.md`, theo [TEMPLATE](../handoff/TEMPLATE.md).
- Commit sớm, commit nhỏ. Việc dở dùng commit `wip:` trên nhánh task.
- Tại một thời điểm chỉ một agent làm trên một nhánh.
- Handoff ghi sự thật có thể kiểm tra, không ghi cảm nhận.
- File bị xóa trong PR trước khi merge. Quyết định có giá trị lâu dài chuyển sang ADR hoặc CR.

## Khi sắp dừng
1. Commit toàn bộ, kể cả việc dở, lên nhánh task.
2. Cập nhật handoff: đang dở chỗ nào, lỗi đang gặp, lệnh tái hiện.
3. Push nếu người dùng đã cho phép.

## Khi tiếp nhận
1. Lấy nhánh mới nhất, đọc handoff.
2. Chạy lệnh kiểm tra trong handoff. **Tin kết quả chạy, không tin mô tả.**
3. Nếu lệch mô tả, ghi lại rồi xử lý trước khi làm tiếp.
4. Tiếp tục theo `start-task`.

## Nếu handoff cũ hơn commit mới nhất
Agent trước đã dừng đột ngột. Đọc `git log` và `git diff` của các commit sau đó, chạy test, rồi cập nhật handoff trước khi làm tiếp.
