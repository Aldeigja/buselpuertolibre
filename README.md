# Horarios Bus El Puerto

An offline timetable and map for the urban bus network of El Puerto de Santa María (Cádiz, Spain). Package `es.elpuertobus.app`.

**Play short description**

- Spanish: Horarios y mapa sin conexión de los buses de El Puerto. App independiente.
- English: Offline bus timetables and map for El Puerto. Independent app.

> **Aviso importante / Important notice:**  
> Herramienta independiente. No es un servicio oficial del Ayuntamiento ni de ninguna administración.  
> Independent tool. Not an official service of the City Council or any government entity.  
> Horarios oficiales / Official timetables: https://bus.elpuertodesantamaria.es/horarios.html

---

## Features

- **Offline-first:** Uses local static timetable and geometry data; requires zero network connectivity at runtime.
- **Route and stop map:** View active urban lines and stop locations on a bundled municipal basemap.
- **Stop marks:** A filled navy dot is a stop with its own printed clock. A white dot with a navy edge has no printed clock.
- **Departure schedules:** Tap a stop to see scheduled departures based on the device clock.

## Timetable data and accuracy

- **Current dataset:** Winter 2026/2027 official schedules (*horarios de invierno*).
- **Stops without a printed clock:** The app shows the official time of an earlier stop on the same line, or of a later printed stop when the sheet says so. That time belongs to the named stop. It is not an arrival estimate.
- **Days:** Saturday uses the Saturday timetable. Sunday, and a holiday on any weekday, uses the Sunday and holiday timetable. Holidays follow the Junta de Andalucía work calendar (Consejería de Empleo, Empresa y Trabajo Autónomo), scoped to province Cádiz and municipality El Puerto de Santa María. Line 6 does not run on Saturday, Sunday, or a holiday. Line 7 does not run on Sunday or a holiday.

## Limitations

- **No real-time tracking:** No live CAD/AVL feed, vehicle GPS, or delay information.
- **No multi-modal routing:** No point-to-point trip planner or automated transfer calculations.

## Disclaimer / Aviso

### English

This application is an independent tool and is not affiliated with, endorsed by, or operated by the City Council of El Puerto de Santa María or any government entity. It is not an official government service.

Official timetables and service announcements can be consulted at https://bus.elpuertodesantamaria.es/horarios.html.

This app provides an intuitive, offline-friendly interface to easily check urban bus routes, stops, and scheduled departure times in El Puerto de Santa María.

The schedules in this app are the published winter timetable bundled here. They are theoretical. The operator may change the official timetable without notice. This app keeps the bundled copy until a new version is installed.

### Español

Esta aplicación es una herramienta independiente y no está vinculada, respaldada ni gestionada por el Ayuntamiento de El Puerto de Santa María ni por ninguna administración pública. No es un servicio oficial de la administración.

Los horarios oficiales y los avisos del servicio pueden consultarse en https://bus.elpuertodesantamaria.es/horarios.html.

Esta aplicación ofrece una interfaz clara y sin conexión para consultar las líneas urbanas, las paradas y las horas de salida programadas en El Puerto de Santa María.

Los horarios de esta aplicación son el horario de invierno publicado e incluido aquí. Son teóricos. El operador puede cambiar el horario oficial sin aviso. Esta aplicación conserva la copia incluida hasta que se instale una versión nueva.

## Licensing

```text
Licensing:
- Code (app and build pipeline): Apache License 2.0 (see LICENSE)
- Map Data: © OpenStreetMap contributors (ODbL 1.0)
- Holiday Calendars: Junta de Andalucía (CC BY 4.0)
- Timetable Data: Sourced from public timetable at bus.elpuertodesantamaria.es.
  This data is NOT covered by the Apache-2.0 code license and remains the property
  of its respective creators.
```

- **Map data:** [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright), [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/)
- **Holiday calendars:** [Junta de Andalucía](https://www.juntadeandalucia.es/datosabiertos/portal/) work calendar, Consejería de Empleo, Empresa y Trabajo Autónomo, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Province Cádiz, municipality El Puerto de Santa María.

## Repository layout

| Path | Purpose |
| --- | --- |
| [`buselpuertolibre/`](buselpuertolibre/) | Android app (`es.elpuertobus.app`) |
| [`pipeline/`](pipeline/) | Python pipeline that builds the offline database from maintainer-local sources |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to run checks and where build docs live |

Timetable PDFs and OSM extracts are not published in this repository. The app ships two runtime files: [`buselpuertolibre/app/src/main/assets/transit.db`](buselpuertolibre/app/src/main/assets/transit.db) and [`buselpuertolibre/app/src/main/assets/el_puerto.pmtiles`](buselpuertolibre/app/src/main/assets/el_puerto.pmtiles). Those files are not under Apache-2.0. The map is © OpenStreetMap contributors (ODbL). See [`pipeline/README.md`](pipeline/README.md) and [`buselpuertolibre/README.md`](buselpuertolibre/README.md) for how a maintainer rebuilds them.
