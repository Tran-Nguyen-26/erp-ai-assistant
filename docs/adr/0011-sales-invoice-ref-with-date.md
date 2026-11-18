# ADR 0011 — Khóa hóa đơn bán hàng dạng GUID@yyyy-MM-dd

- Trạng thái: Accepted
- Bối cảnh & lý do: PK của SAInvoice là (Date, ID) và dòng nối qua DatePartition; kèm ngày vào khóa để tra cứu theo index thay vì quét bảng hàng chục triệu dòng.
- Phương án đã cân nhắc: Chỉ dùng GUID (chậm); tra thêm bảng ánh xạ (phức tạp)

Xem `docs/architecture.md` mục 13.
