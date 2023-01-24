# Realm & Atlas Device Sync Demo App

This repo serve as a source code for SA’s wanting to deliver a demo around Realm and Atlas Device Sync. The scope of the demo includes the ability to keep user data automatically synchronized between intermittently internet-connected devices and a central database (Atlas) using Atlas Device Sync.

__SA Maintainer__: [Aicha Sarr](mailto:aicha.sarr@mongodb.com) & [Younes Berrada](mailto:younes.berrada@mongodb.com) <br/>

---
## Summary

This demo leverages the Realm Kotlin SDK and is essentially based on the Kotlin Flexible Sync Template App, named kotlin.todo.flex, which illustrates the creation of a Todo Item List management application. This application enables users to view, create, modify, and delete todo items.

The demo uses the new Flexible Sync subscription to only show items within a range of priorities for the user (based on _id, owner_id, and priority fields) and adds functionality to the Template App. We have added a new ‘priority’ field to the existing Item model and updated the Flexible Sync subscription to only show items within a range of priorities depending on the connected user. User1 will sync all the tasks and User2 only tasks with priority Severe or High.

To make life easier for SA, there is no need to download and install android studio on your local environment and use an emulator. We have used online tools to build the artifacts and run the mobile application directly in a virtual device on your browser. These tools are respectively AppCircle and Appetize.


Appcircle is an easy-to-use mobile CI/CD platform that manages mobile app lifecycle end-to-end. You can build your iOS, Android, React Native, Flutter apps within seconds, run tests and distribute to tester groups and App Stores.

Appetize.io enables you to run native iOS and Android mobile apps directly in your browser. No downloads, plugins, or extra permissions needed.


Please use [document](https://docs.google.com/document/d/1ldX4c4JLs36pfnIqK8j9Tblx561QSevy85ZXilnMd5E/edit#heading=h.y6269l79uwg) for the demo script, setup and execution.