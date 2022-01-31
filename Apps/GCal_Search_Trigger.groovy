def appVersion() { return "3.1.0" }
/**
 *  GTask Search Trigger Child Application
 *  https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GTask_Search_Trigger
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
import hubitat.helper.RMUtils

definition(
    name: "GCal Search Trigger",
    namespace: "HubitatCommunity",
    author: "Mike Nestor & Anthony Pastor, cometfish, ritchierich",
    description: "Integrates Hubitat with Google Tasks to toggle virtual switch.",
    category: "Convenience",
    parent: "HubitatCommunity:GCal Search",
    documentationLink: "https://community.hubitat.com/t/release-google-calendar-search/71397",
    importUrl: "https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GTask_Search_Trigger",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
    return dynamicPage(name: "mainPage", title: "${parent.getFormat("title", "GCal Search Trigger Version ${appVersion()}, ${(state.installed == true) ? "Update" : "Create new"} search trigger")}", install: true, uninstall: true, nextPage: "" ) {
    	section(){
			if (!state.isPaused) {
				input name: "pauseButton", type: "button", title: "Pause", backgroundColor: "Green", textColor: "white", width: 4, submitOnChange: true
			} else {
				input name: "resumeButton", type: "button", title: "Resume", backgroundColor: "Crimson", textColor: "white", width: 4, submitOnChange: true
			}
            if (state.refreshed) {
                paragraph "Last Refreshed:\n${state.refreshed}", width: 4
            }
            input name: "refreshButton", type: "button", title: "Refresh", width: 4, submitOnChange: true
        }

        section("${parent.getFormat("box", "Search Preferences")}") {
            def isAuthorized = parent.authTokenValid("search trigger mainPage")
            if (!isAuthorized) {
                paragraph "${parent.getFormat("warning", "Authentication Problem! Please setup again in the parent GCal Search app.")}"
            } else {
                def scopesAuthorized = parent.getScopesAuthorized()
                if (scopesAuthorized == null) {
                    log.error "The parent GCal Search is using an old OAuth credential type.  Please open this app and follow the steps to complete the upgrade."
                }
                def watchListOptions
                input name: "searchType", title:"Do you want to search Google Calendar Event, Task or Reminder?", type: "enum", required:true, multiple:false, options:scopesAuthorized, defaultValue: "Calendar", submitOnChange: true
                //Default value above isn't working so set a default to prevent app errors
                if ((settings.searchType == null || settings.searchType == "Calendar") && scopesAuthorized != null) {
                    if (scopesAuthorized.indexOf("Calendar") > -1) {
                        settings.searchType = "Calendar Event"
                        app.updateSetting("searchType", [value:"Calendar Event", type:"enum"])
                    } else {
                        settings.searchType = scopesAuthorized[0]
                        app.updateSetting("searchType", [value:"${scopesAuthorized[0]}", type:"enum"])
                    }
                }
                logDebug("mainPage - settings.searchType: ${settings.searchType}, scopesAuthorized: ${scopesAuthorized}")
                if ( settings.searchType == "Calendar Event" ) {
                    watchListOptions = parent.getCalendarList()
                    if (watchListOptions == "error") {
                        paragraph "${parent.getFormat("warning", "The Google Calendar API has not been enabled in your Google project.  <a href='https://console.cloud.google.com/apis/api/calendar-json.googleapis.com' target='_blank'>Please click here to add it the in Google Console</a>. Then refresh this page.")}"
                    } else {
                        logDebug("Calendar list = ${watchListOptions}")
                        input name: "watchList", title:"Which calendar do you want to search?", type: "enum", required:true, multiple:false, options:watchListOptions, submitOnChange: true
                        input name: "includeAllDay", type: "bool", title: "Include All Day Events?", defaultValue: true, required: false
                        input name: "searchField", type: "enum", title: "Calendar field to search", required: true, defaultValue: "title", options:["title","location"]
                        input name: "GoogleMatching", type: "bool", title: "Use Google Query Matching? By default calendar event matching is done by the HE hub and it allows multiple search strings. If you prefer to use Google search features and special characters, toggle this setting. Caching of events is not supported when using Google query matching.", defaultValue: false, submitOnChange: true
                        if ( settings.GoogleMatching == true) {
                            paragraph "${parent.getFormat("text", "If not familiar with Google Search special characters, please visit <a href='http://www.googleguide.com/crafting_queries.html' target='_blank'>GoogleGuide</a> for examples.")}"
                        }
                    }
                } else if ( settings.searchType == "Task" ) {
                    settings.GoogleMatching == null // Task API doesn't allow text searching
                    watchListOptions = parent.getTaskList()
                    if (watchListOptions == "error") {
                        paragraph "${parent.getFormat("warning", "The Google Tasks API has not been enabled in your Google project.  <a href='https://console.cloud.google.com/apis/library/tasks.googleapis.com' target='_blank'>Please click here to add it the in Google Console</a>. Then refresh this page.")}"
                    } else {
                        logDebug("Task list = ${watchListOptions}")
                        input name: "watchList", title:"Which task list do you want to search?", type: "enum", required:true, multiple:false, options:watchListOptions, submitOnChange: true
                    }
                } else {
                    logDebug("scopesAuthorized: ${scopesAuthorized}")
                    settings.GoogleMatching == null // Reminder API doesn't allow text searching
                }
                if ( settings.GoogleMatching == false || settings.GoogleMatching == null ) {
                    paragraph '<p><span style="font-size: 14pt;">Search String Options:</span></p><ul style="list-style-position: inside;font-size:15px;"><li>By default matches are CaSe sensitive, toggle \'Enable case sensitive matching\' to make search matching case insensitive.</li><li>By default the search string is matched to the ' + settings.searchType.toLowerCase() + ' title using a starts with search.</li><li>For exact match, prefix the search string with an = sign. For example enter =Kids No School to find events with the exact title/location of \'Kids No School\'.</li><li>For a contains search, include an * sign. For example to find any event with the word School, enter *School. This also works for multiple non consecutive words. For example to match both Kids No School and Kids Late School enter Kids*School.</li><li>Multiple search strings may be entered separated by commas.</li><li>To match any ' + settings.searchType.toLowerCase() + ' for that day, enter *</li><li>To exclude ' + settings.searchType.toLowerCase() + ' with specific words, prefix the word with a \'-\' (minus) sign.  For example if you would like to match all events except ones with the words \'personal\' and \'lunch\' enter \'* -personal -lunch\'</li></ul>'
                    input name: "caseSensitive", type: "bool", title: "Enable case sensitive matching?", defaultValue: true
                }
                input name: "search", type: "text", title: "Search String", required: true, submitOnChange: true
                paragraph "${parent.getFormat("line")}"
            }
        }

        if ( settings.search ) {
            section("${parent.getFormat("box", "Schedule Settings")}") {
                paragraph "${parent.getFormat("text", "${settings.searchType} searches can be triggered once a day or periodically. Periodic options include every N hours, every N minutes, or you may enter a Cron expression.")}"
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
                if ( settings.searchType == "Calendar Event" ) {
                    paragraph "${parent.getFormat("text", "<u>Sequential Event Preference</u>: By default the Event End Time will be set to the end date of the last sequential event matching the search criteria. This prevents the switch from toggling and additonal actions triggering multiple times when using periodic searches. If this setting is set to false, it is recommended to set an Event End Offset in the optional setting below. If no Event End Offset is set, the scheduled trigger will be adjusted by -1 minute to ensure the switch has time to toggle.")}"
                    input name: "sequentialEvent", type: "bool", title: "Expand end date for sequential events?", defaultValue: true
                }
                paragraph "${parent.getFormat("text", "<u>Delay Event Toggle Preference</u>: By default the switch will toggle and additional actions will trigger based on the matching Event Start Time. If this setting is set to false, the switch will toggle and additional actions will trigger at the run time of this search trigger if a match is found. The switch will continue to toggle and additional actions will trigger again based on the Event End Time.")}"
                input name: "delayToggle", type: "bool", title: "Delay toggle to event start?", defaultValue: true
                paragraph "${parent.getFormat("text", "<u>Optional Event Offset Preferences</u>: Based on the defined Search Range, if an item is found in the future from the current time, scheduled triggers will be created to toggle the switch and trigger additional actions based on the item's start and end times. Use the settings below to set an offset to firing of these triggers N number of minutes before/after the item date(s).  For example, if you wish for the switch to toggle or additional actions to trigger 60 minutes prior to the start of the event, enter -60 in the Event Start Offset setting. This may be useful for reminder notifications where a message is sent/spoken in advance of a task.  Again this is dependent on When to Run (how often the trigger is executed) and the Search Range of events.")}"
                input name: "setOffset", type: "bool", title: "Set offset?", defaultValue: false, required: false, submitOnChange: true
                if ( settings.setOffset == true ) {
                    input name: "offsetStart", type: "decimal", title: "Event Start Offset in minutes (+/-)", required: false
                    if ( settings.searchType == "Calendar Event" ) {
                        input name: "offsetEnd", type: "decimal", title: "Event End Offset in minutes (+/-)", required: false
                    }
                }
                paragraph "${parent.getFormat("line")}"
            }
        }
        
        if ( settings.search ) {
            section("${parent.getFormat("box", "Child Switch Preferences")}") {
                paragraph "${parent.getFormat("text", "<u>Create child switch</u>: By default this app will create and toggle a child switch when an item matches the search criteria.  Many inbuilt apps have restrictions based on the state of a switch where the rule won't if say for example a particular switch is turned on.  In other use cases, a child switch may bring unnecessary overhead.  Choose what you wish to happen when an item matches the search criteria.")}"
                input name: "createChildSwitch", type: "bool", title: "Create child switch?", defaultValue: true, required: false, submitOnChange: true
                if ( settings.createChildSwitch != false ) {
                    def defName = settings.search - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")
                    input name: "deviceName", type: "text", title: "Switch Device Name (Name of the Switch that gets created by this search trigger)", required: true, multiple: false, defaultValue: "${defName} Switch"
                    paragraph "${parent.getFormat("text", "<u>Switch Default Value</u>: Adjust this setting to the switch value preferred when there is no task. If a task is found, the switch will toggle from this value.")}"
                    input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, defaultValue: "off", options:["on","off"]
                    paragraph "${parent.getFormat("text", "<u>Date Format</u>: Adjust this setting to your desired date format.  By default time format will be based on the hub's time format setting.  Choose other to enter your own custom date/time format.  Please see <a href='https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html' target='_blank'>this website</a> for examples.")}"
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
                }
                paragraph "${parent.getFormat("line")}"
            }
            section("${parent.getFormat("box", "Additional Action Preferences")}") {
                paragraph "${parent.getFormat("text", "Notifications can be sent/spoken and Rule Machine rules can be evaluated based on an item matching search criteria.")}"
                input "sendNotification", "bool", title: "Send notifications?", defaultValue: false, submitOnChange: true
                if ( settings.sendNotification == true ) {
                    def startMsg, endMsg
                    if ( settings.searchType == "Calendar Event" ) {
                        startMsg = "Custom message to send at event <u>scheduled start</u>"
                        endMsg = "Custom message to send at event <u>scheduled end</u>"
                    } else {
                        startMsg = "Custom message to send at " + settings.searchType.toLowerCase() + " due date"
                        endMsg = "Custom message to send when " + settings.searchType.toLowerCase() + " is completed"
                    }
                    
                    paragraph "${parent.getFormat("text", "<u>Custom message to send</u>: " + getNotificationMsgDescription(settings.searchType))}"
                    input name: "notificationStartMsg", type: "textarea", title: "${startMsg}", required: false
                    input name: "notificationEndMsg", type: "textarea", title: "${endMsg}", required: false
                    input "notificationDevices", "capability.notification", title: "Send notification to device(s)?", required: false, multiple: true
                    input "speechDevices", "capability.speechSynthesis", title: "Speak notification on these device(s)?", required: false, multiple: true
                }
                paragraph "${parent.getFormat("line")}"
                input "runRuleActions", "bool", title: "Run Rule Machine actions?", defaultValue: false, submitOnChange: true
                if ( settings.runRuleActions == true ) {
                    def legacyRules = RMUtils.getRuleList()
                    if (legacyRules != null) {
                        input "legacyRule", "enum", title: "Select which Rule Machine Legacy rules to run", options: legacyRules, multiple: true
                    }
                    def currentRules = RMUtils.getRuleList('5.0')
                    input "currentRule", "enum", title: "Select which rules to run", options: currentRules, multiple: true
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

def getNotificationMsgDescription(searchType) {
    def answer
    if (searchType == "Calendar Event") {
        answer = "Use %eventTitle% to include event title, %eventLocation% to include event location, %eventStartTime% to include event start time, %eventEndTime% to include event end time, and %eventAllDay% to include event all day."
    } else {
        answer = "Use %taskTitle% to include task title and %taskDueDate% to include task due date."
    }
    
    return answer
}

def installed() {
	state.isPaused = false
    initialize()
}

def updated() {
	unschedule()
    initialize()
}

def initialize() {
    state.installed = true
    if ((settings.setOffset == null || settings.setOffset == false) && (settings.offsetStart != null || settings.offsetEnd != null)) {
        app.updateSetting("setOffset", [value:"true", type:"bool"])
    }
    
    // Sets Label of Trigger
    updateAppLabel()
    
    if ( settings.createChildSwitch != false ) {
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

def getNextItems() {
    if ( settings.searchType == "Task" ) {
        return getNextTasks()
    } else if ( settings.searchType == "Reminder" ) {
        return getNextReminders()
    } else {
        return getNextEvents()
    }
}

def getNextEvents() {
    // Code to be removed later, temporarily set watchList variable so the calendar search continues to work
    if (settings.watchList == null && settings.watchCalendars != null) {
        settings.watchList = settings.watchCalendars
        log.error "The parent GCal Search is using an old OAuth credential type.  Please open this app and follow the steps to complete the upgrade."
    }
    
    def logMsg = []
    def search = (!settings.search) ? "" : settings.search
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : settings.endTimePref
    
    def offsetEnd
    if (settings.setOffset && settings.offsetEnd != null && settings.offsetEnd != "") {
        offsetEnd = settings.offsetEnd.toInteger()
        offsetEnd = offsetEnd * 60 * 1000
    }
    
    def items = parent.getNextEvents(settings.watchList, settings.GoogleMatching, search, endTimePreference, offsetEnd, dateFormat)
    logMsg.push("getNextEvents - BEFORE search: ${search}, items: ${items} AFTER ")
    
    def item = [
        eventTitle: " ",
        eventLocation: " ",
        eventAllDay: " ",
        eventStartTime: " ",
        eventEndTime: " ",
        switch: "defaultValue"
    ]
    def foundMatch = false
    def sequentialEventOffset = false
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
        def tempEndTime
        if (offsetEnd != null) {
            tempItems = []
            def offsetEndFilter = []
            def currentTime = new Date()
            for (int o = 0; o < items.size(); o++) {
                tempItem = items[o]
                tempEndTime = tempItem.eventEndTime.getTime()
                tempEndTime = tempEndTime + offsetEnd
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
        
        // If Google Matching is disabled, check for match
        if (settings.GoogleMatching == false && items.size() > 0) {
            def eventField = (settings.searchField == "title") ? "eventTitle" : "eventLocation"
            items = parent.matchItem(items, caseSensitive, search, eventField)
        }
        
        if (items.size() > 0) {
            item = items[0]
            foundMatch = true
            
            if (items.size() > 1) {
                for (int i = 1; i < items.size(); i++) {
                    def newItem = items[i]
                    def currentEventEndTime = new Date(item.eventEndTime.getTime())
                    def nextEventStartTime = new Date(newItem.eventStartTime.getTime())
                    if (settings.sequentialEvent) {
                        if (settings.includeAllDay && item.eventAllDay && newItem.eventAllDay) {
                            tempEndTime = currentEventEndTime.getTime()
                            tempEndTime = tempEndTime + 60
                            currentEventEndTime.setTime(tempEndTime)
                        }
                        logMsg.push("includeAllDay(${settings.includeAllDay}) && currentEventEndTime(${currentEventEndTime}) >= nextEventStartTime(${nextEventStartTime}): ${currentEventEndTime >= nextEventStartTime}")
                        if (currentEventEndTime >= nextEventStartTime) {
                            item.eventEndTime = newItem.eventEndTime
                        }
                    } else if (currentEventEndTime == nextEventStartTime && settings.whenToRun == "Periodically") {
                        sequentialEventOffset = true
                        break
                    }
                }
            }
        }
        
        logMsg.push("foundMatch: ${foundMatch}, item: ${item}")
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
            if (offsetEnd != null || sequentialEventOffset) {
                def origEndTime = new Date(item.eventEndTime.getTime())
                if (sequentialEventOffset && offsetEnd == null) {
                    offsetEnd = -1 * 60 * 1000
                }
                tempEndTime = item.scheduleEndTime.getTime()
                tempEndTime = tempEndTime + offsetEnd
                item.scheduleEndTime.setTime(tempEndTime)
                logMsg.push("Event end offset: ${settings.offsetEnd}, adjusting time from ${origEndTime} to ${item.scheduleEndTime}")
            }
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    atomicState.item = item
    runAdditionalActions(item)
    return item
}

def getNextTasks() {
    def logMsg = []
    def search = (!settings.search) ? "" : settings.search
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : settings.endTimePref
    
    def items = parent.getNextTasks(settings.watchList, search, endTimePreference)
    logMsg.push("getNextTasks - BEFORE search: ${search}, items:\n${items.join("\n")}\nAFTER ")
    
    def item = [
        taskTitle: " ",
        taskID: " ",
        taskDueDate: " ",
        switch: "defaultValue"
    ]
    def foundMatch = false
    def sequentialEventOffset = false
    if (items && items.size() > 0) {        
        // Check for search string match
        if (items.size() > 0) {
            def eventField = "taskTitle"
            items = parent.matchItem(items, caseSensitive, search, eventField)
        }
        
        if (items.size() > 0) {
            item = items[0]
            foundMatch = true
        }
        
        logMsg.push("foundMatch: ${foundMatch}, item: ${item}")
        if (foundMatch) {
            item.scheduleStartTime = new Date(item.taskDueDate.getTime())
            if (settings.delayToggle == false) {
                item.scheduleStartTime = new Date()
            }
            if (settings.setOffset && settings.offsetStart != null && settings.offsetStart != "") {
                def origStartTime = new Date(item.taskDueDate.getTime())
                int offsetStart = settings.offsetStart.toInteger()
                def tempStartTime = item.scheduleStartTime.getTime()
                tempStartTime = tempStartTime + (offsetStart * 60 * 1000)
                item.scheduleStartTime.setTime(tempStartTime)
                logMsg.push("Task start offset: ${settings.offsetStart}, adjusting time from ${origStartTime} to ${item.scheduleStartTime}")
            }
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    atomicState.item = item
    runAdditionalActions(item)
    return item
}

def completeTask(taskID) {
    def logMsg = ["completeTask - watchList: ${settings.watchList}, taskID: ${taskID}"]
    def answer = false
    def item = parent.completeTask(settings.watchList, taskID)
    logMsg.push("item: ${item}")
    
    if (item.status && item.status == "completed") {
        answer = true
    }
    
    triggerEndNotification()
    triggerEndRule()
    logMsg.push("returning : ${answer}")
    logDebug("${logMsg}")
    return answer
}

def getNextReminders() {
    def logMsg = []
    def search = (!settings.search) ? "" : settings.search
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : settings.endTimePref
    
    def items = parent.getNextReminders(search, endTimePreference)
    logMsg.push("getNextReminders - BEFORE search: ${search}, items:\n${items.join("\n")}\nAFTER ")
    
    def item = [
        taskTitle: " ",
        taskID: " ",
        taskDueDate: " ",
        switch: "defaultValue"
    ]
    def foundMatch = false
    def sequentialEventOffset = false
    if (items && items.size() > 0) {        
        // Check for search string match
        if (items.size() > 0) {
            def eventField = "taskTitle"
            items = parent.matchItem(items, caseSensitive, search, eventField)
        }
        
        if (items.size() > 0) {
            item = items[0]
            foundMatch = true
        }
        
        logMsg.push("foundMatch: ${foundMatch}, item: ${item}")
        if (foundMatch) {
            item.scheduleStartTime = new Date(item.taskDueDate.getTime())
            if (settings.delayToggle == false) {
                item.scheduleStartTime = new Date()
            }
            if (settings.setOffset && settings.offsetStart != null && settings.offsetStart != "") {
                def origStartTime = new Date(item.taskDueDate.getTime())
                int offsetStart = settings.offsetStart.toInteger()
                def tempStartTime = item.scheduleStartTime.getTime()
                tempStartTime = tempStartTime + (offsetStart * 60 * 1000)
                item.scheduleStartTime.setTime(tempStartTime)
                logMsg.push("Task start offset: ${settings.offsetStart}, adjusting time from ${origStartTime} to ${item.scheduleStartTime}")
            }
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    atomicState.item = item
    runAdditionalActions(item)
    return item
}

def completeReminder(taskID) {
    def logMsg = ["completeReminder - taskID: ${taskID}"]
    def answer = parent.completeReminder(taskID)
    triggerEndNotification()
    triggerEndRule()

    logMsg.push("returning : ${answer}")
    logDebug("${logMsg}")
    return answer
}

def runAdditionalActions(item) {
    def logMsg = ["runAdditionalActions - item: ${item}"]
    if (settings.sendNotification != true || (settings.notificationStartMsg == null && settings.notificationEndMsg == null) || (settings.notificationDevices == null && settings.speechDevices == null)) {
        logMsg.push("sendNotification: ${settings.sendNotification}, not scheduling notification(s)")
    } else {
        unschedule(triggerStartNotification)
        unschedule(triggerEndNotification)

        if (item.scheduleStartTime != null && settings.notificationStartMsg != null) {
            def scheduleStartTime = (now() > item.scheduleStartTime.getTime()) ? new Date() : item.scheduleStartTime
            logMsg.push("scheduling start notification at ${scheduleStartTime}")
            runOnce(scheduleStartTime, triggerStartNotification)
        }

        if (item.scheduleEndTime != null && settings.notificationEndMsg != null && searchType == "Calendar Event") {
            logMsg.push("scheduling end notification at ${item.scheduleEndTime}")
            runOnce(item.scheduleEndTime, triggerEndNotification)
        }
    }

    if (settings.runRuleActions != true || (settings.legacyRule == null && settings.currentRule == null)) {
        logMsg.push("runRuleActions: ${settings.runRuleActions}, not evaluating rule")
    } else {
        unschedule(triggerStartRule)
        unschedule(triggerEndRule)

        if (item.scheduleStartTime != null) {
            def scheduleStartTime = (now() > item.scheduleStartTime.getTime()) ? new Date() : item.scheduleStartTime
            logMsg.push("scheduling start rule actions at ${scheduleStartTime}")
            runOnce(scheduleStartTime, triggerStartRule)
        }

        if (item.scheduleEndTime != null && searchType == "Calendar Event") {
            logMsg.push("scheduling end rule actions at ${item.scheduleEndTime}")
            runOnce(item.scheduleEndTime, triggerEndRule)
        }
    }
    logDebug("${logMsg}")
}

def triggerStartNotification() {
    def msg = settings.notificationStartMsg
    composeNotification(msg)
}

def triggerEndNotification() {
    if (settings.sendNotification != true || settings.notificationEndMsg == null || (settings.notificationDevices == null && settings.speechDevices == null)) {
        return
    }
    
    def msg = settings.notificationEndMsg
    composeNotification(msg)
}

def composeNotification(msg) {
    if (msg.indexOf("%") > -1) {
        def pattern = /(?<=%).*?(?=%)/
        def counter = 0
        while (msg.indexOf("%") > -1) {
            def matches = msg =~ pattern
            def match = matches[0]
            def value
            switch (match) {
                case "eventStartTime":
                    value = formatDateTime(atomicState.item["scheduleStartTime"])
                    break
                case "eventEndTime":
                    value = formatDateTime(atomicState.item["scheduleEndTime"])
                    break
                case "taskDueDate":
                    value = formatDateTime(atomicState.item["scheduleStartTime"])
                    break
                default:
                    value = atomicState.item[match]
            }
            def textMatch = "%" + match + "%"
            msg = msg.replace(textMatch, value)
        }
    }
    
    logDebug("composeNotification msg: ${msg}")
    notificationDevices?.deviceNotification(msg)
    speechDevices?.speak(msg)
}

def triggerStartRule() {
    if (settings.legacyRule) {
        RMUtils.sendAction(settings.legacyRule, "runRuleAct", app.label)
    }
    
    if (settings.currentRule) {
        RMUtils.sendAction(settings.currentRule, "runRuleAct", app.label, "5.0")
    }
}

def triggerEndRule() {
    if (settings.legacyRule) {
        RMUtils.sendAction(settings.legacyRule, "runRuleAct", app.label)
    }
    
    if (settings.currentRule) {
        RMUtils.sendAction(settings.currentRule, "runRuleAct", app.label, "5.0")
    }
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
    if (dateTime instanceof String) {
        def stringSDF = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        dateTime = stringSDF.parse(dateTime)
    }
    
    return sdf.format(dateTime)
}

def poll() {
    if ( settings.createChildSwitch == true ) {
        def childDevice = getChildDevice(state.deviceID)
        logDebug "poll - childDevice: ${childDevice}"
        childDevice.poll()
    } else {
        logDebug "poll - no childDevice"
        getNextItems()
    }
    state.refreshed = formatDateTime(new Date())
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
        case "refreshButton":
			poll()
			return
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

def upgradeSettings(){
    if (settings.watchCalendars && settings.watchCalendars != null) {
        app.updateSetting("watchList", [value:"${settings.watchCalendars}", type:"enum"])
        app.removeSetting("watchCalendars")
        app.updateSetting("searchType", [value:"Calendar Event", type:"enum"])
        log.info "Upgraded ${app.label} settings"
    }
}
