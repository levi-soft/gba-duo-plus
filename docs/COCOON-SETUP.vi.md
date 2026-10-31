# Chọn GBA Duo Plus trong Cocoon

Cocoon nhận emulator từ danh sách player của từng platform, gồm tên package và
Activity cụ thể. Mục GBADuo chính thức trỏ tới `com.khumam.gbaduo`; nó không tự
nhận `com.khumam.gbaduo.plus` hoặc `com.khumam.gbaduo.plus.ss`. Việc Plus mở được
ROM qua intent chưa chứng minh nó đã xuất hiện trong danh sách emulator.

Bộ profile ở đây thêm **GBA Duo Plus** và **GBA Duo Plus SS** cho ba platform
**Game Boy, Game Boy Color và Game Boy Advance**. Nó dùng đúng đường mở game của
bản chính thức: `VIEW` tới `LaunchActivity`, truyền `{file.uri}`. Plus vẫn giữ
package và dữ liệu ứng dụng hiện tại.

## Tải profile có sẵn

Tải file cho platform cần dùng từ bản phát hành 0.1.9-plus.2:
[Game Boy](https://github.com/levi-soft/gba-duo-plus/releases/download/v0.1.9-plus.2/GBA-Duo-Plus-GB.json),
[Game Boy Color](https://github.com/levi-soft/gba-duo-plus/releases/download/v0.1.9-plus.2/GBA-Duo-Plus-GBC.json),
[Game Boy Advance](https://github.com/levi-soft/gba-duo-plus/releases/download/v0.1.9-plus.2/GBA-Duo-Plus-GBA.json).
Nhập JSON qua giao diện Cocoon như hướng dẫn bên dưới.

## Tạo ba file import từ mã nguồn

Tại thư mục mã nguồn Plus, chạy:

```sh
node tools/cocoon/build-profiles.mjs
```

Ba file nằm trong `reports/cocoon-import-profiles-0.1.9/`:

- `GBA-Duo-Plus-GB.json`
- `GBA-Duo-Plus-GBC.json`
- `GBA-Duo-Plus-GBA.json`

Mỗi file chỉ có hai player mới. ID và metadata platform giữ theo định nghĩa
Cocoon được khóa bằng commit/hash; không đưa các player cũ vào để ghi đè cấu
hình riêng. Các file không đặt emulator mặc định hay đường dẫn ROM.

## Nhập trong Cocoon

1. Cài bản Plus hoặc Plus SS bạn muốn dùng. Thêm thư mục ROM trong Plus một lần
   để cấp quyền đọc thư mục Android.
2. Trong **Settings → System & Advance → Backup**, xuất dữ liệu đang có trước
   khi thử import. Cocoon 3.11 trên Thor chỉ hiện **Game Data**, **Appearance** và
   **Layout & Folders**; chọn **Game Data** và **Layout & Folders**. Nếu bản Cocoon
   của bạn có **Sources & Players**, xuất thêm mục đó. Ghi lại player mặc định và
   thư mục ROM của platform vì bản backup thực tế trên Thor không chứa cấu hình
   nguồn/player. Không cần xóa platform, quét lại toàn bộ hay khôi phục thư viện.
3. Mở **Cocoon Settings → Library & data**, cuộn xuống danh sách platform rồi
   bấm nút **Y / +**. Trong hộp **Add Platforms**, cuộn đến cuối và chọn
   **Import Custom Platform**, có mô tả **Load a Daijisho-compatible JSON file**.
4. Chọn file JSON của platform cần dùng. Lặp lại với GB/GBC/GBA nếu cần cả ba.
   Chờ việc import hoàn tất rồi đóng hộp **Add Platforms**. Platform hiện trong
   danh sách bên dưới nút **Y / +**.
5. Mở platform tương ứng và chọn **Default player → GBA Duo Plus** hoặc
   **GBA Duo Plus SS**. Chỉ chọn edition đã cài. Nếu một game có **Player
   Override**, đổi riêng game đó hoặc bỏ override để theo mặc định. Bấm dấu
   **Y / ✓** góc trên phải để lưu; Cocoon hiện **Changes saved**.
6. Thử **Settings → System & Advance → Restart Cocoon**, mở lại danh sách để
   kiểm tra lựa chọn đã lưu. Sau đó mở một game từ Cocoon và kiểm tra đúng game,
   save và companion.

Nếu GB/GBC chưa có trong thư viện, import sẽ thêm platform đó và Cocoon có thể
tự chọn player đầu tiên (**GBA Duo Plus**). Chưa có thư mục ROM cho platform mới
cho đến khi bạn tự chọn. Với platform đã có, kiểm tra lại player và thư mục hiện
tại trước khi thay lựa chọn.

Mã import của Cocoon 3.11 đọc các player đã có, ghép với mục được nhập theo ID
và dùng bộ máy cập nhật platform của Cocoon. Bộ tạo profile không sửa trực tiếp
database, thư viện, lựa chọn mặc định hoặc cấu hình của Cocoon trên thiết bị.

## Phạm vi đã kiểm tra

Đã import cả ba JSON bằng giao diện Cocoon **3.11** trên **AYN Thor** ngày
**31/10/2026**. Dropdown của GB/GBC/GBA đều hiện **GBA Duo Plus**, **GBA Duo Plus
SS** và **GBADuo**. GBA giữ player **GBADuo** ngay sau import; sau khi chủ động
đổi sang normal Plus và lưu, lựa chọn vẫn còn sau **Restart Cocoon**.

Thư mục GBA vẫn là `primary:Roms/gba`. Player/thư mục của NDS và Switch vẫn giữ
nguyên. Bốn file `gamelist.xml` GBA/NDS/pod/Switch có SHA-256 giống hệt trước và
sau import/restart. GB/GBC là hai platform mới, chưa có thư mục ROM. Đợt kiểm tra
profile ban đầu chưa khởi chạy ROM. Sau đó bản Plus `0.1.9-plus.1` đã được mở
Maiden qua menu Launch của Cocoon: hai lượt khởi động mới lên màn tiêu đề, nhận
đúng CRC/pack và không cần bấm Resume. Save/state đã sao lưu vẫn khớp sau khi
thoát. Đây chưa phải kiểm tra chơi hết game hay mọi kiểu mở lại phiên đang chạy;
xem [phạm vi kiểm tra](VALIDATION.md).

Đây là profile import cục bộ, không phải xác nhận Plus đã được thêm vào catalog
mặc định của tác giả Cocoon. Không có issue, PR hoặc thông báo nào được gửi.

Nguồn: [Cocoon player definitions](https://github.com/inssekt/CocoonFE/tree/3c2b6785a024e7ae73d4bbb2b331ff7519a9b7e1/platforms),
[hướng dẫn chọn emulator](https://cocoon-shell.com/wiki/emulator-setup/),
[hướng dẫn backup của Cocoon](https://cocoon-shell.com/wiki/backup-and-restore/).
