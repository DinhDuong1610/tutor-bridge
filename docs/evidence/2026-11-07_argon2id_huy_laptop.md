# Tham khảo đo Argon2id trên máy của Huy

Chuyển từ handoff TB-8 (commit `ad7ea13`) khi xóa handoff trước lúc merge #92. Không phải tham số và không phải số đo năng lực (INV-73); tham số ở [ADR-0008](../decisions/adr/0008-bam-mat-khau.md).

07/11/2026, máy Windows của Huy (i7-12700H, RAM 15,6 GB), JDK 21, BouncyCastle `bcprov-jdk18on` 1.80 (`Argon2BytesGenerator`), 15 lần sau 3 lần làm nóng, mật khẩu giả:

| m (MiB) | t | p | Trung vị (ms) | p90 (ms) |
|---|---|---|---|---|
| 19 | 2 | 1 | 26 | 33 |
| 46 | 1 | 1 | 37 | 39 |
| 64 | 2 | 1 | 204 | 219 |
| 64 | 3 | 1 | 309 | 322 |
| 128 | 2 | 1 | 443 | 461 |

Không dùng làm tham số của ADR-0008: ADR yêu cầu đo trên máy sẽ chạy, chưa chọn (Đính, trước M6). Số này đo trên JDK 21 và `bcprov` 1.80; dự án chạy JDK 25 và PR 1 chọn `bcprov` 1.86.
