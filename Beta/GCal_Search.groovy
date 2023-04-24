def appVersion() { return "4.0.1" }
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
    page(name: "addNotificationDevice")
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
        def isAuthorized = authTokenValid("mainPage")
        logDebug("mainPage - isAuthorized: ${isAuthorized}")
        if (isAuthorized) {
            section("${getFormat("box", "Search Triggers")}") {
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "HubitatCommunity", title: "New Search...", multiple: true)
                paragraph "${getFormat("line")}"
            }

            section("${getFormat("box", "Gmail Notification Devices")}") {
                if (state.scopesAuthorized.indexOf("mail.google.com") > -1) {
                    clearNotificationDeviceSettings()
                    paragraph notificationDeviceInstructions()
                    input name: "security", type: "bool", title: "Do you plan to send local files from File Manager <b>and</b> have hub security enabled? Credentials are required to get the local file.", defaultValue: false, submitOnChange: true
                    if (settings.security == true) { 
                        input name: "username", type: "string", title: "Hub Security Username", required: true
                        input name: "password", type: "password", title: "Hub Security Password", required: true
                    }
                    paragraph getNotificationDevices()
                    paragraph "${getFormat("line")}"

                } else {
                    paragraph "${getFormat("text", "This app is capable of creating Gmail Notification devices to send email notifications from rules. In order to leverage this feature:\n1. Enable the <a href='https://console.cloud.google.com/apis/api/gmail.googleapis.com' target='_blank'>Gmail API</a> in the Google Console\n2. Click Google API Authorization below and then Reset Google Authentication. Leave your existing credentials alone; you just need to reauthorize the APIs including Gmail\n3. Follow steps to complete the Google authentication process again and be sure to allow Hubitat access to Gmail when prompted.")}"
                }
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
            input name: "appName", type: "text", title: "Name this parent app", required: true, defaultValue: "GCal Search", submitOnChange: true
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
    def isOAuthEnabled = oauthEnabled()
    def readyToInstall = false
    def isAuthorized = false
    if (isOAuthEnabled) {
        isAuthorized = authTokenValid("authenticationPage")
        if (isAuthorized && !atomicState.version) {
            readyToInstall = true
        }
    }
    
    dynamicPage(name: "authenticationPage", install: readyToInstall, uninstall: false, nextPage: "mainPage") {
        section("${getFormat("box", "Google Authentication")}") {
            if (isOAuthEnabled) {
                // Make sure no leading or trailing spaces on gaClientID and gaClientSecret
                if (settings.gaClientID && settings.gaClientID != settings.gaClientID.trim()) {
                    app.updateSetting("gaClientID",[type: "text", value: settings.gaClientID.trim()])
                }
                if (settings.gaClientSecret && settings.gaClientSecret != settings.gaClientSecret.trim()) {
                    app.updateSetting("gaClientSecret",[type: "text", value: settings.gaClientSecret.trim()])
                }
                if (!atomicState.authToken && !isAuthorized) {
                    paragraph "${getFormat("text", "Enter your Google API credentials below.  Instructions to setup these credentials can be found in <a href='https://github.com/HubitatCommunity/Google_Calendar_Search' target='_blank'>HubitatCommunity GitHub</a>.")}"
                    input "gaClientID", "text", title: "Google API Client ID", required: true, submitOnChange: true
                    input "gaClientSecret", "text", title: "Google API Client Secret", required: true, submitOnChange: true
                } else if (!isAuthorized) {
                    paragraph "${getFormat("warning", "Authentication Problem! Please click Reset Google Authentication and try the setup again.")}"
                } else if (readyToInstall) {
                    paragraph "${getFormat("text", "<strong>Authentication process complete!</strong>")}"
                    paragraph "${getFormat("warning", "Click Done to complete the installation of this app.  Open the GCal Search app again to setup Google search triggers.")}"
                } else {
                    paragraph "${getFormat("text", "<strong>Authentication process complete! Click Next to continue setup.</strong>")}"
                }
                if (gaClientID && gaClientSecret) {
                    if (!atomicState.authToken) {
                        paragraph "${authenticationInstructions()}"
                        href url: getOAuthInitUrl(), style: "external", required: true, title: "Authenticate GCal Search", description: "Tap to start the authentication process"
                    }
                    paragraph "${getFormat("text", "At any time click the button below to restart the authentication process.")}"
                    href "authenticationReset", title: "Reset Google Authentication", description: "Tap to reset Google API Authentication and start over"
                    paragraph "${getFormat("text", "Use the browser back button or click Next to exit.")}"
                }
            } else {
                paragraph "${getFormat("warning", "<strong>OAuth must be enabled on the GCal Search app.</strong>")}"
                paragraph "${oAuthInstructions()}"
            }
        }
    }
}

