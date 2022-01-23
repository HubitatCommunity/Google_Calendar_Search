def appVersion() { return "3.0.0" }
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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
private getClientId()     { return settings.gaClientID }
private getClientSecret() { return settings.gaClientSecret }
private getRedirectURL()  { "https://cloud.hubitat.com/oauth/stateredirect" }
private oauthInitState()  { "${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}" }

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
    page(name: "utilitiesPage")
    page name: "authenticationReset"
    page(name: "removePage")
}

mappings {
	path("/callback") {action: [GET: "callback"]}
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "${getFormat("title", "GCal Search Version " + appVersion())}", uninstall: false, install: true) {
        def isAuthorized = false
        if (atomicState.authToken) {
            section("${getFormat("box", "Calendar Search")}") {
                if (atomicState.scopesAuthorized.indexOf("auth/calendar") > -1) {
                    app(name: "childApps", appName: "GCal Search Trigger", namespace: "HubitatCommunity", title: "New Calendar Search...", multiple: true)
                } else {
                    paragraph "${getFormat("text", "Please repeat the Google API Authorization and select calendar access.")}"
                }
                paragraph "${getFormat("line")}"
            }

            section("${getFormat("box", "Task Search")}") {
                if (atomicState.scopesAuthorized.indexOf("auth/tasks") > -1) {
                    app(name: "childApps", appName: "GTask Search Trigger", namespace: "HubitatCommunity", title: "New Task Search...", multiple: true)
                } else {
                    paragraph "${getFormat("text", "Authentication Problem! Please repeat the Google API Authorization and select task access.")}"
                }
                paragraph "${getFormat("line")}"
            }
        }	  
        section("${getFormat("box", "Authentication")}") {
            if (!atomicState.authToken) {
                paragraph "${getFormat("warning", "Authentication Problem! Please click the button below to setup Google API Authorization.")}"
            }
            href ("authenticationPage", title: "Google API Authorization", description: "Click for Google Authentication")
            paragraph "${getFormat("line")}"
        }
        section("${getFormat("box", "Options")}") {
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
                if (!atomicState.authToken) {
                    paragraph "${authenticationInstructions(false)}"
                    href url: getOAuthInitUrl(), style: "external", required: true, title: "Authenticate GCal Search", description: "Tap to start the authentication process"
                }
                paragraph "${getFormat("text", "At any time click the button below to restart the authentication process.")}"
                href "authenticationReset", title: "Reset Google Authentication", description: "Tap to reset Google API Authentication and start over"
                paragraph "${getFormat("text", "Use the browser back button or click Next to exit.")}"
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

def authenticationReset() {
    revokeAccess()
    atomicState.authToken = null
    atomicState.oauthInitState = null
    atomicState.refreshToken = null
    atomicState.tokenExpires = null
    atomicState.scopesAuthorized = null
    
    authenticationPage()
}

def utilitiesPage() {
    if (settings.resyncNow == true) {
        runIn(10, resyncChildApps)
        app.updateSetting("resyncNow",[type: "bool", value: false])
    }
    dynamicPage(name: "utilitiesPage", title: "${getFormat("box", "App Utilities")}", uninstall: false, install: false, nextPage: "mainPage") {
        section() {
            paragraph "${getFormat("text", "<b>All commands take effect immediately!</b>")}"
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
    // Removed old states from previous version that are no longer utilized.  This code will be removed in the future
    state.remove("setup")
    state.remove("calendars")
    state.remove("events")
    state.remove("authCode")
    state.remove("last_use")
    
    childApps.each {
        child ->
            logDebug "initialize - child app: ${child.label}"
    }
    if (!state.accessToken) {
        def accessToken = createAccessToken()
		state.accessToken = accessToken
        state.oauthInitState = "${getHubUID()}/apps/${app.id}/callback?access_token=${accessToken}"
        logDebug("Access token is : ${state.accessToken}, oauthInitState: ${state.oauthInitState}")
	}
}

def uninstalled() {
    revokeAccess()
}

def childUninstalled() { 

}

/* ============================= Start Google APIs ============================= */
def getOAuthInitUrl() {
    if (!state.accessToken) {
        initialize()
    }
    
    def OAuthInitUrl = "https://accounts.google.com/o/oauth2/v2/auth"
    
    def oauthParams = [
		response_type: "code",
        access_type: "offline",
        prompt: "consent",
		client_id: getClientId(),
		state: state.oauthInitState,
		redirect_uri: getRedirectURL(),
        scope: "https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/reminders"
	]
    
    OAuthInitUrl += "?" + toQueryString(oauthParams)
    
    //logDebug("OAuthInitUrl: ${OAuthInitUrl}")
    return OAuthInitUrl
}

def callback() {
	def code = params.code
	def oauthState = params.state
    def logMsg = ["callback - params: $params, code: ${code}, oauthState: ${oauthState}"]

	if (oauthState == state.oauthInitState) {
		def tokenParams = [
			code: code,
            client_id : getClientId(),
            client_secret: getClientSecret(),
            redirect_uri: getRedirectURL(),
			grant_type: "authorization_code"			
		]

		def tokenUrl = "https://oauth2.googleapis.com/token"
		def params = [
			uri: tokenUrl,
			contentType: 'application/x-www-form-urlencoded',
			body: tokenParams
		]
        logMsg.push("params: ${params}")

		httpPost(params) { resp ->
            logMsg.push("Resp Status: ${resp.status}, Data: ${resp.data}")
			def slurper = new JsonSlurper()

			resp.data.each { key, value ->
				def data = slurper.parseText(key)
				state.refreshToken = data.refresh_token
				state.authToken = data.access_token
				state.tokenExpires = now() + (data.expires_in * 1000)
				state.scopesAuthorized = data.scope
			}
		}

		// Handle success and failure here, and render stuff accordingly
        def message = ""
        if (state.authToken) {
            logMsg.push("OAuth flow succeeded")
            message = """
            <p>Your Google Account has been successfully authorized.</p>
            <p>Close this page to continue with the setup.</p>
            """
        } else {
			logMsg.push("OAuth flow failed")
            message = """
	        <p>The connection could not be established!</p>
	        <p>Close this page and click Reset Google Authentication to try again.</p>
	        """
		}
        logDebug("${logMsg}")
        connectionStatus(message)
	} else {
		log.error "callback() failed oauthState != state.oauthInitState"
	}
}

private refreshAuthToken() {
    def answer
    def logMsg = ["refreshAuthToken - "]
    if(!atomicState.refreshToken && !state.refreshToken) {
        answer = false
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
        		
        def refreshParams = [
            uri   : "https://www.googleapis.com",
            path  : "/oauth2/v4/token",
            body : [
                refresh_token: "${refTok}", 
                client_secret: getClientSecret(),
                grant_type: 'refresh_token', 
                client_id: getClientId()
            ],
        ]
        logMsg.push("refreshParams: ${refreshParams}")

        try {
            httpPost(refreshParams) {
                resp ->
                if(resp.data) {
                    logMsg.push("resp callback ${resp.data}")
                    atomicState.authToken = resp.data.access_token
                    atomicState.tokenExpires = now() + (resp.data.expires_in * 1000)
                    answer = true
                }
            }
        }
        catch(Exception e) {
            //log.error "caught exception refreshing auth token: " + e + ", " + e.getResponse().getData()
            log.error "refreshAuthToken - caught exception refreshing auth token: " + e
            answer = false
        }
    }
    
    logMsg.push("returning ${answer}")
    logDebug("${logMsg}")
    return answer
}

def authTokenValid(fromFunction) {
    if (atomicState.tokenExpires >= now()) {
        logDebug "authTokenValid - fromFunction: ${fromFunction}, authToken good expires ${new Date(atomicState.tokenExpires)}"
        return true
    } else {
        def refreshAuthToken = refreshAuthToken()
        logDebug "authTokenValid - fromFunction: ${fromFunction}, authToken null or expired (${new Date(atomicState.tokenExpires)}) - calling refreshAuthToken: ${refreshAuthToken}"
        return refreshAuthToken
    }
}

def revokeAccess() {
    logDebug "GCalSearch: revokeAccess()"
    revokeAccessToken()
	refreshAuthToken()
	
	if (!atomicState.authToken) {
    	return
    }
    
	try {
    	def uri = "https://accounts.google.com/o/oauth2/revoke?token=${atomicState.authToken}"
        logDebug "Revoke: ${uri}"
		httpGet(uri) { resp ->
            logDebug "Resp Status: ${resp.status}, Data: ${resp.data}"    		
            //atomicState.accessToken = atomicState.refreshToken = atomicState.authToken = state.refreshToken = null
		}
	} catch (e) {
        log.error "revokeAccess - something went wrong: ${e}, ${e.getResponse().getData()}"
	}
}


def apiGet(fromFunction, uri, path, queryParams) {
    def logMsg = []
    def apiResponse = []  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiGet - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}")
    
    if (isAuthorized == true) {
        def apiParams = [
            uri: uri,
            path: path,
            headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
            query: queryParams
        ]
        logMsg.push("apiParams: ${apiParams}")
        
        try {
            httpGet(apiParams) {
                resp ->
                apiResponse = resp.data
                logDebug "Resp Status: ${resp.status}, apiResponse: ${apiResponse}"
            }
        } catch (e) {
            if (refreshAuthToken()) {
                return apiGet(fromFunction, path, queryParams)
            } else {
                log.error "apiGet - path: ${path}, ${e}, ${e.getResponse().getData()}"
            }
        }
    } else {
        logMsg.push("Authentication Problem")
    }
    
    logDebug("${logMsg}")
    return apiResponse
}

def apiPut(fromFunction, uri, path, bodyParams) {
    def logMsg = []
    def apiResponse = []  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiPut - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}")
    
    if (isAuthorized == true) {
        def apiParams = [
            uri: uri,
            path: path,
            contentType: "application/json",
            headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
            body: bodyParams
        ]
        logMsg.push("apiParams: ${apiParams}")
        
        try {
            httpPut(apiParams) {
                resp ->
                apiResponse = resp.data
                logDebug "Resp Status: ${resp.status}, apiResponse: ${apiResponse}"
            }
        } catch (e) {
            if (refreshAuthToken()) {
                return apiPut(fromFunction, path, bodyParams)
            } else {
                log.error "apiPut - path: ${path}, ${e}, ${e.getResponse().getData()}"
            }
        }
    } else {
        logMsg.push("Authentication Problem")
    }
    
    logDebug("${logMsg}")
    return apiResponse
}

/* ============================= End Google APIs ============================= */

/* ============================= Start Google Calendar ============================= */
def getCalendarList() {
    def logMsg = []
    def calendarList = [:]
    def uri = "https://www.googleapis.com"
    def path = "/calendar/v3/users/me/calendarList"
    def queryParams = [format: 'json']
    def calendars = apiGet("getCalendarList", uri, path, queryParams)
    logMsg.push("getCalendarList - path: ${path}, queryParams: ${queryParams}, calendars: ${calendars}")
    
    if (calendars.size() > 0) {
        calendars.items.each {
            calendarItem ->
            calendarList[calendarItem.id] = (calendarItem.summaryOverride) ? calendarItem.summaryOverride : calendarItem.summary
        }
        logMsg.push("calendarList: ${calendarList}")
    }
    
    logDebug("${logMsg}")
    return calendarList
}

def getNextEvents(watchCalendar, GoogleMatching, search, endTimePreference, offsetEnd, dateFormat) {    
    endTimePreference = translateEndTimePref(endTimePreference)
    def logMsg = ["getNextEvents - watchCalendar: ${watchCalendar}, search: ${search}, endTimePreference: ${endTimePreference}"]
    def eventList = []
    def uri = "https://www.googleapis.com"
    def path = "/calendar/v3/calendars/${watchCalendar}/events"
    def queryParams = [
        //maxResults: 1,
        orderBy: "startTime",
        singleEvents: true,
        //timeMin: getCurrentTime(),
        timeMin: getStartTime(offsetEnd),
        timeMax: getEndDate(endTimePreference)
    ]
    
    if (GoogleMatching == true && search != "") {
        queryParams['q'] = "${search}"
    }

    def events = apiGet("getNextEvents", uri, path, queryParams)
    logMsg.push("queryParams: ${queryParams}, events: ${events}")

    if (events.items && events.items.size() > 0) {
        for (int i = 0; i < events.items.size(); i++) {
            def event = events.items[i]
            def eventDetails = [:]
            eventDetails.kind = event.kind
            //eventDetails.timeZone = events.timeZone
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
                sdf.setTimeZone(TimeZone.getTimeZone(events.timeZone))	
                eventStartTime = sdf.parse(event.start.dateTime)
                eventEndTime = sdf.parse(event.end.dateTime)
            }

            eventDetails.eventAllDay = eventAllDay
            eventDetails.eventStartTime = eventStartTime
            eventDetails.eventEndTime = eventEndTime
            eventList.push(eventDetails)
        }
    }
    
    logMsg.push("eventList:\n${eventList.join("\n")}")
    logDebug("${logMsg}")
    return eventList
}

/* ============================= End Google Calendar ============================= */

/* ============================= Start Google Task ============================= */

def getTaskList() {
    def logMsg = []
    def taskList = [:]
    def uri = "https://www.googleapis.com"
    def path = "/tasks/v1/users/@me/lists"
    def queryParams = [format: 'json']
    def taskLists = apiGet("getTaskList", uri, path, queryParams)
    logMsg.push("getTaskList - path: ${path}, queryParams: ${queryParams}, taskLists: ${taskLists}")
    
    if (taskLists.size() > 0) {
        taskLists.items.each {
            taskListItem ->
            taskList[taskListItem.id] = taskListItem.title
        }
        logMsg.push("taskLists: ${taskLists}")
    }
    
    logDebug("${logMsg}")
    
    return taskList
}

def getNextTasks(watchCalendar, search, endTimePreference) {
    endTimePreference = translateEndTimePref(endTimePreference)
    def logMsg = ["getNextTasks - watchCalendar: ${watchCalendar}, search: ${search}, endTimePreference: ${endTimePreference}"]
    def taskList = []
    def uri = "https://www.googleapis.com"
    def path = "/tasks/v1/lists/${watchCalendar}/tasks"
    def queryParams = [
        //maxResults: 1,
        showCompleted: false,
        dueMax: getEndDate(endTimePreference)
    ]
    def tasks = apiGet("getNextTasks", uri, path, queryParams)
    logMsg.push("queryParams: ${queryParams}, tasks: ${tasks}")
        
    if (tasks.items && tasks.items.size() > 0) {
        for (int i = 0; i < tasks.items.size(); i++) {
            def task = tasks.items[i]
            def taskDetails = [:]
            taskDetails.kind = task.kind
            taskDetails.taskTitle = task.title.trim()
            taskDetails.taskID = task.id
            def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            taskDetails.taskDueDate = sdf.parse(task.due)
            taskList.push(taskDetails)
        }
    }
    
    logMsg.push("taskList:\n${taskList.join("\n")}")
    logDebug("${logMsg}")
    return taskList
}

def completeTask(watchTaskList, taskID) {
    def logMsg = ["completeTask - watchTaskList: ${watchTaskList}, taskID: ${taskID} - "]
    def uri = "https://tasks.googleapis.com"
    def path = "/tasks/v1/lists/${watchTaskList}/tasks/${taskID}"
    def putParams = [
        id: taskID,
        status: "completed"
    ]
    def output = new JsonOutput()
    def task = apiPut("completeTask", uri, path, output.toJson(putParams))
    logMsg.push("putParams: ${putParams}, task: ${task}")
    logDebug("${logMsg}")
    return task
}

/* ============================= End Google Task ============================= */

/* ============================= Start Google Reminder ============================= */

/*
WARNING USING UNOFFICIAL API
TODO to add later

def getReminders() {
    def logMsg = ["getReminders - "]
    def pathParams = [
        "5": 1,
        "6": 20
    ]
    def output = new JsonOutput()
    pathParams = output.toJson(pathParams)
    
    logMsg.push("pathParams: ${pathParams}")
    
    def path = "/v1internalOP/reminders/list"
    def eventListParams = [
        uri: "https://reminders-pa.clients6.google.com",
        path: path,
        headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
        //body: pathParams
    ]
    logMsg.push("eventListParams: ${eventListParams}")

    def evs = []
    try {
        def queryResponse = []            
        httpPost(eventListParams) {
            resp ->
            log.debug "Resp Status: ${resp.status}, resp.data: ${resp.data}"
        }
    } catch (e) {
        log.error "getReminders - error: ${path}, ${e}, ${e.getResponse().getData()}"
        if (refreshAuthToken()) {
            return getNextEvents(watchCalendar, search)
        } else {
            log.error "getReminders - fatality, ${e.getResponse().getData()}"
        }
    }
                  
    //logMsg.push("events: ${evs}")
    logDebug("${logMsg}")
}
*/

/* ============================= End Google Reminder ============================= */

def matchItem(items, caseSensitive, searchTerms, searchField) {
    def logMsg = ["matchItem - caseSensitive: ${caseSensitive}, searchTerms: ${searchTerms}, searchField: ${searchField}, items: ${items}"]
    def tempItems = []
    searchTerms = searchTerms.toString()
    if (caseSensitive == false) {
        searchTerms = searchTerms.toLowerCase()
    }
    searchTerms = searchTerms.split(",")
    for (int s = 0; s < searchTerms.size(); s++) {
        def searchTerm = searchTerms[s].trim()
        logMsg.push("searchTerm: '${searchTerm}'")

        for (int i = 0; i < items.size(); i++) {
            tempItem = items[i]
            def itemMatch = false
            def itemSearchFieldValue = items[i][searchField]
            if (caseSensitive == false) {
                itemSearchFieldValue = itemSearchFieldValue.toLowerCase()
            }
            logMsg.push("itemSearchFieldValue: ${itemSearchFieldValue}")

            def ignoreMatch = false
            if (searchTerm.indexOf("-") > -1) {
                def pattern = ~/-[\w]+/
                def ignoreWords = (searchTerm =~ pattern).findAll()
                for (int iG = 0; iG < ignoreWords.size(); iG++) {
                    def ignoreWord = ignoreWords[iG].substring(1).trim()
                    if (itemSearchFieldValue.indexOf(ignoreWord) > -1) {
                        logMsg.push("No Match: ignore word '${ignoreWord}' found")
                        ignoreMatch = true
                        break
                    } else {
                        //Remove word from searchTerm
                        searchTerm = searchTerm.replace(ignoreWords[iG], "").trim()
                    }
                }
                logMsg.push("searchTerm trimmed to'${searchTerm}'")
            }

            if (ignoreMatch == false) {
                if (searchTerm == "*") {
                    itemMatch = true
                } else if (searchTerm.startsWith("=") && itemSearchFieldValue == searchTerm.substring(1)) {
                    itemMatch = true
                } else if (searchTerm.indexOf("*") > -1) {
                    def searchList = searchTerm.toString().split("\\*")
                    for (int sL = 0; sL < searchList.size(); sL++) {
                        def searchItem = searchList[sL].trim()
                        if (itemSearchFieldValue.indexOf(searchItem) > -1) {
                            itemMatch = true
                        } else {
                            itemMatch = false
                            break
                        }
                    }
                } else if (itemSearchFieldValue.startsWith(searchTerm)) {
                    itemMatch = true
                }
            }

            logMsg.push("itemMatch: ${itemMatch}")
            if (itemMatch) {
                tempItems.push(tempItem)
            }
        }
    }
    
    logDebug("${logMsg}")
    return tempItems
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

def getFormat(type, displayText=""){ // Modified from @Stephack and @dman2306 Code   
    def color = "#1A77C9"
    if(type == "title") return "<h2 style='color:" + color + ";font-weight:bold'>${displayText}</h2>"
    if(type == "box") return "<div style='color:white;text-align:left;background-color:#1A77C9;padding:2px;padding-left:10px;'><h3><b><u>${displayText}</u></b></h3></div>"
    if(type == "text") return "<span style='font-size: 14pt;'>${displayText}</span>"
    if(type == "warning") return "<span style='font-size: 14pt;color:red'><strong>${displayText}</strong></span>"
    if(type == "line") return "<hr style='background-color:" + color + "; height: 1px; border: 0;'>"
}

def connectionStatus(message, redirectUrl = null) {
    def redirectHtml = ""
	if (redirectUrl) {
		redirectHtml = """
			<meta http-equiv="refresh" content="3; url=${redirectUrl}" />
		"""
	}

	def html = """
		<!DOCTYPE html>
		<html>
		    <head>
		        <meta name="viewport" content="width=device-width, initial-scale=1">
		        <title>Google Connection</title>
		    </head>
		    <body>
			    <div class="container">
				    <img src="https://cdn.shopify.com/s/files/1/2575/8806/t/20/assets/logo-image-file.png" alt="Hubitat logo" />
				    ${message}
			    </div>
            </body>
        </html>
	"""
	render contentType: 'text/html', data: html
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ")
        }
        log.debug "$msg"
    }
}
