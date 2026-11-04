"""Winter 2026 timetable.

Columns are read from their horizontal position. Joining a row into one string
drops the gaps and shifts a time into the neighbouring column.
"""

from __future__ import annotations

import re
from pathlib import Path

import pdfplumber

from elpuerto.models import ServiceDay, TimetableColumn, Withdrawal

_TIME = re.compile(r"(\d{1,2})[:.](\d{2})")
_RET = re.compile(r"\bRet\.?\b", re.I)
_LINE = re.compile(r"LINEA\s+(\d+)", re.I)
_MAX_COLUMN_DX = 52.0

# Every label must be present before a row is treated as that line's header.
_LINE_LABELS: dict[int, tuple[str, ...]] = {
    1: ("C.C. El Paseo", "Esc. Acuña"),
    2: ("C.C. El Paseo", "Esc. Acuña"),
    3: (
        "T. MARÍTIMA",
        "VALDELAGRANA",
        "EE.FF",
        "PZA.POLVORISTA",
        "ANCORA",
        "FUENTEBRAVIA",
    ),
    4: ("SAN BARTOLOMÉ", "URBALUZ", "PZA. ESPAÑA", "CREVILLET"),
    5: ("POBLADO", "PZA. POLVORISTA", "HIJUELAS", "PAGO ALHAJA", "TER. MARÍTIMA"),
    6: ("PZA. TOROS", "FUENTEBRAVÍA", "Cantarranas"),
    7: ("SAN BARTOLOMÉ", "BDA. LAS NIEVES", "LOS MILAGROS", "AMB. ZONA NORTE"),
}

_DAY_MARKERS: tuple[tuple[str, ServiceDay], ...] = (
    ("LABORABLES", ServiceDay.LABORABLES),
    ("SÁBADOS", ServiceDay.SABADOS),
    ("SABADOS", ServiceDay.SABADOS),
    ("DOMINGOS", ServiceDay.FESTIVOS),
)


def parse_winter_pdf(path: Path) -> tuple[
    list[TimetableColumn],
    list[tuple[TimetableColumn, int]],
    list[Withdrawal],
]:
    declared: list[TimetableColumn] = []
    times: list[tuple[TimetableColumn, int]] = []
    withdrawals: list[Withdrawal] = []

    current_line = 0
    active_services: tuple[ServiceDay, ...] = (ServiceDay.LABORABLES,)
    day_bands: list[tuple[float, ServiceDay]] = []
    columns: list[tuple[float, str, tuple[ServiceDay, ...]]] = []

    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            for band in _bands(page.chars):
                text = "".join(ch["text"] for ch in band)
                stripped = re.sub(r"\s+", " ", text).strip()
                if not stripped:
                    continue
                line_match = _LINE.search(stripped)
                if line_match:
                    current_line = int(line_match.group(1))
                    columns = []
                    day_bands = []
                    active_services = (ServiceDay.LABORABLES,)
                    continue
                if current_line == 0:
                    continue

                bands = _day_strip(stripped, band)
                if bands is not None:
                    day_bands = bands
                    continue

                section = _section_services(stripped)
                if section is not None:
                    active_services = section
                    columns = []
                    day_bands = []
                    continue

                anchors = _header_anchors(current_line, text, band)
                if anchors:
                    columns = [
                        (x, label, _services_for(day_bands, active_services, x))
                        for x, label in anchors
                    ]
                    for _x, label, services in columns:
                        for service in services:
                            declared.append(
                                TimetableColumn(
                                    line=current_line,
                                    service_day=service,
                                    column_label=label,
                                )
                            )
                    continue

                if current_line == 3:
                    ee_x = _eeff_x(text, band, _tokens(band))
                    if ee_x is not None and not any(label == "EE.FF" for _x, label, _services in columns):
                        columns.append(
                            (ee_x, "EE.FF", _services_for(day_bands, active_services, ee_x))
                        )
                if not columns or not _TIME.search(stripped):
                    continue
                band_tokens = _tokens(band)
                withdraw_at = _ret_time_indexes(band_tokens)
                for index, (x, token) in enumerate(band_tokens):
                    if index not in withdraw_at and _RET.search(token) and len(token) > 8:
                        continue
                    nearest = _nearest_column(x, columns)
                    if nearest is None:
                        continue
                    label, services = nearest
                    for service in services:
                        column = TimetableColumn(
                            line=current_line,
                            service_day=service,
                            column_label=label,
                        )
                        seconds = _parse_time_token(token)
                        if index in withdraw_at or (
                            _RET.search(token) and seconds is not None
                        ):
                            withdrawals.append(
                                Withdrawal(
                                    line=column.line,
                                    service_day=column.service_day,
                                    column_label=column.column_label,
                                    seconds_since_midnight=seconds,
                                    raw=token,
                                )
                            )
                            continue
                        if seconds is None:
                            continue
                        times.append((column, seconds))
    return declared, times, withdrawals


def fold_column(label: str) -> str:
    return re.sub(r"\s+", " ", label.strip().lower())


def _bands(chars: list[dict]) -> list[list[dict]]:
    ordered = sorted(chars, key=lambda ch: (ch["top"], ch["x0"]))
    bands: list[list[dict]] = []
    for ch in ordered:
        if not bands or ch["top"] - bands[-1][0]["top"] > 2.0:
            bands.append([ch])
        else:
            bands[-1].append(ch)
    for band in bands:
        band.sort(key=lambda ch: ch["x0"])
    return bands


