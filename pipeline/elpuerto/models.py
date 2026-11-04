from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class ServiceDay(str, Enum):
    LABORABLES = "laborables"
    SABADOS = "sabados"
    FESTIVOS = "festivos"


class ColumnSide(str, Enum):
    LEFT = "left"
    RIGHT = "right"


@dataclass(frozen=True)
class ItineraryStop:
    stop_id: str
    line: int
    column: ColumnSide
    sequence: int
    name: str
    lat: float | None = None
    lon: float | None = None
    osm_node_id: int | None = None
    direction: str = ""
    role: str = ""
    coord_source: str = ""


@dataclass(frozen=True)
class TimetableColumn:
    line: int
    service_day: ServiceDay
    column_label: str


@dataclass(frozen=True)
class Departure:
    line: int
    service_day: ServiceDay
    column_label: str
    seconds_since_midnight: int
    bound_stop_id: str


@dataclass(frozen=True)
class Withdrawal:
    line: int
    service_day: ServiceDay
    column_label: str
    seconds_since_midnight: int | None
    raw: str


@dataclass(frozen=True)
class UnboundColumn:
    line: int
    column_label: str
    candidate_stop_ids: tuple[str, ...]


@dataclass(frozen=True)
class Holiday:
    date_iso: str
    year: int
    description: str


@dataclass(frozen=True)
class OsmStop:
    osm_id: int
    name: str
    lat: float
    lon: float
