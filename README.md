# Neon VPN

Лёгкий VPN-клиент для Android с тёмным неоновым дизайном.

- Протоколы: AmneziaWG и WireGuard
- Импорт: ключ `vpn://` (из Amnezia), файл `.conf`, QR-код (в том числе составной QR Amnezia)
- Android 8.0 и новее

## Как получить APK

Каждый пуш в `main` запускает сборку в GitHub Actions. Готовый `NeonVPN.apk` появляется в разделе **Releases**.

Подпись: `app/release.jks` (пароль в `app/build.gradle.kts`). Не меняйте ключ, иначе обновления не встанут поверх старой версии.

## Лицензии

Туннель: библиотека [amneziawg-android](https://github.com/zaneschepke/amneziawg-android) (Apache 2.0).
QR: [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded) (Apache 2.0).
