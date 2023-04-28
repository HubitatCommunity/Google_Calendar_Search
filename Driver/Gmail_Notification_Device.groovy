def driverVersion() { return "4.1.0" }
/**
 *  Gmail Notification Device Driver
 *  https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Driver/Gmail_Notification_Device.groovy
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
	definition (name: "Gmail Notification Device", namespace: "HubitatCommunity", author: "ritchierich") {
		capability "Notification"
	}
    
    attribute "lastMessage", "string"
    
    preferences {
		input name: "toEmail", type: "text", title: "Email address:", description: "Default email address to send email to", required: true
        input name: "toSubject", type: "text", title: "Email subject:", description: "Default email subject", required: true
        input name: "fromDisplayName", type: "text", title: "From Display Name:", description: "Default display name for email sender", required: false
        input name: "enableNofications", type: "bool", title: "Enable Notifications?", defaultValue: true, required: false
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
    
}

def parse(String description) {

}

def deviceNotification(message) {
    if (settings.enableNofications == null || settings.enableNofications == true) {
        parent.sendMessage(settings.toEmail, settings.fromDisplayName, settings.toSubject, message)
        sendEvent(name: "lastMessage", value: "${message}", descriptionText: "Sent to ${settings.toEmail}")
        logInfo(message + " sent to " + settings.toEmail)
        logDebug(message + " sent to " + settings.toEmail)
    } else {
        logDebug("Device ${settings.toEmail} disabled: ${message}")
    }
}

def getPreferenceValues() {
    def answer = [:]
    answer.toEmail = settings.toEmail
    answer.toSubject = settings.toSubject
    return answer
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
