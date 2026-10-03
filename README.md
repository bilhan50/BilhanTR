# ☁️ BilhanTR — CloudStream için Türkçe Eklentiler

[![Boyut](https://img.shields.io/github/repo-size/bilhan50/BilhanTR?logo=git&logoColor=white&label=Boyut)](#)
[![CloudStream Derleyici](https://img.shields.io/github/actions/workflow/status/bilhan50/BilhanTR/Derleyici.yml?label=CloudStream%20Derleyici&logo=github)](https://github.com/bilhan50/BilhanTR/actions/workflows/Derleyici.yml)
[![Lisans](https://img.shields.io/badge/Lisans-GPL--3.0-blue)](LICENSE)

CloudStream için Türkçe yayın yapan film ve dizi sitelerine ait **41 eklentilik** deposu.

---

## 💾 Kurulum

1. **[cloudstream/pre-release](https://github.com/recloudstream/cloudstream/releases/releases)** adresinden güncel APK dosyasını indirip kurun.
2. Depoyu eklemek için **herhangi bir** yöntemi kullanın:

| Yöntem | Yapmanız gereken |
| :--- | :--- |
| **Otomatik** | Cihazda [bu bağlantıya tıklayın](cloudstreamrepo://raw.githubusercontent.com/bilhan50/BilhanTR/main/repo.json) |
| **Kısa kod** | `Depo ekle` → `Depo URL'si` kutusuna **`!bilhantr`** yazın |
| **Manuel URL** | `Depo ekle` → `Depo URL'si` kutusuna `https://raw.githubusercontent.com/bilhan50/BilhanTR/main/repo.json` yazın |

> ⚠️ `Depo URL'si` kutusuna **`https://github.com/bilhan50/BilhanTR`** yazmak **çalışmaz**.
> Doğru adres her zaman `repo.json` dosyasının **raw** bağlantısıdır (tabloda üçüncü satır).

### Kısa kod nedir?

CloudStream kısa kodları üçüncü parti kısaltma servisleri üzerinden çözümler:

- `!` **ile başlayan** kodlar → `py.md` servisinde aranır → `!bilhantr` ✔
- `!` **olmayan** kodlar → `cutt.ly` servisinde aranır → `bilhantr` (cutt.ly hesabında `bilhantr` takma adı tanımlandıktan sonra çalışır)

Kısa kod çalışmıyorsa **manuel URL** her zaman sorunsuzdur.

---

## 📺 Desteklenen Sağlayıcılar

### 🎬 Film
FilmModu · FullHDFilm · FullHDFilmizlesene · WebteIzle · HDFilmCehennemi · FilmMakinesi · JetFilmizle · KultFilmler · RareFilmm · SetFilmIzle · SuperFilmGeldi · UgurFilm · Watch2Movies · IzleAI · SinemaCX · SineWix · UncutMaza · GolgeTV · NetflixMirror

### 📺 Dizi / Anime
DiziBox · DiziPal · Dizilla · DiziMom · DiziKorea · DiziYou · SezonlukDizi · InatBox · AnimeciX · TurkAnime · KoreanTurk · CizgiMax · BelgeselX · CanliTV · RecTV · YouTube

### 🌐 Diğer
OxAx

---

## 🛠️ Depo Yapısı ve Derleme

```
BilhanTR/
├── repo.json                  → CloudStream'in okuduğu depo tanımı
├── build.gradle.kts           → ortak derleme ayarları (setRepo, JVM 11, Kotlin 2.4)
├── <Eklenti>/
│   ├── build.gradle.kts       → name, status, version, internalName, iconUrl
│   └── src/main/kotlin/…      → provider + extractor'lar
└── .github/workflows/
    ├── Derleyici.yml          → push'ta tüm eklentileri derler, builds dalına yükler
    └── Kontrol.yml            → 9 saatte bir canlı domainleri kontrol eder
```

- **Eklenti listesi:** `builds` dalındaki `plugins.json` (CI tarafından üretilir)
- **Eklenti dosyaları:** `builds` dalındaki `*.cs3`
- **Yerel derleme:**

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat make makePluginsJson
```

Yeni eklenti eklemek için klasör açın, `build.gradle.kts` + `src/main/kotlin` ekleyin; `settings.gradle.kts` klasörü otomatik dahil eder.

---

## 🔄 Otomatik Güncellemeler

| Akış | Ne yapar |
| :--- | :--- |
| `Derleyici.yml` | `main` dalına push edildiğinde 41 eklentiyi derler, `.cs3` + `plugins.json` üreterek `builds` dalına yazar |
| `Kontrol.yml` | 9 saatte bir canlı domainleri tarar, değişiklik varsa PR açar |

---

## 🎁 Teşekkürler

- [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream)
- [hexated/cloudstream-extensions-hexated](https://github.com/hexated/cloudstream-extensions-hexated)
- [Jacekun/cs3xxx-repo](https://github.com/Jacekun/cs3xxx-repo)
- [recloudstream/extensions](https://github.com/recloudstream/extensions)
- Orijinal geliştirici: [keyiflerolsun/Kekik-cloudstream](https://github.com/keyiflerolsun/Kekik-cloudstream)

---

## 🌐 Telif Hakkı ve Lisans

* *Copyright (C) 2023–2026 by* [bilhan50](https://github.com/bilhan50)
* [GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007](LICENSE) *koşullarına göre lisanslanmıştır.*

## 💻 Katkı Sağlayanlar

<a href="https://github.com/bilhan50/BilhanTR/graphs/contributors?selectedMetric=additions" target="_blank">
  <img src="https://stg.contrib.rocks/image?repo=bilhan50/BilhanTR" />
</a>

***

> **[@bilhan50](https://github.com/bilhan50)** için yazılmıştır.
