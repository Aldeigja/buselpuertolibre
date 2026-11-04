from __future__ import annotations

import re
from pathlib import Path

import pdfplumber

from elpuerto.models import ColumnSide, ItineraryStop

_LINE_HEADER = re.compile(r"^L([1-7])\b", re.I)
_HORARIO = re.compile(r"HORARIO\s+INVIERNO", re.I)
_TIME_NOISE = re.compile(
    r"\d{1,2}[:.]\d{2}|SALIDAS|PASO|LUNES|SÁBAD|SABAD|DOMING|FESTIV|Excepto|cada \d+|^\*",
    re.I,
)


def _stop_id(line: int, column: ColumnSide, sequence: int) -> str:
    return f"L{line}_{column.value[0].upper()}{sequence:03d}"


def _clean_stop(raw: str) -> str | None:
    text = raw.strip()
    if not text:
        return None
    text = re.sub(r"\s+", " ", text)
    text = text.replace("*", "").strip()
    if _TIME_NOISE.search(text):
        return None
    if len(text) < 2:
        return None
    if text.upper() in {"CIRCULAR", "CREVILLET", "URBALUZ", "CENTRO", "FUENTEBRAVÍA", "FUENTEBRAVIA"}:
        if len(text) < 15:
            return None
    skip = {
        "VALDELAGRANA",
        "CENTRO",
        "FUENTEBRAVÍA",
        "FUENTEBRAVIA",
        "POBLADO Dª BLANCA",
        "CENTRO-HIJUELAS",
        "PLAZA ESPAÑA",
        "PAGO LA ALHAJA",
        "LAS NIEVES-LOS MILAGROS",
    }
    if text.upper() in skip:
        return None
    return text


def _split_columns(line: str) -> tuple[str, str]:
    """Split a layout line into left and right itinerary columns."""
    if len(line) < 20:
        return line.strip(), ""
    mid = len(line) // 2
    # Prefer a wide gap near the middle.
    window = line[max(0, mid - 15) : min(len(line), mid + 15)]
    gap = re.search(r"\s{4,}", window)
    if gap:
        split_at = max(0, mid - 15) + gap.start() + len(gap.group(0)) // 2
        return line[:split_at].strip(), line[split_at:].strip()
    return line[:mid].strip(), line[mid:].strip()


def parse_itinerary_pdf(path: Path) -> list[ItineraryStop]:
    text = _extract_text(path)
    return parse_itinerary_text(text)


def _extract_text(path: Path) -> str:
    parts: list[str] = []
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            parts.append(page.extract_text(layout=True) or "")
    return "\f".join(parts)


def parse_itinerary_text(text: str) -> list[ItineraryStop]:
    stops: list[ItineraryStop] = []
    current_line: int | None = None
    left_seq = 0
    right_seq = 0

    for chunk in text.split("\f"):
        lines = chunk.splitlines()
        in_stops = False
        for raw in lines:
            line = raw.rstrip()
            header = _LINE_HEADER.match(line.strip())
            if header:
                current_line = int(header.group(1))
                left_seq = 0
                right_seq = 0
                in_stops = True
                continue
            if current_line is None:
                continue
            if _HORARIO.search(line):
                in_stops = False
                current_line = None
                continue
            if not in_stops:
                continue
            left_raw, right_raw = _split_columns(line)
            left = _clean_stop(left_raw)
            if left:
                left_seq += 1
                stops.append(
                    ItineraryStop(
                        stop_id=_stop_id(current_line, ColumnSide.LEFT, left_seq),
                        line=current_line,
                        column=ColumnSide.LEFT,
                        sequence=left_seq,
                        name=left,
                    )
                )
            right = _clean_stop(right_raw)
            if right:
                right_seq += 1
                stops.append(
                    ItineraryStop(
                        stop_id=_stop_id(current_line, ColumnSide.RIGHT, right_seq),
                        line=current_line,
                        column=ColumnSide.RIGHT,
                        sequence=right_seq,
                        name=right,
                    )
                )
    return stops
