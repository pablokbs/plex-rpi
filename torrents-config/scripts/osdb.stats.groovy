// filebot -script fn:osdb.stats

// e.g. [users_online_program:2483, xmlrpc_url:http://api.opensubtitles.org/xml-rpc, application:OpenSuber v0.2, download_limits:[limit_check_by:user_id, global_24h_download_limit:200, client_24h_download_count:0, client_download_quota:200, user_id:607030, client_24h_download_limit:200, client_ip:140.112.25.51], subs_downloads:1183798660, subs_subtitle_files:2621058, contact:admin@opensubtitles.org, total_subtitles_languages:67, users_online_total:3600, users_max_alltime:27449, xmlrpc_version:0.1, movies_total:164709, seconds:0.006, users_registered:1114040, usersloggedin:27, website_url:http://www.opensubtitles.org, movies_aka:421878]
def serverInfo = WebServices.OpenSubtitles.getServerInfo()

serverInfo.download_limits.each{ k, v ->
	println "${k} = ${v}"
}
