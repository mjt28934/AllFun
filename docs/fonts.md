# Bundled fonts

AllFun bundles two variable font families, both under the SIL Open Font License 1.1.

| File | Family | Upstream | License |
| --- | --- | --- | --- |
| `app/src/main/res/font/fraunces.ttf` | Fraunces (variable, optical size + weight) | https://github.com/undercasetype/Fraunces | SIL OFL 1.1 |
| `app/src/main/res/font/inter_tight.ttf` | Inter Tight (variable, weight) | https://github.com/rsms/inter | SIL OFL 1.1 |

The OFL permits bundling and redistribution with the application, including commercial
use, provided the fonts are not sold on their own and the license notice travels with them.
This file is that notice. The full license text is available at
https://openfontlicense.org/open-font-license-official-text/ and in each upstream
repository (`OFL.txt`).

Fonts are loaded as variable fonts in `ui/glass/Tokens.kt`; weights and optical sizes are
selected with `FontVariation` settings, so the APK ships one file per family instead of one
file per weight.
