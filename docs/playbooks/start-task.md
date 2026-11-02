# Playbook: start-task

Dùng khi bắt đầu một Issue, hoặc tiếp nhận task người khác đang làm dở.

## Các bước
1. **Kiểm tra handoff.** Nếu có `docs/handoff/<mã-issue>.md`, đây là task đang dở: đọc file, chạy lại lệnh kiểm tra ghi trong đó để tự xác nhận trạng thái, không tin lời kể. Sang bước 5.
2. **Đọc Issue.** Phải có: chủ task, mã FR/UC/AC, tiêu chí xong. Thiếu thì dừng và hỏi.
3. **Kiểm tra phạm vi** với [r0a-scope](../context/r0a-scope.md). Ngoài phạm vi thì dừng và báo.
4. **Đọc đúng tài liệu:** [invariants](../context/invariants.md), UC liên quan, BR liên quan, file luồng trong `docs/design/`. Không đọc cả bộ.
5. **Kiểm tra nhánh.** Một nhánh, một agent. Nếu người hoặc agent khác đang làm cùng nhánh thì dừng.
6. **Viết kế hoạch ngắn** vào handoff theo [TEMPLATE](../handoff/TEMPLATE.md): việc sẽ làm, file dự kiến đụng, INV liên quan, ca kiểm thử, câu hỏi còn mở.
7. **Chọn playbook tiếp theo:** `implement-slice`, `transaction-change` hoặc `contract-change`.

## Dừng và hỏi khi
- Tài liệu mâu thuẫn hoặc thiếu quyết định. Ghi `TBD` kèm câu hỏi, không tự chọn.
- Task đụng quyền, lịch, nguồn mà UC không nói rõ.
- Ước tính PR vượt khoảng 400 dòng logic: đề xuất chia nhỏ.
