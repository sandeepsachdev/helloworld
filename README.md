# Explorer Hub — Spring Boot

A Spring Boot application serving a collection of data-driven pages powered entirely by public APIs.

## Pages

| Page | URL | Description |
|------|-----|-------------|
| Home | `/` | Landing page with links to all sections |
| Sydney Suburbs | `/suburbs.html` | Demographics for 60+ Sydney suburbs (ABS Census 2021 + Wikipedia) |
| Celebrity Birthdays | `/birthdays.html` | Famous birthdays this week (Wikipedia) |
| Trending on Wikipedia | `/trending.html` | Most-read Wikipedia articles by day |
| Countries of the World | `/countries.html` | Countries ranked by 10+ indicators (World Bank + REST Countries) |
| Medical Conditions | `/medical.html` | Search conditions, treatments, and symptoms (Wikipedia) |
| Daily Horoscopes | `/horoscopes.html` | Horoscopes for all 12 signs |
| Space Explorer | `/space.html` | NASA APOD, live ISS tracker, launches, asteroids |
| Flight Tracker | `/flights.html` | Live aircraft over Sydney airspace (OpenSky Network) |

## Run Locally

```bash
./mvnw spring-boot:run
```

Then visit http://localhost:8080

## Deploy to Render (Free)

1. Push to GitHub
2. Go to https://render.com → New → Web Service
3. Connect your repo, set Environment to **Docker**, Instance Type to **Free**
4. Deploy — live at `https://your-app-name.onrender.com`

> Free tier spins down after 15 min of inactivity. First request may take ~30s.

---

## How to recreate this app with Claude Code

The entire app was built through a conversation with Claude Code. Below are the prompts used, in order.

### Initial setup
The project was created as a standard Spring Boot application using Spring Initializr with Maven and Java 21.

### Prompts used

---

**1. Sydney Suburbs page**
```
add a page which shows information about sydney suburbs including information about the demographics of the population
```
Creates `suburbs.html` with a searchable, filterable grid of 60+ suburbs showing population, median age, weekly household income, population density, % overseas-born, and % speaking a language other than English. Includes region tabs, card/table toggle, sortable columns, and a detail panel that loads a Wikipedia description on click.

---

**2. Celebrity Birthdays page**
```
create a new page which shows the birthdays of celebrities or famous people that have birthdays today or in the coming week
```
Creates `birthdays.html` using the Wikipedia On This Day API. Shows photos, birth year, age turning, and bio for every famous person with a birthday in the next 7 days. Includes a live text filter.

---

**3. Trending Wikipedia page**
```
build a page which uses the wikipedia api to display articles that are trending now
```
Creates `trending.html` using the Wikimedia Pageviews API. Shows the top most-read articles for any day with view counts, summaries, and thumbnails loaded progressively in batches. Includes date navigation and a filter.

---

**4. Countries of the World page**
```
build a page which allows displaying countries ranking by different criteria like religion, wealth, population, industry etc
```
Creates `countries.html` using the REST Countries API and World Bank Open Data API. Supports 10+ ranking indicators including GDP, GDP per capita, GINI inequality, industry %, literacy, health spending, military spending, CO₂ emissions, and dominant religion (grouped view). Includes region filter and visual bar charts.

---

**5. Index / hub page + breadcrumbs**
```
replace the index page with a colourful page which displays links to the other pages in this application
```
```
the index page should be accessible with the url /. also show breadcrumbs and link back to the index page from each page
```
Creates `index.html` as a dark-themed hub with themed cards for each page. Removes the `@GetMapping("/")` controller method that was blocking Spring Boot's default `index.html` serving. Adds a breadcrumb bar to every page.

---

**6. Medical Conditions page**
```
create a page which allows you to search for information on different medical conditions including treatments
```
Creates `medical.html` using the Wikipedia REST API and OpenSearch API. Includes autocomplete search, quick-launch chips for common conditions, and a detail view showing overview, treatment sections, causes, symptoms, and links to all Wikipedia article sections.

---

**7. Horoscopes page**
```
add a page with horoscopes
```
Creates `horoscopes.html` with all 12 zodiac signs. Readings are deterministically generated from the sign and date so the same person sees the same reading all day. Includes star ratings for overall/love/career, lucky number, lucky colour, and love/career/health blurbs. Shows a full-page featured reading plus an at-a-glance overview grid of all signs.

---

**8. Space Explorer page**
```
add a page for something you think i might be interested in
```
Creates `space.html` with four live data sections:
- **NASA Astronomy Picture of the Day** — high-resolution image or video with explanation
- **ISS Live Tracker** — position updating every 5 seconds, shown on a world map overlay
- **Upcoming Rocket Launches** — countdown timers, rocket name, launch site, and status
- **Near-Earth Asteroids** — next 7 days sorted by closest approach distance, with hazard flag

---

### APIs used (all free, no account required except NASA DEMO_KEY)

| API | Used for |
|-----|----------|
| Wikipedia REST API | Birthdays, trending, medical, suburb descriptions |
| Wikimedia Pageviews API | Trending articles |
| REST Countries API | Country flags, population, area |
| World Bank Open Data API | GDP, income, literacy, CO₂, and other indicators |
| NASA APOD API (`DEMO_KEY`) | Astronomy picture of the day |
| NASA NeoWs API (`DEMO_KEY`) | Near-Earth asteroids |
| wheretheiss.at | ISS live position |
| The Space Devs Launch Library | Upcoming rocket launches + crew in space |
| ABS Census 2021 | Sydney suburb demographics (embedded dataset) |

### Tips for recreating or extending

- All pages are plain HTML + vanilla JS in `src/main/resources/static/` — no build step needed
- The Spring Boot backend only provides the `/api/crime` endpoint; all other data fetches happen client-side
- To add a new page: create the HTML file, then ask Claude to add it to `index.html`
- The `DEMO_KEY` for NASA APIs allows ~30 requests/hour per IP; register at api.nasa.gov for a free key with higher limits
