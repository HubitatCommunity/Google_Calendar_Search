def appVersion() { return "3.0.2" }
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

/*
    To Do remove upgrade check from authTokenValid in future
*/

def mainPage() {
    dynamicPage(name: "mainPage", title: "${getFormat("title", "GCal Search Version " + appVersion())}", uninstall: false, install: true) {
        def isAuthorized = authTokenValid("mainPage")
        logDebug("mainPage - isAuthorized: ${isAuthorized}")
        if (isAuthorized) {
            section("${getFormat("box", "Search Triggers")}") {
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "HubitatCommunity", title: "New Search...", multiple: true)
                paragraph "${getFormat("line")}"
            }
        }	  
        section("${getFormat("box", "Authentication")}") {
            if (!isAuthorized) {
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
            def isAuthorized = authTokenValid("authenticationPage")
            if (!isAuthorized && !atomicState.authToken) {
                paragraph "${getFormat("text", "Enter your Google API credentials below.  Instructions to setup these credentials can be found in <a href='https://github.com/HubitatCommunity/Google_Calendar_Search' target='_blank'>HubitatCommunity GitHub</a>.")}"
                input "gaClientID", "text", title: "Google API Client ID", required: true, submitOnChange: true
                input "gaClientSecret", "text", title: "Google API Client Secret", required: true, submitOnChange: true
            } else if (!isAuthorized) {
                paragraph "${getFormat("warning", "Authentication Problem! Please click Reset Google Authentication and try the setup again.")}"
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
    def text = "<p><span style='text-decoration:underline;font-size: 14pt;'><strong>Steps required to complete the Google authentication process:</strong></span></p>"
    text += "<ul style='list-style-position: inside;font-size:15px;'>"
    text += "<li>Tap the 'Authenticate GCal Search' button below to start the authentication process.</li>"
    text += "<li>A popup will appear walking you through process as outlined in the instructions on GitHub.</li>"
    text += "<li>Be sure to select the appropriate access to Google Calendar, Google Reminders, and/or Google Tasks.</li>"
    text += "<li>Troubleshooting Note: If the popup presents an 'Authorization Error, Error 400: redirect_uri_mismatch' please check your OAuth credential in the Google Console to ensure it is of type Web application and that the redirect URI is set correctly.</li>"
    text += "</ul>"
    
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
    upgradeSettings()
    state.version = appVersion()
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
    //Upgrade check
    if (state.scopesAuthorized == null && ["mainPage", "authenticationPage"].indexOf(fromFunction) > -1) {
        return false
    }
    
    if (atomicState.tokenExpires >= now()) {
        logDebug "authTokenValid - fromFunction: ${fromFunction}, authToken good expires ${new Date(atomicState.tokenExpires)}"
        return true
    } else {
        def refreshAuthToken = refreshAuthToken()
        logDebug "authTokenValid - fromFunction: ${fromFunction}, authToken ${(atomicState.tokenExpires == null) ? "null" : "expired (" + new Date(atomicState.tokenExpires) + ")"} - calling refreshAuthToken: ${refreshAuthToken}"
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
                logDebug "Resp Status: ${resp.status}}"
            }
        } catch (e) {
            if (e.response.status == 401 && refreshAuthToken()) {
                return apiGet(fromFunction, uri, path, queryParams)
            } else if (e.response.status == 403) {
                log.error "apiGet - path: ${path}, ${e}, ${e.getResponse().getData()}"
                apiResponse = "error"
            } else {
                log.error "apiGet - path: ${path}, ${e}, ${e.getResponse().getData()}"
            }
        }
    } else {
        logMsg.push("Authentication Problem")
    }
    
    logMsg.push("apiResponse: ${apiResponse}")
    logDebug("${logMsg}")
    return apiResponse
}

def apiPut(fromFunction, uri, path, bodyParams) {
    def logMsg = []
    def apiResponse = []  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiPut - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}")
    
    if (isAuthorized == true) {
        def output = new JsonOutput()
        def apiParams = [
            uri: uri,
            path: path,
            contentType: "application/json",
            headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
            body: output.toJson(bodyParams)
        ]
        logMsg.push("apiParams: ${apiParams}")
        
        try {
            httpPut(apiParams) {
                resp ->
                apiResponse = resp.data
                logDebug "Resp Status: ${resp.status}, apiResponse: ${apiResponse}"
            }
        } catch (e) {
            if (e.response.status == 401 && refreshAuthToken()) {
                return apiPut(fromFunction, uri, path, bodyParams)
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

def apiPatch(fromFunction, uri, path, bodyParams) {
    def logMsg = []
    def apiResponse = []  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiPatch - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}")
    
    if (isAuthorized == true) {
        def output = new JsonOutput()
        def apiParams = [
            uri: uri,
            path: path,
            contentType: "application/json",
            headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
            body: output.toJson(bodyParams)
        ]
        logMsg.push("apiParams: ${apiParams}")
        
        try {
            httpPatch(apiParams) {
                resp ->
                apiResponse = resp.data
                logDebug "Resp Status: ${resp.status}, apiResponse: ${apiResponse}"
            }
        } catch (e) {
            if (e.response.status == 401 && refreshAuthToken()) {
                return apiPatch(fromFunction, uri, path, bodyParams)
            } else {
                log.error "apiPatch - path: ${path}, ${e}, ${e.getResponse().getData()}"
            }
        }
    } else {
        logMsg.push("Authentication Problem")
    }
    
    logDebug("${logMsg}")
    return apiResponse
}

def apiPost(fromFunction, uri, path, protobuf, bodyParams) {
    def logMsg = []
    def apiResponse = [:]  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiPost - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}")
    
    if (isAuthorized == true) {
        def contentType = "application/json"
        
        if (protobuf) {
            contentType += "+protobuf"
        }
        
        def apiParams = [
            uri: uri,
            path: path,
            contentType: "application/json",
            headers: ["Content-Type": contentType, "Authorization": "Bearer ${atomicState.authToken}"]
        ]
        if (bodyParams) {
            def output = new JsonOutput()
            apiParams.body = output.toJson(bodyParams)
        }
        logMsg.push("apiParams: ${apiParams}")
        
        try {
            httpPost(apiParams) {
                resp ->
                apiResponse.status = resp.status
                apiResponse.data = resp.data
                logDebug "apiResponse: ${apiResponse}"
            }
        } catch (e) {
            if (e.response.status == 401 && refreshAuthToken()) {
                return apiPost(fromFunction, uri, path, bodyParams)
            } else {
                log.error "apiPost - path: ${path}, ${e}, ${e.getResponse().getData()}"
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
    
    if (calendars instanceof Map && calendars.size() > 0) {
        calendars.items.each {
            calendarItem ->
            calendarList[calendarItem.id] = (calendarItem.summaryOverride) ? calendarItem.summaryOverride : calendarItem.summary
        }
        logMsg.push("calendarList: ${calendarList}")
    } else {
        calendarList = calendars
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
    
    if (taskLists instanceof Map && taskLists.size() > 0) {
        taskLists.items.each {
            taskListItem ->
            taskList[taskListItem.id] = taskListItem.title
        }
        logMsg.push("taskLists: ${taskLists}")
    } else {
        taskList = taskLists
    }
    
    logDebug("${logMsg}")
    
    return taskList
}

def getNextTasks(taskList, search, endTimePreference) {    
    endTimePreference = translateEndTimePref(endTimePreference)
    def logMsg = ["getNextTasks - taskList: ${taskList}, search: ${search}, endTimePreference: ${endTimePreference}"]
    def tasksList = []
    def uri = "https://www.googleapis.com"
    def path = "/tasks/v1/lists/${taskList}/tasks"
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
            tasksList.push(taskDetails)
        }
    }
    
    logMsg.push("tasksList:\n${tasksList.join("\n")}")
    logDebug("${logMsg}")
    return tasksList
}

def completeTask(watchTaskList, taskID) {
    def logMsg = ["completeTask - watchTaskList: ${watchTaskList}, taskID: ${taskID} - "]
    def uri = "https://tasks.googleapis.com"
    def path = "/tasks/v1/lists/${watchTaskList}/tasks/${taskID}"
    def bodyParams = [
        id: taskID,
        status: "completed"
    ]
    def task = apiPatch("completeTask", uri, path, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, task: ${task}")
    logDebug("${logMsg}")
    return task
}

/* ============================= End Google Task ============================= */

/* ============================= Start Google Reminder - WARNING: Uses unnofficial API ============================= */

def getNextReminders(search, endTimePreference) {
    endTimePreference = translateEndTimePref(endTimePreference)
    def logMsg = ["getNextReminders - search: ${search}, endTimePreference: ${endTimePreference}"]
    def reminderList = []
    def uri = "https://reminders-pa.clients6.google.com"
    def path = "/v1internalOP/reminders/list"
    def dueMax = getEndDate(endTimePreference, false)
    logMsg.push("dueMax: ${dueMax}")
    def bodyParams = [
        //"max_results": 10,
        //"utc_due_before_ms": dueMax.getTime()
        "due_before_ms": dueMax.getTime()
    ]
    def reminders = apiPost("getNextReminders", uri, path, false, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, reminders: ${reminders}")
        
    if (reminders.status == 200 && reminders.data && reminders.data.task && reminders.data.task.size() > 0) {
        for (int i = 0; i < reminders.data.task.size(); i++) {
            def reminder = reminders.data.task[i]
            def dueDate = getReminderDate(reminder.dueDate)
            if (dueDate <= dueMax) {
                def reminderDetails = [:]
                reminderDetails.kind = "reminder"
                reminderDetails.taskTitle = reminder.title.trim()
                reminderDetails.taskID = reminder.taskId.serverAssignedId
                reminderDetails.taskDueDate = dueDate
                reminderList.push(reminderDetails)
            }
        }
        reminderList.sort{it.taskDueDate}
    }
    
    logMsg.push("reminderList:\n${reminderList.join("\n")}")
    logDebug("${logMsg}")
    return reminderList
}

def getSpecificReminder(taskID) {
    def logMsg = ["getSpecificReminder - taskID: ${taskID}"]
    def reminderList = []
    def uri = "https://reminders-pa.clients6.google.com"
    def path = "/v1internalOP/reminders/get"
    def bodyParams = [
        "taskId": [['serverAssignedId': taskID]]
    ]
    def reminders = apiPost("getNextReminders", uri, path, false, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, reminders: ${reminders}")
        
    if (reminders.status == 200 && reminders.data && reminders.data.task && reminders.data.task.size() > 0) {
        for (int i = 0; i < reminders.data.task.size(); i++) {
            def reminder = reminders.data.task[i]
            def reminderDetails = [:]
            reminderDetails.kind = "reminder"
            reminderDetails.taskTitle = reminder.title.trim()
            reminderDetails.taskID = reminder.taskId.serverAssignedId
            reminderDetails.taskDueDate = getReminderDate(reminder.dueDate)
            reminderList.push(reminderDetails)
        }
    }
    
    logMsg.push("reminderList:\n${reminderList.join("\n")}")
    logDebug("${logMsg}")
    return reminderList
}

def getReminderDate(dueDate) {
    //[year:2022, month:1, day:24, time:[hour:20, minute:0, second:0]]
    //[year:2022, month:2, day:3, allDay:true]
    def dateString = new Date().copyWith(
        year: dueDate.year, 
        month: dueDate.month-1,
        dayOfMonth: dueDate.day, 
        hourOfDay: (dueDate.time) ? dueDate.time.hour : 0,
        minute: (dueDate.time) ? dueDate.time.minute : 0,
        second: (dueDate.time) ? dueDate.time.second : 0
    )
    
    return dateString
}

def deleteReminder(taskID) {
    def logMsg = ["deleteReminder - taskID: ${taskID}"]
    def reminderDeleted = false
    def uri = "https://reminders-pa.clients6.google.com"
    def path = "/v1internalOP/reminders/delete"
    def bodyParams = [
        "taskId": [["serverAssignedId": taskID]]
    ]
    def reminders = apiPost("getNextReminders", uri, path, false, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, reminders: ${reminders}")
        
    if (reminders.status == 200 ) {
        reminderDeleted = true
    }
    
    logMsg.push("reminderDeleted: ${reminderDeleted}")
    logDebug("${logMsg}")
    return reminderDeleted
}

def completeReminder(taskID) {
    def logMsg = ["completeReminder - taskID: ${taskID}"]
    def reminderCompleted = false
    def uri = "https://reminders-pa.clients6.google.com"
    def path = "/v1internalOP/reminders/update"
    def bodyParams = [
        "1": ["4": "WRP / /WebCalendar/calendar_190319.03_p1"],
        "2": ["1": taskID],
        "4": ["1": ["1": taskID], "8": 1],
        "7": ["1": [1, 10, 3]],
    ]
    
    def reminders = apiPost("getNextReminders", uri, path, true, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, reminders: ${reminders}")
        
    if (reminders.status == 200 ) {
        reminderCompleted = true
    }
    
    logMsg.push("reminderCompleted: ${reminderCompleted}")
    logDebug("${logMsg}")
    return reminderCompleted
}

/*
def completeReminder2(taskID) {
    //taskID = "1723034518730493062"
    def logMsg = ["completeReminder - "]
    def bodyParams = [
    '1': ['4': "WRP / /WebCalendar/calendar_190319.03_p1"],
        '2': ['1': taskID],
        '4': ['1': ['1': taskID],
              '8': 1],
        '7': ['1': [1, 10, 3]]
    ]
    
    def output = new JsonOutput()
    bodyParams = output.toJson(bodyParams)
    
    logMsg.push("bodyParams: ${bodyParams}")
    
    def path = "/v1internalOP/reminders/update"
    def eventListParams = [
        uri: "https://reminders-pa.clients6.google.com",
        path: path,
        headers: ["Content-Type": "application/json+protobuf", "Authorization": "Bearer ${atomicState.authToken}"],
        body: bodyParams
    ]
    logMsg.push("eventListParams: ${eventListParams}")

    try {
        httpPost(eventListParams) {
            resp ->
            log.debug "Resp Status: ${resp.status}, resp: ${resp}, resp.data: ${resp.data}"
        }
    } catch (e) {
        log.error "completeReminder - error: ${path}, ${e}, ${e.getResponse().getData()}"
    }
                  
    logDebug("${logMsg}")
}

def getReminders() {
    def logMsg = ["getReminders - "]
    def bodyParams = [
        "5": 1,
        "6": 20
    ]
    def output = new JsonOutput()
    bodyParams = output.toJson(bodyParams)
    
    logMsg.push("bodyParams: ${bodyParams}")
    
    def path = "/v1internalOP/reminders/list"
    def eventListParams = [
        uri: "https://reminders-pa.clients6.google.com",
        path: path,
        headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
        //body: bodyParams
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

def getEndDate(endTimePreference, format=true) {
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
    
    def returnDate
    if (format) {
        returnDate = endDate.format("yyyy-MM-dd'T'HH:mm:ssZ", location.timeZone)
    } else {
        returnDate = endDate
    }
    return returnDate
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

def getScopesAuthorized() {
    def answer = []
    def scopesAuthorized = state.scopesAuthorized
    
    if (scopesAuthorized.indexOf("auth/calendar") > -1) {
        answer.push("Calendar Event")
    }
    if (scopesAuthorized.indexOf("auth/tasks") > -1) {
        answer.push("Task")
    }
    if (scopesAuthorized.indexOf("auth/reminders") > -1) {
        answer.push("Reminder")
    }
    
    return answer
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

def upgradeSettings() {
    if (state.version == null || state.version != appVersion()) {
        childApps.each {
            child ->
            child.upgradeSettings()
        }

        // Remove old states from previous version that are no longer utilized.  This code will be removed in the future
        state.remove("authCode")
        state.remove("calendars")
        state.remove("deviceCode")
        state.remove("events")
        state.remove("isScheduled")
        state.remove("last_use")
        state.remove("setup")
        state.remove("userCode")
        state.remove("verificationUrl")

        // Remove old settings from previous version that are no longer utilized.
        app.removeSetting("cacheThreshold")
        app.removeSetting("clearCache")
        app.removeSetting("resyncNow")

        log.info "Upgraded GCal Search settings"
    }
}
