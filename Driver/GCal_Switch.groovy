/**
 *  GCal Switch Driver v1.2.0
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

def driverVersion() { return "1.2.0" }

metadata {
	definition (name: "GCal Switch", namespace: "HubitatCommunity", author: "ritchierich") {
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        
        attribute "lastUpdated", "string"
        attribute "eventTitle", "string"
        attribute "eventLocation", "string"
        attribute "eventStartTime", "string"
        attribute "eventEndTime", "string"
        attribute "eventAllDay", "bool"
        
        command "clearEventCache"
	}
    
    preferences {
		input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
        input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, options:["on","off"]
        input name: "offsetStart", type: "decimal", title: "Event Start Offset in minutes (+/-)", required: false
        input name: "offsetEnd", type: "decimal", title: "Event End Offset in minutes (+/-)", required: false
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

    def nowDateTime = new Date()
    def currentValue = device.currentSwitch
    def defaultValue = determineSwitch(false)
    def toggleValue = determineSwitch(true)
    logMsg.push("poll - BEFORE nowDateTime: ${nowDateTime}, currentValue: ${currentValue} AFTER ")
    
    def result = []
    result << sendEvent(name: "lastUpdated", value: nowDateTime, displayed: false)
    def item = parent.getNextEvents()
    if (item && item.eventTitle) {
        logMsg.push("event found, item: ${item}")
        
        result << sendEvent(name: "eventTitle", value: item.eventTitle )
        result << sendEvent(name: "eventLocation", value: item.eventLocation )
        result << sendEvent(name: "eventAllDay", value: item.eventAllDay )
        result << sendEvent(name: "eventStartTime", value: item.eventStartTime )
        result << sendEvent(name: "eventEndTime", value: item.eventEndTime )
        
        def eventStartTime = new Date(item.eventStartTime.getTime())
        if (settings.offsetStart != null && settings.offsetStart != "") {
            def origStartTime = new Date(item.eventStartTime.getTime())
            int offsetStart = settings.offsetStart.toInteger()
            def tempStartTime = eventStartTime.getTime()
            tempStartTime = tempStartTime + (offsetStart * 60 * 1000)
            eventStartTime.setTime(tempStartTime)
            logMsg.push("Event start offset: ${settings.offsetStart}, adjusting time from ${origStartTime} to ${eventStartTime}")
        }
        
        def eventEndTime = new Date(item.eventEndTime.getTime())
        if (settings.offsetEnd != null && settings.offsetEnd != "") {
            def origEndTime = new Date(item.eventEndTime.getTime())
            int offsetEnd = settings.offsetEnd.toInteger()
            def tempEndTime = eventEndTime.getTime()
            tempEndTime = tempEndTime + (offsetEnd * 60 * 1000)
            eventEndTime.setTime(tempEndTime)
            logMsg.push("Event end offset: ${settings.offsetEnd}, adjusting time from ${origEndTime} to ${eventEndTime}")
        }
        
        logMsg.push("nowDateTime(${nowDateTime}) < eventStartTime(${eventStartTime})")
        if (nowDateTime < eventStartTime) {
            scheduleSwitch(toggleValue, eventStartTime)
            scheduleSwitch(defaultValue, eventEndTime)
            logMsg.push("Scheduling ${toggleValue} at ${eventStartTime} and ${defaultValue} at ${eventEndTime}")
            if (currentValue != defaultValue) {
                logMsg.push("Turning ${defaultValue} switch")
                result << sendEvent(name: "switch", value: defaultValue)
            }
        } else {
            scheduleSwitch(defaultValue, eventEndTime)
            logMsg.push("Scheduling ${defaultValue} at ${eventEndTime}")
            if (currentValue != toggleValue) {
                logMsg.push("Turning ${toggleValue} switch")
                result << sendEvent(name: "switch", value: toggleValue)
            }
        }
    } else {
        logMsg.push("no events found, turning ${defaultValue} switch")
        result << sendEvent(name: "eventTitle", value: " ")
        result << sendEvent(name: "eventLocation", value: " ")
        result << sendEvent(name: "eventAllDay", value: " ")
        result << sendEvent(name: "eventStartTime", value: " ")
        result << sendEvent(name: "eventEndTime", value: " ")
        result << sendEvent(name: "switch", value: defaultValue)
    }
    
    logDebug("${logMsg}")
    return result
}

def on() {
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendEvent(name: "switch", value: "off")
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

def clearEventCache() {
    parent.clearEventCache()
}

private logDebug(msg) {
    if (isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
