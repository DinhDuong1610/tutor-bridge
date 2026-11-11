# Thiết kế

Đọc [kien-truc.md](kien-truc.md) và [mo-rong.md](mo-rong.md) trước, rồi tới file theo luồng đang làm.

## Hiện hành
| File | Nội dung |
|---|---|
| [kien-truc.md](kien-truc.md) | Kiến trúc tổng thể, mô-đun, cấu trúc repo |
| [mo-rong.md](mo-rong.md) | Đường mở rộng theo tải |
| [../decisions/adr/](../decisions/adr/) | ADR-0001 đến ADR-0011 |
| [migrations-proposed/](migrations-proposed/) | Migration V6, thành Flyway khi dựng repo |
| [ui/](ui/README.md) | UX-UI mobile và web: định hướng, token, component, trạng thái, đặc tả 26 màn hình Lát 1, quy ước Figma, quy trình (ADR-0011) |

## Bản hợp nhất TB G3 TH 01, giữ nguyên văn
| File | Nội dung |
|---|---|
| [01-pham-vi-tinh-trang.md](01-pham-vi-tinh-trang.md) | Phạm vi |
| [02-kien-truc-bao-mat.md](02-kien-truc-bao-mat.md) | Kiến trúc gốc, mô hình đe dọa |
| [03-man-hinh.md](03-man-hinh.md) | 52 màn hình |
| [f01-tai-khoan-ho-so.md](f01-tai-khoan-ho-so.md) | F01 |
| [f02-nhu-cau-matching-chot-lop.md](f02-nhu-cau-matching-chot-lop.md) | F02, TX01–TX06 |
| [f03-doi-lich-huy-ket-thuc.md](f03-doi-lich-huy-ket-thuc.md) | F03, TX17–TX20 |
| [f04-hoc-lieu-soan-bai.md](f04-hoc-lieu-soan-bai.md) | F04, TX07–TX16 |
| [f05-sau-buoi-bai-tap-tien-trinh.md](f05-sau-buoi-bai-tap-tien-trinh.md) | F05, TX21–TX30 |
| [f06-ho-tro-thu-hoi-xoa.md](f06-ho-tro-thu-hoi-xoa.md) | F06 |
| [10-hop-dong-api.md](10-hop-dong-api.md) | Quy ước API |
| [11-du-lieu-vong-doi.md](11-du-lieu-vong-doi.md) | Dữ liệu |
| [12-ai-chi-phi-migration-phuc-hoi.md](12-ai-chi-phi-migration-phuc-hoi.md) | AI, migration, phục hồi |
| [13-ban-mau-ux.md](13-ban-mau-ux.md) | Bản mẫu |
| [14-bang-chung-dong-thiet-ke.md](14-bang-chung-dong-thiet-ke.md) | Bằng chứng tại 30/10/2026 |
| [15-nguon-quyet-dinh.md](15-nguon-quyet-dinh.md) | Nguồn |

## Tên file phụ lục
Tài liệu G3 gọi phụ lục bằng tên khác với file thật:

| Tên trong tài liệu | File thật trong `docs/appendix/g3_ky_thuat_v0_1/` |
|---|---|
| `api_openapi_hop_nhat.yaml` | `api/openapi.yaml` |
| `data_dictionary_hop_nhat.md` | `data/physical_dictionary.md` |
| `erd_toan_bo.mmd` | `data/all_tables.mmd` |
| `fr_traceability.csv` | `review/fr_traceability.csv` |

## Lịch sử
[chi-tiet/](chi-tiet/) chứa 7 tài liệu G3 chi tiết ở độ ưu tiên thấp nhất, chỉ tra khi bản hợp nhất thiếu chi tiết. Hình wireframe nằm trong bản Word tại `docs/source-docx/g3/`.
