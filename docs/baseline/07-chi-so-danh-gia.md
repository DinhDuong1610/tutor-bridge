# Chỉ số và đánh giá sản phẩm

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).

## 10 Chỉ số và đánh giá sản phẩm

## 10.1 Quy tắc đánh giá hai mô đun cốt lõi

Phỏng vấn tối thiểu 10 phụ huynh và 10 gia sư nhằm khám phá nhu cầu, không coi là mẫu đại diện. Dương Đính ghi quy trình tìm và theo dõi gần nhất; Đức Huy ghi quy trình soạn, nguồn và thiết bị. Lưu ghi chép được phép, kết luận và thay đổi yêu cầu; không gắn mốc hoàn thành khi chưa có bằng chứng.

Matching: trước đo phải khóa rubric, tập phát triển và tập đánh giá giữ riêng. Khởi điểm đánh giá nội bộ tối thiểu 30 nhu cầu với danh sách ứng viên có nhãn 0–3; cần ít nhất hai người thẩm định chuyên môn thảo luận bất đồng. So S với B cùng điều kiện cứng, cùng tập ứng viên. NDCG@5 dùng gain 2^nhãn−1; ca IDCG=0 báo riêng, không tự tính điểm hoàn hảo. Dữ liệu mô phỏng được ghi rõ và chỉ kết luận về kịch bản thử.

Ngưỡng khám phá cho matching: 100% kết quả qua điều kiện cứng; tỷ lệ ứng viên được gán nhãn ≥2 trên tổng ứng viên thực trả tối thiểu 80%; NDCG@5 trung bình của S cao hơn B ít nhất 0,05 trên nhóm có IDCG>0. Báo số nhu cầu, số trả, số rỗng, độ phân tán và bất đồng nhãn. Nếu không đạt, tiếp tục danh sách B có nhãn, thu hẹp hoặc sửa và đánh giá lại; không tuyên bố AI tốt hơn.

Soạn bài: tối thiểu 20 cặp tác vụ tương đương, ít nhất 3 gia sư; đảo thứ tự và chia nhiệm vụ để giảm lợi thế làm lại. Đo toàn thời gian từ bắt đầu đến bản dùng được. Chấm 1–5 ở độ đúng Toán, phù hợp mục tiêu, khả thi thời lượng và mức hỗ trợ nguồn; người thẩm định che phương pháp tạo khi khả thi. Không chấp nhận lỗi nghiêm trọng trong bản đã duyệt.

Ngưỡng khám phá: trung vị thời gian đến bản dùng được giảm ≥20%; từng tiêu chí chất lượng đạt trung bình ≥4/5 và không thấp hơn thủ công quá 0,25 điểm; không có lỗi Toán nghiêm trọng/nguồn vượt quyền trong bản được công bố. Báo tỷ lệ sửa nhỏ/sửa lớn/loại, thời gian và chi phí cả thử lại. Đây là tiêu chí lựa chọn thí điểm, chưa là chứng minh hiệu quả thống kê hoặc tăng kết quả học tập.

## 10.2 Chỉ số phễu duy trì và chi phí

Bảng 11 Định nghĩa chỉ số sản phẩm

| Chỉ số | Định nghĩa và mẫu số |
|---|---|
| Phễu ghép lớp | Nhu cầu xác nhận → có kết quả → gửi → chốt lớp; mỗi bước báo số tuyệt đối và mẫu số của cùng nhóm. Tách loại AI/B và nguyên nhân thiếu nguồn cung. |
| Ghép trong 7 ngày | Nhóm nhu cầu hợp lệ có đủ 7 ngày theo dõi: số có lớp xác nhận trong 7 ngày / tổng nhóm. Không loại nhu cầu thất bại/hủy sau đăng để tăng tỷ lệ. |
| Duy trì 4 tuần | Nhóm lớp đã bắt đầu và đủ 28 ngày: số có buổi hoàn thành ở ngày 22–28 / tổng nhóm. Nêu lớp kết thúc sớm và lý do; thí điểm ngắn chưa đủ kết luận. |
| Thời gian chọn | Từ bắt đầu xem kết quả đến chọn hoặc kết thúc tác vụ; so cùng kịch bản B, ghi riêng ca bỏ cuộc và timeout. |
| Tỷ lệ phù hợp | Số ứng viên trả được thẩm định phù hợp / số thực trả; không dùng mẫu số 5 khi chỉ có 1–4 người. Báo riêng nhu cầu rỗng. |
| Quay lại soạn | Trong nhóm gia sư đã duyệt bản đầu và đủ 14 ngày: số có lần duyệt thứ hai trong ngày 2–14 / tổng nhóm; ghi riêng người không có buổi cần soạn tiếp. |
| Cập nhật buổi | Số buổi có nhật ký và đủ xác nhận / số buổi đã đến hạn trong kỳ; báo riêng vắng/hủy/chờ/tranh chấp, không suy ra năng lực. |
| Chi phí AI | Tổng chi thực tế gồm lỗi/thử lại / số lần tạo và / số bản duyệt, báo cả hai. Trần theo cấu hình duyệt trước bật; không dùng giá giả định như hóa đơn thật. |

Mẫu số bằng 0 là không áp dụng. Nhóm chưa đủ thời gian theo dõi được báo riêng, không gộp vào tỷ lệ của nhóm đã đủ kỳ. Trước thử nghiệm phải lưu phiên bản kế hoạch, cỡ mẫu mục tiêu và cách loại trừ; khi mẫu nhỏ hoặc thiếu dữ liệu, ghi rõ hạn chế, không chỉnh tiêu chí sau khi thấy kết quả.
