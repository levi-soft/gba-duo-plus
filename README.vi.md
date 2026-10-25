# GBA Duo Plus

**Bản fork cộng đồng của [GBA Duo](https://github.com/khumam/gba-duo), dự án gốc do [khumam](https://github.com/khumam) và các cộng tác viên phát triển.**

Xin cảm ơn khumam và đội ngũ GBA Duo đã tạo ra ứng dụng Android, ý tưởng chơi
game trên một màn hình và companion tương tác trên màn hình còn lại, cùng định
dạng pack, Lua và hệ thống giao diện CSS. Đây là nền tảng của Plus. Lịch sử Git,
thông tin tác giả và các thông báo bản quyền gốc được giữ lại.

Plus do [levi-soft](https://github.com/levi-soft) duy trì, phát triển thêm trên
nền GBA Duo chính thức **v0.1.7**. Đây là bản fork độc lập, không phải bản phát
hành chính thức hay dự án được tác giả gốc bảo trợ.

**Phiên bản mã nguồn:** `0.1.7-plus.4` · **Giấy phép:** [GPL v3](LICENSE)

**[Tải GBA Duo Plus 0.1.7-plus.4 — APK và thông tin phát hành](https://github.com/levi-soft/gba-duo-plus/releases/tag/v0.1.7-plus.4)**

**[GBA Duo Companion Builder — tạo companion từ ROM](https://levi-soft.github.io/gba-duo-companion-builder/)** · [Mã nguồn Builder](https://github.com/levi-soft/gba-duo-companion-builder) · [GBA Duo Studio — thiết kế companion trực quan](https://github.com/levi-soft/gba-duo-studio)

**[Giới thiệu đầy đủ tính năng Plus và hướng dẫn sử dụng](docs/PLUS-FEATURES.vi.md)** · [Chi tiết bản 0.1.7-plus.4](docs/RELEASE-0.1.7-plus.4.md)

[English](README.md) · [So sánh tính năng](docs/FEATURES.md) · [Hướng dẫn build](docs/BUILDING.md) · [Tri ân và tác giả](CREDITS.md)

## Mới trong 0.1.7-plus.4

Companion có thể gửi các nút GB/GBA tiêu chuẩn cho game khớp profile. Chọn unit trên Map dùng metadata của pack, giúp thêm ROM tương thích mà không cần thêm whitelist APK. Khi một lượt Lua lỗi, Plus giữ dữ liệu hợp lệ gần nhất và ghi rõ trạng thái cũ/lỗi trong dữ liệu chẩn đoán, tránh làm trắng companion. [Ghi chú phát hành](docs/RELEASE-0.1.7-plus.4.md).

## Plus bổ sung những gì?

- **Tạo companion tự động:** tìm profile phù hợp với đúng game code và CRC32,
  tải builder đã kiểm tra, tạo pack từ ROM ngay trên thiết bị rồi cài và gán cho
  game. ROM không được tải lên máy chủ. Builder đã kiểm tra được lưu để dùng
  lại khi ngoại tuyến; vẫn có thể nhập ZIP thủ công.
- **Ảnh bìa thư viện:** tìm ảnh qua Libretro không cần tài khoản, chọn ảnh từ
  SteamGridDB bằng API key riêng hoặc nhập PNG/JPEG/WebP. Ảnh giữ đúng tỷ lệ,
  được lưu trên máy và có thể dùng ngoại tuyến; hỗ trợ tìm ảnh còn thiếu.
- **Mở rộng RetroAchievements:** dùng client chính thức từ GBA Duo 0.1.7, thêm
  lưu token mã hóa, danh sách achievement, dữ liệu tiến độ đi kèm save state và
  phần trình bày thông báo của Plus. Đăng nhập và nhận diện ROM là chức năng của
  client gốc được kế thừa.
- **Shader:** bổ sung xBRZ, HQ2x và ScaleFX từ các tác giả được ghi nhận trong
  bộ shader Libretro; Plus thực hiện phần tích hợp trên Android GLES3.
- **Điều khiển:** ghi nhớ Swap A/B; hỗ trợ chạm chọn nhân vật để di chuyển con
  trỏ ở các profile Fire Emblem đã được xác minh.
- **Kết nối Studio:** bật theo từng phiên để công cụ máy tính đọc RAM, dữ liệu
  companion và kiểm tra ROM/VRAM/palette/OAM có giới hạn qua USB/ADB.
- **Launcher và bảo trì:** hỗ trợ cấu hình player Daijisho; mã mở rộng Plus
  được đặt trong `plus/`, có danh mục các điểm nối với mã chính thức để phục vụ
  việc cập nhật upstream.

## GBA Duo Companion Builder

[Mở website Builder](https://levi-soft.github.io/gba-duo-companion-builder/) để tạo
companion ngay trong Chrome/Edge: chọn ROM, bấm **Build companion**, tải ZIP để
nhập vào ứng dụng hoặc tải project JSON để mở trong GBA Duo Studio. Website có
giao diện tiếng Việt/Anh. Plus dùng chung bộ builder này để tạo, cài và gán pack
ngay trong ứng dụng qua **Companion → Find and create companion**.

ROM, hình ảnh và văn bản trích từ game được xử lý trên máy, không gửi lên máy chủ.
ROM gốc không bị sửa. Builder đã tải và kiểm tra được lưu để dùng lại ngoại tuyến.

### Builder 1.1.0 — mười profile ROM chính xác

Bảy companion tiếng Anh được chốt **1.0.0**, cùng ba companion Việt hóa **1.0.0**. Builder **1.1.0** bổ sung bảy profile cho website và chức năng tạo tự động trong Plus. Sau này chỉ tăng Builder khi thêm profile; sửa companion dùng phiên bản riêng.

| Profile | Game code | CRC32 | Companion |
|---|---|---|---|
| FE7 Vietnamese | `AE7E` | `32CD990D` | 1.0.0 |
| FE6 Vietnamese | `BE8E` | `BD53D5F3` | 1.0.0 |
| FE8 Vietnamese | `BE8E` | `3CD932D7` | 1.0.0 |
| FE7 English | `AE7E` | `2A524221` | 1.0.0 |
| FE8 Europe | `BE8P` | `B3005195` | 1.0.0 |
| FE8 English - USA/Australia | `BE8E` | `A47246AE` | 1.0.0 |
| FE6 T-EN | `AFEJ` | `197DE10B` | 1.0.0 |
| Project Ember | `AFEJ` | `636F7B05` | 1.0.0 |
| Project Ember Reverse Recruitment | `AFEJ` | `29BC2257` | 1.0.0 |
| Void's Blitzarre Adventure | `BE8E` | `9D22107C` | 1.0.0 |

FE6 T-EN là FE6 gốc; FE6 Việt hóa Remastered là ROM hack trên nền FE8. ROM khác hoặc bản vá khác cần profile riêng. Các profile tiếng Anh dùng UI, Growth và Analysis tiếng Anh. Void hỗ trợ sáu ô skill và Talk; lớp phủ phạm vi trên Map hiện chưa tính skill thay đổi tầm/di chuyển. Xem [phạm vi và kiểm chứng](https://github.com/levi-soft/gba-duo-companion-builder/blob/main/docs/RELEASE-1.1.0.md).


## Những gì được kế thừa từ bản chính thức

Giả lập GB/GBC/GBA bằng mGBA, bố trí hai màn hình, điều khiển bằng tay cầm,
thư viện game, Settings, save/save state, đồng bộ thư mục save, định dạng
companion JSON/CSS/Lua, API đồ họa và giới hạn thực thi Lua đều dựa trên GBA Duo.
Lưu lựa chọn shader cũng đã có trong bản chính thức; Plus mở rộng bộ shader.

Companion phụ thuộc phiên bản ROM và bố cục bộ nhớ của từng game. Tạo tự động
chỉ áp dụng cho những ROM được catalogue liệt kê, không phải mọi ROM hoặc bản
dịch/mod. Bản này được thiết kế cho thiết bị Android **hai màn hình**, Android
11 trở lên, ARM64; thiết bị phát triển chính là AYN Thor.

## Sử dụng

1. Lấy APK Plus được người duy trì phát hành tại
   [Releases](https://github.com/levi-soft/gba-duo-plus/releases), hoặc tự build
   theo [hướng dẫn](docs/BUILDING.md).
2. Thêm thư mục ROM của bạn trong ứng dụng và chọn game.
3. Mở menu Companion để tạo pack tự động, nhập ZIP hoặc gán pack có sẵn.
4. Chọn Artwork để tìm hoặc nhập ảnh bìa; cấu hình nguồn tại Settings → Artwork sources.
5. Dùng L2/BACK trong game để mở menu save state, shader, Swap A/B và RetroAchievements.

Plus có application ID riêng `com.khumam.gbaduo.plus` và cài tách biệt với bản
chính thức. Save, cài đặt, phiên đăng nhập và quyền thư mục của hai ứng dụng
không tự di chuyển qua lại.

## Tri ân

- **khumam và cộng đồng GBA Duo:** ứng dụng, kiến trúc hai màn hình, companion,
  định dạng pack và các công cụ/thư viện gốc.
- **Vicki Pfau (endrift) và cộng đồng mGBA:** lõi giả lập.
- **Lua.org / PUC-Rio:** ngôn ngữ Lua và thư viện thực thi.
- **RetroAchievements, rcheevos và tác giả các bộ achievement:** dịch vụ,
  runtime, cơ sở dữ liệu, huy hiệu và nội dung achievement.
- **Hyllian, Zenju, Lior Halphon, hunterk, Sp00kyFox và Libretro:** các thuật
  toán shader và phần chuyển đổi được ghi trong mã nguồn tương ứng.
- **Libretro thumbnails, SteamGridDB, các họa sĩ và người đóng góp ảnh:** nguồn
  ảnh bìa và dữ liệu ghi nhận tác giả khi dịch vụ cung cấp.
- **IBM Plex Mono, Silkscreen, Android, AndroidX, Compose, Material Components,
  Kotlin và các công cụ phát triển:** font, nền tảng và thư viện hỗ trợ.
- **Tác giả companion và người thử nghiệm:** bố cục, script, báo lỗi và phản hồi.

Danh sách tác giả, phiên bản, nguồn và giấy phép chi tiết nằm trong
[CREDITS.md](CREDITS.md) và [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
Plus tiếp tục dùng GPL v3; từng thành phần bên thứ ba giữ thông báo và giấy
phép của tác giả. Kho này không chứa ROM, BIOS, save cá nhân, API key hoặc khóa ký.

Kết quả build/kiểm thử và những phần chưa kiểm chứng trên thiết bị được ghi rõ
trong [VALIDATION.md](docs/VALIDATION.md).
