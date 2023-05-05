def appVersion() { return "4.2.1" }
/**
 *  GCal Search Trigger Child Application
 *  https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search_Trigger.groovy
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
 *  Unless required by applicable law or agreed to in wr iting, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
import hubitat.helper.RMUtils
import groovy.json.JsonSlurper

definition(
    name: "GCal Search Trigger",
    namespace: "HubitatCommunity",
    author: "Mike Nestor & Anthony Pastor, cometfish, ritchierich",
    description: "Integrates Hubitat with Google Calendar, Tasks, and Reminders.",
    category: "Convenience",
    parent: "HubitatCommunity:GCal Search",
    documentationLink: "https://community.hubitat.com/t/release-google-calendar-search/71397",
    importUrl: "https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search_Trigger.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
    return dynamicPage(name: "mainPage", title: "${parent.getFormat("title", "GCal Search Trigger Version ${appVersion()}, ${(state.installed == true) ? "Update" : "Create new"} search trigger")}", install: true, uninstall: true, nextPage: "" ) {
    	section() {
			if (!state.isPaused) {
				input name: "pauseButton", type: "button", title: "Pause", backgroundColor: "Green", textColor: "white", width: 4, submitOnChange: true
			} else {
				input name: "resumeButton", type: "button", title: "Resume", backgroundColor: "Crimson", textColor: "white", width: 4, submitOnChange: true
			}
            if (state.refreshed) {
                paragraph "Last Refreshed:\n${parseDateTime(state.refreshed)}", width: 4
            }
            if (!state.isPaused) {
                input name: "refreshButton", type: "button", title: "Refresh", width: 4, submitOnChange: true
            }
        }
        
        section() {
            def nextItemDescription = getNextItemDescription()
            if (nextItemDescription) {
                paragraph "${nextItemDescription}"
            }
        }

        section("${parent.getFormat("box", "Search Preferences")}") {
            def isAuthorized = parent.authTokenValid("search trigger mainPage")
            if (!isAuthorized) {
                paragraph "${parent.getFormat("warning", "Authentication Problem! Please setup again in the parent GCal Search app.")}"
            } else {
                def scopesAuthorized = parent.getScopesAuthorized()
                if (scopesAuthorized == null) {
                    log.error "The parent GCal Search is using an old OAuth credential type. Please open this app and follow the steps to complete the upgrade and click Done."
                }
                def watchListOptions, mailLabels
                input name: "searchType", title:"Do you want to search Google Calendar Event, Task, Reminder, or Gmail?", type: "enum", required:true, multiple:false, options:scopesAuthorized, defaultValue: "Calendar", submitOnChange: true
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
                if (settings.searchType == "Calendar Event") {
                    watchListOptions = parent.getCalendarList()
                    if (watchListOptions == "error") {
                        paragraph "${parent.getFormat("warning", "The Google Calendar API has not been enabled in your Google project.  <a href='https://console.cloud.google.com/apis/api/calendar-json.googleapis.com' target='_blank'>Please click here to add it the in Google Console</a>. Then refresh this page.")}"
                    } else {
                        logDebug("Calendar list = ${watchListOptions}")
                        input name: "watchList", title:"Which calendar do you want to search?", type: "enum", required:true, multiple:false, options:watchListOptions, submitOnChange: true
                        input name: "includeAllDay", type: "bool", title: "Include All Day Events?", defaultValue: true, required: false
                        input name: "searchField", type: "enum", title: "Calendar field to search", required: true, defaultValue: "title", options:["title","location"]
                        input name: "GoogleMatching", type: "bool", title: "Use Google Query Matching? By default calendar event matching is done by the HE hub and it allows multiple search strings. If you prefer to use Google search features and special characters, toggle this setting. Caching of events is not supported when using Google query matching.", defaultValue: false, submitOnChange: true
                    }
                } else if (settings.searchType == "Task") {
                    settings.GoogleMatching = false // Task API doesn't allow text searching
                    watchListOptions = parent.getTaskList()
                    if (watchListOptions == "error") {
                        paragraph "${parent.getFormat("warning", "The Google Tasks API has not been enabled in your Google project.  <a href='https://console.cloud.google.com/apis/library/tasks.googleapis.com' target='_blank'>Please click here to add it the in Google Console</a>. Then refresh this page.")}"
                    } else {
                        logDebug("Task list = ${watchListOptions}")
                        input name: "watchList", title:"Which task list do you want to search?", type: "enum", required:true, multiple:false, options:watchListOptions, submitOnChange: true
                    }
                } else if (settings.searchType == "Gmail") {
                    settings.GoogleMatching = true // Gmail must use Google Matching
                    settings.appName = "Gmail Search"
                } else {
                    logDebug("scopesAuthorized: ${scopesAuthorized}")
                    settings.GoogleMatching = false // Reminder API doesn't allow text searching
                }
                if (settings.GoogleMatching == true) {
                    def searchHelp = ""
                    if (settings.searchType == "Gmail") {
                        searchHelp = "If not familiar with Google Search special characters, please visit <a href='https://support.google.com/mail/answer/7190?hl=en' target='_blank'>Search operators you can use with Gmail</a> for examples."
                    } else {
                        searchHelp = "If not familiar with Google Search special characters, please visit <a href='http://www.googleguide.com/crafting_queries.html' target='_blank'>GoogleGuide</a> for examples."
                    }
                    
                    paragraph "${parent.getFormat("text", searchHelp)}"
                } else {
                    paragraph '<p><span style="font-size: 14pt;">Search String Options:</span></p><ul style="list-style-position: inside;font-size:15px;"><li>By default matches are CaSe sensitive, toggle \'Enable case sensitive matching\' to make search matching case insensitive.</li><li>By default the search string is matched to the ' + settings.searchType.toLowerCase() + ' title using a starts with search.</li><li>For exact match, prefix the search string with an = sign. For example enter =Kids No School to find items with the exact title/location of \'Kids No School\'.</li><li>For a contains search, include an * sign. For example to find any item with the word School, enter *School. This also works for multiple non consecutive words. For example to match both Kids No School and Kids Late School enter Kids*School.</li><li>Multiple search strings may be entered separated by commas.</li><li>To match any ' + settings.searchType.toLowerCase() + ' for that day, enter *</li><li>To exclude ' + settings.searchType.toLowerCase() + ' with specific words, prefix the word with a \'-\' (minus) sign.  For example if you would like to match all items except ones with the words \'personal\' and \'lunch\' enter \'* -personal -lunch\'</li></ul>'
                    input name: "caseSensitive", type: "bool", title: "Enable case sensitive matching?", defaultValue: true
                }
                if (settings.searchType == "Gmail") {
                    paragraph "${parent.getFormat("text", getGmailSearchDescription())}"
                    mailLabels = parent.getUserLabels()
                    if (settings.messageQueryLabels != null && settings.messageQueryLabels.indexOf("none") > -1 && settings.messageQueryLabels.size() > 1) {
                        app.updateSetting("messageQueryLabels", [value:["none"], type:"enum"])
                    }
                    input name: "messageQueryLabels", title:"Search for emails with the following labels:", type: "enum", required:true, multiple:true, options:mailLabels, defaultValue: ["INBOX", "UNREAD"], submitOnChange: true
                    input name: "messageQueryAfterLastRefresh", type: "bool", title: "Search for emails received since the last refresh of this app?", defaultValue: true, required: false, submitOnChange: true
                }
                def requireSearchString = (settings.searchType == "Gmail" && (settings.messageQueryLabels == null || settings.messageQueryLabels.indexOf("none") == -1)) ? false : true
                input name: "search", type: "text", title: "Search String", required: requireSearchString, submitOnChange: true
                if (settings.searchType == "Gmail") {
                    def mailQuery = getGmailQuery()
                    def mailURL = "https://mail.google.com/mail/u/1/#search/" + URLEncoder.encode(mailQuery.toString())
                    paragraph "${parent.getFormat("text", "When adjusting the Search Preferences of this app, you should test within <a href='${mailURL}' target='_blank'>the Gmail website</a> to ensure the right messages are found. The following is the query that will be used to query for emails.")}"
                    paragraph "${parent.getFormat("code", "${mailQuery}")}"
                    paragraph "${parent.getFormat("text", "<u>Add/Remove labels</u>: Labels can be added and removed to emails matching the search criteria which may help designate that an email was processed.  Enable this option to adjust the labels.")}"
                    input name: "messageApplyLabels", type: "bool", title: "Add/Remove labels on matching emails?", defaultValue: true, required: false, submitOnChange: true
                    if (settings.messageApplyLabels == null || settings.messageApplyLabels == true) {
                        input name: "messageSetLabels", title:"Matched Email Labels. <b>Note</b>: Labels selected will be added to the emails and labels unselected will be <u>removed</u>. Choose NONE to remove all labels.", type: "enum", required:true, multiple:true, options:mailLabels, defaultValue: ["INBOX"]
                    }
                }
                paragraph "${parent.getFormat("line")}"
            }
        }

        if (settings.search || settings.searchType == "Gmail") {
            section("${parent.getFormat("box", "Schedule Settings")}") {
                paragraph "${parent.getFormat("text", "${settings.searchType} searches can be triggered once a day or periodically. Periodic options include every N hours, every N minutes, or you may enter a Cron expression.")}"
                input name: "whenToRun", type: "enum", title: "When to Run", required: true, options:["Once Per Day", "Periodically"], submitOnChange: true
                if (settings.whenToRun == "Once Per Day") {
                    input name: "timeToRun", type: "time", title: "Time to run", required: true
                }
                if (settings.whenToRun == "Periodically") {
                    input name: "frequency", type: "enum", title: "Frequency", required: true, options:["Hours", "Minutes", "Cron String"], submitOnChange: true
                    if (settings.frequency == "Hours") {
                        input name: "hours", type: "number", title: "Every N Hours: (range 1-12)", range: "1..12", required: true, submitOnChange: true
                        input name: "hourlyTimeToRun", type: "time", title: "Starting at", defaultValue: "08:00", required: true
                    }
                    if (settings.frequency == "Minutes") {
                        input name: "minutes", type: "enum", title: "Every N Minutes", required: true, options:["1", "2", "3", "4", "5", "6", "10", "12", "15", "20", "30"], submitOnChange: true
                    }
                    if (settings.frequency == "Cron String") {
                        paragraph "${parent.getFormat("text", "If not familiar with Cron Strings, please visit <a href='https://www.freeformatter.com/cron-expression-generator-quartz.html#' target='_blank'>Cron Expression Generator</a>")}"
                        input name: "cronString", type: "text", title: "Enter Cron string", required: true, submitOnChange: true
                    }
                }
                if (settings.searchType != "Gmail") {
                    paragraph "${parent.getFormat("text", "<u>Search Range</u>: By default, items from the time of search through the end of the current day are collected.  Adjust this setting to expand the search to the end of the following day or a set number of hours from the time of search.")}"
                    input name: "endTimePref", type: "enum", title: "Search Range", defaultValue: "End of Current Day", options:["End of Current Day","End of Next Day", "Number of Hours from Current Time"], submitOnChange: true
                    if (settings.endTimePref == "Number of Hours from Current Time") {
                        input name: "endTimeHours", type: "number", title: "Number of Hours from Current Time (How many hours into the future at the time of the search, would you like to query for events?)", required: true
                    }
                    if (settings.searchType == "Calendar Event") {
                        paragraph "${parent.getFormat("text", "<u>Sequential Event Preference</u>: By default the Event End Time will be set to the end date of the last sequential event matching the search criteria. This prevents the switch from toggling and additonal actions triggering multiple times when using periodic searches. If this setting is set to false, it is recommended to set an Event End Offset in the optional setting below. If no Event End Offset is set, the scheduled trigger will be adjusted by -1 minute to ensure the switch has time to toggle.")}"
                        input name: "sequentialEvent", type: "bool", title: "Expand end date for sequential events?", defaultValue: true
                    }
                    paragraph "${parent.getFormat("text", "<u>Delay to ${settings.searchType} Start Preference</u>: By default the switch will toggle and additional actions will trigger based on the matching ${settings.searchType} Start Time. If this setting is set to false, the switch will toggle and additional actions will trigger at the run time of this search trigger if a match is found. The switch will continue to toggle and additional actions will trigger again based on the Event End Time (if applicable).")}"
                    input name: "delayToggle", type: "bool", title: "Delay to ${settings.searchType} start?", defaultValue: true
                    paragraph "${parent.getFormat("text", "<u>Optional Offset Preferences</u>: Based on the defined Search Range, if an item is found in the future from the current time, scheduled triggers will be created to toggle the switch and trigger additional actions based on the item's start and end times. Use the settings below to set an offset to firing of these triggers N number of minutes before/after the item date(s).  For example, if you wish for the switch to toggle or additional actions to trigger 60 minutes prior to the start of the event, enter -60 in the Event Start Offset setting. This may be useful for reminder notifications where a message is sent/spoken in advance of a task.  Again this is dependent on When to Run (how often the trigger is executed) and the Search Range of events.")}"
                    input name: "setOffset", type: "bool", title: "Set offset?", defaultValue: false, required: false, submitOnChange: true
                    if (settings.setOffset == true) {
                        if (settings.searchType == "Calendar Event") {
                            input name: "offsetStartFromReminder", type: "bool", title: "Start Offset from Event Reminder Value?", defaultValue: false, required: false, submitOnChange: true
                        }
                        if (settings.searchType != "Calendar Event" || (settings.searchType == "Calendar Event" && settings.offsetStartFromReminder != true)) {
                            input name: "offsetStart", type: "decimal", title: "Start Offset in minutes (+/-)", required: false
                        } else {
                            paragraph "${parent.getFormat("text", "Reminders are always stored in minutes even if hours or weeks is set when creating the event.  By default offset will be X minutes before the event start time, however you may choose to delay the start time X minutes after the event start time too.")}"
                            input name: "offsetStartFromReminderWhen", type: "enum", title: "Before or After", required: true, options:["Before", "After"], defaultValue: "Before"
                        }
                        if (settings.searchType == "Calendar Event") {
                            input name: "offsetEnd", type: "decimal", title: "End Offset in minutes (+/-)", required: false
                        }
                    }
                }
                paragraph "${parent.getFormat("line")}"
            }
        }
        
        if (settings.search || settings.searchType == "Gmail") {
            section("${parent.getFormat("box", "Child Switch Preferences")}") {
                paragraph "${parent.getFormat("text", "<u>Create child switch</u>: By default this app will create and toggle a child switch when an item matches the search criteria.  Many inbuilt apps have restrictions based on the state of a switch where the rule won't fire if say for example a particular switch is turned on.  In other use cases, a child switch may bring unnecessary overhead.  Choose what you wish to happen when an item matches the search criteria.")}"
                input name: "createChildSwitch", type: "bool", title: "Create child switch?", defaultValue: true, required: false, submitOnChange: true
                if (settings.createChildSwitch != false) {
                    def defName = (settings.search) ? settings.search : settings.appName
                    defName = defName - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")
                    input name: "deviceName", type: "text", title: "Switch Device Name (Name of the Switch that gets created by this search trigger)", required: true, multiple: false, defaultValue: "${defName} Switch"
                    paragraph "${parent.getFormat("text", "<u>Switch Default Value</u>: Adjust this setting to the switch value preferred when there is no task. If a task is found, the switch will toggle from this value.")}"
                    input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, defaultValue: "off", options:["on","off"]
                    paragraph "${parent.getFormat("text", "<u>Date Format</u>: Adjust this setting to your desired date format.  By default time format will be based on the hub's time format setting.  Choose other to enter your own custom date/time format.  Please see <a href='https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html' target='_blank'>this website</a> for examples.")}"
                    input name: "dateFormat", type: "enum", title: "Date Format", required: true, defaultValue: "yyyy-MM-dd", options:["yyyy-MM-dd", "MM-dd-yyyy", "dd-MM-yyyy", "Other"], submitOnChange: true
                    if (settings.dateFormat == "Other") {
                        input name: "dateFormatOther", type: "text", title: "Enter custom date format", required: true
                    }
                    paragraph "${parent.getFormat("text", "<u>Toggle/Sync Additional Switches</u>: If you would like other existing switches to follow the switch state of the child GCal Switch, set the following list with those switch(es). Please keep in mind that this is one way from the GCal switch to these switches.")}"
                    input name: "controlOtherSwitches", type: "bool", title: "Toggle/Sync Additional Switches?", defaultValue: false, required: false, submitOnChange: true
                    if (settings.controlOtherSwitches == true) {
                        input "syncSwitches", "capability.switch", title: "Synchronize These Switches", multiple: true, required: false
                        input "reverseSwitches", "capability.switch", title: "Reverse These Switches", multiple: true, required: false
                    }
                }
                paragraph "${parent.getFormat("line")}"
            }
            section("${parent.getFormat("box", "Additional Action Preferences")}") {
                paragraph "${parent.getFormat("text", "Additional actions are optional and can be useful if you choose not to create a child switch to send notifications and run Rule Machine rules as items are found. Please be aware if you have rules set to trigger based on the child switch and you choose to run actions for those same rules problems may arise.")}"
                paragraph "${parent.getFormat("text", "Notifications can be sent/spoken based on an item matching search criteria.")}"
                input "sendNotification", "bool", title: "Send notifications?", defaultValue: false, submitOnChange: true
                if (settings.sendNotification == true) {
                    def startMsg, endMsg
                    if (settings.searchType == "Calendar Event") {
                        startMsg = "Custom message to send at event <u>scheduled start</u>"
                        endMsg = "Custom message to send at event <u>scheduled end</u>"
                    } else if (settings.searchType == "Gmail") {
                        startMsg = "Custom message to send when message is found"
                    } else {
                        startMsg = "Custom message to send at " + settings.searchType.toLowerCase() + " due date"
                        endMsg = "Custom message to send when " + settings.searchType.toLowerCase() + " is completed"
                    }
                    
                    paragraph "${parent.getFormat("text", "<u>Custom message to send</u>: " + getNotificationMsgDescription(settings.searchType))}"
                    if (settings.searchType == "Calendar Event") {
                        input "sendReminder", "bool", title: "Send reminder?", defaultValue: false, submitOnChange: true
                        if (settings.sendReminder == true) {
                            input name: "notificationReminderMsg", type: "textarea", title: "Custom reminder to send at event reminder setting", required: true, defaultValue: "%eventTitle% is starting at %eventStartTime%"
                        }
                    }
                    input name: "notificationStartMsg", type: "textarea", title: "${startMsg}", required: false
                    if (endMsg != null) {
                        input name: "notificationEndMsg", type: "textarea", title: "${endMsg}", required: false
                    }
                    paragraph "${parent.getFormat("text", "<u>Include details from all matching items</u>: Based on the defined Search Range, multiple items may be found matching the search criteria. By default only details from the first item will be included in notifications. Set this setting to true if you want details from all matching items to be included.")}"
                    input "includeAllItems", "bool", title: "Include details from all matching items?", defaultValue: false
                    input "notificationDevices", "capability.notification", title: "Send notification to device(s)?", required: false, multiple: true
                    input "speechDevices", "capability.speechSynthesis", title: "Speak notification on these device(s)?", required: false, multiple: true
                }
                paragraph "${parent.getFormat("line")}"
                
                paragraph "${parent.getFormat("text", "Rule Machine rules can be evaluated based on an item matching search criteria.")}"
                input "runRuleActions", "bool", title: "Run Rule Machine actions?", defaultValue: false, submitOnChange: true
                if (settings.runRuleActions == true) {
                    def legacyRules = RMUtils.getRuleList()
                    if (legacyRules != null) {
                        input "legacyRule", "enum", title: "Select which Rule Machine Legacy rules to run", options: legacyRules, multiple: true
                    }
                    def currentRules = RMUtils.getRuleList('5.0')
                    input "currentRule", "enum", title: "Select which rules to run", options: currentRules, multiple: true
                    if (settings.searchType == "Calendar Event") {
                        paragraph "${parent.getFormat("text", "<u>Set Rule Machine Private Boolean</u>: Rule actions will be invoked at the event start and event end.  If you wish to differentiate between the two times, set this setting to true and the Rule Private Boolean will be set to True at the event start and False at the event end.  Then build your rule conditions around these values to have a different flow.")}"
                        input "updateRuleBoolean", "bool", title: "Set Rule Machine Private Boolean?", defaultValue: false
                    }
                }
                paragraph "${parent.getFormat("line")}"
                
                def controlSwitchesDescription = getControlSwitchesDescription(settings.searchType)
                paragraph "${parent.getFormat("text", controlSwitchesDescription.description)}"
                input "controlSwitches", "bool", title: "${controlSwitchesDescription.title}", defaultValue: false, submitOnChange: true
                if (settings.controlSwitches == true) {
                    paragraph "${parent.getFormat("text", controlSwitchesDescription.instructions)}"
                    input name: "itemField", type: "enum", title: "${settings.searchType} field to use for switch instructions", required: true, defaultValue: "title", options:controlSwitchesDescription.options, width: 4
                    input name: "offTranslation", type: "text", title: "Translation for Off", required: true, defaultValue: "off", width: 4
                    input name: "onTranslation", type: "text", title: "Translation for On", required: true, defaultValue: "on", width: 4
                    input name: "ignoreWords", type: "text", title: "Verbs within text to ignore (separate multiple words by comma)", required: false, defaultValue: "turn", width: 6
                    input name: "conjunctionWords", type: "text", title: "Conjunction words for multiple switches (separate multiple words by comma)", required: false, defaultValue: "and", width: 6
                    input "controlSwitchList", "capability.switch", title: "Control These Switches", multiple: true, required: true
                }
                paragraph "${parent.getFormat("line")}"
                
                def parseFieldDescription = getParseFieldDescription(settings.searchType)
                paragraph "${parent.getFormat("text", parseFieldDescription.description)}"
                input "parseField", "bool", title: "${parseFieldDescription.title}", defaultValue: false, submitOnChange: true
                if (settings.parseField == true) {
                    //paragraph "${parent.getFormat("text", parseFieldDescription.instructions)}"
                    input name: "fieldToParse", type: "enum", title: "${settings.searchType} field to search for text matches", required: true, defaultValue: "title", options:parseFieldDescription.options
                    paragraph parseFieldDescription.mappingsTable
                }
                paragraph "${parent.getFormat("line")}"
                
                if (settings.sendNotification == true || settings.runRuleActions == true || settings.controlSwitches == true || settings.parseField == true) {
                    paragraph "${parent.getFormat("text", "Toggle 'Enable descriptionText logging' below if you want this app to create an event log entry when the additional actions are executed with details on that action.")}"
                    input name: "txtEnable", type: "bool", title: "Enable descriptionText logging?", defaultValue: false, required: false
                    paragraph "${parent.getFormat("line")}"
                }
            }
        }
        
        if (settings.search || settings.searchType == "Gmail") {
            section("${parent.getFormat("box", "App Preferences")}") {
                def defName = (settings.search) ? settings.search : settings.appName
                defName = defName - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")
                input name: "appName", type: "text", title: "Name this child app", required: true, defaultValue: "${defName}", submitOnChange: true
                input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
                paragraph "${parent.getFormat("line")}"
            }
        }
            
        if (state.installed) {
	    	section ("Remove Trigger and Corresponding Device") {
            	paragraph "ATTENTION: The only way to uninstall this trigger and the corresponding child switch device is by clicking the Remove button below. Trying to uninstall the corresponding device from within that device's preferences will NOT work."
            }
    	}   
	}       
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
    "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

def getNotificationMsgDescription(searchType) {
    def answer = "Use %now% to include current date/time of when notification is sent, "
    if (searchType == "Calendar Event") {
        answer += "%eventTitle% to include event title, %eventLocation% to include event location, %eventDescription% to include event description, %eventStartTime% to include event start time, %eventEndTime% to include event end time, and %eventAllDay% to include event all day."
        if (settings.setOffset) {
            answer += " Offset values can be be added by using %scheduleStartTime% and %scheduleEndTime%."
        }
        if (settings.sendReminder) {
            answer += " Reminder minutes can be be added by using %eventReminderMin%."
        }
    } else if (searchType == "Gmail") {
        answer += "%messageTitle% to include message title, %messageBody% to include message body, %messageTo% to include message to, %messageFrom% to include message from, and %messageReceived% to include message received date."
    } else {
        answer += "%taskTitle% to include task title and %taskDueDate% to include task due date."
        if (settings.setOffset) {
            answer += " Offset values can be be added by using %scheduleStartTime%."
        }
    }
    if (settings.controlSwitches == true) {
        answer += " With 'Control switches from ${searchType.toLowerCase()} details' enabled switches that will be turned on/off can be added by using %onSwitches% and %offSwitches%."
    }
    if (settings.parseField == true) {
        answer += " With 'Parse data from ${searchType.toLowerCase()} details and update hub variables' enabled variables that will be updated can be added by using %variableUpdates%."
    }
    
    return answer
}

def getNextItemDescription() {    
    def answer
    if (state.item) {
        def item = (state.item instanceof ArrayList) ? state.item[0] : state.item
        def itemTitle
        if (searchType == "Calendar Event") {
            itemTitle = item.eventTitle
        } else if (searchType == "Gmail") {
            itemTitle = item.messageTitle
        } else {
            itemTitle = item.taskTitle
        }
        
        if (itemTitle != " ") {
            def searchType = (settings.searchType) ? settings.searchType : "Item"
            def itemDetails = ""
            if (searchType == "Calendar Event") {
                if (item.eventDescription != null) {
                    itemDetails += "<b>Description</b>: ${item.eventDescription}\n"
                }
                itemDetails += "<b>Start Time</b>: ${formatDateTime(item.eventStartTime)}"
                itemDetails += (item.eventStartTime != item.scheduleStartTime) ? " (<b>Start Offset</b>: ${formatDateTime(item.scheduleStartTime)}) " : ""
                itemDetails += ", "
                itemDetails += "<b>End Time</b>: ${formatDateTime(item.eventEndTime)}"
                itemDetails += (item.eventEndTime != item.scheduleEndTime) ? " (<b>End Offset</b>: ${formatDateTime(item.scheduleEndTime)}) " : " "
                itemDetails += "\n<b>Location</b>: ${item.eventLocation}"
                itemDetails += "\n<b>Event All Day</b>: ${item.eventAllDay}, <b>Reminder</b>: ${item.eventReminderMin} Minutes"
            } else if (searchType == "Gmail") {
                itemDetails += "<b>Body</b>: ${item.messageBody}\n"
                itemDetails += "<b>Received</b>: ${formatDateTime(item.messageReceived)}\n"
                itemDetails += "<b>Messages found</b>: ${state.item.size()}\n"
            } else {
                itemDetails += "<b>Due Date</b>: ${formatDateTime(item.taskDueDate)}"
                itemDetails += (item.taskDueDate != item.scheduleStartTime) ? " (<b>Due Date Offset</b>: ${formatDateTime(item.scheduleStartTime)}) " : ""
            }
            
            if (item.containsKey("additionalActions") && item.additionalActions.containsKey("triggerSwitchControl") && !item.additionalActions.triggerSwitchControl.matchSwitches.isEmpty()) {
                if (item.additionalActions.triggerSwitchControl.matchSwitches.on) {
                    itemDetails += "\n<b>Switches to turn on</b>: "
                    itemDetails += gatherSwitchNames(item, "on")
                }
                if (item.additionalActions.triggerSwitchControl.matchSwitches.off) {
                    itemDetails += "\n<b>Switches to turn off</b>: "
                    itemDetails += gatherSwitchNames(item, "off")
                }
            }
            
            if (item.containsKey("additionalActions") && item.additionalActions.containsKey("triggerVariableUpdate") && !item.additionalActions.triggerVariableUpdate.variableUpdates.isEmpty()) {
                itemDetails += "\n<b>Variables to update</b>: "
                itemDetails += gatherVariableUpdates(item)
            }
            
            if (searchType == "Gmail") {
                answer = "<b>Last Message</b>:\n"
                answer += "<b>Title</b>: ${itemTitle}"
            } else {
                answer = "<b>Next ${searchType}</b>: ${itemTitle}"
            }
            
            answer += "\n${itemDetails}"
        }
    }
    
    return answer
}

def getControlSwitchesDescription(searchType) {
    def answer = [
        title: "Control switches from ${searchType.toLowerCase()} details?",
        options: ["title"]
    ]
    answer.description = "Switches can be toggled dynamically based on text/instructions found within the ${searchType.toLowerCase()}. For confirmation purposes, the matched switches will appear at the top of this app above the Search Preferences section when a maching ${searchType.toLowerCase()} is found. "
    answer.description += "The same search matching options described above in the Search Preferences section are available to match switch names. Examples:\n"
    answer.description += "<ul style='list-style-position: inside;font-size:15px;'>"
    answer.description += "<li>'Turn off bedroom fan and turn on bedroom overhead lights, bedroom lamps, and bedroom tv' to turn off and on several switches</li>" 
    answer.description += "<li>'Turn off bedroom*' to turn off all switches that start with bedroom</li>"
    answer.description += "</ul>"
    
    if (searchType == "Calendar Event") {
        answer.options.push("location")
        answer.options.push("description")
    } else if (searchType == "Gmail") {
        answer.options.push("body")
    }
    
    answer.instructions = "Options Explanation:\n"
    answer.instructions += "<ul style='list-style-position: inside;font-size:15px;'>"
    answer.instructions += "<li><u>${searchType.toLowerCase()} field to use...</u>: Field within the the ${searchType.toLowerCase()} to look for instructions.  Tasks and Reminders only have the title field available.</li>"
    answer.instructions += "<li><u>Translation for Off</u>: Defaults to the word 'off' but international users may enter their translation for 'off'</li>"
    answer.instructions += "<li><u>Translation for On</u>: Defaults to the word 'on' but international users may enter their translation for 'on'</li>"
    answer.instructions += "<li><u>Verbs within text to ignore</u>: Optional: Enter word(s) that might be included within the instructions that you want to ignore during the matching process. In English you might say 'turn on the overhead lights' but the word 'turn' isn't really necessary and should be ignored.</li>"
    answer.instructions += "<li><u>Conjunction words...</u>: Optional: Words that might be used to separate multiple switches such as 'and' or 'along with'. By default multiple switches can be separated by a comma or semicolon.</li>"
    answer.instructions += "<li><u>Control These Switches</u>: Choose all switches that you would ever want to control from a ${searchType.toLowerCase()}.  Text within the ${searchType.toLowerCase()} will be matched to the switch name/label set witin the device. Note: apostrophe's will be removed prior to matching since they can be problematic.</li>"
    answer.instructions += "</ul>"
    
    return answer
}

def getParseFieldDescription(searchType) {
    def answer = [
        title: "Parse data from ${searchType.toLowerCase()} details and update hub variables",
        options: ["title"]
    ]
    answer.description = "Text found within the ${searchType.toLowerCase()} can be mapped to Hub Variables for additional rule processing. For example with an AirBnB rental calendar event, the last 4 digits of the renter's phone number can be mapped to a hub variable that will fire a rule to add these digits as a code to the lock on the property."
    answer.description += "<ul style='list-style-position: inside;font-size:15px;'>"
    answer.description += "<li>Each line of the chosen field will be processed looking for specific text entered in the Text Prefix input</li>" 
    answer.description += "<li>If a match is found, the remaining text on that line will become the value of the selected Hub Variable</li>"
    answer.description += "</ul>"

    if (searchType == "Calendar Event") {
        answer.options.push("location")
        answer.options.push("description")
    } else if (searchType == "Gmail") {
        answer.options.push("body")
    }

    /*answer.instructions = "Options Explanation:\n"
    answer.instructions += "<ul style='list-style-position: inside;font-size:15px;'>"
    answer.instructions += "<li><u>Reminder field to use...</u>: Field within the the ${searchType.toLowerCase()} to look for instructions.  Tasks and Reminders only have the title field available.</li>"
    answer.instructions += "</ul>"
    */
    
    if (settings.parseField == true) {
        def parseMappings = (atomicState.parseMappings) ? atomicState.parseMappings : [[text:"",location:"SameLine",variable:"None"]]
        HashMap globalVars = getAllGlobalVars()
        def globalVarNames = globalVars.keySet()
        String locationOptions = "<option value='SameLine'>Same Line</option><option value='NextLine'>Next Line</option>"
        String variableOptions = "<option value='None'>Click to set</option>"
        for (int i = 0; i < globalVarNames.size(); i++) {
            def optionVal = globalVarNames[i]
            variableOptions += "<option value='" + optionVal + "'>" + optionVal + "</option>"
        }
        
        String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
        str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
            "</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black' id='parseMappings'>" +
            "<thead><tr style='border-bottom:2px solid black'>" + 
            "<th>Text Prefix</th>" +
            "<th>Value Location</th>" +
            "<th>Variable</th>" +
            "<th><iconify-icon icon='ion:trash-sharp'></iconify-icon></th></tr></thead>"
        for (int m = 0; m < parseMappings.size(); m++) {
            def mapping = parseMappings[m]
            String deleteRow = buttonLink("deleteRow" + m, "<iconify-icon icon='ion:trash-sharp'></iconify-icon>", "#FF0000", "20px")
            str += "<tr style='color:black'>" + 
                "<td><input id='text$m' name='text$m' type='text' value='${mapping.text}' onChange='captureValue($m)'></td>" +
                "<td><select id='location$m' name='location$m' onChange='captureValue($m)'>" + locationOptions + "</select></td>" +
                "<td><select id='variable$m' name='variable$m' onChange='captureValue($m)'>" + variableOptions + "</select></td>" +
                "<script>document.querySelector('#location$m').value= '${mapping.location}';document.querySelector('#variable$m').value= '${mapping.variable}';</script>" +
                "<td>$deleteRow</td></tr>"
        }

        str += "</table>"
        String newMapping = buttonLink("addRow", "+", "#007009", "25px")
        str += "<table id='parseMappings2' class='mdl-data-table tstat-col' style=';border:none'><thead><tr>" +
            "<th style='border-bottom:2px solid black;border-right:2px solid black;border-left:2px solid black;width:50px' title='New Mapping'>$newMapping</th>" +
            "<th style='border:none;color:green;font-size:25px'><b><i class='he-arrow-left2' style='vertical-align:middle'></i> New Mapping</b></th>" +
            "</tr></thead></table></div>" +
            "<script>function captureValue(val) {" +
            "var answer = {};answer.row = val; answer.text = document.getElementById('text' + val).value;answer.location = document.getElementById('location' + val).value;answer.variable = document.getElementById('variable' + val).value;answer = JSON.stringify(answer);" +
            "var postBody = {'id': " + app.id + ",'name': 'mapping' + answer + ''};" +
            "\$.post('/installedapp/btn', postBody,function (msg) {if (msg.status == 'success') {/*window.location.reload()*/}}, 'json');}</script>"
        
        answer.mappingsTable = str
    }

    return answer
}

