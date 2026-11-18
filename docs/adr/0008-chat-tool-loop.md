# ADR 0008 — Chat dùng vòng lặp công cụ tường minh

- Trạng thái: Accepted
- Bối cảnh & lý do: Cần giới hạn số bước, phân quyền công cụ trước khi đưa vào prompt và test được bằng LLM giả; mỗi bước LLM trả JSON {tool,args} hoặc final.
- Phương án đã cân nhắc: @Tool và vòng lặp nội bộ của Spring AI (khó kiểm soát số bước và phân quyền theo vai trò)

Xem `docs/architecture.md` mục 13.
