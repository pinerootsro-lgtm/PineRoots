# PineRoots Studio pentru Android

Ramura asta conține doar aplicația Android PineRoots Studio, generatorul de postări Instagram pentru cabană. Site-ul pentru oaspeți rămâne pe ramura `main` și nu e afectat.

## Cum instalezi aplicația

1. Pe telefon, deschide pagina **Releases** a acestui repository: https://github.com/pinerootsro-lgtm/PineRoots/releases
2. La cea mai nouă versiune „PineRoots Studio 1.0.x”, apasă pe `PineRoots-Studio.apk`.
3. Deschide fișierul descărcat. Android îți cere o singură dată să permiți instalarea din browser („Instalează aplicații necunoscute”), apoi apasă **Instalează**.

Versiunile noi se instalează peste cea veche și îți păstrează preseturile.

## Cum faci o versiune nouă

Orice modificare salvată pe ramura `android-app` pornește compilarea automat. În 3–5 minute apare o versiune nouă la Releases.

Generatorul propriu-zis e în `studio-android/app/src/main/assets/www/index.html`.

## Cheia de semnare

Cheia cu care e semnată aplicația (`studio-android/app/release.jks`) e salvată aici ca versiunile noi să se poată instala peste cele vechi. Fiind un repository public, oricine o poate vedea. Dacă vrei s-o ascunzi, adaugă în Settings › Secrets and variables › Actions două secrete, `KEYSTORE_B64` (fișierul .jks codat base64) și `KEYSTORE_PASSWORD`, apoi șterge fișierul din repository.