def getGmailSearchDescription() {
    def answer = ""
    answer = "Search Options:\n"
    answer += "<ul style='list-style-position: inside;font-size:15px;'>"
    answer += "<li>By default this will query for unread emails in the inbox received after the last refreshed time (in Unix epoch time)</li>"
    answer += "<li>The default search labels (INBOX and UNREAD) can be adjusted in the selection below or manually in the Search String via 'label:' search</li>"
    answer += "<li>The received query can be removed by toggling the 'Search for emails received...' setting below or manually in the Search String via 'after:' search</li>"
    answer += "<li>By default any email matching the search criteria will remain in the inbox but the unread label will be removed. This can be adjusted with the Matched Email Labels setting below.</li>"
    answer += "</ul>"
    
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
    atomicState.installed = true
    atomicState.item = null
    
    // Sets Label of Trigger
    updateAppLabel()
    
    // Since 'none' is a special label make sure no other labels are selected
    if (settings.searchType == "Gmail") {
        if (settings.messageQueryLabels.indexOf("none") > -1 && settings.messageQueryLabels.size() > 1) {
            app.updateSetting("messageQueryLabels", [value:'["none"]', type:"enum"])
        }
        if (settings.messageSetLabels.indexOf("none") > -1 && settings.messageSetLabels.size() > 1) {
            app.updateSetting("messageSetLabels", [value:'["none"]', type:"enum"])
        }
    }
    
    if (settings.createChildSwitch == true) {
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
        if (settings.whenToRun == "Once Per Day") {
            schedule(timeToRun, poll)
            logDebug("initialize - creating schedule once per day at: ${timeToRun}")
        } else {
            def cronString = ""
            if (settings.frequency == "Hours") {
                def hourlyTimeToRun = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", settings.hourlyTimeToRun)
                def hour = hourlyTimeToRun.hours
                def minute = hourlyTimeToRun.minutes
                cronString = "0 ${minute} ${hour}/${hours} * * ? *"
            } else if (settings.frequency == "Minutes") {
                cronString = "0 0/${settings.minutes} * * * ?"
            } else if (settings.frequency == "Cron String") {
                cronString = settings.cronString
            }
            schedule(cronString, poll)
            logDebug("initialize - creating schedule with cron string: ${cronString}")
        }
    }
    //poll()
    runIn(5, poll)
}

