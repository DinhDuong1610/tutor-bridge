# Dữ liệu vật lý và vòng đời

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).
> **Lưu ý:** chuỗi migration được bổ sung theo CR-009 và ADR-0006.

Căn cứ: TB G3 07 mục 5; từ điển vật lý đã hợp nhất

### Schema vật lý và phạm vi ràng buộc

Chuỗi 001–005 tạo 77 bảng. Từ điển cột, kiểu, NULL, mặc định, CHECK và FK được trích từ schema đã thực thi, không chỉ từ phân tích văn bản. Sơ đồ erd_toan_bo.mmd biểu diễn hướng phụ thuộc FK, không gán lực lượng quan hệ khi chưa xét nullable và unique.

005 bổ sung credential hash và phiên theo epoch, challenge một lần theo mục đích, lời mời học sinh, scope quản trị, hồ sơ MFA dưới dạng handle, minh chứng chuyên môn, vụ việc, quyền tạm, nhiệm vụ xóa theo kho và bằng chứng xuất ledger. Dữ liệu thử chỉ có định danh mô phỏng, không có mật khẩu hoặc email thật.

DB hỗ trợ kiểm tính đúng thành phần. Service vẫn phải kiểm actor, quan hệ và trạng thái; không cấp quyền toàn bộ bảng/hàm cho ứng dụng. Tác vụ xóa tệp, chỉ mục và backup không được thực hiện bằng một CHECK trong DB.

| Cụm ràng buộc | Điều đã thử | Điều service phải làm |
|---|---|---|
| Phiên và challenge | Epoch, TTL, mục đích và consume lặp | Hash mật khẩu, email, MFA và revoke toàn giao dịch |
| Lời mời và xác minh | Đúng người, đúng owner, cấm tự duyệt | Tạo account/link, receipt/audit/outbox nguyên tử |
| Grant hỗ trợ | Hạn ≤24 giờ; owner cấp; nguồn thu hồi | ACL HTTP, proxy đọc và kiểm trước stream |
| Yêu cầu xóa | Không báo đủ kho hoặc đóng sớm | Xóa thật từng kho; backup và ledger độc lập |

| Phụ lục tra cứu | Vai trò |
|---|---|
| data_dictionary_hop_nhat.md | 77 bảng ứng viên, cột, kiểu, NULL, mặc định, CHECK và khóa ngoại |
| erd_toan_bo.mmd | Quan hệ phụ thuộc khóa ngoại; là mô tả sơ đồ, không phải mã ứng dụng |
| fr_traceability.csv | Truy vết 43 FR tới thiết kế/API; chưa thay ma trận truy vết hai chiều toàn bộ dự án |

Mã E01–E22 là nhóm dữ liệu nghiệp vụ, không phải số bảng. Các số 47 hoặc 61 bảng ở báo cáo từng đợt là phạm vi lịch sử; bản vật lý hợp nhất có 77 bảng. Các hợp đồng quyền và nghiệp vụ vẫn phải được kiểm ở service, worker, đường đọc tệp và cache.
