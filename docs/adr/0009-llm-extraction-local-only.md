# ADR 0009 — Trích xuất hóa đơn bằng LLM chỉ chạy với LLM local

- Trạng thái: Accepted
- Bối cảnh & lý do: Ảnh/PDF hóa đơn không thể che PII, còn che MST và tên đối tác sẽ phá chính dữ liệu cần trích xuất; áp dụng nguyên tắc dữ liệu nhạy cảm không rời hệ thống.
- Phương án đã cân nhắc: Che rồi gửi LLM cloud; chỉ hỗ trợ XML

Xem `docs/architecture.md` mục 13.
