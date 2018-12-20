/****************************************************************************
 * Pushover
 * 				https://pushover.net
 ****************************************************************************/
def Pushover(user, token) {
	new PushoverClient(user:user, token:token)
}

class PushoverClient {
	def user
	def token

	def endpoint = new URL('https://api.pushover.net/1/messages.xml')
	def titleLimit = 100
	def messageLimit = 512

	def send = { title, message ->
		def lines = message.readLines().collect{ it + '\n' }
		def pages = []

		def currentPage = ''
		lines.each{ line ->
			if (currentPage.length() + line.length() >= messageLimit) {
				pages << currentPage
				currentPage = ''
			}
			currentPage += line
		}
		pages << currentPage

		// use title and make space for pagination
		def pageTitle = (title as String)

		// submit post requests
		pages = pages.findAll{ it.length() > 0 }

		pages.eachWithIndex(){ m, i ->
			if (i > 0) sleep(1000) // max 1 request / sec
			def t = pages.size() <= 1 ? pageTitle : "${pageTitle} (${i+1}/${pages.size()})"
			post(m, [title: t])
		}
	}

	def post = { text, parameters = [:] ->
		// inject default post parameters
		parameters << [token:token, user:user, message:text as String]

		// post and process response
		endpoint.post(parameters, [:])
	}
}



/****************************************************************************
 * PushBullet
 * 				https://www.pushbullet.com
 ****************************************************************************/
def PushBullet(apikey) {
	new PushBulletClient(apikey:apikey)
}

class PushBulletClient {
	def apikey

	def endpoint_pushes = new URL('https://api.pushbullet.com/v2/pushes')
	def endpoint_upload = new URL('https://api.pushbullet.com/v2/upload-request')

	def sendFile = { file_name, file_content, file_type, body = null, email = null ->
		def requestProperties = [Authorization: 'Basic '+apikey.getBytes().encodeBase64()]
		
		// prepare upload
		def uploadRequestData = [file_name: file_name, file_type: file_type]
		def response = new JsonSlurper().parseText(endpoint_upload.post(uploadRequestData, requestProperties).text)
		
		// build multipart/form-data -- the most horrible data format that we will never get rid off :(
		def MBD = '--'		// random -- before the boundry, or after, or not at all (there goes 4 hours of my life)
		def EOL = '\r\n'	// CR+NL required per spec?! I shit you not!

		def multiPartFormBoundary = '----------NCC-1701-E'
		def multiPartFormType = 'multipart/form-data; boundary='+multiPartFormBoundary
		def multiPartFormData = new ByteArrayOutputStream()

		multiPartFormData.withWriter('UTF-8') { out ->
			response.data.each{ key, value -> 
				out << MBD << multiPartFormBoundary << EOL
				out << 'Content-Disposition: form-data; name="' << key << '"' << EOL << EOL
				out << value << EOL
			}
			out << MBD << multiPartFormBoundary << EOL
			out << 'Content-Disposition: form-data; name="file"; filename="' << file_name << '"' << EOL
			out << 'Content-Type: ' << file_type << EOL << EOL
			out << file_content << EOL
			out << MBD << multiPartFormBoundary << MBD << EOL
		}

		// do upload to Amazon S3
		new URL(response.upload_url).post(multiPartFormData.toByteArray(), multiPartFormType, ['Content-Encoding':'gzip'])

		// push file link
		def pushFileData = [type: 'file', file_url: response.file_url, file_name: file_name, file_type: file_type, body: body, email: email]
		endpoint_pushes.post(pushFileData, requestProperties)
	}
}
