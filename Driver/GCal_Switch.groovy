/**
 *  GCal Switch Driver v1.1
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

def driverVersion() { return "1.1" }

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
    
    // Update lastUpdated date and time
    //def nowDateTime = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    def nowDateTime = new Date()
    sendEvent(name: "lastUpdated", value: nowDateTime, displayed: false)
    logMsg.push("poll - BEFORE nowDateTime: ${nowDateTime}, AFTER ")
    
    def result = []
    def item = parent.getNextEvents()
    if (item && item.eventTitle) {
        logMsg.push("event found, item: ${item}")
        result << sendEvent(name: "eventTitle", value: item.eventTitle )
        result << sendEvent(name: "eventLocation", value: item.eventLocation )
        result << sendEvent(name: "eventAllDay", value: item.eventAllDay )
        result << sendEvent(name: "eventStartTime", value: item.eventStartTime )
        result << sendEvent(name: "eventEndTime", value: item.eventEndTime )
        
        logMsg.push("nowDateTime(${nowDateTime}) < eventStartTime(${item.eventStartTime})")
        if (nowDateTime < item.eventStartTime) {
            scheduleSwitch("on", item.eventStartTime)
            scheduleSwitch("off", item.eventEndTime)
            logMsg.push("Scheduling On at ${item.eventStartTime}, and Off at ${item.eventEndTime}")
        } else {
            determineSwitch(true)
        }
    } else {
        logMsg.push("no events found")
        result << sendEvent(name: "eventTitle", value: " ")
        result << sendEvent(name: "eventLocation", value: " ")
        result << sendEvent(name: "eventAllDay", value: " ")
        result << sendEvent(name: "eventStartTime", value: " ")
        result << sendEvent(name: "eventEndTime", value: " ")
        determineSwitch(false)
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
    def defaultValue = (settings.switchValue == null) ? parent.getDefaultSwitchValue() : settings.switchValue
    def toggleSwitch = (defaultValue == null) ? true : false
    def toggleValue = defaultValue
    def currentValue = device.currentSwitch
    logMsg.push("defaultValue: ${defaultValue}, toggleSwitch: ${toggleSwitch}, toggleValue: ${toggleValue}, currentValue: ${currentValue} AFTER ")
    
    if (currentValue == null) {
        toggleSwitch = true
        toggleValue = defaultValue
    }
    
    if (hasCurrentEvent && currentValue == defaultValue) {
        toggleSwitch = true
        toggleValue = (defaultValue == "on") ? "off" : "on"
    }
    
    if (!hasCurrentEvent && currentValue != defaultValue) {
        toggleSwitch = true
        toggleValue = (defaultValue == "on") ? "on" : "off"
    }
    
    logMsg.push("toggleValue: ${toggleValue}")
    if (toggleSwitch) {
        if (toggleValue == "on") {
            logMsg.push("Turning on switch")
            on()
        } else {
            logMsg.push("Turning off switch")
            off()
        }
    }
    logDebug("${logMsg}")
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
    logDebug("scheduleOn - running determineSwitch")
    determineSwitch(true)
}

def scheduleOff() {
    logDebug("scheduleOff - running determineSwitch")
    determineSwitch(false)
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