def oAuthInstructions() {
    def text = "<p><span style='text-decoration:underline;font-size: 14pt;'><strong>Steps to enable OAuth:</strong></span></p>"
    text += "<ol style='list-style-position: inside;font-size:15px;'>"
    text += "<li>Please <a href='/app/list' target='_blank'><u>click this link</u></a> to open another browser tab to enable this setting in Apps Code.</li>"
    text += "<ul><li> Instructions to enable OAuth can be found in the 'Enabling OAuth' Section of the <a href='https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps' target='_blank'><u>How to Install Custom Apps</u></a> article.</li></ul>"
    text += "<li>After completing Step 1 is <a href='authenticationPage'><u>refresh this page</u></a> (browser refresh) to continue setup.</li>"
    text += "</ol>"
    
    return text
}

def authenticationInstructions() {
    def text = "<p><span style='text-decoration:underline;font-size: 14pt;'><strong>Steps required to complete the Google authentication process:</strong></span></p>"
    text += "<ul style='list-style-position: inside;font-size:15px;'>"
    text += "<li>Tap the 'Authenticate GCal Search' button below to start the authentication process.</li>"
    text += "<li>A popup will appear walking you through process as outlined in the instructions on GitHub.</li>"
    text += "<li>Be sure to select the appropriate access to Google Calendar, Google Reminders, Google Tasks and/or Gmail.</li>"
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

def addNotificationDevice() {
    dynamicPage(name: "addNotificationDevice", title: "${getFormat("box", "Add Gmail Notification Device")}", uninstall: false, install: false, nextPage: "mainPage") {
        section() {
            if (state.missingDriver == null) {
                paragraph "${getFormat("text", "<b>Fill in the following details and click anywhere on the screen to expose the 'Create Notification Device' button. Click this to add a new Gmail notification device and repeat steps to add additional Gmail notification devices.  Click Next to return to the main menu.</b>")}"
                input "notifLabel", "text", title: "Notification device name", required: false, submitOnChange: true
                input "notifTo", "text", title: "Default Email address to send notification (if one is not passed in the notification)", required: false, submitOnChange: true
                input "notifSubject", "text", title: "Default Email Subject (if one is not passed in the notification)", defaultValue: "${location.name} Notification", required: false, submitOnChange: true
                if (settings.notifLabel && settings.notifTo) {
                    input name: "createChild", type: "button", title: "Create Notification Device", backgroundColor: "Green", textColor: "white", width: 4, submitOnChange: true
                }
                paragraph "${getFormat("line")}"
                paragraph "${getFormat("text", "<b>Existing Gmail Notification Devices:</b>\n${getNotificationDevices(false)}")}"
            } else {
                paragraph "${getFormat("text", "Gmail Notification Device driver is missing and a notification device cannot be created.\n1. Please download the <a href='https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Driver/Gmail_Notification_Device.groovy' target='_blank'>Gmail Notification Device driver from GitHub</a>\n2. Navigate to <a href='/driver/list' target='_blank'>Drivers code</a> and install this driver\n3. Once installed click the 'Driver Installed' button to continue adding Gmail notification devices")}"
                input name: "driverInstalled", type: "button", title: "Driver Installed", backgroundColor: "Green", textColor: "white", width: 4, submitOnChange: true
            }
		}
    }
}

def notificationDeviceInstructions() {
    def text = "<p><span style='text-decoration;font-size: 14pt;'><strong>Email message settings can dynamically get set via notification message. <u>Optionally</u> include the following keys separated by commas at the beginning of the message, followed by the email body. Keys are case sensitive.</strong></span></p>"
    text += "<ul style='list-style-position: inside;font-size:15px;'>"
    text += "<li><u>To</u>: recipient@example.com <b>Note</b>: Multiple email addresses can be separated by ';' (semicolon)</li>"
    text += "<li><u>Subject</u>: Dynamic Email Subject</li>"
    text += "<li><u>File</u>: Exact name of file within your hub's File Manager</li>"
    text += "<li>If including any dynamic settings from above, the final comma value should be the body of the email. The body is base64 encoded so HTML tags can be included in the email body if desired.</li>"
    text += "<li>For example 'Subject:Urgent from Hubitat, To:newRecipient@example.com, File:CameraImage.jpg, &#60;b&#62;Dishwasher&#60;/b&#62; water sensor is wet!' will send the email to 'newRecipient@example.com', make the email subject 'Urgent from Hubitat', attach the 'CameraImage.jpg' file, and the body of the email '<b>Dishwasher</b> water sensor is wet!' with the word Dishwasher in bold.</li>"
    text += "</ul>"
    
    return text
}

def getNotificationDevices(showAdd=true) {
    def childDevices = getAllChildDevices()
    if (childDevices.size() == 0 && showAdd == false) return "None"
    
    String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'>" + 
        "<th style='border-right:2px solid black'>Device Name</th>" +
		"<th>Email Address</th>" +
		"<th>Email Subject</th></tr></thead>"
	childDevices.sort{it.displayName.toLowerCase()}.each {dev ->
		def devPrefs = dev.getPreferenceValues()
		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
            "<td>${devPrefs.toEmail}</td>" +
			"<td>${devPrefs.toSubject}</td></tr>"
	}
	str += "</table>"
    if (showAdd) {
        String newNotificationDevice = buttonLink("createNewDevice", "<iconify-icon icon='fluent:mail-add-16-regular'></iconify-icon>", "#007009", "25px")
        str += "<table class='mdl-data-table tstat-col' style=';border:none'><thead><tr>" +
            //"<th style='border-bottom:2px solid black;border-right:2px solid black;border-left:2px solid black;width:50px' title='Create New Gmail Notification Device'><a href='/installedapp/configure/${app.id}/mainPage/addNotificationDevice'>$newNotificationDevice</a></th>" +
            "<th style='border-bottom:2px solid black;border-right:2px solid black;border-left:2px solid black;width:50px' title='Create New Gmail Notification Device'><a href='addNotificationDevice'>$newNotificationDevice</a></th>" +
            "<th style='border:none;color:green;font-size:1.125rem'><b><i class='he-arrow-left2' style='vertical-align:middle'></i> Create New Gmail Notification Device</b></th>" +
            "</tr></thead></table>"
    }
    str += "</div>"
    
    return str
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

def clearNotificationDeviceSettings() {
    app.updateSetting("notifLabel", [value:"", type:"text"])
    app.updateSetting("notifTo", [value:"", type:"text"])
    app.updateSetting("notifSubject", [value:"", type:"text"])
}

def appButtonHandler(btn) {
    switch(btn) {
        case "createChild":
            createDevice()
            clearNotificationDeviceSettings()
            break
        case "driverInstalled":
            atomicState.missingDriver = null
            return
    }
}

def createDevice(){
    try{
    	state.vsIndex = (state.vsIndex) ? state.vsIndex + 1 : 1	//increment even on invalid device type
        def deviceLabel = settings.notifLabel.toString().trim()
		def deviceID = deviceLabel.toLowerCase().replace(" ", "_")
        deviceID += "-${state.vsIndex}"
		logDebug "Attempting to create Virtual Device: Label: ${deviceLabel}, deviceID: ${deviceID}"
		childDevice = addChildDevice("HubitatCommunity", "Gmail Notification Device", "${deviceID}", [label: "${deviceLabel}", isComponent: false])
    	logDebug "createDevice Success"
		childDevice.updateSetting("toEmail",[value:"${settings.notifTo}",type:"text"])
        childDevice.updateSetting("toSubject",[value:"${settings.notifSubject}",type:"text"])
		logDebug "toEmail Update Success"
        app.removeSetting("missingDriver")
    } catch (Exception e) {
        if (e.toString().indexOf("Device type 'Gmail Notification Device' in namespace 'HubitatCommunity' not found") > -1) {
            log.error "Gmail Notification Device driver is missing.  Please navigate to Drivers code and install this driver.\\nInstructions can be found in the Hubitat Documentation: https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers\\nDriver can be found here: https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Driver/Gmail_Notification_Device.groovy"
            state.missingDriver = true
        } else {
            log.error "Unable to create device. Error: ${e}"
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
            paragraph("${getFormat("text", "Removing GCal Search will revoke its access to your Google Account, removes all child search triggers, and also removes all child devices! This may impact existing rules you have in place. Please note that you will need to manually delete the project in the Google Console.")}")
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
    // Make sure no leading or trailing spaces on gaClientID and gaClientSecret
    if (settings.gaClientID && settings.gaClientID != settings.gaClientID.trim()) {
        app.updateSetting("gaClientID",[type: "text", value: settings.gaClientID.trim()])
    }
    if (settings.gaClientSecret && settings.gaClientSecret != settings.gaClientSecret.trim()) {
        app.updateSetting("gaClientSecret",[type: "text", value: settings.gaClientSecret.trim()])
    }
    
    updateAppLabel()
    upgradeSettings()
}

def uninstalled() {
    revokeAccess()
}

def childUninstalled() { 

}

def oauthEnabled() {
    def answer = false
    if (state.accessToken) {
        answer = true
    } else {
        def accessToken
        try {
            accessToken = createAccessToken()
        } catch (e) {
            if (e.toString().indexOf("OAuth is not enabled for this App") > -1) {
                log.error "OAuth must be enabled on the GCal Search app.  Please navigate to Apps code and enable OAuth.  Instructions can be found in the Hubitat Documentation: https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps"
            } else {
                log.error "${e}"
            }
            answer = false
        }
        if (accessToken) {
            state.accessToken = accessToken
            state.oauthInitState = "${getHubUID()}/apps/${app.id}/callback?access_token=${accessToken}"
            answer = true
            logDebug("Access token is : ${state.accessToken}, oauthInitState: ${state.oauthInitState}")
        }
    }
    
    return answer
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
        scope: "https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/reminders https://mail.google.com/"
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
        
        try {
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
        } catch (e) {
            log.error "callback - ${e}, ${e.getResponse().getData()}"
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
    def logMsg = ["refreshAuthToken - state.refreshToken: ${state.refreshToken}"]
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
		}
	} catch (e) {
        log.error "revokeAccess - something went wrong: ${e}"
	}
}


def apiGet(fromFunction, uri, path, queryParams) {
    def logMsg = []
    def apiResponse = []  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiGet - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}")
    
    if (isAuthorized == true) {
        def output = new JsonOutput()
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
                logDebug "Resp Status: ${resp.status}"
            }
        } catch (e) {
            if (e.toString().indexOf("HttpResponseException") > -1) {
                if (e.response.status == 401 && refreshAuthToken()) {
                    return apiGet(fromFunction, uri, path, queryParams)
                } else if (e.response.status == 403) {
                    log.error "apiGet - path: ${path}, ${e}, ${e.getResponse().getData()}"
                    apiResponse = "error"
                }
            } else {
                log.error "apiGet - fromFunction: ${fromFunction}, path: ${path}, error: ${e}"
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
            if (e.toString().indexOf("HttpResponseException") > -1 && e.response.status == 401 && refreshAuthToken()) {
                return apiPut(fromFunction, uri, path, bodyParams)
            } else {
                log.error "apiPut - fromFunction: ${fromFunction}, path: ${path}, ${e}"
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
            if (e.toString().indexOf("HttpResponseException") > -1 && e.response.status == 401 && refreshAuthToken()) {
                return apiPatch(fromFunction, uri, path, bodyParams)
            } else {
                log.error "apiPatch - fromFunction: ${fromFunction}, path: ${path}, ${e}"
            }
        }
    } else {
        logMsg.push("Authentication Problem")
    }
    
    logDebug("${logMsg}")
    return apiResponse
}

def apiPost(fromFunction, apiPrefs, bodyParams) {
    def logMsg = []
    def apiResponse = [:]  
    def isAuthorized = authTokenValid(fromFunction)
    logMsg.push("apiPost - fromFunction: ${fromFunction}, isAuthorized: ${isAuthorized}, apiPrefs: ${apiPrefs}")
    
    if (isAuthorized == true) {
        def apiParams = [
            uri: apiPrefs.uri,
            path: (apiPrefs.containsKey("path")) ? apiPrefs.path : null,
            contentType: "application/json",
            headers: ["Content-Type": apiPrefs.contentType, "Authorization": "Bearer ${atomicState.authToken}"]
        ]
        
        if (bodyParams) {
            def output = new JsonOutput()
            apiParams.body = (apiPrefs.jsonBody == true) ? output.toJson(bodyParams) : bodyParams
        }
        
        def trimFilefromLog = false
        
        //Remove file contents from logging, if trying to troubleshoot the API, comment the following line so it gets logged. Performance issues will arise if this is left on.
        trimFilefromLog = true
        
        logMsg.push("apiParams: ${(trimFilefromLog && apiParams.toString().indexOf("filename=") > -1) ? apiParams.toString().substring(0, apiParams.toString().indexOf("filename=")) : apiParams}")
        
        try {
            httpPost(apiParams) {
                resp ->
                apiResponse.status = resp.status
                apiResponse.data = resp.data
                logDebug "apiResponse: ${apiResponse}"
            }
        } catch (e) {
            if (e.toString().indexOf("HttpResponseException") > -1 && e.response.status == 401 && refreshAuthToken()) {
                return apiPost(fromFunction, apiPrefs, bodyParams)
            } else {
                log.error "apiPost - fromFunction: ${fromFunction}, path: ${path}, ${e}"
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
    def queryParams = [
        format: 'json'
    ]
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
        def defaultReminder = (events.containsKey("defaultReminders") && events.defaultReminders.size() > 0) ? events.defaultReminders[0] : [method:"popup", minutes:15]
        for (int i = 0; i < events.items.size(); i++) {
            def event = events.items[i]
            
            def reminderMinutes
            if (event.containsKey("reminders") && event.reminders.containsKey("overrides")) {
                def reminders = event.reminders.overrides
                if (reminders.size() == 1) {
                    reminderMinutes = reminders[0].minutes
                } else {
                    reminderMinutes = reminders.find{it.method == defaultReminder.method}
                    reminderMinutes = (reminderMinutes) ? reminderMinutes.minutes : defaultReminder.minutes
                }
            } else {
                reminderMinutes = defaultReminder.minutes
            }
            
            def eventDetails = [:]
            eventDetails.kind = event.kind
            //eventDetails.timeZone = events.timeZone
            eventDetails.eventID = event.id
            eventDetails.eventTitle = event.summary ? event.summary.trim() : "none"
            eventDetails.eventLocation = event.location ? event.location : "none"
            eventDetails.eventReminderMin = reminderMinutes
            if (event.description && event.description != null && event.description.trim() != "") {
                eventDetails.eventDescription = event.description
                //Description is an HTML field, remove html tags, special characters, and spaces
                eventDetails.eventDescription = eventDetails.eventDescription.replaceAll("\n"," ")
                eventDetails.eventDescription = eventDetails.eventDescription.replaceAll("\\<.*?\\>", " ")
                eventDetails.eventDescription = eventDetails.eventDescription.replaceAll("\\&.*?\\;", " ")
                eventDetails.eventDescription = eventDetails.eventDescription.trim().replaceAll(" +", " ")
            } else {
                eventDetails.eventDescription = "none"
            }
            
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
    def queryParams = [
        format: 'json'
    ]
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
            taskDetails.taskTitle = task.title ? task.title.trim() : "none"
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
    def dueMax = getEndDate(endTimePreference, false)
    logMsg.push("dueMax: ${dueMax}")
    def bodyParams = [
        //"max_results": 10,
        //"utc_due_before_ms": dueMax.getTime()
        "due_before_ms": dueMax.getTime()
    ]
    
    def apiPrefs = [
        uri: "https://reminders-pa.clients6.google.com",
        path: "/v1internalOP/reminders/list",
        contentType: "application/json",
        jsonBody: true
    ]
    def reminders = apiPost("getNextReminders", apiPrefs, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, apiPrefs: ${apiPrefs}, reminders: ${reminders}")
        
    if (reminders.status == 200 && reminders.data && reminders.data.task && reminders.data.task.size() > 0) {
        for (int i = 0; i < reminders.data.task.size(); i++) {
            def reminder = reminders.data.task[i]
            def dueDate = getReminderDate(reminder.dueDate)
            if (dueDate <= dueMax) {
                def reminderDetails = [:]
                reminderDetails.kind = "reminder"
                reminderDetails.taskTitle = reminder.title ? reminder.title.trim() : "none"
                reminderDetails.taskID = reminder.taskId.serverAssignedId
                reminderDetails.taskDueDate = dueDate
                if (reminder.recurrenceInfo && reminder.recurrenceInfo.recurrence.frequency) {
                    reminderDetails.repeat = reminder.recurrenceInfo.recurrence.frequency
                    reminderDetails.recurrenceId = reminder.recurrenceInfo.recurrenceId.id
                } else {
                    reminderDetails.repeat = "none"
                }
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
    def bodyParams = [
        "taskId": [['serverAssignedId': taskID]]
    ]
    
    def apiPrefs = [
        uri: "https://reminders-pa.clients6.google.com",
        path: "/v1internalOP/reminders/get",
        contentType: "application/json",
        jsonBody: true
    ]
    def reminders = apiPost("getSpecificReminder", apiPrefs, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, apiPrefs: ${apiPrefs}, reminders: ${reminders}")
        
    if (reminders.status == 200 && reminders.data && reminders.data.task && reminders.data.task.size() > 0) {
        for (int i = 0; i < reminders.data.task.size(); i++) {
            def reminder = reminders.data.task[i]
            def reminderDetails = [:]
            reminderDetails.kind = "reminder"
            reminderDetails.taskTitle = reminder.title ? reminder.title.trim() : "none"
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
    def taskIDList = taskID.split(",")
    for (int i = 0; i < taskIDList.size(); i++) {
        taskID = taskIDList[i]
        def bodyParams = [
            "taskId": [["serverAssignedId": taskID]]
        ]

        def apiPrefs = [
            uri: "https://reminders-pa.clients6.google.com",
            path: "/v1internalOP/reminders/delete",
            contentType: "application/json",
            jsonBody: true
        ]
        def reminders = apiPost("deleteReminder", apiPrefs, bodyParams)
        logMsg.push("bodyParams: ${bodyParams}, apiPrefs: ${apiPrefs}, reminders: ${reminders}")

        if (reminders.status == 200 ) {
            reminderDeleted = true
        }
    }
    
    logMsg.push("reminderDeleted: ${reminderDeleted}")
    logDebug("${logMsg}")
    return reminderDeleted
}

def completeReminder(taskID) {
    def logMsg = ["completeReminder - taskID: ${taskID}"]
    def reminderCompleted = false
    def taskIDList = taskID.split(",")
    for (int i = 0; i < taskIDList.size(); i++) {
        taskID = taskIDList[i]
        def bodyParams = [
            "1": ["4": "WRP / /WebCalendar/calendar_190319.03_p1"],
            "2": ["1": taskID],
            "4": ["1": ["1": taskID], "8": 1],
            "7": ["1": [1, 10, 3]],
        ]
        
        def apiPrefs = [
            uri: "https://reminders-pa.clients6.google.com",
            path: "/v1internalOP/reminders/update",
            contentType: "application/json+protobuf",
            jsonBody: true
        ]
        def reminders = apiPost("completeReminder", apiPrefs, bodyParams)
        logMsg.push("bodyParams: ${bodyParams}, apiPrefs: ${apiPrefs}, reminders: ${reminders}")

        if (reminders.status == 200 ) {
            reminderCompleted = true
        }
    }

    logMsg.push("reminderCompleted: ${reminderCompleted}")
    logDebug("${logMsg}")
    return reminderCompleted
}

/* ============================= End Google Reminder ============================= */

/* ============================= Start Gmail ============================= */

def getUserLabels() {
    def logMsg = []
    def userLabelList = ["none":"NONE"]
    def uri = "https://gmail.googleapis.com"
    def path = "/gmail/v1/users/me/labels"
    def queryParams = [:]
    def userLabels = apiGet("getUserLabels", uri, path, queryParams)
    logMsg.push("getUserLabels - path: ${path}, queryParams: ${queryParams}, userLabels: ${userLabels}")

    if (userLabels instanceof Map && userLabels.labels.size() > 0) {
        def includeSystemLabels = ["INBOX", "IMPORTANT", "STARRED", "TRASH", "UNREAD"]
        for (int i = 0; i < userLabels.labels.size(); i++) {
            def userLabelItem = userLabels.labels[i]
            //if (userLabelItem.containsKey("labelListVisibility") || ignoreLabels.indexOf(userLabelItem.id) > -1) continue
            if (userLabelItem.type == "system" && includeSystemLabels.indexOf(userLabelItem.id) == -1) continue
            userLabelList[userLabelItem.id] = userLabelItem.name
        }
        logMsg.push("userLabelList: ${userLabelList}")
    } else {
        userLabelList = userLabels
    }

    logDebug("${logMsg}")
    return userLabelList
}

def getNextMessages(search, setlabelList=null) {    
    def logMsg = ["getNextMessages - search: ${search}, setlabelList: ${setlabelList}"]
    def messageList = []
    def uri = "https://gmail.googleapis.com"
    def path = "/gmail/v1/users/me/messages"
    def queryParams = [
        //maxResults: 1,
        q: "${search}"
    ]
    
    if (labelList != null) {
        //queryParams['labelIds'] = "${labelList}"
    }
    
    def messages = apiGet("getNextMessages", uri, path, queryParams)
    logMsg.push("queryParams: ${queryParams}, messages: ${messages}")
    def messageIDs = []
    
    if (messages.resultSizeEstimate > 0) {
        for (int i = 0; i < messages.messages.size(); i++) {
            def message = messages.messages[i]
            def messageID = message.id
            messageIDs.push(messageID)

            def messageDetails = getMessage(messageID)
            messageDetails.kind = "message"
            messageList.push(messageDetails)
        }

        if (setlabelList != null) {
            batchModifyMessages(messageIDs, setlabelList.add, setlabelList.remove)
        }
    }
    
    messageList.sort{it.messageReceived}
    logMsg.push("messageList:\n${messageList.join("\n")}")
    logDebug("${logMsg}")
    return messageList
}

def getMessage(messageID) {    
    def logMsg = ["getMessage - messageID: ${messageID}"]
    def uri = "https://gmail.googleapis.com"
    def path = "/gmail/v1/users/me/messages/${messageID}"
    def queryParams = [:]
    def message = apiGet("getMessage", uri, path, queryParams)
    logMsg.push("queryParams: ${queryParams}, message: ${message}")
    def messageDetails = [:]
        
    if (message && message.id) {
        messageDetails.messageID = message.id
        messageDetails.threadID = message.threadId
        messageDetails.labelIDs = message.labelIds
        def messageBody = message.snippet
        messageDetails.messageBody = messageBody ? messageBody : "none"
        messageDetails.messageReceived = new Date(message.internalDate.toLong())
        def payloadHeaders = message.payload.headers
        def messageTitle = payloadHeaders.find{it.name == "Subject"}.value
        messageDetails.messageTitle = messageTitle ? messageTitle : "none"
        messageDetails.messageFrom = payloadHeaders.find{it.name == "From"}.value.replace("\u003c", "").replace("\u003e", "")
        messageDetails.messageTo = payloadHeaders.find{it.name == "To"}.value.replace("\u003c", "").replace("\u003e", "")
    }
    
    logMsg.push("messageDetails: ${messageDetails}")
    logDebug("${logMsg}")
    return messageDetails
}

def batchModifyMessages(messageIDs, addLabels, removeLabels) {
    def logMsg = ["batchModifyMessages - messageIDs: ${messageIDs}, addLabels: ${addLabels}, removeLabels: ${removeLabels} - "]
    def bodyParams = [
        ids: messageIDs,
        addLabelIds: addLabels,
        removeLabelIds: removeLabels
    ]
    
    def apiPrefs = [
        uri: "https://gmail.googleapis.com",
        path: "/gmail/v1/users/me/messages/batchModify",
        contentType: "application/json",
        jsonBody: true
    ]
    
    def messages = apiPost("batchModifyMessages", apiPrefs, bodyParams)
    logMsg.push("bodyParams: ${bodyParams}, apiPrefs: ${apiPrefs}, reminders: ${reminders}")
    logDebug("${logMsg}")
    return messages
}

def sendMessage(toEmail, subject, message) {
    def logMsg = ["sendMessage - toEmail: ${toEmail}, subject: ${subject}, message: ${message} - "]
    def keyWords = ["To", "Subject", "File"]
    def foundKeywords = [:]
    for (int k = 0; k < keyWords.size(); k++) {
        def keyWord = keyWords[k]
        def keyWordIndex = message.indexOf(keyWord + ":")
        def commaIndex = message.indexOf(",", keyWordIndex)
        if (keyWordIndex > -1 && commaIndex > -1 && keyWordIndex < commaIndex) {
            def word = message.substring(keyWordIndex + keyWord.length() +1, commaIndex)
            foundKeywords[keyWord] = word
            message = message.replace(keyWord + ":" + word + ",", "").trim()
        }
    }
    logMsg.push("foundKeywords: ${foundKeywords}")
    toEmail = (foundKeywords.containsKey("To")) ? foundKeywords.To : toEmail
    subject = (foundKeywords.containsKey("Subject"))? foundKeywords.Subject : subject
    def bodyParams = [
        to: "${toEmail}",
        subject: "${subject}",
        body: "${message}"
    ]
    
    if (foundKeywords.containsKey("File") && foundKeywords.File.indexOf(".") > -1) {
        def file = getFile(foundKeywords.File)
        if (file.startsWith("File Error")) {
            bodyParams.body += "<br><br>" + file
        } else {
            bodyParams.file = [
                name: foundKeywords.File,
                type: "application/" + foundKeywords.File.substring(foundKeywords.File.indexOf(".") +1),
                bytes: getFile(foundKeywords.File)
            ]
        }
    }
    
    def apiPrefs = [
        uri: "https://www.googleapis.com/upload/gmail/v1/users/me/messages/send?uploadType=media",
        contentType: "message/rfc822",
        jsonBody: false
    ]
    
    def messages = apiPost("sendMessage", apiPrefs, createMimeMessage(bodyParams))
    
    //Remove file contents from logging, Comment the following line to troubleshoot the file so it gets logged
    if (bodyParams.containsKey("file")) bodyParams.file.bytes = ""
    
    logMsg.push("bodyParams: ${bodyParams}, messages: ${messages}")
    logDebug("${logMsg}")
    return messages
}

def createMimeMessage(msg) {
    def nl = '\n';
    def boundary = 'hubitat_attachment';

    def mimeBody = [
        'Content-Type: multipart/mixed; boundary=' + boundary,
        'MIME-Version: 1.0',
        'To: ' + msg.to,
        'Subject: ' + msg.subject + nl,
        '--' + boundary,

        'Content-Type: text/html; charset=UTF-8',
        'Content-Transfer-Encoding: base64',
        msg.body.encodeAsBase64() + nl
    ];
    
    if (msg.containsKey("file")) {
        def attachment = [
            '--' + boundary,
            'Content-Type: ' + msg.file.type,
            'MIME-Version: 1.0',
            'Content-Transfer-Encoding: base64',
            'Content-Disposition: attachment; filename="' + msg.file.name + '"' + nl,
            msg.file.bytes,
        ]
        mimeBody.push(attachment.join(nl))
        mimeBody.push('--' + boundary);
    }
    
    return mimeBody.join(nl);
}

//Thanks to community members @thebearmay and @younes for example code to get and send files
def getFile(fileName) {
    if (security) cookie = securityLogin().cookie
    
    def uri = "http://${location.hub.localIP}:8080/local/${fileName}"
    
    def params = [
        uri: uri,
        contentType: "*/*",
        textParser: false,
        headers: [
            "Cookie": cookie,
            "Accept": "application/octet-stream"
        ]
    ]
    
    try {
        httpGet(params) { resp ->
            def file
            if (resp!= null) {      
                imageData = resp.data

                def bSize = imageData.available()
                byte[] imageArr = new byte[bSize]
                imageData.read(imageArr, 0, bSize)
                
                ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream();
                fileOutputStream.write(imageArr);
                byte[] fileByteArray = fileOutputStream.toByteArray();
                file = fileByteArray
            } else {
                file = "${fileName} could not be found within File Manager"
            }
            return file.encodeAsBase64()
        }
    } catch (exception) {
        //log.error "File Read Error: ${exception.message}"
        //return null;
        return "File Error: ${fileName} could not be found within File Manager"
    }
    
}

//Thanks to community member @thebearmay for example code to get login security cookie
HashMap securityLogin() {
    def result = false
    try {
        httpPost(
            [
                uri: "http://127.0.0.1:8080",
                path: "/login",
                query: [
                    loginRedirect: "/"
                ],
                body: [
                    username: username,
                    password: password,
                    submit: "Login"
                ],
                textParser: true,
                ignoreSSLIssues: true
            ]
        )
        { resp ->
            //			log.debug resp.data?.text
            if (resp.data?.text?.contains("The login information you supplied was incorrect.")) {
                result = false
            } else {
                cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0)
                result = true
            }
        }
    } catch (e) {
        log.error "Error logging in: ${e}"
        result = false
        cookie = null
    }

    return [result: result, cookie: cookie]
}

/* ============================= End Gmail ============================= */

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
    long numberOfHours
    
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
    if(type == "code") return "<textarea rows=1 class='mdl-textfield' readonly='true'>${displayText}</textarea>"
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
    if (scopesAuthorized.indexOf("mail.google.com") > -1) {
        answer.push("Gmail")
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

def updateAppLabel() {
    String appName = settings.appName
    app.updateLabel(appName)
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ")
        }
        log.debug "$msg"
    }
}

def versionToInt(version=null) {
    version = (version == null) ? appVersion() : version
    return version.replace(".", "").toInteger()
}

def upgradeSettings() {
    if (state.version == null || state.version != appVersion()) {
        childApps.each {
            child ->
            child.upgradeSettings()
        }
        
        int currentVersionInt = versionToInt()
        if (currentVersionInt < versionToInt("3.0.0")) {
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
        }
        
        atomicState.version = appVersion()
        log.info "Upgraded GCal Search settings"
    }
}
