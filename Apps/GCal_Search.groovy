/**
 *  GCal Search v1.2.1
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
def appVersion() { return "1.2.1" }

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
    dynamicPage(name: "mainPage", title: "GCal Search Version ${appVersion()}", uninstall: false, install: true) {
        if (atomicState.authToken) {
            getCalendarList()

            section() {
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "HubitatCommunity", title: "New Calendar Search...", multiple: true)
            }
        }	  
        section("Authentication") {
            href ("authenticationPage", title: "Google API Authorization", description: "Click for Google Authentication")
        }
        section("Options") {
            href "utilitiesPage", title: "Utilities", description: "Tap to access utilities"
            paragraph "To streamline multiple child searches, calendar events are gathered once and cached.  Set the number of minutes you wish to cache the events.  You may always clear the cache on the Utilities page."
            input "cacheThreshold", "number", title: "Number of minutes to cache events [default=5 minutes]", required: false
            input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
        }
        section("Removal") {
            href ("removePage", description: "Click to remove ${app.label?:app.name}", title: "Remove GCal Search")
        }
    }
}

def authenticationPage() {
    dynamicPage(name: "authenticationPage", uninstall: false, nextPage: "mainPage") {
        section("Google Authentication") {
            if (!atomicState.authToken) {
                paragraph "Enter your Google API credentials below:"
                input "gaClientID", "text", title: "Google API Client ID", required: true, submitOnChange: true
                input "gaClientSecret", "text", title: "Google API Client Secret", required: true, submitOnChange: true
            } else {
                paragraph "<p><strong>Authentication process complete! Click Next to continue setup.</strong></p>"
            }
            if (gaClientID && gaClientSecret) {
                if (!atomicState.deviceCode) {
                    paragraph "${authenticationInstructions(false)}"
                    href "getUserCode", title: "Step 1: Get Google API User Code", description: "Tap to Get Google API User Code"
                }
                if (atomicState.deviceCode && atomicState.verificationUrl && atomicState.userCode && !atomicState.authToken) {
                    paragraph "${authenticationInstructions(true)}"
                    paragraph "<p>Copy the following code to leverage in Step 2: <span style='background-color: #ffff00;'>" + atomicState.userCode + "</span></p>"
                    href url: atomicState.verificationUrl, style: "external", required: true, title: "Step 2: Authenticate GCal Search", description: "Tap to enter User Code and your Google Credentials"
                    paragraph "Once authenticated tap the button below to finish the authentication."
                    href "authenticationCheck", title: "Step 3: Check Authentication", description: "Tap to check authentication once you have successfully authenticated."
                }
                if (atomicState.userCode || !atomicState.authToken) {
                    paragraph "At any time click the button below to restart the authentication process."
                    href "authenticationReset", title: "Reset Google Authentication", description: "Tap to reset Google API Authentication and start over"
                    paragraph "Select  '<'  at upper left corner to exit."
                }
            }
        }
    }
}

def authenticationInstructions(step1Complete) {
    def text = "<p><span style='text-decoration: underline;'><strong>Three steps are required to complete the Google authentication process:</strong></span></p>"
    text += "<ol style='list-style-position: inside;'>"
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
    dynamicPage(name: "utilitiesPage", title: "App Utilities", uninstall: false, install: false, nextPage: "mainPage") {
        section() {
			paragraph "<b>All commands take effect immediately!</b>"
            input "clearCache", "bool", title: "Clear event cache", required: false, defaultValue: false, submitOnChange: true
            input "resyncNow", "bool", title: "Sync all calendar searches now", required: false, defaultValue: false, submitOnChange: true
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
	dynamicPage(name: "removePage", title: "GCal Search\nRemove GCal Search and its Children", install: false, uninstall: true) {
		section () {
			paragraph("Removing GCal Search also removes all Devices!")
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
                        stats[stat.id] = stat.summary
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

def getNextEvents(watchCalendar, search) {
    def logMsg = ["getNextEvents - watchCalendar: ${watchCalendar}, search: ${search}"]
    isTokenExpired("getNextEvents")
    def tempCacheMinutes = cacheThreshold ?: 5 // By default, cache is 5 minutes
    tempCacheMinutes = tempCacheMinutes * 60
    
    def pathParams = [
        //maxResults: 1,
        orderBy: "startTime",
        singleEvents: true,
        timeMin: getCurrentTime(),
        timeMax: getEndOfDay()
    ]
    /*if (search != "") {
        pathParams['q'] = "${search}"
    }*/
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
    if (state.events && state.events[watchCalendar]) {
        evs = state.events[watchCalendar]
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
                        sdf.setTimeZone(TimeZone.getTimeZone(queryResponse.timeZone))            	
                        eventStartTime = sdf.parse(event.start.date)
                        eventEndTime = new Date(sdf.parse(event.end.date).time - 60)
                    } else {
                        eventAllDay = false
                        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                        sdf.setTimeZone(TimeZone.getTimeZone(queryResponse.timeZone))	            
                        eventStartTime = sdf.parse(event.start.dateTime)
                        eventEndTime = sdf.parse(event.end.dateTime)
                    }

                    eventDetails.eventAllDay = eventAllDay
                    eventDetails.eventStartTime = eventStartTime
                    eventDetails.eventEndTime = eventEndTime
                    evs.push(eventDetails)
                }
            }
            state.events[watchCalendar] = evs
        } catch (e) {
            log.error "error: ${path}, ${e}, ${e.getResponse().getData()}"
            if (refreshAuthToken()) {
                return getNextEvents(watchCalendar, search)
            } else {
                log.error "fatality, ${e.getResponse().getData()}"
            }
        }
    }
    logMsg.push("events: ${evs}")
    
    if (!state.isScheduled) {
        logMsg.push("scheduling cache in ${tempCacheMinutes}")
        runIn(tempCacheMinutes, clearEventCache)
        state.isScheduled = true
    }
    logDebug("${logMsg}")
    return evs
}

def clearEventCache(calendarName) {
    logDebug "clearEventCache - calendarName: ${calendarName}"
    
    if (calendarName != null && state.events[calendarName]) {  
        //state.events[calendarName] = []
        state.events.remove(calendarName);
    } else {
        state.events = [:]
    }
    state.isScheduled = false
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
            log.error "caught exception refreshing auth token: " + e + ", " + e.getResponse().getData()
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
   def d = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
   return d
}

def getEndOfDay() {
    //RFC 3339 format
    //2015-06-20T11:39:45.0Z
    def endDate = new Date()
    endDate.setHours(23);
    endDate.setMinutes(59);
    endDate.setSeconds(59);

    def d = endDate.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
    return d
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

private logDebug(msg) {
    if (isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
