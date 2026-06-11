# Verify the parasha algorithm planned for source/Parasha.mc against pyluach.
#
# The functions below mirror the Monkey C implementation EXACTLY:
#  - same JD-based Hebrew date math as source/HebrewDate.mc (Reingold-Dershowitz)
#  - Tishrei-based month numbering (1=Tishrei .. 6=Adar, 7=Nisan .. 12=Elul,
#    13=Adar I, 14=Adar II) - the numbering HebrewDate.mc uses
#  - integer-only operations (portable to Monkey C as-is)
#
# Algorithm: port of pyluach.parshios._gentable (Dr. Irv Bromberg's rules),
# rewritten as a Shabbat-by-Shabbat walk with a virtual deque index instead
# of a table, since Monkey C just needs "parasha for one date".
#
# Run: python verify_parsha.py   ->  must print 0 mismatches for both schedules.

import sys
from datetime import date, timedelta

from pyluach import dates as pdates
from pyluach import parshios as pparshios

# ---------------------------------------------------------------- HebrewDate.mc math

def hebrew_elapsed_days(y):
    m = (235 * y - 234) // 19
    parts = 12084 + 13753 * m
    day = 29 * m + parts // 25920
    if (3 * (day + 1)) % 7 < 3:
        day += 1
    return day

def hebrew_new_year(y):
    n1 = hebrew_elapsed_days(y)
    corr = 0
    if hebrew_elapsed_days(y + 1) - n1 == 356:
        corr = 2
    elif n1 - hebrew_elapsed_days(y - 1) == 382:
        corr = 1
    return 347998 + n1 + corr

def is_leap(y):
    return ((7 * y) + 1) % 19 < 7

def days_in_month(mo, y):
    year_len = hebrew_new_year(y + 1) - hebrew_new_year(y)
    if mo == 1:  return 30
    if mo == 2:  return 30 if year_len in (355, 385) else 29
    if mo == 3:  return 29 if year_len in (353, 383) else 30
    if mo == 4:  return 29
    if mo == 5:  return 30
    if mo == 6:  return 29
    if mo == 13: return 30
    if mo == 14: return 29
    if mo == 7:  return 30
    if mo == 8:  return 29
    if mo == 9:  return 30
    if mo == 10: return 29
    if mo == 11: return 30
    if mo == 12: return 29
    return 29

def month_order(y):
    return [1, 2, 3, 4, 5, 13, 14, 7, 8, 9, 10, 11, 12] if is_leap(y) \
      else [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

def hebrew_to_jd(y, m, d):
    jd = hebrew_new_year(y)
    for mo in month_order(y):
        if mo == m:
            break
        jd += days_in_month(mo, y)
    return jd + d - 1

def jd_to_hebrew(jd):
    """-> (year, month, day) in Tishrei-based numbering. Mirrors HebrewDate.fromJD."""
    yr = (jd - 347997) * 10000 // 3652468 + 1   # integer approximation of /365.2468
    while hebrew_new_year(yr + 1) <= jd:
        yr += 1
    while hebrew_new_year(yr) > jd:
        yr -= 1
    day_of_year = jd - hebrew_new_year(yr)
    elapsed = 0
    for mo in month_order(yr):
        ml = days_in_month(mo, yr)
        if day_of_year < elapsed + ml:
            return (yr, mo, day_of_year - elapsed + 1)
        elapsed += ml
    return (yr, 1, 1)

# ---------------------------------------------------------------- Parasha.mc logic

def dow(jd):
    """0=Sunday .. 6=Shabbat."""
    return (jd + 1) % 7

def shabbat_on_or_after(jd):
    return jd + (6 - dow(jd)) % 7

def _seq(i):
    """Virtual reading sequence: Vayeilech, Haazinu, then Bereishit..Vayeilech."""
    if i == 0: return 51
    if i == 1: return 52
    return i - 2

def _parshaless(m, d, israel):
    # Festival Shabbatot with no weekly portion. Israel reads on the diaspora's
    # second days (Tishrei 23 / Nisan 22 / Sivan 7).
    if israel and (m, d) in ((1, 23), (7, 22), (9, 7)):
        return False
    if m == 1 and (d in (1, 2, 10) or 15 <= d <= 23):
        return True
    if m == 7 and 15 <= d <= 22:
        return True
    if m == 9 and d in (6, 7):
        return True
    return False

def parsha_for_jd(jd, israel):
    """Parasha read on the Shabbat on-or-after jd: list of 1-2 indices, or None."""
    shab = shabbat_on_or_after(jd)
    y, _, _ = jd_to_hebrew(shab)

    rh = hebrew_new_year(y)
    leap = is_leap(y)
    pesach_dow = dow(hebrew_to_jd(y, 7, 15))
    erev_pesach = hebrew_to_jd(y, 7, 14)
    av9 = hebrew_to_jd(y, 11, 9)
    next_rh_late = dow(hebrew_new_year(y + 1)) >= 4   # next RH on Thu/Sat

    cur = shabbat_on_or_after(rh)
    idx = 1 if dow(rh) >= 4 else 0   # RH Thu/Sat: Shabbat Shuva reads Haazinu

    while cur <= shab:
        _, m, d = jd_to_hebrew(cur)
        if _parshaless(m, d, israel):
            if cur == shab:
                return None
        else:
            p = _seq(idx)
            idx += 1
            double = (
                (p == 21 and (erev_pesach - cur) // 7 < 3)
                or (p in (26, 28) and not leap)
                or (p == 31 and not leap and (not israel or pesach_dow != 6))
                or (p == 38 and not israel and pesach_dow == 4)
                or (p == 41 and (av9 - cur) // 7 < 2)
                or (p == 50 and next_rh_late)
            )
            if double:
                if cur == shab:
                    return [p, _seq(idx)]
                idx += 1
            if cur == shab:
                return [p]
        cur += 7
    return None  # unreachable

# ---------------------------------------------------------------- verification

def main():
    # Every single day, Gregorian 2020-01-01 .. 2090-01-01, both schedules.
    start = date(2020, 1, 1)
    end = date(2090, 1, 1)
    ordinal_to_jd = 1721425  # jd = date.toordinal() + 1721425

    mismatches = {False: 0, True: 0}
    checked = 0
    d = start
    while d < end:
        jd = d.toordinal() + ordinal_to_jd
        pd = pdates.GregorianDate(d.year, d.month, d.day)
        for israel in (False, True):
            expected = pparshios.getparsha(pd, israel=israel)
            got = parsha_for_jd(jd, israel)
            if expected != got:
                mismatches[israel] += 1
                if mismatches[False] + mismatches[True] <= 20:
                    print(f"MISMATCH {d} israel={israel}: pyluach={expected} ours={got}")
        checked += 1
        d += timedelta(days=1)

    print(f"checked {checked} days x 2 schedules "
          f"({start} .. {end})")
    print(f"diaspora mismatches: {mismatches[False]}")
    print(f"israel   mismatches: {mismatches[True]}")
    if mismatches[False] or mismatches[True]:
        sys.exit(1)
    print("OK - 0 mismatches")

if __name__ == "__main__":
    main()
