# GBA Duo Plus 0.1.7-plus.2

Bản Plus trên nền **GBA Duo chính thức v0.1.7**, dành cho thiết bị Android **hai màn hình**, Android 11 trở lên, **ARM64**. Phiên bản trong ứng dụng là `0.1.7-plus.2`, version code `10`.

## Tải và cài

Tải **GBA-Duo-Plus-0.1.7-plus.2.apk** trong Assets bên dưới. File **SHA256SUMS.txt** cung cấp mã kiểm tra APK. Đây là APK Release đã ký với package `com.khumam.gbaduo.plus`, cài riêng với GBA Duo chính thức.

APK được build lại từ mã nguồn công bố, gồm các sửa lỗi từ đợt rà soát cuối; không phải APK lưu trữ trước đợt sửa lỗi. Nếu đang dùng Plus có cùng khóa ký và phiên bản tương thích, có thể cài cập nhật. Hãy sao lưu save trước khi đổi khóa ký hoặc gỡ ứng dụng. Release không kèm ROM, BIOS hay companion chứa tài nguyên game.

## Tính năng Plus

| Nhóm | Nội dung |
|---|---|
| Tạo companion tự động | Companion → **Find and create companion**: khớp game code/CRC32, tải và xác minh builder, tạo pack từ ROM ngay trên thiết bị, cài và gán cho game. Có cache ngoại tuyến, hủy thao tác và giữ pack cũ khi lỗi. |
| GBA Duo Companion Builder | [Website Việt/Anh](https://levi-soft.github.io/gba-duo-companion-builder/) dùng chung builder với Plus; tạo Local ZIP để nhập thủ công hoặc project JSON cho Studio. ROM không tải lên máy chủ, không bị sửa. |
| Ảnh bìa thư viện | Tìm ảnh Libretro không cần tài khoản, gallery SteamGridDB bằng API key riêng, nhập PNG/JPEG/WebP và tìm ảnh còn thiếu. Lưu cục bộ, giữ tỷ lệ và hình cartridge dự phòng. |
| RetroAchievements | Dùng client chính thức 0.1.7, thêm token mã hóa, danh sách achievement và tiến độ RA kèm save state/đồng bộ save. Thông báo game/đăng nhập và popup achievement dưới bên phải, nền 70%. |
| Shader | xBRZ, HQ2x và ScaleFX, tích hợp từ các tác giả shader Libretro; giữ cơ chế ghi nhớ shader của upstream. |
| Điều khiển | Ghi nhớ Swap A/B; chạm chọn nhân vật để điều hướng con trỏ ở profile Fire Emblem được hỗ trợ. |
| Hiển thị companion | Mở rộng sizing, raster grid, giữ tỷ lệ và snapshot; tối ưu selector/cache CSS. |
| Launcher | Hỗ trợ cấu hình custom player Daijisho, alias đường dẫn và quản lý phiên trên launch entry của upstream. |
| Studio | [GBA Duo Studio](https://github.com/levi-soft/gba-duo-studio) để kéo thả/chỉnh sửa pack. Studio Connection trong Plus cho đọc RAM/snapshot và kiểm tra ROM/VRAM/palette/OAM có giới hạn qua USB/ADB, bật theo phiên. |

**[Đọc đầy đủ tính năng và cách dùng bằng tiếng Việt](https://github.com/levi-soft/gba-duo-plus/blob/main/docs/PLUS-FEATURES.vi.md)** · [English features](https://github.com/levi-soft/gba-duo-plus#features)

## Companion Builder 0.9.7 hỗ trợ ROM nào?

| Profile | Game code | CRC32 | Companion |
|---|---|---|---|
| FE7 Việt | `AE7E` | `32CD990D` | `0.7.15` |
| FE6 Việt Remastered, dựng trên FE8 | `BE8E` | `BD53D5F3` | `0.4.16` |
| FE8 Việt | `BE8E` | `3CD932D7` | `0.6.7` |

FE6 Remastered ở đây không phải FE6 gốc. Chỉ đúng ROM được catalogue hỗ trợ mới tạo tự động được; ZIP thủ công vẫn dùng được. Catalogue có thể cập nhật độc lập với APK. Pack tạo từ ROM có thể chứa tài nguyên game: hãy giữ riêng và chia sẻ [website Builder](https://levi-soft.github.io/gba-duo-companion-builder/) thay cho pack.

## Sửa lỗi và nền tảng

- Không giữ khóa UI trong quá trình ghi/đồng bộ ảnh; tải credential nguồn ảnh ở worker.
- Chuẩn bị ảnh trước bước ghi nhận cuối; việc hủy không làm mất ảnh cũ trước khi nhận kết quả.
- Gán companion bằng file tạm và thay thế nguyên tử để không phá lựa chọn cũ khi hết dung lượng hoặc ghi lỗi.
- Đưa test Builder/Artwork vào CI; giữ ảnh tham chiếu gốc và bổ sung tham chiếu renderer Windows cho screenshot test.
- Kế thừa thư viện/Settings pixel, đồng bộ save, API đồ họa ROM, responsive CSS, giới hạn Lua và quyền ghi RAM theo consent của GBA Duo v0.1.7.
- Giữ thiết kế hai màn hình. Chế độ chơi trên điện thoại một màn hình đã hủy không có trong release này.

Kiểm tra trước phát hành: **364/364 Debug tests**, **197/197 Release tests trong gate CI**, build Release/lint thành công. CI GitHub kiểm tra thêm native sandbox/graphics. Kiểm thử tự động không xác nhận mọi thiết bị, hiệu năng GPU hay nhận achievement thực tế. [Bằng chứng và giới hạn](https://github.com/levi-soft/gba-duo-plus/blob/main/docs/VALIDATION.md).

## Tri ân tác giả gốc và cộng đồng

Xin cảm ơn **[khumam](https://github.com/khumam)** và cộng tác viên **[GBA Duo](https://github.com/khumam/gba-duo)** đã tạo ứng dụng, ý tưởng companion hai màn hình, định dạng pack, Lua/CSS và nền tảng mà Plus kế thừa. Plus do **[levi-soft](https://github.com/levi-soft)** duy trì là fork cộng đồng độc lập; lịch sử và thông tin tác giả gốc được giữ lại.

Cảm ơn **Vicki Pfau (endrift) và mGBA**, **Lua.org/PUC-Rio**, **RetroAchievements/rcheevos và tác giả achievement**, **Hyllian, Zenju, Lior Halphon, hunterk, Sp00kyFox và Libretro**, **Libretro thumbnails**, **SteamGridDB và các họa sĩ/người đóng góp**, tác giả font, Android/AndroidX/Compose/Kotlin, tác giả companion và người thử nghiệm.

[Đầy đủ tác giả và nguồn](https://github.com/levi-soft/gba-duo-plus/blob/main/CREDITS.md) · [Thông báo bên thứ ba](https://github.com/levi-soft/gba-duo-plus/blob/main/THIRD-PARTY-NOTICES.md) · [GPL v3](https://github.com/levi-soft/gba-duo-plus/blob/main/LICENSE)

## English

Community fork of **GBA Duo v0.1.7 by khumam**, for dual-screen Android 11+ ARM64 handhelds. This release adds local automatic companion generation through **GBA Duo Companion Builder**, persistent library artwork, extensions to the official RetroAchievements client, xBRZ/HQ2x/ScaleFX shaders, persistent Swap A/B, supported Fire Emblem cursor navigation, companion rendering improvements, Daijisho configuration and the opt-in read-only USB Studio bridge. The separate Builder website creates ZIPs and Studio projects using the same core without uploading or modifying your ROM.

Download the signed APK and SHA-256 file from Assets. Exact-ROM profiles are listed above. This rebuild includes the final artwork cancellation and atomic companion-assignment fixes. No phone single-screen mode, Hardcore, ROM-hash override or translation engine is included. Full original-author and third-party attribution is retained in the linked credits and license notices.
