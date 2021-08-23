def appVersion() { return "2.4.1" }
/**
 *  GCal Search Trigger Child Application
 *  https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search_Trigger
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
    name: "GCal Search Trigger",
    namespace: "HubitatCommunity",
    author: "Mike Nestor & Anthony Pastor, cometfish, ritchierich",
    description: "Integrates Hubitat with Google Calendar events to toggle virtual switch.",
    category: "Convenience",
    parent: "HubitatCommunity:GCal Search",
    documentationLink: "https://community.hubitat.com/t/release-google-calendar-search/71397",
    importUrl: "https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search_Trigger",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
	page(name: "selectCalendars")
}

def selectCalendars() {
    def calendars = parent.getCalendarList()
    logDebug "selectCalendars - Calendar list = ${calendars}"
    
    return dynamicPage(name: "selectCalendars", title: "${parent.getFormat("title", "GCal Search Trigger Version ${appVersion()}, Create new calendar search")}", install: true, uninstall: true, nextPage: "" ) {
    	section(){
			if (!state.isPaused) {
				input name: "pauseButton", type: "button", title: "Pause", backgroundColor: "Green", textColor: "white", submitOnChange: true
			} else {
				input name: "resumeButton", type: "button", title: "Resume", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
			}
		}
        section("${parent.getFormat("box", "Search Preferences")}") {
            //we can't do multiple calendars because the api doesn't support it and it could potentially cause a lot of traffic to happen
            input name: "watchCalendars", title:"Which calendar do you want to search?", type: "enum", required:true, multiple:false, options:calendars, submitOnChange: true
            input name: "GoogleMatching", type: "bool", title: "Use Google Query Matching? By default calendar event matching is done by the HE hub and it allows multiple search strings. If you prefer to use Google search features and special characters, toggle this setting. Caching of events is not supported when using Google query matching.", defaultValue: false, submitOnChange: true
            if ( settings.GoogleMatching == false || settings.GoogleMatching == null) {
                paragraph '<p><span style="font-size: 14pt;">Search String Options:</span></p><ul style="list-style-position: inside;font-size:15px;"><li>By default matches are CaSe sensitive, toggle \'Enable case sensitive matching\' to make search matching case insensitive.</li><li>By default the search string is matched to the calendar event using a starts with search.</li><li>For exact match, prefix the search string with an = sign. For example enter =Kids No School to find events with the exact title/location of \'Kids No School\'.</li><li>For a contains search, include an * sign. For example to find any event with the word School, enter *School. This also works for multiple non consecutive words. For example to match both Kids No School and Kids Late School enter Kids*School.</li><li>Multiple search strings may be entered separated by commas.</li><li>To match any event on the calendar for that day, enter *</li><li>To exclude calendar events with specific words, prefix the word with a \'-\' (minus) sign.  For example if you would like to match all events except ones with the words \'personal\' and \'lunch\' enter \'* -personal -lunch\'</li></ul>'
                input name: "caseSensitive", type: "bool", title: "Enable case sensitive matching?", defaultValue: true
            } else {
                paragraph "${parent.getFormat("text", "If not familiar with Google Search special characters, please visit <a href='http://www.googleguide.com/crafting_queries.html' target='_blank'>GoogleGuide</a> for examples.")}"
            }
            input name: "search", type: "text", title: "Search String", required: true, submitOnChange: true
            input name: "includeAllDay", type: "bool", title: "Include All Day Events?", defaultValue: true, required: false
            input name: "searchField", type: "enum", title: "Calendar field to search", required: true, defaultValue: "title", options:["title","location"]
            paragraph "${parent.getFormat("line")}"
        }
        
        if ( settings.search ) {
            section("${parent.getFormat("box", "Schedule Settings")}") {
                paragraph "${parent.getFormat("text", "Calendar searches can be triggered once a day or periodically. Periodic options include every N hours, every N minutes, or you may enter a Cron expression.")}"
                input name: "whenToRun", type: "enum", title: "When to Run", required: true, options:["Once Per Day", "Periodically"], submitOnChange: true
                if ( settings.whenToRun == "Once Per Day" ) {
                    input name: "timeToRun", type: "time", title: "Time to run", required: true
                }
                if ( settings.whenToRun == "Periodically" ) {
                    input name: "frequency", type: "enum", title: "Frequency", required: true, options:["Hours", "Minutes", "Cron String"], submitOnChange: true
                    if ( settings.frequency == "Hours" ) {
                        input name: "hours", type: "number", title: "Every N Hours: (range 1-12)", range: "1..12", required: true, submitOnChange: true
                        input name: "hourlyTimeToRun", type: "time", title: "Starting at", defaultValue: "08:00", required: true
                    }
                    if ( settings.frequency == "Minutes" ) {
                        input name: "minutes", type: "enum", title: "Every N Minutes", required: true, options:["1", "2", "3", "4", "5", "6", "10", "12", "15", "20", "30"], submitOnChange: true
                    }
                    if ( settings.frequency == "Cron String" ) {
                        paragraph "${parent.getFormat("text", "If not familiar with Cron Strings, please visit <a href='https://www.freeformatter.com/cron-expression-generator-quartz.html#' target='_blank'>Cron Expression Generator</a>")}"
                        input name: "cronString", type: "text", title: "Enter Cron string", required: true, submitOnChange: true
                    }
                }
                paragraph "${parent.getFormat("text", "<u>Search Range</u>: By default, events from the time of search through the end of the current day are collected.  Adjust this setting to expand the search to the end of the following day or a set number of hours from the time of search.")}"
                input name: "endTimePref", type: "enum", title: "Search Range", defaultValue: "End of Current Day", options:["End of Current Day","End of Next Day", "Number of Hours from Current Time"], submitOnChange: true
                if ( settings.endTimePref == "Number of Hours from Current Time" ) {
                    input name: "endTimeHours", type: "number", title: "Number of Hours from Current Time (How many hours into the future at the time of the search, would you like to query for events?)", required: true
                }
                paragraph "${parent.getFormat("text", "<u>Sequential Event Preference</u>: By default the Event End Time will be set to the end date of the last sequential event matching the search criteria. This prevents the switch from toggling multiple times when using periodic searches. If this setting is set to false, it is recommended to set an Event End Offset in the optional setting below. If no Event End Offset is set, the scheduled trigger will be adjusted by -1 minute to ensure the switch has time to toggle.")}"
                input name: "sequentialEvent", type: "bool", title: "Expand end date for sequential events?", defaultValue: true
                paragraph "${parent.getFormat("text", "<u>Delay Event Toggle Preference</u>: By default the switch will toggle based on the matching Event Start Time. If this setting is set to false, the switch will toggle at the time a match by this search trigger. The switch will continue to toggle again based on the Event End Time.")}"
                input name: "delayToggle", type: "bool", title: "Delay toggle to event start?", defaultValue: true
                paragraph "${parent.getFormat("text", "<u>Optional Event Offset Preferences</u>: Based on the defined Search Range, if an event is found in the future from the current time, scheduled triggers will be created to toggle the switch based on the event start and end times. Use the settings below to set an offset to firing of these triggers N number of minutes before/after the event dates.  For example, if you wish for the switch to toggle 60 minutes prior to the start of the event, enter -60 in the Event Start Offset setting. This may be useful for reminder notifications where a message is sent/spoken in advance of a calendar event.  Again this is dependent on When to Run (how often the trigger is executed) and the Search Range of events.")}"
                input name: "setOffset", type: "bool", title: "Set offset?", defaultValue: false, required: false, submitOnChange: true
                if ( settings.setOffset == true ) {
                    input name: "offsetStart", type: "decimal", title: "Event Start Offset in minutes (+/-)", required: false
                    input name: "offsetEnd", type: "decimal", title: "Event End Offset in minutes (+/-)", required: false
                }
                paragraph "${parent.getFormat("line")}"
            }
        }
        
        if ( settings.search ) {
            section("${parent.getFormat("box", "Child Switch Preferences")}") {
                def defName = settings.search - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")
                input name: "deviceName", type: "text", title: "Switch Device Name (Name of the Switch that gets created by this search trigger)", required: true, multiple: false, defaultValue: "${defName} Switch"
                paragraph "${parent.getFormat("text", "<u>Switch Default Value</u>: Adjust this setting to the switch value preferred when there is no calendar entry. If a calendar entry is found, the switch will toggle from this value.")}"
                input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, defaultValue: "on", options:["on","off"]
                paragraph "${parent.getFormat("text", "<u>Date Fomat</u>: Adjust this setting to your desired date format.  By default time format will be based on the hub's time format setting.  Choose other to enter your own custom date/time format.  Please see <a href='https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html' target='_blank'>this website</a> for examples.")}"
                input name: "dateFormat", type: "enum", title: "Date Format", required: true, defaultValue: "yyyy-MM-dd", options:["yyyy-MM-dd", "MM-dd-yyyy", "dd-MM-yyyy", "Other"], submitOnChange: true
                if ( settings.dateFormat == "Other" ) {
                    input name: "dateFormatOther", type: "text", title: "Enter custom date format", required: true
                }
                paragraph "${parent.getFormat("text", "<u>Toggle/Sync Additional Switches</u>: If you would like other existing switches to follow the switch state of the child GCal Switch, set the following list with those switch(es). Please keep in mind that this is one way from the GCal switch to these switches.")}"
                input name: "controlOtherSwitches", type: "bool", title: "Toggle/Sync Additional Switches?", defaultValue: false, required: false, submitOnChange: true
                if ( settings.controlOtherSwitches == true ) {
                    input "syncSwitches", "capability.switch", title: "Synchronize These Switches", multiple: true, required: false
                    input "reverseSwitches", "capability.switch", title: "Reverse These Switches", multiple: true, required: false
                }
                paragraph "${parent.getFormat("line")}"
            }
        }
        
        if ( settings.search ) {
            section("${parent.getFormat("box", "App Preferences")}") {
                def defName = settings.search - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")
                input name: "appName", type: "text", title: "Name this child app", required: true, multiple: false, defaultValue: "${defName}", submitOnChange: true
                input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
                paragraph "${parent.getFormat("line")}"
            }
        }
            
        if ( state.installed ) {
	    	section ("Remove Trigger and Corresponding Device") {
            	paragraph "ATTENTION: The only way to uninstall this trigger and the corresponding child switch device is by clicking the Remove button below. Trying to uninstall the corresponding device from within that device's preferences will NOT work."
            }
    	}   
	}       
}

def installed() {
	state.isPaused = false
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : parent.translateEndTimePref(settings.endTimePref)
    def tempEndTimePref = [:]
    tempEndTimePref[settings.watchCalendars] = endTimePreference
    if (settings.GoogleMatching == false) {
        parent.setCacheDuration("add", tempEndTimePref)
    }
    initialize()
}

def updated() {
	unschedule()
    parent.setCacheDuration("update")
    initialize()
}

def initialize() {
    state.installed = true
    if ((settings.setOffset == null || settings.setOffset == false) && (settings.offsetStart != null || settings.offsetEnd != null)) {
        app.updateSetting("setOffset", [value:"true", type:"bool"])
    }
    
    // Sets Label of Trigger
    updateAppLabel()
    
    state.deviceID = "GCal_${app.id}"
    def childDevice = getChildDevice(state.deviceID)
    if (!childDevice) {
        logDebug("initialize - creating device: deviceID: ${state.deviceID}")
        childDevice = addChildDevice("HubitatCommunity", "GCal Switch", "GCal_${app.id}", null, [name: "GCal Switch", label: deviceName])
        childDevice.updateSetting("isDebugEnabled",[value:"${isDebugEnabled}",type:"bool"])
        childDevice.updateSetting("switchValue",[value:"${switchValue}",type:"enum"])
    } else {
        childDevice.updateSetting("switchValue",[value:"${switchValue}",type:"enum"])
    }
    if (!state.isPaused) {
        if ( settings.whenToRun == "Once Per Day" ) {
            schedule(timeToRun, poll)
            logDebug("initialize - creating schedule once per day at: ${timeToRun}")
        } else {
            def cronString = ""
            if ( settings.frequency == "Hours" ) {
                def hourlyTimeToRun = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", settings.hourlyTimeToRun)
                def hour = hourlyTimeToRun.hours
                def minute = hourlyTimeToRun.minutes
                cronString = "0 ${minute} ${hour}/${hours} * * ? *"
            } else if ( settings.frequency == "Minutes" ) {
                cronString = "0 0/${settings.minutes} * * * ?"
            } else if ( settings.frequency == "Cron String" ) {
                cronString = settings.cronString
            }
            schedule(cronString, poll)
            logDebug("initialize - creating schedule with cron string: ${cronString}")
        }
    }
}

def getDefaultSwitchValue() {
    return settings.switchValue
}

def getNextEvents() {
    def logMsg = []
    def search = (!settings.search) ? "" : settings.search
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : settings.endTimePref
    def items = parent.getNextEvents(settings.watchCalendars, settings.GoogleMatching, search, endTimePreference)
    logMsg.push("getNextEvents - BEFORE search: ${search}, items: ${items} AFTER ")
    
    def item = []
    def foundMatch = false
    if (items && items.size() > 0) {
        // Filter out all day events if set in settings
        def tempItems,tempItem
        if (settings.includeAllDay == false) {
            tempItems = []
            def allDayFilter = []
            for (int a = 0; a < items.size(); a++) {
                tempItem = items[a]
                if (tempItem.eventAllDay == false) {
                    tempItems.push(tempItem)
                } else {
                    allDayFilter.push("${tempItem.eventTitle}")
                }
            }
            items = tempItems
            if (allDayFilter.size() > 0) {
                logMsg.push("Filtered these All Day Events: ${allDayFilter}")
            }
        }
        
        // Filter out events if Event Offset End is before current time to prevent false toggle
        def offsetEnd,tempEndTime
        if (settings.setOffset && settings.offsetEnd != null && settings.offsetEnd != "") {
            tempItems = []
            def offsetEndFilter = []
            for (int o = 0; o < items.size(); o++) {
                tempItem = items[o]
                def currentTime = new Date()
                offsetEnd = settings.offsetEnd.toInteger()
                tempEndTime = tempItem.eventEndTime.getTime()
                tempEndTime = tempEndTime + (offsetEnd * 60 * 1000)
                tempEndTime = new Date(tempEndTime)
                if (tempEndTime > currentTime) {
                    tempItems.push(tempItem)
                } else {
                    offsetEndFilter.push("${tempItem.eventTitle}")
                }  
            }
            items = tempItems
            if (offsetEndFilter.size() > 0) {
                logMsg.push("Filtered these events because of end offset: ${offsetEndFilter}")
            }
        }
        
        if (settings.GoogleMatching == true) {
            // Default to the first event found by Google API
            item = items[0]
            foundMatch = true
        } else {
            def searchTerms = search.toString()
            if (caseSensitive == false) {
                searchTerms = searchTerms.toLowerCase()
            }
            searchTerms = searchTerms.split(",")
            for (int s = 0; s < searchTerms.size(); s++) {
                def searchTerm = searchTerms[s].trim()
                logMsg.push("searchTerm: '${searchTerm}'")
                def sequentialEventOffset = false

                for (int i = 0; i < items.size(); i++) {
                    def itemMatch = false
                    def eventTitle = (settings.searchField == "title") ? items[i].eventTitle : items[i].eventLocation
                    if (caseSensitive == false) {
                        eventTitle = eventTitle.toLowerCase()
                    }
                    logMsg.push("eventTitle: ${eventTitle}")

                    def ignoreMatch = false
                    if (searchTerm.indexOf("-") > -1) {
                        def pattern = ~/-[\w]+/
                        def ignoreWords = (searchTerm =~ pattern).findAll()
                        for (int iG = 0; iG < ignoreWords.size(); iG++) {
                            def ignoreWord = ignoreWords[iG].substring(1).trim()
                            if (eventTitle.indexOf(ignoreWord) > -1) {
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
                        } else if (searchTerm.startsWith("=") && eventTitle == searchTerm.substring(1)) {
                            itemMatch = true
                        } else if (searchTerm.indexOf("*") > -1) {
                            def searchList = searchTerm.toString().split("\\*")
                            for (int sL = 0; sL < searchList.size(); sL++) {
                                def searchItem = searchList[sL].trim()
                                if (eventTitle.indexOf(searchItem) > -1) {
                                    itemMatch = true
                                } else {
                                    itemMatch = false
                                    break
                                }
                            }
                        } else if (eventTitle.startsWith(searchTerm)) {
                            itemMatch = true
                        }
                    }

                    logMsg.push("itemMatch: ${itemMatch}")
                    if (itemMatch) {
                        foundMatch = true
                        if (item == []) {
                            item = items[i]
                        } else if (i < items.size()) {
                            def newItem = items[i]
                            if (settings.sequentialEvent) {
                                if (item.eventEndTime >= newItem.eventStartTime) {
                                    item.eventEndTime = newItem.eventEndTime
                                }
                            } else {
                                if (item.eventEndTime == newItem.eventStartTime && settings.whenToRun == "Periodically") {
                                    sequentialEventOffset = true
                                }
                                break
                            }
                        } else {
                            break
                        }
                    }
                }

                if (foundMatch) {
                    break
                }
            }
        }
        
        if (foundMatch) {
            item.scheduleStartTime = new Date(item.eventStartTime.getTime())
            if (settings.delayToggle == false) {
                item.scheduleStartTime = new Date()
            }
            if (settings.setOffset && settings.offsetStart != null && settings.offsetStart != "") {
                def origStartTime = new Date(item.eventStartTime.getTime())
                int offsetStart = settings.offsetStart.toInteger()
                def tempStartTime = item.scheduleStartTime.getTime()
                tempStartTime = tempStartTime + (offsetStart * 60 * 1000)
                item.scheduleStartTime.setTime(tempStartTime)
                logMsg.push("Event start offset: ${settings.offsetStart}, adjusting time from ${origStartTime} to ${item.scheduleStartTime}")
            }
            
            item.scheduleEndTime = new Date(item.eventEndTime.getTime())
            if ((settings.setOffset && settings.offsetEnd != null && settings.offsetEnd != "") || sequentialEventOffset) {
                def origEndTime = new Date(item.eventEndTime.getTime())
                if (sequentialEventOffset && settings.offsetEnd == null) {
                    offsetEnd = -1
                } else {
                    offsetEnd = settings.offsetEnd.toInteger()
                }
                tempEndTime = item.scheduleEndTime.getTime()
                tempEndTime = tempEndTime + (offsetEnd * 60 * 1000)
                item.scheduleEndTime.setTime(tempEndTime)
                logMsg.push("Event end offset: ${settings.offsetEnd}, adjusting time from ${origEndTime} to ${item.scheduleEndTime}")
            }
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    return item
}

def clearEventCache() {
    parent.clearEventCache(settings.watchCalendars)
}

def formatDateTime(dateTime) {
    def defaultDateFormat = "yyyy-MM-dd HH:mm:ss"
    def dateTimeFormat, sdf
    if (settings.dateFormat != null && settings.dateFormat != "Other") {
        def dateFormat = settings.dateFormat
        def timeFormat = (location.getTimeFormat() == 24) ? "HH:mm:ss" : "hh:mm:ss a"
        dateTimeFormat = dateFormat + " " + timeFormat
    } else if (settings.dateFormat != null) {
        dateTimeFormat = settings.dateFormatOther
    } else {
        dateTimeFormat = defaultDateFormat
    }
    
    try {
        sdf = new java.text.SimpleDateFormat(dateTimeFormat)
    } catch (e) {
        log.warn "Custom date format entered is invalid: ${e}"
        sdf = new java.text.SimpleDateFormat(defaultDateFormat)
    }
    sdf.setTimeZone(location.timeZone)	
    return sdf.format(dateTime);
}

def poll() {
    def childDevice = getChildDevice(state.deviceID)
    logDebug "poll - childDevice: ${childDevice}"
    childDevice.poll()
}

def syncChildSwitches(value){
    if (value == "on") {
        syncSwitches?.on()
        reverseSwitches?.off()
    } else {
        syncSwitches?.off()
        reverseSwitches?.on()
    }
}

private uninstalled() {
    logDebug "uninstalled - Delete all child devices"
    
	deleteAllChildren()
    parent.setCacheDuration("remove", app.id)
}

private deleteAllChildren() {    
    getChildDevices().each {
        logDebug "deleteAllChildren ${it.deviceNetworkId}"
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (Exception e) {
            log.error "Fatal exception? $e"
        }
    }
}

private childCreated() {
    def isChild = getChildDevice("GCal_${app.id}") != null
    return isChild
}

private textVersion() {
    def text = "Trigger Version: ${ version() }"
}
private dVersion(){
	def text = "Device Version: ${getChildDevices()[0].version()}"
}

def appButtonHandler(btn) {
    switch(btn) {
        case "pauseButton":
			state.isPaused = true
            break
		case "resumeButton":
			state.isPaused = false
			break
    }
    updated()
}

def updateAppLabel() {
    String appName = settings.appName
    
	if (state.isPaused) {
		appName = appName + '<span style="color:Crimson"> (Paused)</span>'
    }
    app.updateLabel(appName)
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
