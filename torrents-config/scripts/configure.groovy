// filebot -script fn:configure

osdbUser = any{ osdbUser }{ console.printf('Enter OpenSubtitles username: '); console.readLine() }
osdbPwd = any{ osdbPwd }{ console.printf('Enter OpenSubtitles password: '); console.readLine() }

/* --------------------------------------------------------------------- */

if (osdbUser) {
	console.printf('Testing OpenSubtitles... ')
	WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, osdbUser, osdbPwd)
	WebServices.OpenSubtitles.login()
	console.printf('OK\n')
} else if (osdbUser.empty && osdbPwd.empty) {
	console.printf('Clear OpenSubtitles login details... ')
	WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, null, null)
	console.printf('OK\n')
}
