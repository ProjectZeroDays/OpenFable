# Web Clone — Expert Website Cloner

Clones any website to a local directory with full asset pipeline, URL rewriting for offline browsing, robots.txt compliance, and anti-bot bypass.

## When to Use
- Clone a website for offline research/analysis
- Archive a site for auditing
- Extract all assets (HTML, CSS, JS, images, fonts, media)
- Test/validate a site's structure locally
- Capture a page with JS rendering via Playwright

## CLI Interface

```
python scripts/web_clone.py <URL> [options]
```

### Required
- `url` — Target website URL (http:// or https://)

### Common Options
| Flag | Description | Default |
|------|-------------|---------|
| `--output, -o` | Output directory | `clone/<domain>` |
| `--depth, -d` | Max link crawl depth | 1 |
| `--limit, -l` | Max pages to clone | 50 |
| `--max-assets` | Max assets to download | 500 |
| `--no-links` | Clone only the single page | off |
| `--no-images` | Skip image downloads | off |
| `--no-js` | Skip JS downloads | off |
| `--no-fonts` | Skip font downloads | off |
| `--no-robots` | Ignore robots.txt | off |
| `--delay` | Crawl delay in seconds | 0 |
| `--workers, -w` | Concurrent downloads | 10 |
| `--js` | Use Playwright for JS-rendered pages | off |
| `--mobile` | Mobile viewport (375x812) | off |
| `--cookies` | Cookie file (Netscape or JSON) | — |
| `--sitemap` | Discover URLs via sitemap.xml | off |
| `--dry-run, -n` | Show what would be cloned | off |
| `--verbose, -v` | Debug-level output | off |
| `--quiet, -q` | Suppress non-error output | off |

### Examples

```bash
# Clone a single page (root URL only)
python scripts/web_clone.py https://example.com

# Deep clone with 2-level link following
python scripts/web_clone.py https://example.com --depth 2 --limit 100

# Clone to custom output directory
python scripts/web_clone.py https://example.com --output ./clones/site

# Clone with JS rendering (Playwright)
python scripts/web_clone.py https://example.com --js

# Mobile clone
python scripts/web_clone.py https://example.com --mobile

# With cookies/session
python scripts/web_clone.py https://example.com --cookies cookies.txt

# Dry run to preview
python scripts/web_clone.py https://example.com --dry-run

# Deep archive with robots respect
python scripts/web_clone.py https://example.com --depth 3 --delay 1
```

## Output Structure

```
clone/example.com/
  index.html          # Rewritten root page
  about.html          # Crawled subpages
  css/
    styles.css
  js/
    app.js
  img/
    logo.png
    hero.webp
  fonts/
    inter-v20.woff2
  assets/
    pdf/doc.pdf
  screenshot.png      # Playwright render (if --js)
  manifest.json       # Clone metadata and visited URLs
```

All URLs are rewritten to relative paths for offline browsing. Open `index.html` in a browser.

## Dependencies

Requires (already installed in FreeAI):
- `requests` — HTTP downloads
- `beautifulsoup4` — HTML parsing and URL rewriting
- `curl_cffi` — Anti-bot detection bypass (optional, falls back to requests)
- `playwright` — JS rendering (optional, use `--js` flag)

Install extras if missing:
```bash
pip install requests beautifulsoup4 curl_cffi playwright
playwright install chromium
```

## How It Works

1. **URL Resolution** — Resolves relative URLs against the base domain
2. **Robots.txt Check** — Loads and respects robots.txt rules (configurable)
3. **HTML Fetch** — Downloads HTML via curl_cffi (anti-bot) → requests fallback
4. **HTML Rewriting** — BeautifulSoup parses and rewrites all href/src/CSS url() to relative paths
5. **Asset Extraction** — Finds all <script>, <link>, <img>, <style>, srcset references
6. **Concurrent Download** — ThreadPoolExecutor downloads assets in parallel
7. **Manifest** — Writes JSON manifest with stats and visited URLs

## Limitations

- No login-required pages unless cookies are provided
- JavaScript-heavy SPAs need `--js` (Playwright)
- Dynamic content loaded via AJAX may not be captured without `--js`
- Large sites may exceed disk — use `--limit` and `--depth` to control scope
- Some CDNs block automated downloads — use `--cookies` or `--js` to bypass

## Exit Codes

- `0` — Success
- `1` — Failure (network error, invalid URL, missing dependency)
