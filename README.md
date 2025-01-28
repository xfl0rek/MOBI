# 📌 Nazwa aplikacji
 MoneyTrack

## 👨‍💻 Autorzy
- Wojciech Florczak
- Bartosz Podemski
## 📖 Opis projektu
MoneyTrack to aplikacja mobilna zaprojektowana z myślą o łatwym i intuicyjnym zarządzaniu finansami osobistymi. Dzięki niej użytkownicy mogą:

- Ustawić swój całkowity budżet.
- Dodawać wydatki z nazwami i kwotami.
- Śledzić pozostałą kwotę budżetu w czasie rzeczywistym.
- Przeglądać listę wydatków w przejrzystym interfejsie.
- Aplikacja wspiera świadome zarządzanie finansami, ułatwiając monitorowanie codziennych wydatków.

## 🛠️ Funkcjonalności
- Dodawanie budżetu – możliwość ustawienia budżetu początkowego.
- Śledzenie wydatków – dodawanie i aktualizacja bieżących wydatków.
- Lista wydatków – przegląd wszystkich wydatków w jednym miejscu.
- Kategoryzacja wydatków – grupowanie wydatków według kategorii.
- Podsumowanie wydatków – raportowanie wydatków w odniesieniu do budżetu.
- Ostrzeżenia o przekroczeniu budżetu – powiadomienia o nadmiernych wydatkach.
- Obsługa wielu walut – zarządzanie budżetem w różnych walutach.
- Czyszczenie budżetu – resetowanie budżetu i wydatków.
- Logowanie i rejestracja użytkowników – zabezpieczony dostęp dzięki Firebase Authentication.
## 🔧 Technologie
- Kotlin – język programowania używany do tworzenia aplikacji.
- Gradle – system zarządzania budową projektu.
- Firebase – narzędzie do obsługi backendu:
- Firebase Authentication – do logowania i rejestracji.
- Firebase Database – do przechowywania budżetu i wydatków.
- Android SDK – zestaw narzędzi do tworzenia aplikacji na platformę Android.
## 🚀 Jak uruchomić aplikację?
### Wymagania wstępne:

- Zainstalowane Android Studio (zalecana najnowsza wersja).
- Konto w Firebase Console.
- Konfiguracja Firebase:
- Utwórz projekt w Firebase Console.
- Skonfiguruj Firebase Authentication:
- Włącz logowanie przy użyciu wybranych metod (e-mail i hasło).
- Skonfiguruj Firebase Realtime Database:
- Utwórz bazę danych w trybie testowym.
- Pobierz plik google-services.json i umieść go w katalogu app/ projektu.

### Uruchomienie aplikacji:

- Otwórz projekt w Android Studio.
- Zaktualizuj zależności w pliku build.gradle (projekt i moduł) za pomocą opcji Sync Now.
- Podłącz urządzenie z systemem Android lub uruchom emulator.
- Uruchom aplikację klikając Run 'app'.