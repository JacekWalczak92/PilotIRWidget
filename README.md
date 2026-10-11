# Pilot IR – widget Home Assistant (v4)

Widget na ekran główny w stylu pilota, który odpala skrypty `script.pilot_ir_*` z HA.

| Przycisk | Skrypt |
|---|---|
| ⏻ (czerwony) | `script.pilot_ir_power` |
| BT | `script.pilot_ir_bluetooth` |
| FM | `script.pilot_ir_analog` |
| NET | `script.pilot_ir_radio_internetowe` |
| − / 🔇 / + | `script.pilot_ir_ciszej` / `pilot_ir_mute` / `pilot_ir_glosniej` |

## Pobranie APK
Każdy push na `main` buduje podpisany APK (GitHub Actions) i publikuje go w **Releases** jako `PilotIR_vX.apk`.

## Uruchomienie
1. Zainstaluj APK z Releases albo otwórz folder w Android Studio (sync pobierze Gradle 8.9 i zależności) → Run na telefonie,
   albo `./gradlew assembleDebug` i `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. W apce wpisz adres HA i **long-lived token** (profil HA → Bezpieczeństwo), kliknij „Zapisz i przetestuj”.
3. „Dodaj widget na ekran główny” → potwierdź w okienku launchera.

Wywołanie: `POST /api/services/script/turn_on` z `{"entity_id":"script.pilot_ir_…"}` – widget nie czeka na koniec skryptu.
Na górze widgetu pokazuje się status ostatniego kliknięcia (`…` / `✓` / `✗ błąd`).

Uwagi: ruch HTTP (bez TLS) w sieci lokalnej jest dozwolony; token trzymany w SharedPreferences apki.

## Diagnostyka (v4)
Ekran apki pokazuje log każdego kliknięcia: stan sieci, prywatny DNS, serwery DNS, oszczędzanie danych/baterii i błąd. Przyciski: Odśwież / Kopiuj / Udostępnij / Wyczyść.
Gdy DNS systemowy nie rozwiąże adresu, apka próbuje przez DNS-over-HTTPS (Cloudflare, potem Google).
