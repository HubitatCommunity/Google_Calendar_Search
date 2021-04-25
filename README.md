# Google_Calendar_Search
Hubitat Google Calendar Search

For discussion and more information, visit the <a href="https://community.hubitat.com/t/release-google-calendar-search/71397">[RELEASE] Google Calendar Search</a>.

## To Install
1. Back up your hub and save a local copy before proceeding.
2. Install the apps from the "Apps" folder in this repository into the "Apps Code" section of Hubitat:
  * https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Apps/GCal_Search.groovy
  * https://github.com/HubitatCommunity/Google_Calendar_Search/blob/main/Apps/GCal_Search_Trigger.groovy
3. Install the driver from the "Driver" folder in this repository into the "Drivers Code" section of Hubitat:
  * https://raw.githubusercontent.com/HubitatCommunity/Google_Calendar_Search/main/Driver/GCal_Switch.groovy    
4. Follow the instructions below to setup your Google Auth
5. Install an instance of app: go to **Apps > Add User App**, choose **GCal Search**, and follow the prompts.

## Feature Documentation
1. Login to the Google Cloud Console: https://console.cloud.google.com
2. In the left navigation, click APIs & Services, and then choose Create Project on the right
![image](https://user-images.githubusercontent.com/10900324/115976568-44281e00-a53d-11eb-9d7e-03689c5bb3ac.png)
3. Give your project a name and click Create
![image](https://user-images.githubusercontent.com/10900324/115976609-a4b75b00-a53d-11eb-860e-a99b74d2175a.png)
4. In the left navigation, click Credentials
5. At the top, click + Create Credentials and choose OAuth client ID
![image](https://user-images.githubusercontent.com/10900324/115976721-e1378680-a53e-11eb-8c4b-88cfd55022cb.png)
6. Set Application type to Desktop app, set a Name, and click Create
![image](https://user-images.githubusercontent.com/10900324/115976744-0af0ad80-a53f-11eb-99d2-fbeac0d2cd3e.png)
7. In the OAuth client created popup, copy the Client ID and Client Secret into your favorite text editor to use in the GCal Search HE app and click OK
![image](https://user-images.githubusercontent.com/10900324/115976760-3d020f80-a53f-11eb-8b5e-85f749ccb395.png)
8. In the top blue search, enter 'calendar' and choose Google Calendar API under Marketplace
![image](https://user-images.githubusercontent.com/10900324/115977025-b569d000-a541-11eb-859a-410082044a67.png)
9. Click Enable
![image](https://user-images.githubusercontent.com/10900324/115976840-037dd400-a540-11eb-9cd9-83156851f8ed.png)
10. test
11. In the left navigation, click OAuth consent screen
12. ![image](https://user-images.githubusercontent.com/10900324/115976626-d7f9ea00-a53d-11eb-8212-66129f4a3dbb.png)
   Choose External and click Create
12. ![image](https://user-images.githubusercontent.com/10900324/115976691-6cfce300-a53e-11eb-881b-5e996868c97a.png)
   Set an App name, User support email, and Developer contact information and click Save and Continue
