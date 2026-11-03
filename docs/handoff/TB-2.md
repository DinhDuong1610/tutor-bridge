# TB-2 CI ngày đầu

- **Chủ task:** Đính
- **Nhánh:** `feature/TB-2-ci`, tách từ `feature/TB-7-tx05-accept-offer`
- **Agent đang làm:** Claude
- **Cập nhật lần cuối:** 03/11/2026
- **Mã liên quan:** CR-010, ADR-0001, Issue #2

## Mục tiêu
CI chặn những gì bắt buộc đúng, chạy được cả trên máy.

## Trạng thái hiện tại
Đã xong phần việc, chưa push. **Chưa chạy lần nào trên GitHub**: workflow chỉ chạy khi nhánh được push và có PR.

## Lệnh kiểm tra
```bash
sh scripts/ci/skills-sync.sh && sh scripts/ci/immutable-history.sh origin/dev && sh scripts/ci/hooks.sh
cd backend && ./gradlew build   # mong đợi 66 test đạt, không cảnh báo
```

## Kết quả chạy gần nhất
- actionlint 1.7.12: không lỗi.
- Mỗi script thử cả ca đạt và ca hỏng bằng commit tạm: sửa V1, sửa phụ lục, đổi tên V8 đều bị chặn; migration mới được phép; lệch skill bị chặn; token giả bị gitleaks bắt; `pr-format` đúng với 8 ca, gồm template trống, thiếu một câu trả lời, CRLF.
- gitleaks trên toàn bộ lịch sử: không có bí mật.
- `./gradlew build` với `-Xlint:all -Werror`: đạt.

## Quyết định đã chọn
- Hai workflow: `repo.yml` rẻ, chạy mọi PR; `backend.yml` chỉ chạy khi đổi backend hoặc nguồn migration.
- Mọi bước của `repo.yml` đều chạy kể cả khi bước trước hỏng, để tác giả thấy hết lỗi một lần.
- Tắt riêng cảnh báo `serial`: exception không được serialize.
- gitleaks chạy bằng image Docker, không dùng action vì action cần giấy phép với tổ chức.

## Câu hỏi còn mở
- Đính bật Dependabot alerts trong cài đặt repo.
- Lần push đầu tiên cần xem CI chạy thật. Nếu Testcontainers trên runner khác máy local, sửa ở PR đó.

## Bước kế tiếp
Push cả chuỗi nhánh khi PR #25 merge, xem CI chạy thật.
