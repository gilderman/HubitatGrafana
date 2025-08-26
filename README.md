# 📡 Hubitat Heartbeat + Metrics Monitor

A Hubitat app that periodically sends **heartbeat signals** and **hub metrics** (CPU load, temperature, memory, DB size) to **Grafana Cloud Loki**. Paired with the included **Grafana dashboard**, this lets you monitor the health of your Hubitat hub in real time.

---

## ✨ Features

* Sends a **heartbeat log line** every N seconds (default 60s) to Loki.
* Collects hub metrics:

  * CPU load & % utilization
  * Hub temperature (°C/°F)
  * Free memory (KB)
  * Database size (MB)
* Pushes data using **asynchronous HTTP POST** → lightweight and reliable.
* Grafana dashboard for visualization of uptime & trends.
* Debug logging option for troubleshooting.

---

## 📦 Installation

### Option 1: Install via [Hubitat Package Manager (HPM)](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)

1. Open HPM on your hub.
2. Select **Install** → **Search by Keywords**.
3. Search for **Heartbeat Monitor**.
4. Install the package.

*(If not yet published in HPM, add this repo as a **Custom Repository** with the `apps.json` link.)*

### Option 2: Manual Install

1. Go to **Apps Code** in Hubitat admin UI.
2. Click **+ New App** → paste the contents of `HubitatHeartbeatMetricsMonitor.groovy`.
3. Save.
4. Add the app via **Apps → + Add User App**.

---

## ⚙️ Configuration

When adding the app in Hubitat, configure:

* **Grafana Cloud Loki Push URL**
  e.g.

  ```
  https://logs-prod-XX.grafana.net/loki/api/v1/push
  ```

* **Grafana Cloud Instance ID**
  (Numeric ID from your Grafana Cloud account)

* **Grafana Cloud API Key**
  (Create a token with `logs:write` permission in Grafana Cloud)

* **Heartbeat Interval (seconds)**
  Default: `60` (min: `30`)

* **Debug Logging**
  (Optional, auto-disables after 30 minutes)

✅ Once configured, the app will start pushing heartbeat + metrics to Loki.

---

## 📊 Grafana Dashboard

A sample dashboard JSON is included (`Hubitat Heartbeat Health.json`). It shows:

* Hub uptime (alive heartbeats)
* CPU load & usage %
* Hub internal temperature
* Free memory (trend)
* Database size over time

### Import Dashboard

1. In Grafana, go to **Dashboards → Import**.
2. Upload `dashboard.json` or paste its contents.
3. Choose your **Loki datasource**.
4. Save → your hub metrics should appear.

---

## 🔍 Troubleshooting

* **No data in Grafana?**

  * Check Hubitat logs for errors.
  * Verify your `lokiUrl`, `instanceId`, and `apiKey`.
  * Confirm API key has `logs:write` permission.

* **Heartbeat not showing?**

  * Ensure interval ≥ 30s.
  * Check that hub can reach `logs-prod-XX.grafana.net`.

* **High CPU?**

  * Reduce heartbeat frequency.

---

## 🙌 Credits

* Built by **Ilia Gilderman**
* Inspired by **Hub Information Driver v3** by *Jean P. May, Jr. (thebearmay)*
