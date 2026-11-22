# ProductAccounting
Manual accounting of goods

Согласованная постановка задачи находится в [REQUIREMENTS.md](REQUIREMENTS.md).

## Запуск

1. Откройте корневую папку проекта в Android Studio.
2. Дождитесь завершения Gradle Sync.
3. Запустите конфигурацию `app` на эмуляторе или Android-устройстве с Android 8.0 и новее.

Для командной сборки используйте:

```powershell
.\gradlew.bat assembleDebug
```

Отладочный APK создаётся в `app/build/outputs/apk/debug/app-debug.apk`.

## Выпуск версии через GitHub

Workflow `.github/workflows/release-apk.yml` запускается при отправке тега формата
`vMAJOR.MINOR.PATCH`, например `v0.1.0`. Он выполняет тесты, собирает подписанный APK и
публикует его как файл GitHub Release.

В разделе `Settings → Secrets and variables → Actions` репозитория должны быть созданы:

- `RELEASE_KEYSTORE_BASE64` — release keystore в Base64 без переносов строк;
- `RELEASE_KEYSTORE_PASSWORD` — пароль keystore;
- `RELEASE_KEY_ALIAS` — alias ключа;
- `RELEASE_KEY_PASSWORD` — пароль ключа.

Эти значения должны оставаться неизменными для всех версий: Android разрешает обновлять
установленное приложение только APK, подписанным тем же ключом.

Пример публикации версии:

```powershell
git tag v0.1.0
git push origin v0.1.0
```

При запуске приложение проверяет последний релиз
`VRyzhkov/ProductAccounting`, предлагает скачать более новую версию, отслеживает загрузку
и открывает системный установщик Android. При первой установке обновления Android попросит
разрешить установку приложений из ProductAccounting.