def _tokens(chars: list[dict]) -> list[tuple[float, str]]:
    tokens: list[tuple[float, str]] = []
    buf = ""
    x0: float | None = None
    last: float | None = None

    def flush() -> None:
        nonlocal buf, x0
        if buf.strip():
            tokens.append((x0 if x0 is not None else 0.0, buf.strip()))
        buf = ""
        x0 = None

    for ch in chars:
        if ch["text"] == " ":
            flush()
            last = None
            continue
        if last is not None and ch["x0"] - last > 1.6:
            flush()
        if x0 is None:
            x0 = float(ch["x0"])
        buf += ch["text"]
        last = float(ch["x1"])
    flush()
    return tokens


def _header_anchors(line_number: int, text: str, chars: list[dict]) -> list[tuple[float, str]]:
    labels = _LINE_LABELS.get(line_number)
    if not labels:
        return []
    upper = text.upper()
    if "LINEA" in upper:
        return []
    distinct = {label for label in labels if label.upper() in upper}
    if len(distinct) < min(3, len(labels)):
        return []
    found: list[tuple[int, str]] = []
    pos = 0
    while pos < len(upper):
        hits = []
        for label in labels:
            index = upper.find(label.upper(), pos)
            if index >= 0:
                hits.append((index, label))
        if not hits:
            break
        index, label = min(hits)
        found.append((index, label))
        pos = index + max(len(label), 1)
    counts: dict[str, int] = {}
    anchors: list[tuple[float, str]] = []
    for index, label in found:
        counts[label] = counts.get(label, 0) + 1
        name = label
        if line_number == 5 and label == "POBLADO" and counts[label] == 2:
            name = "POBLADO (regreso)"
        if index < len(chars):
            anchors.append((float(chars[index]["x0"]), name))
    return anchors


def _eeff_x(
    text: str,
    chars: list[dict],
    tokens: list[tuple[float, str]],
) -> float | None:
    """Saturday and Sunday print the station as E.E.F.F. beside the first time."""
    upper = text.upper()
    for pattern in ("E.E.F.F", "EE.FF"):
        index = upper.find(pattern)
        if index < 0 or index >= len(chars):
            continue
        origin = float(chars[index]["x0"])
        for token_x, token in tokens:
            if 0 <= token_x - origin <= 40 and _TIME.search(token):
                return token_x
        return origin
    return None


def _day_strip(text: str, chars: list[dict]) -> list[tuple[float, ServiceDay]] | None:
    letters = [ch for ch in text if ch.isalpha()]
    if not letters:
        return None
    if sum(1 for ch in letters if ch.isupper()) / len(letters) < 0.8:
        return None
    upper = text.upper()
    found: list[tuple[float, ServiceDay]] = []
    seen: set[ServiceDay] = set()
    for marker, service in _DAY_MARKERS:
        index = upper.find(marker)
        if index < 0 or index >= len(chars) or service in seen:
            continue
        seen.add(service)
        found.append((float(chars[index]["x0"]), service))
    if len(found) < 2:
        return None
    return found


def _section_services(text: str) -> tuple[ServiceDay, ...] | None:
    upper = re.sub(r"\s+", " ", text).strip().upper()
    if "LINEA" in upper or _TIME.search(upper):
        return None
    if upper.startswith("SÁBAD") or upper.startswith("SABAD"):
        return (ServiceDay.SABADOS,)
    if upper.startswith("DOMING"):
        return (ServiceDay.FESTIVOS,)
    if "LUNES" in upper and ("SÁBAD" in upper or "SABAD" in upper) and "VIERNES" not in upper:
        return (ServiceDay.LABORABLES, ServiceDay.SABADOS)
    if "DOMINGO" in upper and "FESTIV" in upper and "EXCEPTO" not in upper:
        return (ServiceDay.FESTIVOS,)
    if "LABORABLE" in upper or ("LUNES" in upper and "VIERNES" in upper):
        return (ServiceDay.LABORABLES,)
    return None


def _services_for(
    day_bands: list[tuple[float, ServiceDay]],
    active: tuple[ServiceDay, ...],
    x: float,
) -> tuple[ServiceDay, ...]:
    if not day_bands:
        return active
    service = min(day_bands, key=lambda band: abs(band[0] - x))[1]
    return (service,)


def _ret_time_indexes(tokens: list[tuple[float, str]]) -> set[int]:
    """A bare Ret. sits just to the right of the time it withdraws."""
    marked: set[int] = set()
    for index, (x, token) in enumerate(tokens):
        if not _RET.search(token) or _parse_time_token(token) is not None:
            continue
        if len(token) > 8:
            continue
        best: tuple[float, int] | None = None
        for other, (other_x, other_token) in enumerate(tokens):
            if other == index or _parse_time_token(other_token) is None:
                continue
            distance = abs(other_x - x)
            if distance <= 55 and (best is None or distance < best[0]):
                best = (distance, other)
        if best is not None:
            marked.add(best[1])
    return marked


def _nearest_column(
    x: float,
    columns: list[tuple[float, str, tuple[ServiceDay, ...]]],
) -> tuple[str, tuple[ServiceDay, ...]] | None:
    ranked = sorted(
        (abs(x - col_x), label, services) for col_x, label, services in columns
    )
    if not ranked or ranked[0][0] > _MAX_COLUMN_DX:
        return None
    if len(ranked) > 1 and ranked[1][0] - ranked[0][0] < 8:
        return None
    return ranked[0][1], ranked[0][2]


def _parse_time_token(token: str) -> int | None:
    match = _TIME.search(token)
    if not match:
        return None
    hour, minute = int(match.group(1)), int(match.group(2))
    if hour > 23 or minute > 59:
        return None
    return hour * 3600 + minute * 60
