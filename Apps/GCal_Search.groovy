def appVersion() { return "2.5.3" }
/**
 *  GCal Search
 *  https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search.groovy
 *
 *  Credits:
 *  Originally posted on the SmartThings Community in 2017:https://community.smartthings.com/t/updated-3-27-18-gcal-search/80042
 *  Special thanks to Mike Nestor & Anthony Pastor for creating the original SmartApp and DTH
 *      UI/UX contributions made by Michael Struck and OAuth improvements by Gary Spender
 *  Code was ported for use on Hubitat Elevation by cometfish in 2019: https://github.com/cometfish/hubitat_app_gcalsearch
 *  Further improvements made by ritchierich and posted to the HubitatCommunity GitHub Repository so other community members can continue to improve this application
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

definition(
    name: "GCal Search",
    namespace: "HubitatCommunity",
    author: "Mike Nestor & Anthony Pastor, cometfish, ritchierich",
    description: "Integrates Hubitat with Google Calendar events to toggle virtual switch.",
    category: "Convenience",
    documentationLink: "https://community.hubitat.com/t/release-google-calendar-search/71397",
    importUrl: "https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
    page(name: "mainPage")
	page(name: "authenticationPage")
    page(name: "getUserCode")
    page name: "authenticationCheck"
    page(name: "utilitiesPage")
    page name: "authenticationReset"
    page(name: "removePage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "${getFormat("title", "GCal Search Version " + appVersion())}", uninstall: false, install: true) {
        if (atomicState.authToken) {
            getCalendarList()

            section() {
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "HubitatCommunity", title: "New Calendar Search...", multiple: true)
                paragraph "${getFormat("line")}"
            }
        }	  
        section("${getFormat("box", "Authentication")}") {
            href ("authenticationPage", title: "Google API Authorization", description: "Click for Google Authentication")
            paragraph "${getFormat("line")}"
        }
        section("${getFormat("box", "Options")}") {
            paragraph "${getFormat("text", "<u>Number of minutes to cache events</u>: To streamline multiple child searches, calendar events are gathered once and cached.  Set the number of minutes you wish to cache the events.  You may always clear the cache on the Utilities page or on the child device.")}"
            input "cacheThreshold", "number", title: "Number of minutes to cache events [default=5 minutes]", required: false
            input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
            href "utilitiesPage", title: "Utilities", description: "Tap to access utilities"
            paragraph "${getFormat("line")}"
        }
        section("${getFormat("box", "Removal")}") {
            href ("removePage", description: "Click to remove ${app.label?:app.name}", title: "Remove GCal Search")
        }
    }
}

def authenticationPage() {
    dynamicPage(name: "authenticationPage", uninstall: false, nextPage: "mainPage") {
        section("${getFormat("box", "Google Authentication")}") {
            if (!atomicState.authToken) {
                paragraph "${getFormat("text", "Enter your Google API credentials below:")}"
                input "gaClientID", "text", title: "Google API Client ID", required: true, submitOnChange: true
                input "gaClientSecret", "text", title: "Google API Client Secret", required: true, submitOnChange: true
            } else {
                paragraph "${getFormat("text", "<strong>Authentication process complete! Click Next to continue setup.</strong>")}"
            }
            if (gaClientID && gaClientSecret) {
                if (!atomicState.deviceCode) {
                    paragraph "${authenticationInstructions(false)}"
                    href "getUserCode", title: "Step 1: Get Google API User Code", description: "Tap to Get Google API User Code"
                }
                if (atomicState.deviceCode && atomicState.verificationUrl && atomicState.userCode && !atomicState.authToken) {
                    paragraph "${authenticationInstructions(true)}"
                    paragraph "${getFormat("text", "Copy the following code to leverage in Step 2: <span style='background-color: #ffff00;'>" + atomicState.userCode + "</span>")}"
                    href url: atomicState.verificationUrl, style: "external", required: true, title: "Step 2: Authenticate GCal Search", description: "Tap to enter User Code and your Google Credentials"
                    paragraph "${getFormat("text", "Once authenticated tap the button below to finish the authentication.")}"
                    href "authenticationCheck", title: "Step 3: Check Authentication", description: "Tap to check authentication once you have successfully authenticated."
                }
                if (atomicState.userCode || !atomicState.authToken) {
                    paragraph "${getFormat("text", "At any time click the button below to restart the authentication process.")}"
                    href "authenticationReset", title: "Reset Google Authentication", description: "Tap to reset Google API Authentication and start over"
                    paragraph "${getFormat("text", "Use the browser back button or click Next to exit.")}"
                }
            }
        }
    }
}

def authenticationInstructions(step1Complete) {
    def text = "<p><span style='text-decoration:underline;font-size: 14pt;'><strong>Three steps are required to complete the Google authentication process:</strong></span></p>"
    text += "<ol style='list-style-position: inside;font-size:15px;'>"
    if (step1Complete) text += "<strike>"
    text += "<li>Tap the 'Get Google API User Code' button to get a User Code.&nbsp; Once received, copy the code into your clipboard as you will need it in step 2.</li>"
    if (step1Complete) text += "</strike>"
    text += "<li>Click the 'Authenticate GCal Search' button and enter the User Code from step 1 and then enter your Google Credentials and follow the additional steps.</li>"
    text += "<li>After successfully authenticating to Google, click the 'Check Authentication' to finalize the setup.</li>"
    text += "</ol>"
    
    return text
}

def getUserCode() {
    def logMsg = []
    def postParams = [
        uri: "https://accounts.google.com",

        path: "/o/oauth2/device/code",
        requestContentType: "application/x-www-form-urlencoded; charset=utf-8",
        body: [
            client_id: getAppClientId(),
            scope: "https://www.googleapis.com/auth/calendar.readonly"
        ]
    ]
    logMsg.push("getUserCode - postParams: ${postParams}")

    try {
        httpPost(postParams) {
            resp ->
            logMsg.push("resp callback ${resp.data}")
            atomicState.deviceCode = resp.data.device_code
            atomicState.verificationUrl = resp.data.verification_url
            atomicState.userCode = resp.data.user_code
        }
    } catch (e) {
        log.error "something went wrong: ${e}, ${e.getResponse().getData()}"
    }
    logDebug("${logMsg}")
    authenticationPage()
}

def authenticationCheck() {
    getToken()
    dynamicPage(name: "authenticationCheck", title: "Checking Authentication", install: true, nextPage: "mainPage") {
        if (atomicState.authToken) {
            section {
                paragraph "Authentication process complete! Click Done to continue setup. Then navigate back into GCal Search to setup searches."
            }
        } else {
            section {
                paragraph "Still waiting for Authentication..."
                href "authenticationCheck", title: "Check Authentication", description: "Tap to check again"
            }
            section("Restart Auth") {
                href "authenticationReset", title: "Reset Google Authentication", description: "Tap to reset Google API Authentication"
            }
        }
    }
}

def getToken() {
    def logMsg = []
    def postParams = [
        uri: "https://www.googleapis.com",

        path: "/oauth2/v4/token",
        requestContentType: "application/x-www-form-urlencoded; charset=utf-8",
        body: [
            client_id: getAppClientId(),
            client_secret: getAppClientSecret(),
            code: state.deviceCode,
            grant_type: "http://oauth.net/grant_type/device/1.0"
        ]
    ]
    logMsg.push("getToken - postParams: ${postParams}")
    
    try {
        httpPost(postParams) {
            resp ->
            logMsg.push("resp callback ${resp.data}")
            if (resp.data.error) {
                logMsg.push("error: ${resp.data.error_description}")
                displayMessageAsHtml(resp.data.error_description)
            } else {
                state.authToken = resp.data.access_token
                state.refreshToken = resp.data.refresh_token
            }
        }

    } catch (e) {
        log.error "getToken - something went wrong: ${e}, ${e.getResponse().getData()}"
    }
    logDebug("${logMsg}")
}

def authenticationReset() {
    atomicState.authToken = null
    atomicState.refreshToken = null
    atomicState.verificationUrl = null
    atomicState.userCode = null
    atomicState.deviceCode = null
    //app.removeSetting("gaClientID")
    //app.removeSetting("gaClientSecret")
    
    authenticationPage()
}

def utilitiesPage() {
    if (settings.clearCache == true) {
        clearEventCache()
        app.updateSetting("clearCache",[type: "bool", value: false])
    }
    if (settings.resyncNow == true) {
        clearEventCache()
        runIn(10, resyncChildApps)
        app.updateSetting("resyncNow",[type: "bool", value: false])
    }
    dynamicPage(name: "utilitiesPage", title: "${getFormat("box", "App Utilities")}", uninstall: false, install: false, nextPage: "mainPage") {
        section() {
            paragraph "${getFormat("text", "<b>All commands take effect immediately!</b>")}"
            input "clearCache", "bool", title: "Clear event cache", required: false, defaultValue: false, submitOnChange: true
            input "resyncNow", "bool", title: "Sync all calendar searches now.  FYI You can sync individual calendar searches by clicking the Poll button within the child switch.", required: false, defaultValue: false, submitOnChange: true
		}
    }
}

def resyncChildApps() {
    childApps.each {
        child ->
        child.poll()
        logDebug "Syncing ${child.label}"
    }
}

def removePage() {
	dynamicPage(name: "removePage", title: "${getFormat("box", "Remove GCal Search and its Children")}", install: false, uninstall: true) {
		section () {
            paragraph("${getFormat("text", "Removing GCal Search also removes all Devices!")}")
		}
	}
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    clearEventCache()
    childApps.each {
        child ->
            logDebug "initialize - child app: ${child.label}"
    }
    state.setup = true
}

def getCalendarList() {
    def logMsg = []
    isTokenExpired("getCalendarList")

    def path = "/calendar/v3/users/me/calendarList"
    def calendarListParams = [
        uri: "https://www.googleapis.com",
        path: path,
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
        query: [format: 'json', body: requestBody]
    ]
    logMsg.push("getCalendarList - calendarListParams: ${calendarListParams}")

    def stats = [:]
    try {
        httpGet(calendarListParams) {
            resp ->
                logMsg.push("resp callback ${resp.data}")
                resp.data.items.each {
                    stat ->
                        stats[stat.id] = (stat.summaryOverride) ? stat.summaryOverride : stat.summary
                }

        }
    } catch (e) {
        log.error "error: ${path}, ${e}"
        if (refreshAuthToken()) {
            return getCalendarList()
        } else {
            log.error "fatality ${e.getResponse().getData()}"
        }
    }

    def i = 1
    def calList = ""
    def calCount = stats.size()
    calList = calList + "\nYou have ${calCount} available Gcal calendars (Calendar Name - calendarId): \n\n"
    stats.each {
        calList = calList + "(${i})  ${it.value} - ${it.key} \n"
        i = i + 1
    }
    logMsg.push("calList: ${calList}")

    state.calendars = stats
    logDebug("${logMsg}")
    return stats
}

def getNextEvents(watchCalendar, GoogleMatching, search, endTimePreference, offsetEnd) {
    def eventCache = atomicState.events
    def cacheEndTimePreference = translateEndTimePref(GoogleMatching == false && eventCache[watchCalendar].endTimePref ? eventCache[watchCalendar].endTimePref : endTimePreference)
    endTimePreference = translateEndTimePref(endTimePreference)
    def logMsg = ["getNextEvents - watchCalendar: ${watchCalendar}, search: ${search}, endTimePreference: ${endTimePreference}, cacheEndTimePreference: ${cacheEndTimePreference}"]
    isTokenExpired("getNextEvents")
    def cacheMilliseconds = (cacheThreshold ?: 5) * 60 * 1000 // By default, cache is 5 minutes
    
    def pathParams = [
        //maxResults: 1,
        orderBy: "startTime",
        singleEvents: true,
        //timeMin: getCurrentTime(),
        timeMin: getStartTime(offsetEnd),
        timeMax: getEndDate(cacheEndTimePreference)
    ]
    
    if (GoogleMatching == true && search != "") {
        pathParams['q'] = "${search}"
    }
    
    logMsg.push("pathParams: ${pathParams}")

    def path = "/calendar/v3/calendars/${watchCalendar}/events"
    def eventListParams = [
        uri: "https://www.googleapis.com",
        path: path,
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
        query: pathParams
    ]
    logMsg.push("eventListParams: ${eventListParams}")

    def evs = []
    if (GoogleMatching == false && eventCache[watchCalendar] && eventCache[watchCalendar].events != null && eventCache[watchCalendar].events != [] && (now() - Date.parse("yyyy-MM-dd'T'HH:mm:ssZ", eventCache[watchCalendar].lastUpdated).getTime() < (cacheMilliseconds))) {
        evs = atomicState.events[watchCalendar].events
        logMsg.push("events pulled from cache")
        // Since state values are stored as strings, convert date values to date
        for (int i = 0; i < evs.size(); i++) {
            evs[i].eventStartTime = Date.parse("yyyy-MM-dd'T'HH:mm:ssX", evs[i].eventStartTime)
            evs[i].eventEndTime = Date.parse("yyyy-MM-dd'T'HH:mm:ssX", evs[i].eventEndTime)
        }
    } else {
        try {
            def queryResponse = []            
            httpGet(eventListParams) {
                resp ->
                queryResponse = resp.data
            }
            
            if (queryResponse && queryResponse.items && queryResponse.items.size() > 0) {
                for (int i = 0; i < queryResponse.items.size(); i++) {
                    def event = queryResponse.items[i]
                    def eventDetails = [:]
                    eventDetails.timeZone = queryResponse.timeZone
                    eventDetails.eventTitle = event.summary.trim()
                    eventDetails.eventLocation = event?.location ? event.location : "none"

                    def eventAllDay
                    def eventStartTime
                    def eventEndTime
                    
                    if (event.start.containsKey('date')) {
                        eventAllDay = true
                        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
                        sdf.setTimeZone(location.timeZone) 
                        eventStartTime = sdf.parse(event.start.date)
                        eventEndTime = new Date(sdf.parse(event.end.date).time - 60)
                    } else {
                        eventAllDay = false
                        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                        sdf.setTimeZone(TimeZone.getTimeZone(eventDetails.timeZone))	
                        eventStartTime = sdf.parse(event.start.dateTime)
                        eventEndTime = sdf.parse(event.end.dateTime)
                    }

                    eventDetails.eventAllDay = eventAllDay
                    eventDetails.eventStartTime = eventStartTime
                    eventDetails.eventEndTime = eventEndTime
                    evs.push(eventDetails)
                }
            }
            if (GoogleMatching == false) {
                eventCache[watchCalendar].lastUpdated = pathParams.timeMin
                eventCache[watchCalendar].events = evs
            }
        } catch (e) {
            log.error "error: ${path}, ${e}, ${e.getResponse().getData()}"
            if (refreshAuthToken()) {
                return getNextEvents(watchCalendar, search)
            } else {
                log.error "fatality, ${e.getResponse().getData()}"
            }
        }
    }
    
    if (cacheEndTimePreference != endTimePreference) {
        def tempEndDate = getEndDate(endTimePreference)
        def tempEvs = []
        for (int c = 0; c < evs.size(); c++) {
            if (evs[c].eventStartTime <= Date.parse("yyyy-MM-dd'T'HH:mm:ssX", tempEndDate)) {
                tempEvs.push(evs[c]) 
            }
        }
        evs = tempEvs
    }
    
    atomicState.events = eventCache
    logMsg.push("events: ${evs}")
    logDebug("${logMsg}")
    return evs
}

def clearEventCache(calendarName) {
    def eventCache = atomicState.events
    logDebug "clearEventCache - calendarName: ${calendarName}"
    
    // Set last updated to a long time ago so getNextEvents queries for new events regardless of cache
    def longTimeAgo = new Date()
    longTimeAgo.set(year: 2000, month: Calendar.JANUARY, dayOfMonth: 1, hourOfDay: 0, minute: 0, second: 0)
    
    if (calendarName == null && eventCache == null) {
        eventCache = [:]
    } else if (calendarName == null && eventCache) {
        def eventCacheCalendarList = eventCache.keySet()
        for (int i = 0; i < eventCacheCalendarList.size(); i++) {
            calendarName = eventCacheCalendarList[i]
            eventCache[calendarName].events = []
            eventCache[calendarName].lastUpdated = longTimeAgo
        }
    } else if (eventCache[calendarName]) {  
        eventCache[calendarName].events = []
        eventCache[calendarName].lastUpdated = longTimeAgo
    }
    
    atomicState.events = eventCache
}

def oauthInitUrl() {
    def logMsg = []
    def postParams = [
        uri: "https://accounts.google.com",

        path: "/o/oauth2/device/code",
        requestContentType: "application/x-www-form-urlencoded; charset=utf-8",
        body: [
            client_id: getAppClientId(),
            scope: "https://www.googleapis.com/auth/calendar.readonly"
        ]
    ]
    logMsg.push("oauthInitUrl - postParams: ${postParams}")

    try {

        httpPost(postParams) {
            resp ->
            logMsg.push("resp callback: ${resp.data}")
            atomicState.deviceCode = resp.data.device_code
            atomicState.verificationUrl = resp.data.verification_url
            atomicState.userCode = resp.data.user_code
        }

    } catch (e) {
        log.error "oauthInitUrl - something went wrong: ${e}, ${e.getResponse().getData()}"
        return
    }
    logDebug("${logMsg}")
}

def isTokenExpired(whatcalled) {
    if (atomicState.last_use == null || now() - atomicState.last_use > 3000) {
        logDebug "isTokenExpired - whatcalled: ${whatcalled}, authToken null or old (>3000) - calling refreshAuthToken()"
        return refreshAuthToken()
    } else {
        logDebug "isTokenExpired - whatcalled: ${whatcalled}, authToken good"
        return false
    }
}

def displayMessageAsHtml(message) {
    def html = """
        <!DOCTYPE html>
        <html>
            <head>
            </head>
            <body>
                <div>
                    ${message}
                </div>
            </body>
        </html>
    """
    render contentType: 'text/html', data: html
}

private refreshAuthToken() {
    def logMsg = ["refreshAuthToken - "]
    if(!atomicState.refreshToken && !state.refreshToken) {
        logMsg.push("Can not refresh OAuth token since there is no refreshToken stored, ${state}")
    } else {
    	def refTok 
   	    if (state.refreshToken) {
        	refTok = state.refreshToken
            logMsg.push("Existing state.refreshToken = ${refTok}")
        } else if (atomicState.refreshToken) {        
        	refTok = atomicState.refreshToken
    		logMsg.push("Existing state.refreshToken = ${refTok}")
        }    
        def stcid = getAppClientId()		
        def stcs = getAppClientSecret()		
        logMsg.push("ClientId: ${stcid}, ClientSecret = ${stcs}")
        		
        def refreshParams = [
            uri   : "https://www.googleapis.com",
            path  : "/oauth2/v4/token",
            body : [
                refresh_token: "${refTok}", 
                client_secret: stcs,
                grant_type: 'refresh_token', 
                client_id: stcid
            ],
        ]
        logMsg.push("refreshParams: ${refreshParams}")

        //changed to httpPost
        try {
            httpPost(refreshParams) { resp ->
                if(resp.data) {
                    logMsg.push("resp callback ${resp.data}")
                    atomicState.authToken = resp?.data?.access_token
					atomicState.last_use = now()
                    logDebug("${logMsg}")
                    return true
                }
            }
        }
        catch(Exception e) {
            //log.error "caught exception refreshing auth token: " + e + ", " + e.getResponse().getData()
            log.error "caught exception refreshing auth token: " + e
        }
    }
    logDebug("${logMsg}")
    return false
}

def toQueryString(Map m) {
   return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def getCurrentTime() {
    //RFC 3339 format
    //2015-06-20T11:39:45.0Z
    def d = new Date().format("yyyy-MM-dd'T'HH:mm:ssZ", location.timeZone)
    return d
}

def getStartTime(offsetEnd) {
    //RFC 3339 format
    //2015-06-20T11:39:45.0Z
    def startDate = new Date()
    
    if (offsetEnd != null && !offsetEnd.toString().startsWith("-")) {
        def tempStartTime = startDate.getTime()
        tempStartTime = tempStartTime - offsetEnd
        startDate.setTime(tempStartTime)
    }

    def d = startDate.format("yyyy-MM-dd'T'HH:mm:ssZ", location.timeZone)
    return d
}

def getEndDate(endTimePreference) {
    //RFC 3339 format
    //2015-06-20T11:39:45.0Z
    def endDate = new Date()
    int numberOfHours
    
    if (["endOfToday", "endOfTomorrow"].indexOf(endTimePreference) > -1) {
        endDate.setHours(23);
        endDate.setMinutes(59);
        endDate.setSeconds(59);
        
        if (endTimePreference == "endOfTomorrow") {
            numberOfHours = 24
        }
    } else if (endTimePreference instanceof Number) {
        numberOfHours = endTimePreference
    }
    
    if (numberOfHours != null) {
        def tempEndTime = endDate.getTime()
        tempEndTime = tempEndTime + (numberOfHours * 1000 * 60 * 60)
        endDate.setTime(tempEndTime)
    }

    def d = endDate.format("yyyy-MM-dd'T'HH:mm:ssZ", location.timeZone)
    return d
}

def translateEndTimePref(endTimePref) {
    def endTimePreference
    switch (endTimePref) {        
        case "End of Current Day":
            endTimePreference = "endOfToday"
            break
        case "End of Next Day":
            endTimePreference = "endOfTomorrow"
            break
        //case "Number of Hours from Current Time":
            //endTimePreference = settings.endTimeHours
            //break
        default:
            endTimePreference = endTimePref
    }
    
    return endTimePreference
}

def setCacheDuration(type, tempEndTimePref=null) {
    def eventCache = (atomicState.events == null) ? [:] : atomicState.events
    def childApps = app.getAllChildApps()
    def watchCalendar
    def endTimePref
    if (childApps.size() == 0) {
        if (type == "add") {
            def tempEndTimePrefList = tempEndTimePref.keySet()
            for (int i = 0; i < tempEndTimePrefList.size(); i++) {
                watchCalendar = tempEndTimePrefList[i]
                endTimePref = translateEndTimePref(tempEndTimePref[watchCalendar])
                eventCache[watchCalendar] = [:]
                eventCache[watchCalendar].events = []
                eventCache[watchCalendar].endTimePref = endTimePref
            }
        } else {
            // Do nothing for remove and zero child triggers left
        }
    } else {
        int removeAppID
        if (type == "remove") {
            removeAppID = tempEndTimePref
            tempEndTimePref = [:]
        }

        tempEndTimePref = (tempEndTimePref == null) ? [:] : tempEndTimePref
        def calendarCounter = [:]
        for (int c = 0; c < childApps.size(); c++) {
            def childApp = childApps[c]
            if ((type == "remove" && childApp.id == removeAppID) || childApp.GoogleMatching == true ) {
                continue // Skip
            }

            watchCalendar = childApp.watchCalendars.toString()
            if (calendarCounter[watchCalendar] == null) {
                calendarCounter[watchCalendar] = 1
            } else {
                calendarCounter[watchCalendar] += 1
            }
            endTimePref = translateEndTimePref(childApp.endTimePref)
            endTimePref = (["endOfToday", "endOfTomorrow"].indexOf(endTimePref) > -1) ? endTimePref : childApp.endTimeHours
            if (eventCache[watchCalendar] == null || eventCache[watchCalendar] instanceof List) {
                eventCache[watchCalendar] = [:]
            }
            if (eventCache[watchCalendar] && eventCache[watchCalendar].events == null) {
                eventCache[watchCalendar].events = []
            }
            if (eventCache[watchCalendar].endTimePref == null) {
                eventCache[watchCalendar].endTimePref = endTimePref
            } else {
                if (tempEndTimePref[watchCalendar] == null) {
                    tempEndTimePref[watchCalendar] = endTimePref
                }
                if (type == "add" && calendarCounter[watchCalendar] == 1 && getEndDate(tempEndTimePref[watchCalendar]) > getEndDate(endTimePref)) {
                    eventCache[watchCalendar].endTimePref = tempEndTimePref[watchCalendar]
                } else if (getEndDate(tempEndTimePref[watchCalendar]) <= getEndDate(endTimePref)) {
                    eventCache[watchCalendar].endTimePref = endTimePref
                }
            }
        }
    }
    
    atomicState.events = eventCache
}

def getAppClientId() { return gaClientID }
def getAppClientSecret() { return gaClientSecret }

def uninstalled() {
    //revokeAccess()
}

def childUninstalled() { 

}

def revokeAccess() {
    logDebug "GCalSearch: revokeAccess()"

	refreshAuthToken()
	
	if (!atomicState.authToken) {
    	return
    }
    
	try {
    	def uri = "https://accounts.google.com/o/oauth2/revoke?token=${atomicState.authToken}"
        logDebug "Revoke: ${uri}"
		httpGet(uri) { resp ->
            logDebug "resp ${resp.data}"
    		revokeAccessToken()
            atomicState.accessToken = atomicState.refreshToken = atomicState.authToken = state.refreshToken = null
		}
	} catch (e) {
        log.error "something went wrong: ${e}, ${e.getResponse().getData()}"
	}
}

def getFormat(type, displayText=""){ // Modified from @Stephack and @dman2306 Code   
    def color = "#1A77C9"
    if(type == "title") return "<h2 style='color:" + color + ";font-weight:bold'>${displayText}</h2>"
    if(type == "box") return "<div style='color:white;text-align:left;background-color:#1A77C9;padding:2px;padding-left:10px;'><h3><b><u>${displayText}</u></b></h3></div>"
    if(type == "text") return "<span style='font-size: 14pt;'>${displayText}</span>"
    if(type == "line") return "<hr style='background-color:" + color + "; height: 1px; border: 0;'>"
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
