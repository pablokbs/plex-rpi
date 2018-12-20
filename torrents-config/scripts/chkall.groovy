// filebot -script fn:chkall <folder>

/*
 * Check all sfv/md5/sha1 files and stop if a conflict is found
 */
args.getFiles().findAll { it.isVerification() }.each {
	if (!check(file:it))
		die("*ERROR*")
}
