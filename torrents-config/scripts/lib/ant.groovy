
/**
 * Log into a remote host and run a given command.
 * 
 * e.g. 
 * sshexec(command: "ps", host: "filebot.sf.net", username: "rednoah", password: "correcthorsebatterystaple")
 */
def sshexec(param) {
	param << [trust: true] // always trust remote hosts
	param << [outputproperty: 'result'] // output as String
	
	tryLogCatch {
		def antBuilder = ant()
		antBuilder.sshexec(param)
		return antBuilder.project.properties.'result'
	}
}


/**
 * Send email via smtp.
 * 
 * e.g. 
 * sendmail(mailhost:'smtp.gmail.com', mailport:'587', ssl:'no', enableStartTLS:'yes', user:'rednoah@gmail.com', password:'correcthorsebatterystaple', from:'rednoah@gmail.com', to:'someone@gmail.com', subject:'Hello Ant World', message:'Dear Ant, ...')
 */
def sendmail(param) {
	def sender    = param.remove('from')
	def recipient = param.remove('to')

	tryLogCatch {
		ant().mail(param) {
			from(address:sender)
			to(address:recipient)
		}
	}
}


/**
 * Send email using gmail default settings.
 *
 * e.g.
 * sendGmail(subject:'Hello Ant World', message:'Dear Ant, ...', to:'someone@gmail.com', user:'rednoah@gmail.com', password:'correcthorsebatterystaple')
 */
def sendGmail(param) {
	if (!(param.password ==~ /\w{16}/)) {
		throw new IllegalArgumentException('Gmail password must be a 16-digit passcode: Application-specific password required')
	}

	param << [mailhost:'smtp.gmail.com', mailport:'587', ssl:'no', enableStartTLS:'yes']
	param << [from: param.user]

	sendmail(param)
}


/**
 * Upload files via SCP/SFTP
 *
 * e.g.
 * scp(host: 'filebot.net', username: 'rednoah', password: 'correcthorsebatterystaple', file: '/local/file', remoteFile: '/remote/file')
 * scp(host: 'filebot.net', username: 'rednoah', password: 'correcthorsebatterystaple', dir: '/local/dir', remoteDir: '/remote/dir')
 * scp(host: 'filebot.net', username: 'rednoah', password: 'correcthorsebatterystaple', file: '/remote/file', localDir: '/local/dir')
 * scp(host: 'filebot.net', username: 'rednoah', password: 'correcthorsebatterystaple', file: '/remote/file', localFile: '/local/file')
 */
def scp(param) {
	def param_scp = [:]
	def param_fileset = [:]

	def remotePath = { f ->
		if (f == null)
			throw new IllegalArgumentException('Remote path not defined: ' + param)

		return param.username + '@' + param.host + ':' + f.toString().replace('\\', '/')
	}

	// user[:password]@host:/directory/path
	if (param.file == null){
			param_scp.remoteTodir = remotePath(param.remoteDir)
			param_fileset.dir = param.dir as String
			param_fileset.includes = (param.includes == null) ? '**/*' : param.includes as String
	} else if (param.localDir == null && param.localFile == null) {
		if (param.remoteFile == null) {
			param_scp.remoteTodir = remotePath(param.remoteDir)
			param_scp.file = param.file as String
		} else {
			param_scp.remoteTofile = remotePath(param.remoteFile)
			param_scp.localFile = param.file as String
		}
	} else {
		if (param.localFile == null) {
			param_scp.localTodir = param.localDir as String
			param_scp.file = remotePath(param.file)
		} else {
			param_scp.localTofile = param.localFile as String
			param_scp.file = remotePath(param.file)
		}
	}
	
	if (param.keyfile == null) {
		param_scp.password = param.password as String 
	} else {
		param_scp.keyfile = param.keyfile as String
		param_scp.passphrase = (param.passphrase == null) ? '' : param.passphrase as String
	} 

	param_scp.verbose = (param.verbose == null) ? 'no' : param.verbose as String
	param_scp.trust = 'yes'
	param_scp.sftp = 'true'
	
	tryLogCatch {
		if (param_fileset) {
			ant().scp(param_scp) { fileset(param_fileset) }
		} else {
			ant().scp(param_scp)
		}
	}
}

def ant() {
	return new AntBuilder()
}
