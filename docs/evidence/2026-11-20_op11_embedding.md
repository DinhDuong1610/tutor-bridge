# OP-11: đo ứng viên embedding cho điểm s (BR03)

Issue #137. Chủ trì: Huy. Người rà: Đính. Huy giao agent (Claude) chọn các phương án thuộc phần của Huy ngày 20/11/2026; mọi lựa chọn dưới đây là của agent theo ủy quyền đó, **chưa phải quyết định chung**. Ngưỡng ở mục 3 là TBD của cả Huy và Đính trong #137, nên cần Đính đồng ý khi rà PR.

Không phải số đo năng lực của sản phẩm (INV-73): bộ dữ liệu nhỏ và giả, chỉ dùng để chọn model.

## 1. Câu hỏi
Model embedding mở nào, chạy trong worker Python (ADR-0005), cho điểm `s = max(0, min(1, (cos + 1) / 2))` xếp đúng gia sư hợp với mục tiêu học tiếng Việt về Đại số 8? Số chiều bao nhiêu để ghi vào `embedding_configs` (ADR-0006)?

## 2. Giao thức (chốt trước khi chạy, commit riêng)
- **Dữ liệu:** [`scripts/eval/op11/dataset.json`](../../scripts/eval/op11/dataset.json), phiên bản `op11-dataset-2026-11-20`. 10 mô tả gia sư giả (mỗi mô tả một trọng tâm, đều Đại số 8 theo INV-30) và 30 mục tiêu học giả, 3 mục tiêu cho mỗi trọng tâm. Agent viết cả văn bản lẫn nhãn; không có dữ liệu người thật hay trẻ thật.
- **Nhãn theo cách dựng:** mỗi mục tiêu được viết để khớp đúng một gia sư; 9 gia sư còn lại tính là không khớp. Mục tiêu cố ý tránh dùng lại từ khóa của mô tả khớp, để đo hiểu nghĩa chứ không phải trùng chữ. **Hạn chế:** cùng một người viết dữ liệu và nhãn; Đính rà nhãn trước khi chấp nhận kết quả.
- **Ứng viên** (theo #137): `intfloat/multilingual-e5-small` (tiền tố `query: ` / `passage: ` theo trang model), `bkai-foundation-models/vietnamese-bi-encoder` (tách từ bằng `pyvi` theo trang model), `BAAI/bge-m3` (vector dày). Mỗi model ghim theo commit trên Hugging Face, ghi trong kết quả.
- **Mốc so sánh:** cosine trên túi âm tiết, không model.
- **Phần cứng:** chỉ CPU (`device="cpu"`), vì chưa biết máy chạy worker có GPU. Máy đo có GPU nhưng không dùng.
- **Chỉ số** trên 30 mục tiêu, mỗi mục tiêu xếp 10 gia sư theo `s`; hòa điểm tính về thứ hạng xấu nhất:
  - top-1, top-3 (gia sư khớp nằm trong 3 đầu), MRR (kèm khoảng tin cậy 95% bootstrap 1000 lần), AUC trung bình theo mục tiêu;
  - độ trải của `s` (trung bình cặp khớp, cặp không khớp, min, max), vì BR03 cộng `s` với các thành phần khác;
  - độ trễ mã hóa **một** văn bản trên CPU sau 3 lần làm nóng, 40 văn bản × 3 lần: trung vị và p95; thời gian nạp model; dung lượng model.
- **Thư viện:** venv riêng, chỉ cài bản phát hành trước 06/11/2026 (`pip --uploaded-prior-to`, quy tắc hai tuần), khóa trong [`requirements.txt`](../../scripts/eval/op11/requirements.txt).

## 3. Ngưỡng và cách chọn (chốt trước khi chạy)
Ngẫu nhiên với 10 gia sư: top-3 = 0,30, MRR ≈ 0,29, AUC = 0,50. PH06 hiện tối đa 5 gia sư và `s` chỉ là một thành phần của BR03, nên cần gia sư khớp nằm gần đầu.

Một model **đạt** khi thỏa cả bốn:
1. top-3 ≥ 0,80;
2. MRR ≥ 0,65 và AUC ≥ 0,85;
3. MRR hơn mốc túi từ ít nhất 0,10;
4. p95 mã hóa một văn bản trên CPU ≤ 300 ms.

Trong các model đạt, chọn model **ít chiều nhất** (lưu và tính rẻ nhất); bằng nhau thì p95 thấp hơn. Không model nào đạt: OP-11 vẫn mở, Lát 1 tiếp tục chạy phương án B, ghi lý do.

## 4. Kết quả
Chưa chạy.
