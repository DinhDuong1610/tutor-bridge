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
Chạy ngày 20/11/2026 trên máy của Huy: i7-12700H (14 nhân, 20 luồng; torch dùng 14 luồng), RAM 15,6 GB, Windows 11, Python 3.14.10, torch 2.15.0, sentence-transformers 6.2.0, transformers 5.22.1, chỉ CPU. Số liệu đầy đủ, kể cả từng mục tiêu: [`2026-11-20_op11_results.json`](2026-11-20_op11_results.json).

| | Chiều | top-1 | top-3 | MRR (KTC 95%) | AUC | p95 / trung vị (ms) | Nạp (s) |
|---|---|---|---|---|---|---|---|
| Mốc túi từ | — | 0,60 | 0,87 | 0,744 (0,628–0,856) | 0,911 | — | — |
| `multilingual-e5-small` @`614241f622`, MIT | 384 | 0,50 | 0,80 | 0,665 (0,533–0,787) | 0,844 | 24,6 / 18,5 | 6,8 |
| `vietnamese-bi-encoder` @`84f9d9ada0`, Apache-2.0 | 768 | 0,53 | 0,77 | 0,675 (0,532–0,794) | 0,833 | 114,6 / 76,9 | 4,0 |
| `bge-m3` @`5617a9f61b`, MIT | 1024 | 0,63 | 0,87 | 0,777 (0,660–0,879) | 0,907 | 357,8 / 219,0 | 14,3 |

Giấy phép đọc từ model card trên Hugging Face lúc chạy. Dung lượng snapshot (tải cả các bản ONNX/OpenVINO đi kèm): e5-small 2,2 GB, bi-encoder 1,0 GB, bge-m3 4,4 GB.

Độ trải của `s` (trung bình cặp khớp / cặp không khớp; min–max):
- e5-small: 0,931 / 0,919; 0,895–0,947. Khoảng cách chỉ khoảng 0,01.
- bi-encoder: 0,658 / 0,580; 0,461–0,757.
- bge-m3: 0,806 / 0,762; 0,686–0,858.

Đối chiếu ngưỡng mục 3:

| | top-3 ≥ 0,80 | MRR ≥ 0,65, AUC ≥ 0,85 | Hơn mốc ≥ 0,10 MRR | p95 ≤ 300 ms | Đạt |
|---|---|---|---|---|---|
| e5-small | đạt | **AUC 0,844** | **−0,079** | đạt | không |
| bi-encoder | **0,77** | **AUC 0,833** | **−0,069** | đạt | không |
| bge-m3 | đạt | đạt | **+0,033** | **357,8** | không |

## 5. Kết luận
- **Không model nào đạt ngưỡng đã chốt trước.** Theo mục 3, OP-11 vẫn mở và Lát 1 tiếp tục chạy phương án B (BR03). Không có gì bị chặn.
- **Bộ dữ liệu chưa đủ khó.** Mốc túi từ đạt MRR 0,744, cao hơn hai model nhỏ: mục tiêu vẫn trùng nhiều chữ với mô tả khớp dù đã cố tránh. Với 30 mục tiêu, khoảng tin cậy của mọi phương án chồng lên nhau, nên số đo này **không phân biệt được** các model. Đây là hạn chế của bộ dữ liệu, không phải kết luận về chất lượng model.
- **e5-small dồn `s` vào khoảng 0,89–0,95.** Nếu dùng, `s` gần như hằng số trong BR03, trừ khi chuẩn hóa lại. Đây là điểm cần nhớ khi chọn model, độc lập với bộ dữ liệu.
- **bge-m3 tốt nhất trên bộ này nhưng chậm:** p95 358 ms trên CPU này, 1024 chiều, 4,4 GB. Độ trễ phụ thuộc máy chạy worker thật.
- Cả ba model và mốc túi từ đều trượt G04, G06 (mục tiêu thi học sinh giỏi viết không có từ khóa).
- **Độ trễ chỉ tính bước mã hóa.** Tiền tố và tách từ `pyvi` (bi-encoder) chạy trước khi bấm giờ, nên p95 của bi-encoder chưa gồm thời gian tách từ mà worker thật phải làm. Kết luận không đổi vì bi-encoder trượt ở các ngưỡng khác; lần 2 nên bấm giờ cả bước chuẩn bị văn bản. (Ghi sau khi tự rà PR, 20/11/2026; giao thức mục 2 giữ nguyên.)

## 6. Bước tiếp theo đề xuất (agent theo ủy quyền của Huy, chờ Đính)
1. Bộ dữ liệu lần 2 do **người khác người dựng nhãn** viết mục tiêu (ví dụ Đính viết mục tiêu, agent viết mô tả), khoảng 100 mục tiêu, vẫn là dữ liệu giả, chốt trước khi chạy như lần này.
2. Đo lại độ trễ trên máy sẽ chạy worker khi đã biết máy.
3. Giữ nguyên ngưỡng mục 3 cho lần 2, trừ khi Đính sửa khi rà PR này.
