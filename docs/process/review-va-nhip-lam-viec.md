# Review và nhịp làm việc

Đính và Huy cùng vibecode nhưng phải hiểu code. Quy trình này giữ điều đó.

## Nguyên tắc
- **Không hiểu thì không merge.** Áp dụng cho cả tác giả.
- Agent rà giúp tìm lỗi, không thay người duyệt.
- Tác giả tự viết phần giải thích trong PR, không để agent viết hộ.

## Ba lớp rà
| Lớp | Ai | Mục đích |
|---|---|---|
| Tự rà | Tác giả cùng agent đang làm | Theo `review-pr` |
| Chéo agent | Agent còn lại | Tìm điều agent đầu bỏ sót. Claude viết thì Codex rà, và ngược lại |
| Người | Thành viên còn lại | Hiểu và quyết định duyệt |

## Mức rà theo rủi ro
Theo [review-pr](../playbooks/review-pr.md). Phần quyền, lịch, giao dịch, nguồn, đáp án, migration phải đọc từng dòng; `CODEOWNERS` yêu cầu đúng người.

## Khi không đồng ý
1. Trao đổi trực tiếp, dựa trên tài liệu và bất biến.
2. Sản phẩm: Đính quyết. Kiến trúc: người chủ trì phần đó quyết (XC01).
3. Vẫn bất đồng: giữ mở, lập CR. Không merge phần đang tranh chấp.

## Nhịp tuần
| Thời điểm | Việc | Người |
|---|---|---|
| Đầu tuần | Chọn mục tiêu chung, chia Issue, cấp số migration | Đính chủ trì |
| Giữa tuần | Xem nhanh PR đang mở, gỡ vướng | Huy chủ trì |
| Cuối tuần | Demo trên môi trường thử. **Người không viết phần nào thì trình bày phần đó** | Cả hai |
| Cuối tuần | Ghi giờ thực tế theo từng Issue | Cả hai |
| Mỗi hai tuần | Ước lượng lại, cập nhật rủi ro | Cả hai |

Giờ thực tế là dữ liệu để ước lượng lại R0 theo CR-003, nên phải ghi thật.

## Dừng khi
- Có lỗi đọc ghi ngoài quyền, trùng lịch, mất dữ liệu: dừng tính năng mới cho đến khi sửa xong.
- CI đỏ trên `dev`: sửa trước mọi việc khác.
- Agent lặp lại lỗi hơn hai lần: người phải đọc và tự sửa hướng đi.
