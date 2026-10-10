# Bundled fonts

`app/src/main/res/font/` holds Latin-subset static instances of two SIL OFL 1.1 families from
[google/fonts](https://github.com/google/fonts) (design-system §3.1), built with `scripts/subset-fonts.sh`:

| File | Source | Instance |
|---|---|---|
| `fraunces_500.ttf`, `fraunces_600.ttf` | `ofl/fraunces/Fraunces[SOFT,WONK,opsz,wght].ttf` | wght 500/600, opsz 28, SOFT 0, WONK 1 (default) |
| `figtree_400.ttf`, `figtree_500.ttf`, `figtree_700.ttf` | `ofl/figtree/Figtree[wght].ttf` | wght 400/500/700, `tnum` kept |

The licences are `Fraunces-OFL.txt` and `Figtree-OFL.txt` here (no Reserved Font Name is declared, so the subsets keep
the family names) and appear in the app's open-source licences via `app/aboutlibraries/libraries/`.
Nothing is downloaded at runtime.
