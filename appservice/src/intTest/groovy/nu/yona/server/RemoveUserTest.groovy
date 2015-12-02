package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class RemoveUserTest extends Specification {

	def adminServiceBaseURL = System.properties.'yona.adminservice.url'
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = System.properties.'yona.analysisservice.url'
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	@Shared
	def timestamp = YonaServer.getTimeStamp()

	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinVPNLoginID
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnVPNLoginID
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnRichardBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL
	@Shared
	def bobDunnBuddyRemoveMessageProcessURL

	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser("""{
					"firstName":"Richard ${timestamp}",
					"lastName":"Quin ${timestamp}",
					"nickname":"RQ ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", richardQuinPassword)
			if (response.status == 201) {
				richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
				richardQuinVPNLoginID = response.responseData.vpnProfile.vpnLoginID;

				def confirmationCode = response.responseData.confirmationCode;
				appService.confirmMobileNumber(richardQuinURL, """ { "code":"${confirmationCode}" } """, richardQuinPassword)
			}

		then:
			response.status == 201
			richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)
			richardQuinVPNLoginID

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
			def response = appService.addUser("""{
					"firstName":"Bob ${timestamp}",
					"lastName":"Dunn ${timestamp}",
					"nickname":"BD ${timestamp}",
					"mobileNumber":"+${timestamp}2",
					"devices":[
						"iPhone 6"
					],
					"goals":[
						"gambling", "news"
					]
				}""", bobDunnPassword)
			if (response.status == 201) {
				bobDunnURL = appService.stripQueryString(response.responseData._links.self.href)
				bobDunnVPNLoginID = response.responseData.vpnProfile.vpnLoginID;
				def confirmationCode = response.responseData.confirmationCode;
				appService.confirmMobileNumber(bobDunnURL, """ { "code":"${confirmationCode}" } """, bobDunnPassword)
			}

		then:
			response.status == 201
			bobDunnURL.startsWith(appServiceBaseURL + appService.USERS_PATH)
			bobDunnVPNLoginID

		cleanup:
			println "URL Bob: " + bobDunnURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def response = appService.requestBuddy(richardQuinURL, """{
					"_embedded":{
						"user":{
							"firstName":"Bob ${timestamp}",
							"lastName":"Dun ${timestamp}",
							"emailAddress":"bob${timestamp}@dunn.net",
							"mobileNumber":"+${timestamp}2"
						}
					},
					"message":"Would you like to be my buddy?",
					"sendingStatus":"REQUESTED",
					"receivingStatus":"REQUESTED"
				}""", richardQuinPassword)
			richardQuinBobBuddyURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob ${timestamp}"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectRequestMessages) {
				bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
			}

		then:
			response.status == 200
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunnURL + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
			bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
					"properties":{
						"message":"Yes, great idea!"
					}
				}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks his anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0].nickname == "BD ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
			richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
					"properties":{
					}
				}""", richardQuinPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Classification engine detects a potential conflict for Bob'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
					"vpnLoginID":"${bobDunnVPNLoginID}",
					"categories": ["Gambling"],
					"url":"http://www.poker.com"
				}""")

		then:
			response.status == 200
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuinVPNLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
			response.status == 200
	}

	def 'Richard deletes his account'() {
		given:
		when:
			def response = appService.deleteUser(richardQuinURL, richardQuinPassword, "Goodbye friends! I deinstalled the Internet")

		then:
			response.status == 200
	}

	def 'Test what happens if the classification engine detects a potential conflict for Bob (second conflict message) when the buddy disconnect is not yet processed'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
					"vpnLoginID":"${bobDunnVPNLoginID}",
					"categories": ["news/media"],
					"url":"http://www.refdag.nl"
				}""")

		then:
			response.status == 200
	}

	def 'Bob checks his anonymous messages and will find a remove buddy message'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyDisconnectMessages) {
				bobDunnBuddyRemoveMessageProcessURL = response.responseData._embedded.buddyDisconnectMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
			response.responseData._embedded.buddyDisconnectMessages[0].message == "Goodbye friends! I deinstalled the Internet"
			response.responseData._embedded.buddyDisconnectMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bobDunnURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
			bobDunnBuddyRemoveMessageProcessURL.startsWith(response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href)
	}

	def 'Bob processes the remove buddy message'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(bobDunnBuddyRemoveMessageProcessURL, """{
					"properties":{
					}
				}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Bob checks his buddy list and will not find Richard there anymore'(){
		given:

		when:
			def response = appService.getBuddies(bobDunnURL, bobDunnPassword);

		then:
			response.status == 200
			response.responseData._embedded == null
	}

	def 'Bob checks his anonymous messages and the messages of Richard are no longer there'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url =~ /poker/
	}

	def 'Bob checks his direct messages and the messages of Richard are no longer there'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded == null || response.responseData._embedded.buddyConnectRequestMessages == null
	}
}