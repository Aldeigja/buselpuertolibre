from __future__ import annotations

import json
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from elpuerto.models import Holiday

JUNTA_SEARCH = (
    "https://datos.juntadeandalucia.es/api/v0/work-calendar/get/search_calendar"
)
MUNICIPALITY = "EL PUERTO DE SANTA MARÍA"
PROVINCE = "CÁDIZ"
ATTRIBUTION = (
    "Junta de Andalucía work calendar, Consejería de Empleo, Empresa y "
    "Trabajo Autónomo, CC BY 4.0"
)

EXPECTED_2026 = {
    "2026-01-01",
    "2026-01-06",
    "2026-02-28",
    "2026-04-02",
    "2026-04-03",
    "2026-05-01",
    "2026-05-04",
    "2026-08-15",
    "2026-09-08",
    "2026-10-12",
    "2026-11-02",
    "2026-12-07",
    "2026-12-08",
    "2026-12-25",
}

EXPECTED_2027_REGIONAL = {
    "2027-01-01",
    "2027-01-06",
    "2027-03-01",
    "2027-03-25",
    "2027-03-26",
    "2027-05-01",
    "2027-08-16",
    "2027-10-12",
    "2027-11-01",
    "2027-12-06",
    "2027-12-08",
    "2027-12-25",
}


def fetch_holidays(years: tuple[int, ...] = (2026, 2027)) -> list[Holiday]:
    holidays: list[Holiday] = []
    for year in years:
        query = urlencode(
            {
                "province": PROVINCE,
                "municipality": MUNICIPALITY,
                "year": str(year),
            }
        )
        url = f"{JUNTA_SEARCH}?{query}"
        req = Request(url, headers={"User-Agent": "elpuerto-bus-pipeline/0.1"})
        with urlopen(req, timeout=120) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        for row in payload.get("results", []):
            date_int = row.get("date")
            if not date_int:
                continue
            iso = _yyyymmdd_to_iso(int(date_int))
            holidays.append(
                Holiday(
                    date_iso=iso,
                    year=int(str(date_int)[:4]),
                    description=str(row.get("description", "")),
                )
            )
    return _dedupe_holidays(holidays)


def load_holidays_snapshot(path: Path) -> list[Holiday]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return [
        Holiday(
            date_iso=item["date_iso"],
            year=int(item["year"]),
            description=str(item.get("description", "")),
        )
        for item in data.get("holidays", [])
    ]


def save_holidays_snapshot(path: Path, holidays: list[Holiday]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "source": ATTRIBUTION,
        "municipality": MUNICIPALITY,
        "province": PROVINCE,
        "holidays": [
            {
                "date_iso": h.date_iso,
                "year": h.year,
                "description": h.description,
            }
            for h in holidays
        ],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def validate_holiday_snapshot(holidays: list[Holiday]) -> list[str]:
    """Return warning strings (non-fatal)."""
    warnings: list[str] = []
    by_date = {h.date_iso for h in holidays}
    missing_2026 = EXPECTED_2026 - by_date
    if missing_2026:
        raise ValueError(f"2026 holiday snapshot missing dates: {sorted(missing_2026)}")
    missing_2027 = EXPECTED_2027_REGIONAL - by_date
    if missing_2027:
        raise ValueError(
            f"2027 regional holiday snapshot missing dates: {sorted(missing_2027)}"
        )
    local_2027 = [h for h in holidays if h.year == 2027 and h.date_iso not in EXPECTED_2027_REGIONAL]
    if not local_2027:
        warnings.append("2027 local municipal holidays not yet published in Junta feed.")
    return warnings


def _yyyymmdd_to_iso(value: int) -> str:
    s = str(value)
    return f"{s[0:4]}-{s[4:6]}-{s[6:8]}"


def _dedupe_holidays(holidays: list[Holiday]) -> list[Holiday]:
    seen: set[str] = set()
    out: list[Holiday] = []
    for h in sorted(holidays, key=lambda x: x.date_iso):
        if h.date_iso in seen:
            continue
        seen.add(h.date_iso)
        out.append(h)
    return out
