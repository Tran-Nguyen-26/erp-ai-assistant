# ADR 0012 — Vô hiệu hóa `<` `>` trong dữ liệu không tin cậy đưa vào prompt

- Trạng thái: Accepted
- Bối cảnh & lý do: Nguyên tắc #8 yêu cầu dữ liệu từ chứng từ nằm trong delimiter. Nhưng dữ liệu (diễn giải, tên đối tượng, văn bản hóa đơn, nội dung tài liệu, câu hỏi chat) được nối nguyên văn nên có thể chứa `</erp_data>` để đóng vùng dữ liệu sớm rồi chèn chỉ dẫn giả. Phát hiện khi review; test tái hiện lỗi trước khi vá.
- Quyết định: `PromptSafety.data()` đổi `<` `>` thành ký tự full-width `＜` `＞` cho mọi giá trị không tin cậy ở 4 luồng (giải thích, chat, trích xuất hóa đơn, gợi ý tài khoản). Chuẩn hóa so khớp trích dẫn cũng đổi ngược về `<` `>` để trích dẫn LLM chép lại vẫn khớp nội dung gốc (nguyên tắc #4).
- Phương án đã cân nhắc: HTML-escape (`&lt;`) — làm xấu nội dung và LLM hay chép sai; lọc theo danh sách thẻ — dễ sót thẻ mới; dùng delimiter ngẫu nhiên mỗi lượt — phức tạp hơn và vẫn cần escape.
- Hệ quả: nội dung có `<` (ví dụ "giá trị < 20 triệu") hiển thị cho LLM dạng `＜`; người dùng cuối không bị ảnh hưởng vì phản hồi lấy từ LLM + trích dẫn đã đối chiếu. Đây là một lớp phòng thủ, không thay thế việc kiểm chứng đầu ra (citation, rule).

Xem `docs/architecture.md` mục 13.
