# Independent cross-check of the Parasha.mc algorithm against hebcal.com
# (separate codebase from pyluach). Compares every Saturday of 2026-2029,
# both Israel and diaspora schedules, including festival Saturdays that have
# NO weekly portion (absent from hebcal's feed -> ours must return None).
#
# Run: python crosscheck_hebcal.py  ->  must print 0 mismatches.
# Reads the saved hebcal feeds in hebcal_fixtures/ (no network needed).
import json
import os
import sys
from datetime import date, timedelta

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from verify_parsha import parsha_for_jd

# hebcal's fixed English transliterations, in Torah order (index = ours)
HEBCAL_NAMES = [
    "Bereshit", "Noach", "Lech-Lecha", "Vayera", "Chayei Sara", "Toldot",
    "Vayetzei", "Vayishlach", "Vayeshev", "Miketz", "Vayigash", "Vayechi",
    "Shemot", "Vaera", "Bo", "Beshalach", "Yitro", "Mishpatim", "Terumah",
    "Tetzaveh", "Ki Tisa", "Vayakhel", "Pekudei", "Vayikra", "Tzav", "Shmini",
    "Tazria", "Metzora", "Achrei Mot", "Kedoshim", "Emor", "Behar",
    "Bechukotai", "Bamidbar", "Nasso", "Beha'alotcha", "Sh'lach", "Korach",
    "Chukat", "Balak", "Pinchas", "Matot", "Masei", "Devarim", "Vaetchanan",
    "Eikev", "Re'eh", "Shoftim", "Ki Teitzei", "Ki Tavo", "Nitzavim",
    "Vayeilech", "Ha'azinu", "Vezot Haberakhah",
]
NAME_TO_IDX = {n: i for i, n in enumerate(HEBCAL_NAMES)}

def title_to_indices(title):
    name = title.replace("Parashat ", "").replace("’", "'")
    if name in NAME_TO_IDX:
        return [NAME_TO_IDX[name]]
    # doubled portion "A-B"; beware internal hyphens (Lech-Lecha)
    for i, ch in enumerate(name):
        if ch == "-":
            a, b = name[:i], name[i + 1:]
            if a in NAME_TO_IDX and b in NAME_TO_IDX:
                return [NAME_TO_IDX[a], NAME_TO_IDX[b]]
    raise ValueError(f"unknown hebcal title: {title!r}")

ORD_TO_JD = 1721425
mismatches = 0
checked = 0

for year in (2026, 2027, 2028, 2029):
    for suffix, israel in (("d", False), ("i", True)):
        path = os.path.join(HERE, "hebcal_fixtures",
                            f"hebcal_{year}_{suffix}.json")
        with open(path, encoding="utf-8") as f:
            feed = json.load(f)
        by_date = {}
        for item in feed["items"]:
            if item["category"] == "parashat":
                by_date[item["date"]] = title_to_indices(item["title"])

        # walk every Saturday of the civil year
        d = date(year, 1, 1)
        while d.weekday() != 5:  # Python: Saturday == 5
            d += timedelta(days=1)
        while d.year == year:
            expected = by_date.get(d.isoformat())  # None = festival, no portion
            got = parsha_for_jd(d.toordinal() + ORD_TO_JD, israel)
            if expected != got:
                mismatches += 1
                print(f"MISMATCH {d} israel={israel}: hebcal={expected} ours={got}")
            checked += 1
            d += timedelta(days=7)

print(f"checked {checked} Saturdays (2026-2029 x 2 schedules)")
print(f"mismatches: {mismatches}")
sys.exit(1 if mismatches else 0)
