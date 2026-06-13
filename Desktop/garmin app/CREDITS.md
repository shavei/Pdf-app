# Credits & Third-Party Attribution

The "לוח עברי / Hebrew Calendar" widget ships its own original Monkey C code,
but its calendar logic is derived from / verified against the open-source
projects below. This file fulfills their attribution requirements.

---

## pyluach — weekly Torah-portion (parasha) algorithm

The parasha-of-the-week algorithm in `source/Parasha.mc` is a port of the
`parshios` module of **pyluach** (the Shabbat-by-Shabbat reading sequence and
the doubled-parasha / festival rules). pyluach is distributed under the MIT
License and its notice is reproduced here in full as required:

```
The MIT License (MIT)

Copyright (c) 2014 Meir S. List

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

Project: https://github.com/simlist/pyluach

---

## Hebcal — verification reference

The shipped parasha and Hebrew-date output was cross-checked for correctness
against **Hebcal.com** during development (`verify_parsha.py` compares against
pyluach; the Hebcal cross-check was done manually for 2026–2029, Israel and
Diaspora). The app ships **no Hebcal data and makes no Hebcal API calls at
runtime.** Hebcal content is licensed under the Creative Commons Attribution
4.0 International License (CC BY 4.0); this credit is given in appreciation and
to satisfy that attribution.

Hebcal — https://www.hebcal.com/
CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/

---

## Hebrew date math

The core Hebrew-calendar conversion in `source/HebrewDate.mc` implements the
Reingold–Dershowitz "Calendrical Calculations" algorithm (all four dechiyot),
independently verified against pyluach.
