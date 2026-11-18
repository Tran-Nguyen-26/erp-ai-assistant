# ADR 0010 — Rule SQL dùng tham số cho phép và tên file rule-*.sql

- Trạng thái: Accepted
- Bối cảnh & lý do: Rule liên chứng từ vẫn phải thuần đọc: chỉ nhận tham số lấy từ chứng từ hoặc params của rule, truyền dạng tham số có tên, và sqlRef phải khớp rule-<kebab>.
- Phương án đã cân nhắc: Cho rule tự viết SQL tự do (rủi ro injection và đọc file tùy ý)

Xem `docs/architecture.md` mục 13.
