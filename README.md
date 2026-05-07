# WLTest

Минимальное Android-приложение для проверки доступности сайтов через мобильный интерфейс.

Логика проверки:

- сначала приложение запрашивает у Android сеть с `TRANSPORT_CELLULAR`;
- затем получает имя интерфейса через `ConnectivityManager.getLinkProperties(network).getInterfaceName()`;
- после этого native curl выполняет запросы с `CURLOPT_INTERFACE=if!<interface>`;
- proxy отключается через `CURLOPT_PROXY=""` и `CURLOPT_NOPROXY="*"`.

Категории сайтов:

- RU: `ya.ru`, `ozon.ru`, `mail.ru`
- не RU: `google.com`, `whatismyip.com`, `https://api.ipify.org/`

Вердикты:

- не отвечает ни один сайт: `Нет интернета`
- RU отвечают, не RU не отвечают: `Белые списки`
- отвечают все сайты: `Нет белых списков`
- остальные комбинации: `Частичный результат`

Сборка в Android Studio или через установленный Gradle:

```powershell
$env:GRADLE_USER_HOME='C:\WLChecker\.gradle-user'
$env:ANDROID_USER_HOME='C:\WLChecker\.android-user'
$env:ANDROID_HOME='C:\Users\Nekohime\AppData\Local\Android\Sdk'
Remove-Item Env:ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue
& 'C:\Users\Nekohime\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' assembleDebug
```

В текущем проекте CMake загружает `curl` и `mbedtls` через `FetchContent`, поэтому при первой сборке нужен доступ к GitHub.

## Версии и GitHub Release

Номер версии хранится в `gradle.properties`:

- `VERSION_NAME` попадает в `versionName`;
- `VERSION_CODE` попадает в `versionCode`.

Перед новой публикацией можно увеличить версию отдельно:

```powershell
.\scripts\bump-version.ps1 patch
```

Доступные значения: `patch`, `minor`, `major`.

Workflow `.github/workflows/android-release.yml` собирает APK при push тега `v*` и публикует GitHub Release уже с прикрепленным APK. APK будет называться `WLTest-<VERSION_NAME>-<VERSION_CODE>.apk`.

Полная публикация новой версии одной командой:

```powershell
.\scripts\release.ps1 patch
```

Скрипт `release.ps1` поднимает `VERSION_NAME`/`VERSION_CODE`, делает commit, создает tag `v<VERSION_NAME>`, пушит ветку и tag на GitHub. После push тега GitHub Actions создает release и сразу прикрепляет APK.
