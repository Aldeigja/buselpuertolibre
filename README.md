# Bus El Puerto Libre

An offline timetable and route viewer for the urban bus network of El Puerto de Santa María (Cádiz, Spain).

> **Aviso importante / Important notice:**  
> Aplicación independiente y **no oficial**. No afiliada al Ayuntamiento ni a la empresa concesionaria.  
> Independent and **unofficial** application. Not affiliated with the City Council or the municipal transport operator.

---

## Features

- **Offline-first:** Uses local static timetable and geometry data; requires zero network connectivity at runtime.
- **Route and stop map:** View active urban lines and stop locations on a bundled municipal basemap.
- **Departure schedules:** Tap a stop to see scheduled departures based on the device clock.

## Timetable data and accuracy

- **Current dataset:** Winter 2026/2027 official schedules (*horarios de invierno*).
- **Unscheduled stops:** Where official schedules lack published times for intermediate stops, the app displays times inferred from nearby reference stops along the route. Treat those as approximate estimates.
- **Holidays:** Sunday and holiday timetables follow the Junta de Andalucía work calendar (Consejería de Empleo, Empresa y Trabajo Autónomo), scoped to province Cádiz and municipality El Puerto de Santa María.

## Limitations

- **No real-time tracking:** No live CAD/AVL feed, vehicle GPS, or delay information.
- **No multi-modal routing:** No point-to-point trip planner or automated transfer calculations.

## Legal notice and disclaimer / Aviso legal

### Español

Bus El Puerto Libre es un proyecto de código abierto totalmente independiente y de carácter no oficial. No está afiliado, respaldado, contratado ni vinculado al Excmo. Ayuntamiento de El Puerto de Santa María ni a la empresa concesionaria de transporte urbano.

Los horarios mostrados proceden de la información pública general pero son teóricos y pueden sufrir alteraciones sin previo aviso; las horas en paradas intermedias son estimaciones aproximadas basadas en paradas de referencia. Para horarios vigentes en tiempo real, consulte los canales oficiales. Datos cartográficos: © Colaboradores de OpenStreetMap (ODbL).

### English

Bus El Puerto Libre is a completely independent, unofficial open-source project. It is not affiliated with, endorsed by, contracted by, or connected to the City Council of El Puerto de Santa María (*Ayuntamiento*) or the municipal public transport operator.

The schedules shown are compiled from general public information; they are theoretical and subject to change without notice. Times for intermediate stops are approximate estimates calculated from milestone stops. For authoritative or real-time schedules, please consult official operator channels. Map data: © OpenStreetMap contributors (ODbL).

## Licenses and data attribution

- **Application code:** Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) unless a `LICENSE` file in this repository states otherwise.
- **Map data:** [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright), licensed under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).
- **Holiday calendars:** [Junta de Andalucía](https://www.juntadeandalucia.es/datosabiertos/portal/) work calendar, Consejería de Empleo, Empresa y Trabajo Autónomo, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Province Cádiz, municipality El Puerto de Santa María.
- **Timetable information:** Derived from theoretical timetables published publicly at [bus.elpuertodesantamaria.es](https://bus.elpuertodesantamaria.es).

## Repository layout

| Path | Purpose |
| --- | --- |
| [`buselpuertolibre/`](buselpuertolibre/) | Android app (`es.elpuertobus.app`) |
| [`pipeline/`](pipeline/) | Python pipeline that builds the offline database from maintainer-local sources |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to run checks and where build docs live |

Timetable PDFs, OSM extracts, and generated assets are not published in this repository; see [`pipeline/README.md`](pipeline/README.md) and [`buselpuertolibre/README.md`](buselpuertolibre/README.md) for maintainer build steps.