def getDefaultSwitchValue() {
    return settings.switchValue
}

def poll() {
    if (settings.createChildSwitch == true) {
        def childDevice = getChildDevice(state.deviceID)
        logDebug "poll - childDevice: ${childDevice}"
        childDevice.poll()
    } else {
        logDebug "poll - no childDevice"
        getNextItems()
    }
}

def getNextItems() {
    //Just in case verify upgrade
    parent.upgradeSettings()
    
    def answer
    if (settings.searchType == "Task") {
        answer = getNextTasks()
    } else if (settings.searchType == "Reminder") {
        answer = getNextReminders()
    } else if (settings.searchType == "Gmail") {
        answer = getNextMessages()
    } else {
        answer = getNextEvents()
    }
    if (!state.isPaused) {
        atomicState.refreshed = parent.getCurrentTime()
    }
    return answer
}

def completeItem() {
    def taskID = (state.item instanceof ArrayList) ? state.item[0].taskID : state.item.taskID
	if (taskID && settings.searchType == "Task") {
        return completeTask(taskID)
    } else if (taskID && settings.searchType == "Reminder") {
        return completeReminder(taskID)
    } else {
        return false
    }
}

def getNextEvents() {
    def item = [
        eventTitle: " ",
        eventID: " ",
        eventDescription: " ",
        eventLocation: " ",
        eventAllDay: " ",
        eventStartTime: " ",
        eventEndTime: " ",
        eventReminderMin: " ",
        switch: "defaultValue"
    ]
    
    if (state.isPaused) {
        log.warn "${app.label} is paused, cannot refresh."
        return item
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
            items = matchItem(items, caseSensitive, search, eventField)
        }
        
        for (int i = 0; i < items.size(); i++) {
            item = items[i]
            foundMatch = true
            
            if (i < items.size()) {
                for (int j = 1; j < items.size(); j++) {
                    def newItem = items[j]
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
            
            item.scheduleStartTime = new Date(item.eventStartTime.getTime())
            if (settings.delayToggle == false) {
                item.scheduleStartTime = new Date()
            }
            if (settings.setOffset && ((settings.offsetStart != null && settings.offsetStart != "") || settings.offsetStartFromReminder == true)) {
                def origStartTime = new Date(item.eventStartTime.getTime())
                
                int offsetStart
                if (settings.offsetStartFromReminder == true) {
                    offsetStart = item.eventReminderMin.toInteger()
                    offsetStart = (settings.offsetStartFromReminderWhen == "Before") ? -offsetStart : offsetStart
                } else {
                    offsetStart = settings.offsetStart.toInteger()
                }
                
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
            items[i] = item
        }
        
        logMsg.push("foundMatch: ${foundMatch}")
        if (foundMatch) {
            item  = items[0]
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    runAdditionalActions((items.isEmpty()) ? [item] : items)
    return item
}

def getNextTasks() {
    def item = [
        taskTitle: " ",
        taskID: " ",
        taskDueDate: " ",
        switch: "defaultValue"
    ]
    
    if (state.isPaused) {
        log.warn "${app.label} is paused, cannot refresh."
        return item
    }
    
    def logMsg = []
    def search = (!settings.search) ? "" : settings.search
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : settings.endTimePref
    def items = parent.getNextTasks(settings.watchList, search, endTimePreference)
    logMsg.push("getNextTasks - BEFORE search: ${search}, items:\n${items.join("\n")}\nAFTER ")
    
    def sequentialEventOffset = false
    if (items && items.size() > 0) {     
        def foundMatch = false
        // Check for search string match
        if (items.size() > 0) {
            def eventField = "taskTitle"
            items = matchItem(items, caseSensitive, search, eventField)
        }
        
        for (int i = 0; i < items.size(); i++) {
            item = items[i]
            foundMatch = true
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
            items[i] = item
        }
        
        logMsg.push("foundMatch: ${foundMatch}")
        if (foundMatch) {
            item  = items[0]
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    runAdditionalActions((items.isEmpty()) ? [item] : items)
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
    def item = [
        taskTitle: " ",
        taskID: " ",
        taskDueDate: " ",
        switch: "defaultValue"
    ]
    
    if (state.isPaused) {
        log.warn "${app.label} is paused, cannot refresh."
        return item
    }
    
    
    def logMsg = []
    def search = (!settings.search) ? "" : settings.search
    def endTimePreference = (settings.endTimePref == "Number of Hours from Current Time") ? settings.endTimeHours : settings.endTimePref
    def items = parent.getNextReminders(search, endTimePreference)
    logMsg.push("getNextReminders - BEFORE search: ${search}, items:\n${items.join("\n")}\nAFTER ")
    
    def completeAllPastDue = true
    def sequentialEventOffset = false
    if (items && items.size() > 0) {
        def foundMatch = false
        // Check for search string match
        if (items.size() > 0) {
            def eventField = "taskTitle"
            items = matchItem(items, caseSensitive, search, eventField)
        }
        
        for (int i = 0; i < items.size(); i++) {
            item = items[i]
            foundMatch = true
            
            if (completeAllPastDue && item.repeat != "none" && items.size() > 1) {
                def recurrenceId = item.recurrenceId
                def taskIDList = [item.taskID]
                for (int j = 1; j < items.size(); j++) {
                    if (items[j].recurrenceId == recurrenceId && taskIDList.indexOf(items[j].taskID) == -1 && now() >= items[j].taskDueDate.getTime()) {
                        taskIDList.push(items[j].taskID)
                    }
                }
                item.taskID = taskIDList.join(",")
            }
            
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
            items[i] = item
        }
        
        logMsg.push("foundMatch: ${foundMatch}")
        if (foundMatch) {
            item  = items[0]
        }
    }
    
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    runAdditionalActions((items.isEmpty()) ? [item] : items)
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

def getGmailQuery() {
    def userSearchString = (!settings.search) ? "" : settings.search
    def searchString = []
    if (settings.messageQueryLabels == null && !state.installed) {
        settings.messageQueryLabels = ["INBOX", "UNREAD"]
    }
    if (userSearchString.indexOf("label:") == -1 && settings.messageQueryLabels != null && settings.messageQueryLabels.indexOf("none") == -1) {
        def mailLabels = parent.getUserLabels()
        for (int i = 0; i < settings.messageQueryLabels.size(); i++) {
            def messageQueryLabel = mailLabels[settings.messageQueryLabels[i]]
            searchString.push("label:" + messageQueryLabel)
        }
    }
    if (state.refreshed != null && settings.messageQueryAfterLastRefresh == true && userSearchString.indexOf("after:") == -1) {
        def lastRefreshed = parseDateTime(state.refreshed).getTime() / 1000
        searchString.push("after:${lastRefreshed}")
    }
    if (settings.search) {
        searchString.push("${userSearchString}")
    }
    
    return searchString.join(" ")
}

def getNextMessages() {
    def item = [
        messageTitle: " ",
        messageBody: " ",
        messageID: " ",
        messageReceived: " ",
        messageFrom: " ",
        messageTo: " "
    ]
    
    if (state.isPaused) {
        log.warn "${app.label} is paused, cannot refresh."
        return
    }
    
    def logMsg = []
    def searchString = getGmailQuery()
    def setLabels
    
    if (settings.messageApplyLabels == true) {
        def mailLabels = parent.getUserLabels()
        def messageSetLabels = settings.messageSetLabels
        setLabels = [
            add: [],
            remove: []
        ]
        mailLabels.each{key, value -> 
            if (key == "none") {
                // do nothing
            } else if (messageSetLabels.indexOf("none") == -1 && messageSetLabels.indexOf(key) > -1) {
                setLabels.add.push(key)
            } else {
                setLabels.remove.push(key)
            }
        }
    }
    
    def items = parent.getNextMessages(searchString, setLabels)
    if (items && items.size() > 0) {     
        for (int i = 0; i < items.size(); i++) {
            items[i].scheduleStartTime = new Date(items[i].messageReceived.getTime())
            //Toggle switch after 10 seconds
            items[i].scheduleEndTime = new Date(now() + (1000 * 10).toLong())
        }
        item  = items[0]
    }
    
    logMsg.push("getNextMessages - BEFORE searchString: ${searchString}, setLabels: ${setLabels}, items:\n${items.join("\n")}\nAFTER ")
    logMsg.push("item: ${item}")
    logDebug("${logMsg}")
    
    runAdditionalActions((items.isEmpty()) ? [item] : items)
    return item
}

def runAdditionalActions(items) {
    def logMsg = ["runAdditionalActions - items: ${items}"]
    if (settings.controlSwitches != true && settings.sendNotification != true && settings.runRuleActions != true && settings.parseField != true) {
        logMsg.push("No additional actions to run")
    } else {
        def previousItems = atomicState.item
        logMsg.push("previousItems: ${previousItems}")
        def itemSame = compareItem(items)
        itemSame = false
        logMsg.push("itemSame: ${itemSame}")
        if (itemSame == true) {        
            items = previousItems
            logMsg.push("no item changes, skipping additional actions")
        } else {
            def additionalActions = [:]
            def scheduleItems = [
                triggerVariableUpdate : [],
                triggerSwitchControl : [],
                triggerReminderNotification : [],
                triggerStartNotification : [],
                triggerEndNotification : [],
                triggerStartRule : [],
                triggerEndRule : []
            ]
            
            //Gather start and end times in case there are overlapping meetings
            def startTime, endTime
            def nowTime = new Date(now() + (1000 * 10).toLong())
            for (int i = 0; i < items.size(); i++) {
                def item = items[i]
                //If no items are found, scheduleStartTime is not included in the object
                if (!item.containsKey("scheduleStartTime")) {
                    logMsg.push("scheduleStartTime not set, skipping additional actions")
                    continue
                }
                
                def scheduleItem = [:]
                def previousItem
                switch(settings.searchType) {
                    case "Calendar Event":
                        scheduleItem.id = item.eventID
                        previousItem = previousItems.find{it.eventID == item.eventID}
                        break
                    case "Gmail":
                        scheduleItem.id = item.messageID 
                        previousItem = previousItems.find{it.messageID == item.messageID}
                        break
                    default:
                        scheduleItem.id = item.taskID
                        previousItem = previousItems.find{it.taskID == item.taskID}
                        break
                }
                
                def itemCompare = compare2Items(item, previousItem)
                scheduleItem.time = (now() > item.scheduleStartTime.getTime()) ? nowTime : item.scheduleStartTime
                if (item.containsKey("scheduleEndTime") && item.scheduleEndTime != null) {
                    scheduleItem.end = item.scheduleEndTime
                }
                if (item.containsKey("eventReminderMin") && item.eventReminderMin != null) {
                    def reminder = item.eventStartTime.getTime() - (item.eventReminderMin * 60000)
                    scheduleItem.reminder = (now() > reminder) ? nowTime : new Date(reminder)
                }
                
                if (startTime == null) {
                    startTime = scheduleItem.time
                }
                
                //Check to make sure that the first item found's end date is greater than other items found to schedule additional actions
                if (item.containsKey("scheduleEndTime") && item.scheduleEndTime != null) {
                    if (endTime == null) {
                        endTime = item.scheduleEndTime
                    } else if (startTime == scheduleItem.time && endTime > scheduleItem.time) {
                        endTime = item.scheduleEndTime
                    } else if (endTime > scheduleItem.time) {
                        logMsg.push("continuing, endTime(${endTime}) > scheduleItem.time(${scheduleItem.time})")
                    } else {
                        logMsg.push("skipping, scheduleItem.time(${scheduleItem.time}) > endTime(${endTime})")
                        continue
                    }
                }
                
                if (itemCompare.same && (itemCompare.processed.size() > 0 || itemCompare.scheduled.size() > 0)) {
                    additionalActions = [:]
                    if (itemCompare.allProcessed) {
                        logMsg.push("item hasn't changed and additional actions already processed, skipping additional actions")
                        additionalActions = previousItem.additionalActions
                    } else {
                        for (int c = 0; c < itemCompare.processed.size(); c++) {
                            def processedItem = itemCompare.processed[c]
                            additionalActions[processedItem] = previousItem.additionalActions[processedItem]
                            logMsg.push("${processedItem} already processed ${scheduleItem}")
                        }

                        for (int c = 0; c < itemCompare.scheduled.size(); c++) {
                            def scheduledItem = itemCompare.scheduled[c]
                            scheduleItems[scheduledItem].push(scheduleItem)
                            additionalActions[scheduledItem] = previousItem.additionalActions[scheduledItem]
                            logMsg.push("rescheduling ${scheduledItem} ${scheduleItem}")
                        }
                    }
                    if (!additionalActions.isEmpty()) {
                        item.additionalActions = additionalActions
                        items[i] = item
                    }
                    continue
                }
                
                additionalActions = [:]
                
                if (settings.parseField != true) {
                    logMsg.push("parseField: ${settings.parseField}, not parsing field")
                } else {
                    logMsg.push("scheduling parse field actions ${scheduleItem}")
                    scheduleItems.triggerVariableUpdate.push(scheduleItem)
                    def triggerVariableUpdate = [:]
                    triggerVariableUpdate.status = "scheduled"
                    triggerVariableUpdate.variableUpdates = gatherFieldMappings(item)
                    additionalActions.triggerVariableUpdate = triggerVariableUpdate
                }
                
                if (settings.controlSwitches != true || settings.controlSwitchList == null) {
                    logMsg.push("controlSwitches: ${settings.controlSwitches}, not controlling switches")
                } else {
                    logMsg.push("scheduling switch control actions ${scheduleItem}")
                    scheduleItems.triggerSwitchControl.push(scheduleItem)
                    def triggerSwitchControl = [:]
                    triggerSwitchControl.status = "scheduled"
                    triggerSwitchControl.matchSwitches = gatherControlSwitches(item)
                    additionalActions.triggerSwitchControl = triggerSwitchControl
                }
                
                if (settings.sendNotification != true || (settings.notificationReminderMsg == null && settings.notificationStartMsg == null && settings.notificationEndMsg == null) || (settings.notificationDevices == null && settings.speechDevices == null)) {
                    logMsg.push("sendNotification: ${settings.sendNotification}, not scheduling notification(s)")
                } else {
                    if (settings.sendReminder == true && settings.notificationReminderMsg != null && (scheduleItem.time > nowTime || didVariablesChange(settings.notificationReminderMsg, itemCompare.changes))) {
                        logMsg.push("scheduling reminder notification ${scheduleItem}")
                        scheduleItems.triggerReminderNotification.push(scheduleItem)
                        additionalActions.triggerReminderNotification = "scheduled"
                    }
                    
                    if (settings.notificationStartMsg != null && (scheduleItem.time > nowTime || didVariablesChange(settings.notificationStartMsg, itemCompare.changes))) {
                        logMsg.push("scheduling start notification ${scheduleItem}")
                        scheduleItems.triggerStartNotification.push(scheduleItem)
                        additionalActions.triggerStartNotification = "scheduled"
                    }

                    if (settings.searchType == "Calendar Event" && settings.notificationEndMsg != null && scheduleItem.containsKey("end")) {
                        logMsg.push("scheduling end notification ${scheduleItem}")
                        scheduleItems.triggerEndNotification.push(scheduleItem)
                        additionalActions.triggerEndNotification = "scheduled"
                    }
                }

                if (settings.runRuleActions != true || (settings.legacyRule == null && settings.currentRule == null)) {
                    logMsg.push("runRuleActions: ${settings.runRuleActions}, not evaluating rule")
                } else {
                    logMsg.push("scheduling start rule actions ${scheduleItem}")
                    scheduleItems.triggerStartRule.push(scheduleItem)
                    additionalActions.triggerStartRule = "scheduled"

                    if (settings.searchType == "Calendar Event" && scheduleItem.containsKey("end")) {
                        logMsg.push("scheduling end rule actions ${scheduleItem}")
                        scheduleItems.triggerEndRule.push(scheduleItem)
                        additionalActions.triggerEndRule = "scheduled"
                    }
                }
                
                logMsg.push("additionalActions: ${additionalActions}")
                if (!additionalActions.isEmpty()) {
                    item.additionalActions = additionalActions
                    items[i] = item
                }
            }
            
            def scheduleKeys = scheduleItems.keySet()
            for (int k = 0; k < scheduleKeys.size(); k++) {
                def key = scheduleKeys[k]
                if (key == "scheduler") continue
                
                def values = scheduleItems[key]
                unschedule("triggerAdditionalAction")
                
                for (int v = 0; v < values.size(); v++) {
                    def value = values[v]
                    def scheduleTime = (key.indexOf("End") > -1 && value.containsKey("end")) ? value.end : value.time
                    scheduleTime = (key.indexOf("Reminder") > -1 ) ? value.reminder : scheduleTime
                    if (!scheduleItems.containsKey("scheduler")) {
                        scheduleItems.scheduler = [:]
                    }
                    def scheduler = [
                        actionName: key,
                        id: value.id
                    ]
                    if (scheduleItems.scheduler.containsKey(scheduleTime)) {
                        scheduleItems.scheduler[scheduleTime].push(scheduler)
                    } else {
                        scheduleItems.scheduler[scheduleTime] = [scheduler]
                    }
                }
            }
            logMsg.push("scheduleItems: ${scheduleItems}")
            
            if (scheduleItems.containsKey("scheduler")) {
                scheduleKeys = scheduleItems.scheduler.keySet()
                for (int k = 0; k < scheduleKeys.size(); k++) {
                    def key = scheduleKeys[k]
                    def scheduleTime = scheduleKeys[k]
                    def schedulerDetails = (scheduleItems.scheduler[key] instanceof Map) ? [scheduleItems.scheduler[key]] : scheduleItems.scheduler[key]
                    runOnce(scheduleTime, triggerAdditionalAction, [overwrite: false, data: schedulerDetails])
                }
            }
        }
    }
    
    logMsg.push("\nAFTER items:\n${items}")
    atomicState.item = items
    logDebug("${logMsg}")
}

def didVariablesChange(msg, changes) {
    def logMsg = ["didVariablesChange - msg: ${msg}, changes: ${changes}"]
    def answer = false
    def variableNames = gatherVariableNames(msg)
    logMsg.push("variableNames : ${variableNames}")
    for (int i = 0; i < changes.size(); i++) {
        if (changes[i] == "newItem") {
            answer = true
            break
        }
        
        if (variableNames.indexOf(changes[i]) > -1) {
            answer = true
        }
    }
    
    logMsg.push("returning : ${answer}")
    logDebug("${logMsg}")
    //log.trace "${logMsg}"
    return answer
}

def triggerAdditionalAction(ArrayList data=[]) {
    def logMsg = ["triggerAdditionalAction - data: ${data}"]
    def items = atomicState.item
    for (int i = 0; i < data.size(); i++) {
        def actionName = data[i].actionName
        def itemID = data[i].id
        def item
        if (settings.searchType == "Calendar Event") {
            item = items.find{it.eventID == itemID}
        } else if (settings.searchType == "Gmail") {
            item = items.find{it.messageID == itemID}
        } else {
            item = items.find{it.taskID == itemID}
        }
        def itemIndex = items.indexOf(item)
        logMsg.push("\nactionName: ${actionName}, item BEFORE: ${item}")
        
        switch (actionName) {
            case "triggerVariableUpdate":
                triggerVariableUpdate(itemID)
                break
            case "triggerSwitchControl":
                triggerSwitchControl(itemID)
                break
            case "triggerReminderNotification":
                triggerReminderNotification(itemID)
                break
            case "triggerStartNotification":
                triggerStartNotification(itemID)
                break
            case "triggerEndNotification":
                triggerEndNotification(itemID)
                break
            case "triggerStartRule":
                triggerStartRule(itemID)
                break
            case "triggerEndRule":
                triggerEndRule(itemID)
                break
        }
        
        if (item != null) {
            if (item.additionalActions == null) {
                item.additionalActions = [:]
            }

            if (actionName == "triggerSwitchControl") {
                if (item.additionalActions[actionName] instanceof HashMap) {
                    item.additionalActions[actionName].status = "processed"
                } else {
                    def triggerSwitchControl = [:]
                    triggerSwitchControl.status = "processed"
                    item.additionalActions[actionName] = triggerSwitchControl
                }
            } else if (actionName == "triggerVariableUpdate") {
                if (item.additionalActions[actionName] instanceof HashMap) {
                    item.additionalActions[actionName].status = "processed"
                } else {
                    def triggerVariableUpdate = [:]
                    triggerVariableUpdate.status = "processed"
                    item.additionalActions[actionName] = triggerVariableUpdate
                }
            } else {
                item.additionalActions[actionName] = "processed"
            }

            logMsg.push("\nitem AFTER: ${item}")
            items[itemIndex] = item
        }
    }
    
    logMsg.push("\nAFTER items:\n${items}")
    atomicState.item = items
    logDebug("${logMsg}")
}

def gatherFieldMappings(item) {
    def logMsg = ["gatherFieldMappings - item: ${item}"]
    def answer = [:]
    def matchFieldName = settings.fieldToParse
    def matchFieldValue
    switch (matchFieldName) {
        case "location":
            matchFieldValue = item.eventLocation
            break
        case "description":
            matchFieldValue = item.eventDescriptionRaw
            break
        case "body":
            matchFieldValue = item.messageBodyRaw
            break
        default:
            matchFieldValue = (settings.searchType == "Calendar Event") ? item.eventTitle : (settings.searchType == "Gmail") ? item.messageTitle: item.taskTitle
    }
    matchFieldValue = matchFieldValue.toString()
    logMsg.push("matchFieldValue before: ${matchFieldValue}")
    
    if (matchFieldValue != "" && matchFieldValue != "none") {
        def parseMappings = atomicState.parseMappings
        def parseMap = [:]
        for (int i = 0; i < parseMappings.size(); i++) {
            def parseMapping = parseMappings[i]
            parseMap[parseMapping.text] = [
                variable: parseMapping.variable,
                location: parseMapping.location
            ]
        }
        
        def parseTextList = parseMap.keySet()
        def matchFieldSplit = matchFieldValue.split("\n")
        for (int m = 0; m < matchFieldSplit.size(); m++) {
            def matchFieldLine = matchFieldSplit[m]
            for (int p = 0; p < parseTextList.size(); p++) {
                def parseText = parseTextList[p]
                if (parseText != "" && matchFieldLine.startsWith(parseText)) {
                    def varName = parseMap[parseText].variable
                    def varLocation = parseMap[parseText].location
                    def varValue
                    if (varLocation == "NextLine") {
                        varValue = matchFieldSplit[m+1]
                    } else {
                        varValue = matchFieldLine.replace(parseText, "").trim()
                    }
                    answer[varName] = varValue
                }
            }
        }
    }
    
    logMsg.push("answer: ${answer}")
    logDebug("${logMsg}")
    return answer
}

def gatherControlSwitches(item) {
    def logMsg = ["gatherControlSwitches - item: ${item}"]
    def answer = [:]
    def separators = [",", ";"]
    def ignoreChars = ["'", "’", "’"]
    def triggerWords = ["on", "off"]
    def matchCommands = []
    def pattern
    def matchFieldName = settings.itemField
    def matchFieldValue
    switch (matchFieldName) {
        case "location":
            matchFieldValue = item.eventLocation
            break
        case "description":
            matchFieldValue = item.eventDescription
            break
        case "body":
            matchFieldValue = item.messageBody
            break
        default:
            matchFieldValue = (settings.searchType == "Calendar Event") ? item.eventTitle : (settings.searchType == "Gmail") ? item.messageTitle: item.taskTitle
    }
    matchFieldValue = matchFieldValue.toString().toLowerCase()
    logMsg.push("matchFieldValue before: ${matchFieldValue}")
    
    //Remove ignore verbs and chars from matchFieldValue
    def ignoreVerbs = settings.ignoreWords.split(",")
    for (int i = 0; i < ignoreVerbs.size(); i++) {
        def ignoreVerb = ignoreVerbs[i].trim()
        pattern = /\b${ignoreVerb}\b/

        def ignoreWords = (matchFieldValue =~ pattern)
        if (ignoreWords.find()) {
            matchFieldValue = matchFieldValue.replaceAll(pattern, "")
        }
    }
    for (int i = 0; i < ignoreChars.size(); i++) {
        def ignoreChar = ignoreChars[i]
        matchFieldValue = matchFieldValue.replaceAll(ignoreChar, "")
    }
    
    //Change user defined conjunction words to commas
    def userConjunctionWords = settings.conjunctionWords.split(",")
    for (int i = 0; i < userConjunctionWords.size(); i++) {
        def conjunctionWord = userConjunctionWords[i].trim()
        pattern = /\b${conjunctionWord}\b/

        def conjunctionWords = (matchFieldValue =~ pattern)
        if (conjunctionWords.find()) {
            matchFieldValue = matchFieldValue.replaceAll(pattern, ", ")
        }
    }
    
    //Change user defined trigger words to on/off
    def userTriggerWord = (settings.onTranslation) ? settings.onTranslation.trim() : "on"
    pattern = /\b${userTriggerWord}\b/
    def userTriggerWords = (matchFieldValue =~ pattern)
    if (userTriggerWords.find()) {
        matchFieldValue = matchFieldValue.replaceAll(pattern, "on")
    }
    
    userTriggerWord = (settings.offTranslation) ? settings.offTranslation.trim() : "off"
    pattern = /\b${userTriggerWord}\b/
    userTriggerWords = (matchFieldValue =~ pattern)
    if (userTriggerWords.find()) {
        matchFieldValue = matchFieldValue.replaceAll(pattern, "off")
    }
    
    matchFieldValue = matchFieldValue.trim()
    logMsg.push("matchFieldValue after: ${matchFieldValue}")
    
    for (int i = 0; i < triggerWords.size(); i++) {
        def triggerWord = triggerWords[i]
        pattern = /\b${triggerWord}\b/
        def triggerMatches = (matchFieldValue =~ pattern)
        
        while (triggerMatches.find()) {
            def commandDetails = [:]
            commandDetails.index = triggerMatches.start()
            commandDetails.word = triggerWord
            matchCommands.push(commandDetails)
        }
    }
    
    if (matchCommands.size() > 0) {
        matchCommands.sort{it.index}
        logMsg.push("matchCommands: ${matchCommands}")
        
        def matchSwitchList = [:]
        for (int m = 0; m < matchCommands.size(); m++) {
            def matchedCommand = matchCommands[m].word
            def matchedIndex = matchCommands[m].index
            def tempMatchFieldValue
            def nextMatchedCommand = matchCommands[m+1]
            if (nextMatchedCommand) {
                tempMatchFieldValue = matchFieldValue.substring(matchedIndex + matchedCommand.length(), nextMatchedCommand.index - nextMatchedCommand.word.length() + 1).trim()
            } else {
                tempMatchFieldValue = matchFieldValue.substring(matchedIndex + matchedCommand.length()).trim()
            }
            
            //Separate multiple switches            
            def tempMatchFieldList = [tempMatchFieldValue]
            logMsg.push("tempMatchFieldList before separation ${tempMatchFieldList}, matchedCommand: ${matchedCommand}")
            for (int s = 0; s < separators.size(); s++) {
                def separator = separators[s]
                def tempList = tempMatchFieldList
                for (int i = 0; i < tempMatchFieldList.size(); i++) {
                    def matchSwitch = tempMatchFieldList[i]
                    if (matchSwitch.indexOf(separator) > -1) {
                        tempList.remove(tempList.indexOf(matchSwitch))
                        tempList.addAll(matchSwitch.split(separator))
                    }
                }

                tempMatchFieldList = tempList
            }
            
            if (matchSwitchList[matchedCommand]) {
                matchSwitchList[matchedCommand].addAll(tempMatchFieldList)
            } else {
                matchSwitchList[matchedCommand] = tempMatchFieldList
            }
        }
        logMsg.push("matchSwitchList: ${matchSwitchList}")

        def switchList = []
        settings.controlSwitchList.each {
            def switchDetail = [:]
            switchDetail.id = it.id

            def displayName = it.displayName
            for (int i = 0; i < ignoreChars.size(); i++) {
                def ignoreChar = ignoreChars[i]
                displayName = displayName.replaceAll(ignoreChar, "")
            }
            switchDetail.name = displayName
            switchList.push(switchDetail)
        }
        logMsg.push("switchList: ${switchList}")

        if (matchSwitchList.on && matchSwitchList.on.size() > 0) {
            matchSwitchList.on = matchItem(switchList, false, matchSwitchList.on, "name")
        }

        if (matchSwitchList.off && matchSwitchList.off.size() > 0) {
            matchSwitchList.off = matchItem(switchList, false, matchSwitchList.off, "name")
        }
        
        answer = matchSwitchList
    } else {
        logMsg.push("no matched commands")
    }
    
    logMsg.push("answer: ${answer}")
    logDebug("${logMsg}")
    return answer
}

def triggerVariableUpdate(itemID) {
    def items = atomicState.item
    def item
    if (settings.searchType == "Calendar Event") {
        item = items.find{it.eventID == itemID}
    } else if (settings.searchType == "Gmail") {
        item = items.find{it.messageID == itemID}
    } else {
        item = items.find{it.taskID == itemID}
    }
    
    def logMsg = ["triggerSwitchControl - itemID: ${itemID}, item: ${item}"]
    def logInfoMsg = []
    
    if (item.containsKey("additionalActions") && item.additionalActions.containsKey("triggerVariableUpdate") && !item.additionalActions.triggerVariableUpdate.variableUpdates.isEmpty()) {
        def variableList = []
        def variableUpdates = item.additionalActions.triggerVariableUpdate.variableUpdates
        def variableNameList = variableUpdates.keySet()
        for (int i = 0; i < variableNameList.size(); i++) {
            def variableName = variableNameList[i]
            def variableValue = variableUpdates[variableName]
            setGlobalVar(variableName, variableValue)
            logMsg.push("Hub Variable ${variableName} set to ${variableValue}")
            logInfoMsg.push("${variableName} set to ${variableValue}")
        }
    }
    
    logMsg.push("${settings.searchType} completed: ${itemCompleted}")
    logDebug("${logMsg}")
    logInfo("Hub Variable Updates - " + logInfoMsg.join(", "))
}

def triggerSwitchControl(itemID) {
    def items = atomicState.item
    def item
    if (settings.searchType == "Calendar Event") {
        item = items.find{it.eventID == itemID}
    } else if (settings.searchType == "Gmail") {
        item = items.find{it.messageID == itemID}
    } else {
        item = items.find{it.taskID == itemID}
    }
    
    def matchSwitches = [:]
    if (item.containsKey("additionalActions") && item.additionalActions.containsKey("triggerSwitchControl") && !item.additionalActions.triggerSwitchControl.matchSwitches.isEmpty()) {
        matchSwitches = item.additionalActions.triggerSwitchControl.matchSwitches
    }
    def logMsg = ["triggerSwitchControl - itemID: ${itemID}, item: ${item}, matchSwitches: ${matchSwitches}"]
    def logInfoMsg = []
    if (matchSwitches.on) {
        def onSwitches = []
        for (int i = 0; i < matchSwitches.on.size(); i++) {
            def device = settings.controlSwitchList?.find{it.id == matchSwitches.on[i].id}
            logMsg.push("turning on ${device.name}")
            onSwitches.push(device.name)
            device?.on()
        }
        if (onSwitches.size() > 0) {
            logInfoMsg.push("turning on: " + onSwitches.join(", "))
        }
    }
    if (matchSwitches.off) {
        def offSwitches = []
        for (int i = 0; i < matchSwitches.off.size(); i++) {
            def device = settings.controlSwitchList?.find{it.id == matchSwitches.off[i].id}
            logMsg.push("turning off ${device.name}")
            offSwitches.push(device.name)
            device?.off()
        }
        if (offSwitches.size() > 0) {
            logInfoMsg.push("turning off: " + offSwitches.join(", "))
        }
    }
    def itemCompleted = completeItem()
    if (itemCompleted) {
        getNextItems()
        logInfoMsg.push("${settings.searchType.toLowerCase()} completed")
    }
    
    logMsg.push("${settings.searchType} completed: ${itemCompleted}")
    logDebug("${logMsg}")
    logInfo("Toggling Switches - " + logInfoMsg.join(", "))
}

def matchItem(items, caseSensitive, searchTerms, searchField) {
    def logMsg = ["matchItem - caseSensitive: ${caseSensitive}, searchTerms: ${searchTerms}, searchField: ${searchField}, items: ${items}"]
    def tempItems = []
    if (searchTerms instanceof String) {
        if (caseSensitive == false) {
            searchTerms = searchTerms.toLowerCase()
        }
        searchTerms = searchTerms.split(",")
    }
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

def triggerReminderNotification(itemID) {
    def msg = settings.notificationReminderMsg
    composeNotification("Reminder Notification", msg, itemID)
}

def triggerStartNotification(itemID) {
    def msg = settings.notificationStartMsg
    composeNotification("Start Notification", msg, itemID)
}

def triggerEndNotification(itemID) {
    if (settings.sendNotification != true || settings.notificationEndMsg == null || (settings.notificationDevices == null && settings.speechDevices == null)) {
        return
    }
    
    def msg = settings.notificationEndMsg
    composeNotification("End Notification", msg, itemID)
}

def composeNotification(fromFunction, msg, itemID) {
    def logInfoMsg = []
    if (msg.indexOf("%") > -1) {
        def items = atomicState.item
        if (itemID) {
            def tempItem
            if (settings.searchType == "Calendar Event") {
                tempItem = items.find{it.eventID == itemID}
            } else if (settings.searchType == "Gmail") {
                tempItem = items.find{it.messageID == itemID}
            } else {
                tempItem = items.find{it.taskID == itemID}
            }
            def itemIndex = items.indexOf(tempItem)
            
            def tempItems = []
            for (int t = itemIndex; t < items.size(); t++) {
                tempItems.push(items[t])
            }
            items = tempItems
        }
        if (settings.includeAllItems == false) {
            items = [items[0]]
        }
        
        def variableNames = gatherVariableNames(msg)
        def msgList = []
        for (int i = 0; i < items.size(); i++) {
            def tempMsg = msg
            def item = items[i]
            for (int v = 0; v < variableNames.size(); v++) {
                def variableName = variableNames[v]
                def value
                switch (variableName) {
                    case "now":
                        value = new Date()
                        break
                    case "onSwitches":
                        value = gatherSwitchNames(item, "on")
                        break
                    case "offSwitches":
                        value = gatherSwitchNames(item, "off")
                        break
                    case "variableUpdates":
                        value = gatherVariableUpdates(item)
                        break
                    default:
                        value = item[variableName].toString()
                }
                if (value != "null" && ["now", "eventStartTime", "eventEndTime", "taskDueDate", "scheduleStartTime", "scheduleEndTime", "messageReceived"].indexOf(variableName) > -1) {
                    value = formatDateTime(value)
                }
                
                def textMatch = "%" + variableName + "%"
                tempMsg = (value == "null") ? tempMsg.replace(textMatch, "") : tempMsg.replace(textMatch, value)
            }
            if (tempMsg.trim()) {
                msgList.push(tempMsg)
            }
        }
        msg = msgList.join(", ")
    }
    
    logDebug("composeNotification fromFunction: ${fromFunction}, msg: ${msg}")
    logInfoMsg.push("${fromFunction}, Sending Message: " + msg + " to ")
    if (notificationDevices) {
        notificationDevices.deviceNotification(msg)
        logInfoMsg.push(notificationDevices)
    }
    if (speechDevices) {
        speechDevices.speak(msg)
        logInfoMsg.push(speechDevices)
    }
    logInfo("${logInfoMsg.join(" ")}")
}

def gatherSwitchNames(item, key) {
    def answer = "none"
    if (item.containsKey("additionalActions") && item.additionalActions.containsKey("triggerSwitchControl") && !item.additionalActions.triggerSwitchControl.matchSwitches.isEmpty() && item.additionalActions.triggerSwitchControl.matchSwitches[key]) {
        def switchList = []
        for (int i = 0; i < item.additionalActions.triggerSwitchControl.matchSwitches[key].size(); i++) {
            switchList.push(item.additionalActions.triggerSwitchControl.matchSwitches[key][i].name)
        }
        answer = "${switchList.join(", ")}"
    }
    
    return answer
}

def gatherVariableUpdates(item) {
    def answer = "none"
    if (item.containsKey("additionalActions") && item.additionalActions.containsKey("triggerVariableUpdate") && !item.additionalActions.triggerVariableUpdate.variableUpdates.isEmpty()) {
        def variableList = []
        def variableUpdates = item.additionalActions.triggerVariableUpdate.variableUpdates
        def variableNameList = variableUpdates.keySet()
        for (int i = 0; i < variableNameList.size(); i++) {
            def variableName = variableNameList[i]
            variableList.push(variableName + ": " + variableUpdates[variableName])
        }
        answer = "${variableList.join(", ")}"
    }
    
    return answer
}

def gatherVariableNames(msg) {
    def answer = []
    if (msg.indexOf("%") > -1) {
        def pattern = /(?<=%).*?(?=%)/
        def matches = (msg =~ pattern).findAll()
        for (int i = 0; i < matches.size(); i++) {
            def match = matches[i].trim()
            def textMatch = "%" + match + "%"
            if (msg.indexOf(textMatch) > -1) {
                answer.push(match)
                msg = msg.replace(textMatch, "")
            } else {
                msg = msg.replace(match, "")
            }
        }
    }
    
    //logDebug("gatherVariableNames - answer: ${answer}")
    return answer
}

def triggerStartRule(itemID) {
    if (settings.searchType == "Calendar Event" && settings.updateRuleBoolean == true) {
        runRMAPI("setRuleBooleanTrue")
    }
    runRMAPI("runRuleAct")
}

def triggerEndRule(itemID) {
    if (settings.runRuleActions != true || (settings.legacyRule == null && settings.currentRule == null)) {
        return
    }
    
    if (settings.searchType == "Calendar Event" && settings.updateRuleBoolean == true) {
        runRMAPI("setRuleBooleanFalse")
    }
    runRMAPI("runRuleAct")
}

def runRMAPI(action) {
    def logInfoMsg = []
    if (settings.legacyRule) {
        logInfoMsg.push(settings.legacyRule)
        RMUtils.sendAction(settings.legacyRule, action, app.label)
    }
    
    if (settings.currentRule) {
        logInfoMsg.push(settings.currentRule)
        RMUtils.sendAction(settings.currentRule, action, app.label, "5.0")
    }
    logInfo("Running Rules: " + logInfoMsg.join(", "))
}

def compareItem(items) {
    def answer = true
    def previousItems = atomicState.item
    if (previousItems == null || items.size() != previousItems.size()) return false
    
    for (int i = 0; i < items.size(); i++) {
        def item = items[i]
        def previousItem = previousItems[i]
        def itemKeys = item.keySet()
        for (int k = 0; k < itemKeys.size(); k++) {
            def key = itemKeys[k]
            if (["scheduleStartTime", "scheduleEndTime", "additionalActions"].indexOf(key) > -1) {
                continue
            }

            def newValue = item[key]
            def oldValue = previousItem[key]
            if (newValue instanceof Date) {
                newValue = formatDateTime(newValue)
                oldValue = formatDateTime(oldValue)
            }

            if (newValue != oldValue) {
                answer = false
                break
            }
        }
    }
    
    return answer
}

def compare2Items(current, previous) {
    def logMsg = ["compare2Items:\ncurrent item: ${current}\nprevious item: ${previous}"]
    
    def answer = [
        same: false,
        allProcessed: false,
        processed: [],
        scheduled: [],
        changes: []
    ]
    
    if (current != null && previous != null) {
        def compareValues = []
        def itemKeys = current.keySet()
        for (int k = 0; k < itemKeys.size(); k++) {
            def key = itemKeys[k]
            if (["scheduleStartTime", "scheduleEndTime", "additionalActions"].indexOf(key) > -1) {
                continue
            }

            def currentValue = current[key]
            def previousValue = previous[key]
            if (currentValue instanceof Date) {
                currentValue = formatDateTime(currentValue)
                previousValue = formatDateTime(previousValue)
            }
            
            def comparison = currentValue == previousValue
            compareValues.push(comparison)
            if (comparison == false) {
                logMsg.push("Difference: ${key} - currentValue(${currentValue}) != previousValue(${previousValue})")
                answer.changes.push(key)
            }
        }
        if (compareValues.toString().indexOf("false") == -1) {
            answer.same = true
        }
        
        compareValues = []
        if (previous.containsKey("additionalActions")) {
            itemKeys = previous.additionalActions.keySet()
            
            for (int k = 0; k < itemKeys.size(); k++) {
                def key = itemKeys[k]
                def value = (["triggerSwitchControl", "triggerVariableUpdate"].indexOf(key) > -1) ? previous.additionalActions[key].status : previous.additionalActions[key]
                if (value == "processed") {
                    compareValues.push(true)
                    answer.processed.push(key)
                } else if (value == "scheduled") {
                    compareValues.push(false)
                    answer.scheduled.push(key)
                } else {
                    compareValues.push(false)
                }
            }

            if (compareValues.toString().indexOf("false") == -1) {
                answer.allProcessed = true
            }
        }
    } else {
        answer.changes.push("newItem")
    }
    
    logMsg.push("returning : ${answer}")
    logDebug("${logMsg}")
    return answer
}

def formatDateTime(dateTime) {
    if (dateTime == null || dateTime == " ") {
        return
    }
    
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
    dateTime = parseDateTime(dateTime)
    
    return sdf.format(dateTime)
}

def parseDateTime(dateTime) {
    if (dateTime instanceof String) {
        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        dateTime = sdf.parse(dateTime)
    }
    
    return dateTime
}

def syncChildSwitches(value) {
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
private dVersion() {
	def text = "Device Version: ${getChildDevices()[0].version()}"
}

def appButtonHandler(btn) {
    if (btn.startsWith("mapping")) {
        def rowData = btn.replace("mapping", "")
        def slurper = new JsonSlurper()
        rowData = slurper.parseText(rowData)
        def parseMappings = (atomicState.parseMappings) ?:[]
        parseMappings[rowData.row] = [
            text: rowData.text,
            location: rowData.location,
            variable: rowData.variable
        ]
        atomicState.parseMappings = parseMappings
        return
    }
    
    if (btn.startsWith("deleteRow")) {
        def rowNumber = btn.replace("deleteRow", "")
        rowNumber = rowNumber.toInteger()
        def parseMappings = atomicState.parseMappings
        parseMappings.remove(rowNumber)
        atomicState.parseMappings = parseMappings
        return
    }
    
    switch(btn) {
        case "pauseButton":
			atomicState.isPaused = true
            break
		case "resumeButton":
			atomicState.isPaused = false
			break
        case "refreshButton":
		    poll()
			return
        case "addRow":
            def parseMappings = atomicState.parseMappings
            parseMappings.push([text:"",location:"SameLine",variable:"None"])
            atomicState.parseMappings = parseMappings
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

private logInfo(msg) {
    if (settings.txtEnable == true && (settings.sendNotification == true || settings.runRuleActions == true || settings.controlSwitches == true)) {
        log.info "$msg"
    }
}

def upgradeSettings() {
    def upgraded = false
    if (settings.watchCalendars && settings.watchCalendars != null) {
        app.updateSetting("watchList", [value:"${settings.watchCalendars}", type:"enum"])
        app.removeSetting("watchCalendars")
        app.updateSetting("searchType", [value:"Calendar Event", type:"enum"])
        upgraded = true
    }
    
    if (state.refreshed && state.refreshed.toString().indexOf(" ") > -1) {
        atomicState.refreshed = parent.getCurrentTime()
        upgraded = true
    }
    
    if (state.item && state.item instanceof HashMap) {
        //upgrade state.item to an array
        atomicState.item = [atomicState.item]
        upgraded = true
    }
    
    if (state.containsKey("matchSwitches")) {
        app.removeSetting("matchSwitches")
        upgraded = true
    }
    
    if (upgraded) {
        log.info "Upgraded ${app.label} settings"
    }
}
