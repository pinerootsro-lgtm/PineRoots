# Sosire la PineRoots

Pagina pe care o primesc oaspeții înainte de sosire: drumul, cutia cu cifru, poarta, cheia casei, Wi-Fi, regulile casei și recomandări din zonă.

**Link pentru oaspeți:** https://pinerootsro-lgtm.github.io/PineRoots/

Codul cutiei nu e scris nicăieri în fișiere, pentru că tot ce e aici e public. Îl trimiți oaspeților în mesaj, iar pagina le spune să-l caute acolo.

## Cum schimbi conținutul

Tot textul stă în folderul `continut/`. Nu e nevoie să atingi `index.html`.

| Ce vrei să schimbi | Fișierul |
|---|---|
| Regulile casei (un rând = o regulă) | `continut/reguli.txt` |
| Wi-Fi, ore, telefon, adresă, linkuri | `continut/date.txt` |
| Textele pașilor de sosire | `continut/sosire.txt` |
| Drumul până la poartă | `continut/drum.txt` |
| Ce puteți face în zonă | `continut/zona.txt` |
| Restaurante | `continut/restaurante.txt` |

Pași, pe telefon sau pe calculator:
1. Deschide fișierul pe github.com/pinerootsro-lgtm/PineRoots.
2. Apasă creionul (Edit), modifică textul, apoi apasă „Commit changes”.
3. Pagina se actualizează în 1–2 minute.

Reguli simple:
- Rândurile care încep cu `#` sunt notițe pentru tine și nu apar pe pagină.
- În `date.txt` și `sosire.txt` schimbi doar ce e după primele `:`.
- În listele cu `|`, ordinea e: Nume | Unde | Descriere.

## Bine de știut

- Pagina merge și fără semnal, dacă oaspetele a deschis-o măcar o dată înainte, de exemplu acasă.
- Pagina are `noindex`, deci nu apare în Google.
- Dacă vrei o adresă proprie (de ex. `sosire.pineroots.ro`), se setează din Settings → Pages → Custom domain. Atunci schimbă și cele două adrese `og:url` și `og:image` din `index.html`, care dau imaginea din previzualizarea WhatsApp.
