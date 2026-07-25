package com.happwner.desktop

data class UiStrings(
    val title: String,
    val server: String,
    val running: String,
    val stopped: String,
    val lanMode: String,
    val port: String,
    val apply: String,
    val autostart: String,
    val add: String,
    val empty: String,
    val edit: String,
    val delete: String,
    val copy: String,
    val cancel: String,
    val save: String,
    val name: String,
    val source: String,
    val ua: String,
    val enabled: String,
    val decodeBase64: String,
    val jsonToUri: String,
    val xrayToSingBox: String,
    val lanWarning: String,
    val open: String,
    val exit: String,
    val fullExit: String,
    val check: String,
    val checking: String,
    val checkSuccess: String,
    val localData: String,
    val checkFailed: String,
    val profiles: String,
    val noProfiles: String,
    val response: String,
)

fun strings(language: String): UiStrings = if (language == "en") {
    UiStrings(
        "Happwner PC", "Subscription server", "Running", "Stopped", "Home network (LAN)", "Port",
        "Apply", "Start with system", "Add subscription", "No subscriptions yet", "Edit", "Delete",
        "Copy URL", "Cancel", "Save", "Name", "Source URL", "User-Agent", "Enabled",
        "Decode Base64", "JSON to URI", "Xray to sing-box",
        "LAN mode has no authentication. Anyone on your home network who knows a subscription URL can read it.",
        "Open", "Exit", "Exit completely", "Test", "Testing…", "Available", "Local data", "Check failed",
        "Profiles", "No supported profiles found", "Response",
    )
} else {
    UiStrings(
        "Happwner PC", "Сервер подписок", "Работает", "Остановлен", "Домашняя сеть (LAN)", "Порт",
        "Применить", "Запускать с системой", "Добавить подписку", "Подписок пока нет", "Изменить", "Удалить",
        "Копировать URL", "Отмена", "Сохранить", "Название", "Исходная ссылка", "User-Agent", "Включена",
        "Декодировать Base64", "JSON в URI", "Xray в sing-box",
        "В LAN-режиме нет авторизации. Любой участник домашней сети, знающий адрес подписки, сможет её получить.",
        "Открыть", "Выход", "Полностью выйти", "Проверить", "Проверяем…", "Доступна", "Локальные данные", "Ошибка проверки",
        "Профили", "Поддерживаемые профили не найдены", "Ответ",
    )
}
