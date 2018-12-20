// filebot -script fn:ed2k /path/to/anime

/**
 * Calculate ed2k for all video files and output as ed2k links for AniDB
 *
 * e.g.
 * ed2k://|file|<filename, without spaces. unimportant>|<file size in bytes>|<ed2k hash>|/
 */

args.getFiles{ it.isVideo() }.each{ f ->
	def n = f.nameWithoutExtension.space('_') + '.' + f.extension
	def l = f.length()
	def h = VerificationUtilities.computeHash(f, HashType.ED2K);
	println """ed2k://|file|${n}|${l}|${h}|/"""
}
