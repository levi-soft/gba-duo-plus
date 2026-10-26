# Đưa GBA Duo Plus, Plus SS và Companion Builder vào Google

Website đã có nội dung HTML, tiêu đề, mô tả, canonical, dữ liệu cấu trúc và sitemap.
Các phần này giúp máy tìm kiếm hiểu dự án. Chúng không bảo đảm Google lập chỉ mục
hoặc đưa website lên vị trí cụ thể.

## 1. Thêm hai website vào Search Console

Mở [Google Search Console](https://search.google.com/search-console/welcome), đăng nhập
tài khoản Google của bạn và chọn **URL prefix / Tiền tố URL**. Không chọn Domain,
vì bạn không quản lý DNS của `github.io`.

Thêm riêng hai thuộc tính, giữ đúng dấu `/` cuối:

- `https://levi-soft.github.io/gba-duo-plus/`
- `https://levi-soft.github.io/gba-duo-companion-builder/`

Trang Plus SS thuộc website Plus nên không cần thuộc tính thứ ba.

## 2. Xác minh quyền sở hữu

Chọn **HTML file / Tệp HTML**. Google cung cấp một tệp dạng
`google0123456789abcdef.html`. Cần xuất bản tệp nguyên vẹn ở thư mục gốc của website
đang xác minh, mở được URL mà Google chỉ định, rồi bấm **Verify / Xác minh**.

Bạn có thể gửi tệp hoặc tên tệp và nội dung xác minh cho Codex để thêm đúng website.
Không gửi mật khẩu Google, cookie đăng nhập hay mã xác thực hai bước.

- Với Plus: tệp đặt trong `site/` của repo `gba-duo-plus`; workflow Pages tự xuất bản.
- Với Builder: tệp đặt trong `site/` của repo `gba-duo-companion-builder` và xuất bản
  sang nhánh `gh-pages`. Giữ nguyên catalog và bundle Builder khi chỉ thêm tệp xác minh.

Để tệp xác minh trên website sau khi xác minh thành công.

## 3. Gửi sitemap

Trong từng thuộc tính, mở **Sitemaps / Sơ đồ trang web** và gửi URL tương ứng:

- Plus: `https://levi-soft.github.io/gba-duo-plus/sitemap.xml`
- Builder: `https://levi-soft.github.io/gba-duo-companion-builder/sitemap.xml`

## 4. Yêu cầu lập chỉ mục các trang chính

Dùng **URL inspection / Kiểm tra URL**, chọn kiểm tra URL đang hoạt động, rồi
**Request indexing / Yêu cầu lập chỉ mục** cho:

1. `https://levi-soft.github.io/gba-duo-plus/`
2. `https://levi-soft.github.io/gba-duo-plus/single-screen/`
3. `https://levi-soft.github.io/gba-duo-companion-builder/`
4. `https://levi-soft.github.io/gba-duo-companion-builder/supported-games.html`

## 5. Theo dõi

Kiểm tra báo cáo **Pages / Trang** để biết Google đã lập chỉ mục chưa. Sau khi có
lượt hiển thị, dùng **Performance / Hiệu suất** để xem các truy vấn như “GBA Duo Plus”,
“GBA Duo Plus SS” và “GBA Duo Companion Builder”.

Google có thể mất vài ngày đến vài tuần để thu thập lại trang. Gửi yêu cầu nhiều lần
không làm quá trình nhanh hơn. Một lần tìm kiếm chưa thấy kết quả không đủ để kết
luận website bị chặn hoặc có lỗi.

Nguồn: [yêu cầu Google thu thập lại URL](https://developers.google.com/search/docs/crawling-indexing/ask-google-to-recrawl),
[tạo và gửi sitemap](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap).
