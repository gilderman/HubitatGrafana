/**
 *  Hubitat Heartbeat + Metrics Monitor
 *
 *  Sends heartbeat and hub metrics to Grafana Cloud Loki
 *
 *  Credits:
 *    Hub Information Driver v3
 *    namespace: thebearmay
 *    author: Jean P. May, Jr.
 *
 *  Author: Ilia Gilderman
 *  Date: 2025-08-24
 */

definition(
    name: "Hubitat Heartbeat + Metrics Monitor",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Sends periodic heartbeat metrics to Grafana Cloud for monitoring hub status",
    category: "Monitoring",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Hubitat Heartbeat Monitor", install: true, uninstall: true) {
        section("Grafana Cloud Loki Configuration") {
            input "lokiUrl", "text",
                title: "Grafana Cloud Loki Push URL",
                description: "e.g., https://logs-prod-XX.grafana.net/loki/api/v1/push",
                required: true

            input "grafanaInstanceId", "text",
                title: "Grafana Cloud Instance ID",
                description: "Usually a number like 123456",
                required: true

            input "grafanaApiKey", "password",
                title: "Grafana Cloud API Key/Token",
                required: true

            input "heartbeatInterval", "number",
                title: "Heartbeat Interval (seconds)",
                description: "How often to send heartbeat (minimum 30 seconds)",
                defaultValue: 60,
                range: "30..3600",
                required: true
        }

        section("Options") {
            input "debugEnable", "bool",
                title: "Enable Debug Logging",
                defaultValue: false
        }

        if (lokiUrl && grafanaInstanceId && grafanaApiKey) {
            section("Status") {
                paragraph "✅ Configuration looks good! Heartbeats will be sent every ${heartbeatInterval} seconds."
                paragraph "📡 Target: Grafana Cloud Loki"
                paragraph "🏠 Hub: <code>${location.name}</code>"
                paragraph "🔗 URL: <code>${lokiUrl}</code>"
            }
        }
    }
}

def installed() {
    log.info "Hubitat Heartbeat Monitor installed"
    initialize()
}

def updated() {
    log.info "Hubitat Heartbeat Monitor updated"
    unschedule()
    initialize()
}

def uninstalled() {
    log.info "Hubitat Heartbeat Monitor uninstalled"
    unschedule()
}

def initialize() {
    if (!lokiUrl || !grafanaInstanceId || !grafanaApiKey) {
        log.error "Missing required configuration. Please check settings."
        return
    }

    state.metrics = [:]

    def interval = Math.max((heartbeatInterval ?: 60) as Integer, 30)

    log.info "Starting heartbeat monitor - sending to Loki every ${interval} seconds"

    // Send initial heartbeat + metrics
    sendHeartbeatAndMetrics()

    // Schedule recurring heartbeats
    runIn(interval, "sendHeartbeatAndMetrics", [overwrite: true])

    if (debugEnable) {
        log.debug "Debug logging enabled - will disable automatically in 30 minutes"
        runIn(1800, "disableDebugLogging")
    }
}

// Heartbeat + collect metrics
def sendHeartbeatAndMetrics() {
    try {
        // Collect metrics async
        cpuLoadReq()
        cpuTemperatureReq()
        freeMemoryReq()
        dbSizeReq()

        // Delay heartbeat sending slightly to allow async calls to populate state
        runIn(3, "sendHeartbeatToLoki")
    } catch(Exception e) {
        log.error "Error sending heartbeat/metrics: ${e.message}"
    }

    // Reschedule next heartbeat
    def interval = Math.max((heartbeatInterval ?: 60) as Integer, 30)
    runIn(interval, "sendHeartbeatAndMetrics", [overwrite: true])
}

def sendHeartbeatToLoki() {
    def timestampNs = (now() * 1000000).toString()
    def hostname = location.name ?: "hubitat"
    def metrics = state.metrics ?: [:]

    def labels = [
        "job": "hubitat_heartbeat",
        "host": hostname,
        "source": "hubitat_app"
    ]

    def logMessage = "alive timestamp=${(now()/1000).toLong()} " + metrics.collect { k,v -> "${k}=${v}" }.join(" ")

    def payload = [
        "streams": [
            [
                "stream": labels,
                "values": [
                    [timestampNs, logMessage]
                ]
            ]
        ]
    ]

    def credentials = "${grafanaInstanceId}:${grafanaApiKey}"
    def encodedCredentials = credentials.bytes.encodeBase64().toString()

    def params = [
        uri: lokiUrl,
        contentType: 'application/json',
        headers: [
            'Authorization': "Basic ${encodedCredentials}",
            'Content-Type': 'application/json; charset=UTF-8',
            'User-Agent': 'Hubitat-Heartbeat-Monitor/1.0'
        ],
        body: groovy.json.JsonOutput.toJson(payload),
        timeout: 30
    ]

    if (debugEnable) log.debug "Sending heartbeat to Loki: ${params.body}"

    try {
        asynchttpPost("heartbeatResponse", params)
    } catch(Exception e) {
        log.error "Failed async POST to Loki: ${e.message}"
    }
}

