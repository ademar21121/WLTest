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
gradle assembleDebug
```

В текущем проекте CMake загружает `curl` и `mbedtls` через `FetchContent`, поэтому при первой сборке нужен доступ к GitHub.
