# CI

Dặn agent bằng tài liệu là chưa đủ. CI chặn những gì bắt buộc đúng. Workflow ở `.github/workflows/`, script kiểm tra ở `scripts/ci/` chạy được cả trên máy.

## Kiểm tra bắt buộc trên mỗi PR
| Kiểm tra | Chặn điều gì | Trạng thái |
|---|---|---|
| `skills-sync` | `.claude/skills` và `.agents/skills` lệch nhau | Có, `repo.yml` |
| `immutable-history` | Sửa `docs/appendix/`, `docs/source-docx/` hoặc migration đã merge | Có, `repo.yml` |
| `no-handoff` | Còn file `docs/handoff/TB-*.md` khi PR đã Ready | Có, `repo.yml`, chỉ khi PR không phải Draft |
| `secrets-scan` | Khóa, mật khẩu, token trong code | Có, gitleaks trên các commit của PR |
| `pr-format` | Tiêu đề thiếu mã TB hoặc thiếu phần tác giả giải thích | Có, `repo.yml`. Bỏ qua PR của Dependabot |
| `backend-build` | Build, lint, unit test | Có, `backend.yml`. Lint là cảnh báo trình biên dịch tính như lỗi |
| `backend-integration` | Test trên PostgreSQL thật, image `pgvector/pgvector:pg17` | Có, cùng job `backend-build` |
| `migration-check` | Áp được từ DB trống và từ phiên bản trước | Một phần: từ DB trống. Từ phiên bản trước làm khi có bản phát hành đầu |
| `architecture` | Mô-đun gọi ngược chiều, dùng `internal` của nhau (INV-82) | Có, `ArchitectureTest`. Chưa kiểm được SQL đọc bảng của mô-đun khác |
| `contract-check` | Code lệch OpenAPI; đổi phá vỡ không có phiên bản | Có: `oasdiff breaking` so `contracts/openapi.yaml` với nhánh đích trong `repo.yml` (#40), lỗi mức ERR thì chặn, cảnh báo chỉ in ra; phản hồi khớp hợp đồng kiểm bằng `Contract.matches()` trong test HTTP, chạy cùng `backend-build` (#47) |
| `client-sync` | API client sinh ra lệch hợp đồng | Có, `frontend.yml` |
| `mobile-build`, `web-build` | Build, lint, typecheck, test | Có, `frontend.yml`. Mobile bundle Android; web chạy Vitest từ #55 |
| `worker-build` | Lint, test worker Python | Khi có worker |
| `dependency-scan` | Phụ thuộc có lỗ hổng nghiêm trọng | Một phần: Dependabot cập nhật hằng tuần. Cần bật Dependabot alerts trong cài đặt repo |

## Chạy trên máy
```bash
sh scripts/ci/skills-sync.sh
sh scripts/ci/immutable-history.sh origin/dev
sh scripts/ci/hooks.sh
sh scripts/ci/no-handoff.sh   # trước khi chuyển PR sang Ready
sh scripts/ci/client-sync.sh  # sau khi đổi contracts/openapi.yaml
sh scripts/ci/contract-check.sh origin/dev  # cần oasdiff trên PATH hoặc Docker đang chạy
npm run lint && npm run typecheck && npm test
```

### Test mobile khi cache Jest trống (#123)
Không cần làm ấm cache trước. `npm test` chạy được ngay sau `npm ci` hoặc `npx jest --clearCache`, trên máy local và CI.

Ba chỗ trong `mobile/` giữ việc này:
- `jest.setup.ts` (`setupFilesAfterEnv`) chạm sẵn các component `react-native` app dùng. `react-native` nạp chúng lười, nên trước đây Babel biên dịch chúng bên trong ca test đầu của mỗi file, vượt giới hạn 5 giây khi cache trống.
- `src/shared/test/preloadRoutes.ts`: các file test dùng `renderRouter` gọi `beforeAll(preloadRoutes, 120_000)`. Hàm này nạp cả cây `src/app` theo cách `renderRouter` đọc nó, nên phần biên dịch nằm trong `beforeAll` có hạn riêng, không nằm trong ca test. File test route mới cũng cần dòng này.
- `maxWorkers: 4` trong cấu hình Jest. Với mặc định (số luồng trừ một), các worker tranh CPU tới mức ca vẫn hết giờ khi cache đã ấm, và thỉnh thoảng gặp `EPERM` khi cùng ghi cache trên Windows. `"50%"` (#261) đủ khi suite có 323 ca, nhưng với 482 ca thì 10 worker lại hết giờ ở các file test route (Huy chọn 4, #269, 28/11/2026).

Trước đây (15/11/2026) chỉ nạp sẵn trong `setupFilesAfterEnv` đã gặp `EPERM` và bị bỏ. `EPERM` đó đến từ số worker mặc định cùng ghi cache: trong bảng dưới nó còn xuất hiện ở 19 worker, và không xuất hiện ở `maxWorkers: "50%"` hay `4`. Vì vậy ba chỗ trên phải đi cùng nhau.

Không tăng `testTimeout` toàn cục.

Số đo trên máy Huy (Windows, 20 luồng, 323 ca, 27/11/2026), mỗi lần sau `npx jest --clearCache`:

| Cấu hình | Kết quả |
|---|---|
| Trước khi sửa, `npx jest --ci` | Hỏng 32–33 ca, có lần `EPERM` |
| Trước khi sửa, cache đã ấm, 19 worker | Hỏng 14 ca; 50% worker thì qua |
| Chỉ `jest.setup.ts`, 50% worker | Hỏng 14–22 ca, đều ở 4 file test route |
| Chỉ `preloadRoutes`, 50% worker | Hỏng 7–9 ca |
| Cả hai, 19 worker | 1 lần qua, rồi hỏng 3 và 1 ca (có `EPERM`) |
| Cả hai, `maxWorkers: "50%"` (`npx jest --ci`) | Qua 323/323 ba lần liền (53–77 giây); cache ấm 13 giây |
| Cả hai, `--runInBand` | Qua 323/323 (63 giây) |

Số đo lại trên cùng máy với 482 ca (28/11/2026, còn khoảng 2,4 GB RAM trống), cả ba chỗ đã có:

| Cấu hình | Kết quả |
|---|---|
| `maxWorkers: "50%"` (10 worker), 5 lượt | Qua 1 lượt; các lượt khác hỏng 14–29 ca (một lượt 15 ca và 1 suite lỗi khi chạy). Lượt đã xem từng ca (24 ca hỏng): đều hết giờ ở 7 file test route |
| `maxWorkers: 4`, 6 lượt | Qua 5 lượt (74–134 giây); một lượt hỏng 2 ca, là ca đầu của `TutorProfileScreen` và `NeedFormScreen`, không phải file route |

Với 482 ca, ca đầu của vài file màn hình nặng (GS02, PH04, GS03) vẫn có lúc hết giờ khi cache trống, trên máy Huy và trên CI (#265). Chạy lại thì qua. Phần này chưa sửa: các file đó chưa có bước nạp trước như `preloadRoutes`. Hỏng kiểu hết giờ ở chỗ khác là lỗi thật, không phải lỗi cache.

## Quy tắc
- Action pin theo commit SHA, image pin theo digest. Dependabot đề xuất bản mới qua PR.
- Workflow chỉ có quyền đọc. Tiêu đề và nội dung PR đi qua biến môi trường, không chèn thẳng vào lệnh.
- Chỉ chạy kiểm tra của phần có thay đổi, để tiết kiệm phút CI. `backend.yml` chạy khi đổi `backend/`, `infra/db-init/` hoặc nguồn migration. Mọi test backend, gồm test đồng thời, chạy mỗi lần như vậy.
- Không bỏ qua, không tắt kiểm tra để merge. Test chập chờn phải sửa, không chạy lại cho qua.
- CI chạy trên PR vào `dev` và `main`. CI đỏ trên `dev` là việc ưu tiên số một.

## Định kỳ
| Việc | Tần suất |
|---|---|
| Test tải k6 trên môi trường thử | Trước mỗi lần lên nấc tải |
| Diễn tập khôi phục | Hằng tháng từ khi có dữ liệu cần giữ |
| Cập nhật phụ thuộc | Hằng tuần qua Dependabot |
