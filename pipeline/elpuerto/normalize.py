from __future__ import annotations

import re
import unicodedata


def fold_name(value: str) -> str:
    """Case- and accent-insensitive key for stop matching."""
    text = unicodedata.normalize("NFKD", value)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


# Printed column -> folded OSM stop name on that same line.
# Confirmed against the winter timetable and the checked route geometry.
TIMETABLE_ALIASES: dict[tuple[int, str], str] = {
    (1, "c c el paseo"): "centro comercial el paseo",
    (2, "c c el paseo"): "centro comercial el paseo",
    (1, "esc acuna"): "escultor antonio acuna",
    (2, "esc acuna"): "escultor antonio acuna",
    (3, "t maritima"): "terminal maritima",
    (3, "valdelagrana"): "colegio publico de valdelagrana",
    (3, "ee ff"): "estacion de ferrocarril plaza estacion",
    (3, "pza polvorista"): "plaza del polvorista",
    (3, "ancora"): "ancora el ancla",
    (4, "pza espana"): "plaza de espana santa lucia",
    (4, "crevillet"): "av constitucion",
    (5, "poblado"): "poblado de dona blanca",
    (5, "poblado regreso"): "poblado de dona blanca",
    (5, "pza polvorista"): "plaza del polvorista",
    (5, "hijuelas"): "hijuela del tio prieto",
    (5, "pago alhaja"): "pago la alhaja",
    (5, "ter maritima"): "terminal maritima",
    (6, "pza toros"): "plaza de toros anillo central",
    (7, "bda las nieves"): "las nieves",
    (7, "los milagros"): "ronda ferrocarril los milagros",
    (7, "amb zona norte"): "centro de salud pinillo chico",
}


def alias_itinerary_name(line: int, timetable_column: str) -> str:
    """Return folded itinerary name to match against stop rows."""
    folded = fold_name(timetable_column)
    return TIMETABLE_ALIASES.get((line, folded), folded)
