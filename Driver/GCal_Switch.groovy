def driverVersion() { return "3.4.1" }
/**
 *  GCal Switch Driver
 *  https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Driver/GCal_Switch.groovy
 *
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

metadata {
	definition (name: "GCal Switch", namespace: "HubitatCommunity", author: "ritchierich") {
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        
        attribute "lastUpdated", "string"
        
        //Calendar
        attribute "eventTitle", "string"
        attribute "eventLocation", "string"
        attribute "eventDescription", "string"
        attribute "eventStartTime", "string"
        attribute "eventEndTime", "string"
        attribute "eventAllDay", "bool"
        attribute "eventReminderMin", "number"
        
        //Task and Reminder
        attribute "taskTitle", "string"
        attribute "taskID", "string"
        attribute "taskDueDate", "string"
        attribute "repeat", "string" //Only comes across for Reminders
        
	}
    
    preferences {
		input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
        input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, options:["on","off"]
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging?", defaultValue: false, required: false
    }
}

def installed() {
    initialize()
}

def updated() {
	initialize()
}

def initialize() {
    refresh()
}

def parse(String description) {

}

// refresh status
def refresh() {
    poll()
}

def poll() {
    unschedule()
    def logMsg = []
    def result = []
    
    def syncValue
    def item = parent.getNextItems()
    def nowDateTime = new Date()
    result << sendEvent(name: "lastUpdated", value: parent.formatDateTime(nowDateTime), displayed: false)
    def currentValue = device.currentSwitch
    def defaultValue = determineSwitch(false)
    def toggleValue = determineSwitch(true)
    logMsg.push("poll - BEFORE nowDateTime: ${nowDateTime}, currentValue: ${currentValue} AFTER ")
    logMsg.push("item: ${item}")
    
    if (item) {
        def itemKeys = item.keySet()
        def itemFound = false
        for (int i = 0; i < itemKeys.size(); i++) {
            def key = itemKeys[i]
            def value = item[key]
            
            if (key == "kind") {
                state.kind = (value.indexOf("#") > -1) ? value.split("#")[1] : value
                itemFound = true
                continue
            } else if (["scheduleStartTime", "scheduleEndTime", "recurrenceId", "eventID", "additionalActions"].indexOf(key) > -1) {
                // Don't process these keys until later
                itemFound = true
                continue
            } else if (key == "switch") {
                result << sendEvent(name: key, value: (value == "defaultValue") ? defaultValue : toggleValue )
            } else {
                result << sendEvent(name: key, value: (value instanceof Date) ? parent.formatDateTime(value) : value )
            }
            
        }
        
        if (itemFound) {
            logMsg.push("event found")
            def compareValue = toggleValue
            
            logMsg.push("nowDateTime(${nowDateTime}) < item.scheduleStartTime(${item.scheduleStartTime})")
            if (item.scheduleStartTime && nowDateTime < item.scheduleStartTime) {
                scheduleSwitch(toggleValue, item.scheduleStartTime)
                if (item.scheduleEndTime) {
                    scheduleSwitch(defaultValue, item.scheduleEndTime)
                }
                logMsg.push("Scheduling ${toggleValue} at ${item.scheduleStartTime} and ${defaultValue} at ${item.scheduleEndTime}")
                compareValue = defaultValue
            } else if (item.scheduleEndTime) {
                scheduleSwitch(defaultValue, item.scheduleEndTime)
                logMsg.push("Scheduling ${defaultValue} at ${item.scheduleEndTime}")
                compareValue = toggleValue
            }
            
            if (currentValue != compareValue) {
                logMsg.push("Turning ${compareValue} switch")
                logInfo(device.name + " is " + compareValue)
                result << sendEvent(name: "switch", value: compareValue)
                syncValue = compareValue
            }
        } else {
            logMsg.push("no events found, turning ${defaultValue} switch")
        }
    }
    
    if (syncValue) {
        logMsg.push("syncing child switch to ${syncValue}")
        syncChildSwitches(syncValue)
    }
    
    logDebug("${logMsg}")
    return result
}

def on() {
    sendEvent(name: "switch", value: "on")
    logInfo(device.name + " is on")
    syncChildSwitches("on")
    updateTask("on")
}

def off() {
    sendEvent(name: "switch", value: "off")
    logInfo(device.name + " is off")
    syncChildSwitches("off")
    updateTask("off")
}

def updateTask(value) {
    if (state.kind != "task" && state.kind != "reminder") {
        return
    }
    
    def taskID = device.currentValue("taskID")
    if (taskID != "" && determineSwitch(true) != value) {
        if (parent.completeItem()) {
            poll()
        } else {
            log.error "task could not be completed"
        }
    }
}

def determineSwitch(hasCurrentEvent) {
    def logMsg = ["determineSwitch - BEFORE hasCurrentEvent: ${hasCurrentEvent}"]
    def currentValue = device.currentSwitch
    def defaultValue = (settings.switchValue == null) ? parent.getDefaultSwitchValue() : settings.switchValue
    def toggleValue = (defaultValue == "on") ? "off" : "on"
    logMsg.push("currentValue: ${currentValue}, defaultValue: ${defaultValue}, toggleValue: ${toggleValue} AFTER ")
    def answer
    
    if (currentValue == null) {
        currentValue = defaultValue
    }
    
    if (hasCurrentEvent) {
        answer = toggleValue
    } else {
        answer = defaultValue
    }
    
    logMsg.push("answer: ${answer}")
    logDebug("${logMsg}")
    
    return answer
}

def scheduleSwitch(type, eventTime) {
    logDebug("scheduleSwitch - scheduling switch ${type} at ${eventTime}")
    
    if (eventTime == null) {
        return
    }
    
    if (type == "on") {
        runOnce(eventTime, scheduleOn)
    } else {
        runOnce(eventTime, scheduleOff)
    }
}

def scheduleOn() {
    logDebug("scheduleOn - turning on switch}")
    on()
}

def scheduleOff() {
    logDebug("scheduleOff - turning off switch}")
    off()
}

def syncChildSwitches(value) {
    parent.syncChildSwitches(value)
}

private logInfo(msg) {
    if (settings.txtEnable == true) {
        log.info "$msg"
    }
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
