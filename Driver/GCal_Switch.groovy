def driverVersion() { return "4.6.4" }
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
        
        //Added to allow immediate refresh on HE Dashboards since it supports buttons
        capability "PushableButton"
        command "push", [[name: "Push invokes Refresh for use on Dashboard NOT REQUIRED", type: "NUMBER", description: ""]]
        
        attribute "lastUpdated", "string"
        
        //Calendar
        attribute "eventTitle", "string"
        attribute "eventID", "string"
        attribute "eventLocation", "string"
        attribute "eventDescription", "string"
        attribute "eventStartTime", "string"
        attribute "eventEndTime", "string"
        attribute "eventAllDay", "bool"
        attribute "eventReminderMin", "number"
        attribute "nextEvent", "string"
        
        //Task
        attribute "taskTitle", "string"
        attribute "taskID", "string"
        attribute "taskDueDate", "string"
        //attribute "repeat", "string" //Only comes across for Reminders
        
        //Gmail
        attribute "messageTitle", "string"
        attribute "messageBody", "string"
        attribute "messageReceived", "string"
        attribute "messageCount", "number"
        
	}
    
    preferences {
        input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, options:["on","off"]
        input name: "useOffsetTimeStamps", type: "bool", title: "Use offset for start and end timestamps?", defaultValue: false, required: false
        input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
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

def push(buttonNumber) {
    poll()
}

def poll() {
    //unschedule()
    def logMsg = []
    def result = []
    
    def item = parent.getNextItems()
    if (item == null || !item instanceof HashMap) {
        return "connectionError"
    } else {
        unschedule()
    }
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
            } else if (["labelIDs", "messageID", "messageTo", "messageFrom", "messageBodyRaw", "eventDescriptionRaw"].indexOf(key) > -1) {
                // Don't process these keys
                continue
            } else if (["scheduleStartTime", "scheduleEndTime", "recurrenceId", "threadID", "additionalActions"].indexOf(key) > -1 && value != " ") {
                // Don't process these keys until later
                itemFound = true
                continue
            } else if (key == "switch") {
                result << sendEvent(name: key, value: (value == "defaultValue") ? defaultValue : toggleValue )
            } else {
                if (key == "eventStartTime" && settings.useOffsetTimeStamps == true && item.scheduleStartTime) value = item.scheduleStartTime
                if (key == "eventEndTime" && settings.useOffsetTimeStamps == true && item.scheduleEndTime) value = item.scheduleEndTime
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
                logInfo(compareValue)
                result << sendEvent(name: "switch", value: compareValue)
            }
        } else {
            logMsg.push("no events found, turning ${defaultValue} switch")
        }
    }
    
    logDebug("${logMsg}")
    return result
}

def on() {
    def value = "on"
    sendEvent(name: "switch", value: value)
    logInfo(value)
    updateTask(value)
}

def off() {
    def value = "off"
    sendEvent(name: "switch", value: value)
    logInfo(value)
    updateTask(value)
}

def updateTask(value) {
    if (state.kind != "task") {
        return
    }
    
    def taskID = device.currentValue("taskID")
    if (taskID != " " && determineSwitch(true) != value) {
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

private logInfo(value) {
    if (settings.txtEnable == true) {
        def deviceName = (device.label == null) ? device.name : device.label
        log.info "${deviceName} is ${value}"
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
