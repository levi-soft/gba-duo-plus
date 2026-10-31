# GBA Duo Plus: tính năng và bộ công cụ companion

**GBA Duo Plus 0.1.9-plus.2** là bản fork cộng đồng do [levi-soft](https://github.com/levi-soft)
duy trì trên nền [GBA Duo v0.1.9 của khumam](https://github.com/khumam/gba-duo).
Ứng dụng dành cho thiết bị Android hai màn hình, Android 11 trở lên, ARM64.
Thiết bị phát triển chính là AYN Thor; các máy hai màn hình khác cần kiểm tra riêng.

**[Tải APK và đọc ghi chú phát hành](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.9-plus.2)** ·
[Website Companion Builder](https://levi-soft.github.io/gba-duo-companion-builder/) ·
[GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio)

## Nền 0.1.9

Source đã ghép kho pack trực tuyến, FPS/CPU, tỉ lệ/phóng nguyên lần, tilt-shift và game.lua/Wide view của upstream 0.1.9. Điều khiển dùng mapping chính thức; bỏ Swap A/B riêng nhưng giữ mapping đã lưu. Builder 1.2.0, chọn unit FE, Studio, shader mở rộng và SS được giữ. Cocoon dùng [hồ sơ package Plus](COCOON-SETUP.vi.md).

## Mới ở 0.1.7-plus.4

Điều khiển companion dùng nút GB/GBA tiêu chuẩn và metadata con trỏ trong pack. Dữ liệu UI hợp lệ gần nhất được giữ khi một lượt Lua lỗi, kèm cờ stale và thông tin lỗi để chẩn đoán. Quản lý/xóa pack, cập nhật tại chỗ, artwork, shader, Studio và các tính năng Plus trước đó tiếp tục được giữ. [Ghi chú phát hành](RELEASE-0.1.7-plus.4.md).

## 1. Tạo companion tự động ngay trong Plus

Trong thư viện, chọn game → **Companion pack → Get from the web…**.
Trang kho dùng luồng tải ZIP chính thức và có thêm **Create companion** khi tìm
được profile khớp chính xác ROM. Mục tạo riêng trong menu đã được bỏ. Khi chọn
tạo, Plus kiểm tra game code/CRC32, tải builder có kiểm tra kích thước/SHA-256,
tạo pack ngay trên thiết bị rồi trả kết quả về thư viện để gán.

- ROM không được tải lên máy chủ và không bị chỉnh sửa.
- Hình ảnh, sprite và văn bản cần cho pack được chuẩn bị từ ROM trên thiết bị.
- Builder đã xác minh được lưu cục bộ để dùng lại khi ngoại tuyến.
- Có thể hủy quá trình tạo. Lỗi hoặc hủy trước khi hoàn tất gán giữ nguyên lựa chọn cũ.
- Tạo lại cùng companion tự động sẽ cập nhật pack cũ; lỗi cập nhật khôi phục pack và lựa chọn trước đó. Pack thủ công/tùy chỉnh giữ ID riêng.
- Nhập và gán ZIP thủ công vẫn có sẵn cho companion khác.

Chỉ ROM khớp profile trong catalogue mới tạo tự động được. Đây là chức năng của
Plus; bản GBA Duo chính thức cung cấp nền tảng pack và luồng nhập ZIP.
Xem [chi tiết tích hợp](AUTOMATIC-COMPANIONS.md).

## 2. GBA Duo Companion Builder: website và dự án riêng

**[Mở Builder](https://levi-soft.github.io/gba-duo-companion-builder/)** ·
[Mã nguồn và hướng dẫn tiếng Việt](https://github.com/levi-soft/gba-duo-companion-builder/blob/main/README.vi.md)

Builder và Plus dùng chung bộ tạo companion. Trên website Chrome/Edge:

1. Chọn tiếng Việt hoặc tiếng Anh.
2. Chọn file `.gba`, hoặc ZIP chứa đúng một file `.gba` được hỗ trợ.
3. Bấm **Build companion** và chờ xử lý cục bộ.
4. Tải Local ZIP để nhập vào Plus, hoặc project JSON để chỉnh sửa trong Studio.

Builder **1.2.0** có **30 profile**, companion **1.0.0**. Xem [danh sách ROM hiện tại](https://levi-soft.github.io/gba-duo-companion-builder/supported-games.html?lang=vi) và [giới hạn kiểm chứng](https://github.com/levi-soft/gba-duo-companion-builder/releases/tag/v1.2.0).

## 3. Ảnh bìa thư viện game

- **Libretro:** nguồn mặc định, tìm ảnh không cần tài khoản hoặc API key.
- **SteamGridDB:** dùng phần tìm/chọn/tải ảnh của bản chính thức bằng API key riêng của bạn. Không còn client hay màn nhập key riêng của Plus.
- **Ảnh trên máy:** nhập PNG, JPEG hoặc WebP.
- Tìm ảnh cho các game còn thiếu tại **Settings → Scraping**; cấu hình SteamGridDB tại **Providers → SteamGridDB**.
- Ảnh lưu trên thiết bị, dùng lại ngoại tuyến và giữ tỷ lệ; hình cartridge gốc
  là phương án hiển thị khi chưa có ảnh bìa.
- Có thể hủy tìm ảnh; việc thay ảnh được chuẩn bị trước khi ghi nhận kết quả.
- Lưu thông tin nguồn/tác giả khi nhà cung cấp có trả về dữ liệu đó.

Xem [hướng dẫn Artwork](ARTWORK.md).

## 4. RetroAchievements và thông báo achievement

Plus kế thừa client RetroAchievements chính thức của GBA Duo 0.1.7 và mở rộng:

- Bảo vệ token bằng Android Keystore; không ghi token dạng rõ khi Keystore lỗi.
- Giao diện xem danh sách achievement.
- Lưu và nạp dữ liệu tiến độ RA kèm save state; sidecar được đưa vào luồng đồng bộ save.
- Thông báo đăng nhập/game, ảnh game và số achievement đã mở trên tổng số.
- Popup mở achievement đặt dưới bên phải, nền có độ đục 70%, giữ cơ chế xếp hàng
  thông báo của bản chính thức. ROM không hỗ trợ có thông báo không có achievement.

Đăng nhập, nhận diện ROM và xử lý achievement dựa trên client gốc/rcheevos.
Bản này dùng Softcore, không có Hardcore hoặc giả mạo hash để nhận game khác.
ROM dịch/mod phải được RetroAchievements nhận diện và hỗ trợ đúng danh tính ROM.

## 5. Shader và điều khiển

- Bổ sung **xBRZ, HQ2x, ScaleFX** qua tích hợp GLES3 từ shader Libretro.
- Giữ các bộ lọc gốc được lựa chọn và cơ chế ghi nhớ shader đã có ở upstream.
- Dùng bộ mapping/hotkey chính thức; lựa chọn A/B cũ được chuyển một lần vào mapping, không còn công tắc riêng.
- Với profile Fire Emblem đã hỗ trợ, companion có thể gọi `input.focusUnit(id)`
  khi chạm nhân vật để điều hướng con trỏ bằng D-pad có giới hạn.
- Điều hướng xét trạng thái game và nhường cho đầu vào tay cầm; đây không phải
  khả năng áp dụng tự động cho mọi game hay ROM hack.

Tác giả thuật toán shader được ghi trong [CREDITS](../CREDITS.md) và file nguồn.

## 6. Hiển thị companion và launcher

- Mở rộng xử lý kích thước, raster grid và hiển thị giữ tỷ lệ cho pack phức tạp.
- Giữ snapshot hợp lệ khi dữ liệu cập nhật chậm, thay vì làm trống nội dung ngay.
- Tối ưu bộ chọn/cache CSS trên nền responsive CSS của bản chính thức.
- Giữ cơ chế consent cho pack ghi RAM; Plus từ chối ghi nếu không tạo được save an toàn.
- Cấu hình **GBA Duo Plus làm custom player trong Daijisho** để mở ROM trực tiếp.
  Plus bổ sung xử lý alias đường dẫn, kiểm tra đầu vào và vòng đời phiên chơi trên
  `LaunchActivity` có sẵn của upstream. Cần tự thêm player theo [hướng dẫn](LAUNCHERS.md).

Khả năng hiển thị và thao tác còn tùy pack, phiên bản ROM và thiết bị.

## 7. GBA Duo Studio và Studio Connection

**[GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio)** là công cụ riêng
để thiết kế/chỉnh sửa companion bằng kéo thả, Inspector, xem trước dữ liệu mẫu
hoặc dữ liệu live và giao diện Việt/Anh. Có thể mở project từ Builder, sửa giao
diện rồi xuất ZIP để nhập vào Plus.

Trong Plus, chọn **Settings → Studio Connection → On**, rồi mở game và kết nối qua USB/ADB để:

- Đọc RAM và snapshot dữ liệu companion.
- Kiểm tra ROM, VRAM, palette và OAM trong phạm vi giới hạn.
- Cho công cụ phát hiện khả năng mà bridge hỗ trợ qua protocol revision 2.

Bridge máy tính này chỉ đọc, không mở cổng LAN và không có lệnh ghi RAM.
Lựa chọn bật/tắt được lưu cho những lần mở game sau, riêng cho Plus và Plus SS.
Quyền ghi RAM dành cho pack của upstream là cơ chế riêng.
Xem [giao thức và cách kết nối](STUDIO-CONNECTION.md).

## 8. Nền tảng được kế thừa từ GBA Duo

GBA Duo của **khumam và cộng tác viên** cung cấp lõi ứng dụng: giả lập GB/GBC/GBA
qua mGBA, hai màn hình, tay cầm, thư viện game, save và chín slot save state,
fast-forward, JSON/CSS/Lua companion, trang/tab/list/button và dữ liệu trực tiếp.
Bản 0.1.7 còn mang đến thư viện/Settings phong cách pixel, đồng bộ thư mục save,
đồ họa ROM sprite/tilemap/LZ77, responsive CSS, giới hạn thực thi Lua và quyền ghi
RAM cho pack. Plus giữ nền tảng và lịch sử gốc này.

Plus có package riêng `com.khumam.gbaduo.plus`, cài song song với bản chính thức.
Save, cài đặt, quyền thư mục và tài khoản của hai ứng dụng không tự chuyển qua lại.
Bản Plus thường dùng hai màn hình. [Plus SS](SINGLE-SCREEN.md) là APK riêng dành cho điện thoại, tablet và màn gập, phát hành cùng phiên bản.

## 9. Bản phát hành và kiểm chứng

[Release 0.1.7-plus.2](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.7-plus.2)
cung cấp APK ARM64 đã ký và SHA-256. APK được build từ source công bố, bao gồm
các sửa lỗi hủy/tạo pack và ảnh bìa từ đợt rà soát cuối. Mã phiên bản Android là `10`.

Đợt kiểm tra trước phát hành ghi nhận **364/364 test Debug**, **197/197 test Release
được CI lựa chọn**, build Release và lint thành công. CI GitHub còn chạy kiểm tra
native sandbox/graphics. Những con số này không thay cho kiểm tra từng thiết bị,
game, hiệu năng shader hoặc việc nhận achievement thực tế.
Xem [bằng chứng và giới hạn kiểm thử](VALIDATION.md).

## Tri ân

Xin cảm ơn **[khumam](https://github.com/khumam)** và cộng đồng **GBA Duo** đã tạo
ứng dụng, kiến trúc hai màn hình và hệ thống companion. Cảm ơn **endrift/mGBA**,
**Lua.org/PUC-Rio**, **RetroAchievements/rcheevos và tác giả achievement**,
**các tác giả shader Libretro**, **Libretro thumbnails**, **SteamGridDB và họa sĩ**,
các dự án font, Android và thư viện nền tảng, tác giả pack và người thử nghiệm.

Danh sách nguồn, tác giả và giấy phép đầy đủ: [CREDITS](../CREDITS.md) và
[THIRD-PARTY-NOTICES](../THIRD-PARTY-NOTICES.md). Plus là fork cộng đồng GPL v3;
không đại diện cho bản phát hành chính thức của khumam.