def heartbeatResponse(response, data) {
    if (response.status in [200, 204]) {
        if (debugEnable) log.debug "Heartbeat sent successfully to Loki (${response.status})"
    } else {
        log.warn "Heartbeat failed with status: ${response.status} - ${response.errorMessage}"
    }
}

def disableDebugLogging() {
    log.info "Automatically disabling debug logging"
    app.updateSetting("debugEnable", [type: "bool", value: false])
}

/* ------------------ Async Hub Metric Calls ------------------ */

void cpuLoadReq(){
    def params = [
        uri    : "http://127.0.0.1:8080",
        path   : "/hub/advanced/freeOSMemoryLast",
        headers: [ "Connection-Timeout": 600 ]
    ]
    if(debugEnable) log.debug params
    asynchttpGet("getCpuLoad", params)
}

void getCpuLoad(resp, data){
    String loadWork
    try {
        if(resp.getStatus() in [200, 207]) loadWork = resp.data.toString()
    } catch(ignored) {
        if (!state.warnSuppress) log.warn "getCpuLoad httpResp = ${resp.getStatus()} but returned invalid data, will retry next cycle"
    }
    if(loadWork) {
 log.debug loadWork
        def lines = loadWork.readLines()
        if(lines) {
            def workSplit = lines[-1].split(",")
            if(workSplit.size() > 2) {
                def cpuWork = workSplit[2].toDouble()
                state.metrics["cpu5Min"] = cpuWork.round(2)
                cpuWork = (cpuWork / 4.0D) * 100.0D
                state.metrics["cpuPct"] = cpuWork.round(2)
            }
        }
    }
}

void cpuTemperatureReq(){
    def params = [
        uri    : "http://127.0.0.1:8080",
        path   : "/hub/advanced/internalTempCelsius",
        headers: [ "Connection-Timeout":600 ]
    ]
    if(debugEnable) log.debug params
    asynchttpGet("getCpuTemperature", params)
}

void getCpuTemperature(resp, data){
    try {
        if(resp.getStatus() in [200,207]) {
            def temp = resp.data.toDouble()
            if(temp>0) {
                if(location.temperatureScale=="F")
                    temp = (temp * 9/5)+32
                state.metrics["temperature"] = String.format("%.1f", temp)
            }
        }
    } catch(ignored) {
        if (!state.warnSuppress) log.warn "getCpuTemperature httpResp = ${resp.getStatus()} invalid data"
    }
}

void freeMemoryReq(){
    def params = [
        uri    : "http://127.0.0.1:8080",
        path   : "/hub/advanced/freeOSMemory",
        headers: [ "Connection-Timeout":600 ]
    ]
    if(debugEnable) log.debug params
    asynchttpGet("getFreeMemory", params)
}

void getFreeMemory(resp, data){
    try {
        if(resp.getStatus() in [200,207]) {
            def memWork = resp.data.toInteger()
            state.metrics["freeMemoryKB"] = memWork
        }
    } catch(ignored) {
        if (!state.warnSuppress) log.warn "getFreeMemory httpResp = ${resp.getStatus()} invalid data"
    }
}

void dbSizeReq(){
    def params = [
        uri    : "http://127.0.0.1:8080",
        path   : "/hub/advanced/databaseSize",
        headers: [ "Connection-Timeout":600 ]
    ]
    if(debugEnable) log.debug params
    asynchttpGet("getDbSize", params)
}

void getDbSize(resp, data){
    try {
        if(resp.getStatus() in [200,207]) state.metrics["dbSizeMB"] = resp.data.toInteger()
    } catch(ignored) {
        if (!state.warnSuppress) log.warn "getDbSize httpResp = ${resp.getStatus()} invalid data"
    }
}
