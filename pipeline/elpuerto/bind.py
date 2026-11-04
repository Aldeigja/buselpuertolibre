from __future__ import annotations

from elpuerto.models import ItineraryStop, UnboundColumn
from elpuerto.normalize import alias_itinerary_name, fold_name
from elpuerto.winter_pdf import fold_column

# How to pick one visit when the printed name matches several memberships.
# "start": the visit where that direction begins (the departure).
# "end": the later visit on a loop, used for the return column.
# "after": the visit that follows the named markers on one direction.
_VISIT_RULE: dict[tuple[int, str], tuple[str, str | None]] = {
    (4, "san bartolome"): ("end", "outbound"),
    (5, "poblado regreso"): ("end", None),
    (3, "ee ff"): ("after", "outbound"),
    (7, "los milagros"): ("after", "inbound"),
}
_AFTER_MARKERS: dict[tuple[int, str], tuple[str, ...]] = {
    (3, "ee ff"): ("colegio publico de valdelagrana",),
    (7, "los milagros"): ("las nieves", "ronda de valencia", "sudamerica"),
}


def bind_columns(
    itinerary: list[ItineraryStop],
    column_labels: list[tuple[int, str]],
) -> tuple[dict[tuple[int, str], str], list[UnboundColumn]]:
    """Map (line, folded column label) to one route membership."""
    by_line: dict[int, list[ItineraryStop]] = {}
    for stop in itinerary:
        by_line.setdefault(stop.line, []).append(stop)

    bindings: dict[tuple[int, str], str] = {}
    unbound: list[UnboundColumn] = []
    seen: set[tuple[int, str]] = set()

    for line, label in column_labels:
        key = (line, fold_column(label))
        if key in seen:
            continue
        seen.add(key)
        target = alias_itinerary_name(line, label)
        candidates = [
            stop
            for stop in by_line.get(line, [])
            if fold_name(stop.name) == target
        ]
        chosen = _choose(by_line.get(line, []), line, label, candidates)
        if chosen is None:
            unbound.append(
                UnboundColumn(
                    line=line,
                    column_label=label,
                    candidate_stop_ids=tuple(stop.stop_id for stop in candidates),
                )
            )
        else:
            bindings[key] = chosen.stop_id
    return bindings, unbound


def bind_column_label(
    line: int,
    label: str,
    itinerary: list[ItineraryStop],
) -> str | None:
    bindings, _ = bind_columns(itinerary, [(line, label)])
    return bindings.get((line, fold_column(label)))


def _choose(
    line_stops: list[ItineraryStop],
    line: int,
    label: str,
    candidates: list[ItineraryStop],
) -> ItineraryStop | None:
    if not candidates:
        return None
    visits = _visits(candidates)
    rule = _VISIT_RULE.get((line, fold_name(label)))
    if rule is not None:
        kind, direction = rule
        pool = [
            visit
            for visit in visits
            if direction is None or visit[0].direction == direction
        ]
        if kind == "end" and pool:
            return _platform(max(pool, key=lambda visit: visit[0].sequence))
        if kind == "after":
            markers = _AFTER_MARKERS.get((line, fold_name(label)), ())
            mark_seq = max(
                (
                    stop.sequence
                    for stop in line_stops
                    if stop.direction == direction and fold_name(stop.name) in markers
                ),
                default=-1,
            )
            after = [visit for visit in pool if visit[0].sequence > mark_seq]
            if len(after) == 1:
                return _platform(after[0])
            return None
        return None
    if len(visits) == 1:
        return _platform(visits[0])
    starters = [visit for visit in visits if _starts_direction(visit, line_stops)]
    if len(starters) == 1:
        return _platform(starters[0])
    return None


def _visits(stops: list[ItineraryStop]) -> list[list[ItineraryStop]]:
    ordered = sorted(stops, key=lambda stop: (stop.direction, stop.sequence))
    groups: list[list[ItineraryStop]] = []
    for stop in ordered:
        if (
            groups
            and groups[-1][-1].direction == stop.direction
            and stop.sequence == groups[-1][-1].sequence + 1
            and fold_name(groups[-1][-1].name) == fold_name(stop.name)
        ):
            groups[-1].append(stop)
        else:
            groups.append([stop])
    return groups


def _starts_direction(visit: list[ItineraryStop], line_stops: list[ItineraryStop]) -> bool:
    direction = visit[0].direction
    sequences = [
        stop.sequence
        for stop in line_stops
        if stop.direction == direction
    ]
    if not sequences:
        return False
    return min(stop.sequence for stop in visit) == min(sequences)


def _platform(visit: list[ItineraryStop]) -> ItineraryStop:
    platforms = [stop for stop in visit if stop.role == "platform"]
    if platforms:
        return platforms[-1]
    return visit[-1]
