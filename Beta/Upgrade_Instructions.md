# Google_Calendar_Search Upgrade from v2.x to v3.x
For discussion and more information, visit the Hubitat Community <a href="https://community.hubitat.com/t/release-google-calendar-search/71397" target="_blank">[RELEASE] Google Calendar, Task, and Reminder Search</a>.

The steps below are very important but please note that your existing setup should still continue to work however you will not be able to create new or update existing Search triggers until you do.

## Google API Updates
1. Login to the Google Cloud Console: https://console.cloud.google.com
2. In the upper left corner click the "hamburger icon" to open the left navigation, click APIs & Services, and click **Credentials**
   ![image](https://user-images.githubusercontent.com/10900324/151837735-e44554ce-1a65-4de1-b28a-7aec21a6caa5.png)

3. At the top, click **+ Create Credentials** and choose **OAuth client ID**
    ![image](https://user-images.githubusercontent.com/10900324/115976721-e1378680-a53e-11eb-8c4b-88cfd55022cb.png)
4. Set Application type to **Web application**, set a **Name**, set **Authorized redirect URIs** to https://cloud.hubitat.com/oauth/stateredirect
    ![image](https://user-images.githubusercontent.com/10900324/151466429-40365e10-e315-447e-95d0-6da9276600a9.png)
5. Click **Create**
6. In the OAuth client created popup, **copy the Client ID and Client Secret into your favorite text editor** to use in the GCal Search HE app and click **OK**
7. The old "TV and Limited Input/Desktop app" OAuth credential is no longer needed and you should remove it by clicking the trash can at the far right.
8. Optional: This new version extends the search capabilities to Google Tasks and Google Reminders.  If you wish to search Tasks you will need to enable access to the Tasks API (Reminders uses an unofficial API that doesn't need entitlement in the Console).
9. In the top blue search, enter 'tasks api' and choose **Tasks API** under Marketplace
    ![image](https://user-images.githubusercontent.com/10900324/151469694-f62e8531-4b5e-466a-b48c-975421689a86.png)
10. Click **Enable**
11. Be sure you have your Client ID and Client Secret handy to continue setup within your HE hub.  If you ever need to get them again, you can navigate to APIs & Services and then Credentials to view your OAuth credentials.  Clicking the Pencil icon will expose your Client ID and Client Secret.

## Hubitat Installation and Setup
Note: Google Calendar Search is available via <a href="https://community.hubitat.com/t/beta-hubitat-package-manager/38016" target="_blank">Hubitat Package Manager (HPM)</a>. If you originally installed via HPM, skip to Step 4 below.
1. Back up your hub and save a local copy before proceeding.
2. Navigate to Apps Code and update both GCal Search and GCal Search Trigger apps with the new code.
  * GCal Search - **Important**: This app requires you to enable OAuth. Import URL is: https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search.groovy
  * GCal Search Trigger - Import URL is: https://github.com/HubitatCommunity/Google_Calendar_Search/blob/main/Apps/GCal_Search_Trigger.groovy
3. Navigate to Drivers Code and update the GCal Switch driver with the new code.
  * GCal Switch - Import URL is: https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Driver/GCal_Switch.groovy    
4. Navigate to Apps and open the **GCal Search** parent app.
5. Click **Google API Authorization**
6. Click **Reset Google Authentication**
7. Enter the Client ID and Secret copied from Step 6 above
8. Click **Authenticate GCal Search**
9. Follow the prompts in the popup.
10. When prompted that Google hasn't verified this app, click **Advanced**
![image](https://user-images.githubusercontent.com/10900324/115977405-e51ad700-a545-11eb-8d6d-3200e16ec29b.png)
11. In the bottle left click Go to hubitat.com/Hubitat Calendar (or whatever you named your project)
![image](https://user-images.githubusercontent.com/10900324/115977420-1c898380-a546-11eb-84fd-e90d0d481094.png)
12. Choose which Google Apps to entitle access. This needs to match which APIs you enabled access in the Console (except Reminders). Click **Continue**

![image](https://user-images.githubusercontent.com/10900324/151471423-93f96511-f5bc-4024-abd1-5dcce5c4c61f.png)

13. Follow the instructions on the final page and close the popup window.
14. Assuming a successful Google Authentication, a message will appear in HE letting you know and to click Next to continue setup.
15. Click **Next**
16. All existing GCal Search Triggers will be upgraded with the new settings and no additional action is required.
17. If you wish to setup new Google Task or Google Reminder searches, click **New Search...** and follow the instructions.
18. If you have any questions or issues, please post them in the <a href="https://community.hubitat.com/t/release-google-calendar-search/71397" target="_blank">[RELEASE] Google Calendar, Task, and Reminder Search</a> community thread.
